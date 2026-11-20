package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ConnectedDevice
import com.example.model.DeviceRole
import com.example.model.DeviceType
import com.example.model.MusicSourceType
import com.example.model.PlaybackCommandType
import com.example.model.PlaybackSnapshot
import com.example.model.RoomInfo
import com.example.model.RoomSettings
import com.example.model.TrackItem
import com.example.sources.MusicSourcesHelper
import com.example.sync.AudioChimeGenerator
import com.example.sync.ClockSyncEngine
import com.example.sync.QrCodeUtils
import com.example.sync.SyncRoomServer
import com.example.sync.SyncWebSocketClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncSoundEngineTest {

    @Test
    fun `clock sync engine computes offset and rejects RTT spikes`() {
        var fakeLocalTime = 1_000_000L
        val engine = ClockSyncEngine(clockProvider = { fakeLocalTime })

        // Server is +500ms ahead of local clock, RTT = 20ms
        engine.recordPong(t1ClientMs = 1000L, t2ServerRecvMs = 1510L, t3ServerSendMs = 1510L, t4ClientRecvMs = 1020L)
        engine.recordPong(t1ClientMs = 2000L, t2ServerRecvMs = 2508L, t3ServerSendMs = 2508L, t4ClientRecvMs = 2016L)
        engine.recordPong(t1ClientMs = 3000L, t2ServerRecvMs = 3512L, t3ServerSendMs = 3512L, t4ClientRecvMs = 3024L)
        // Outlier spike RTT = 400ms with asymmetric delay
        engine.recordPong(t1ClientMs = 4000L, t2ServerRecvMs = 4850L, t3ServerSendMs = 4850L, t4ClientRecvMs = 4400L)

        assertEquals(500L, engine.clockOffsetMs)
        assertEquals(20L, engine.estimatedRttMs)
        assertEquals(1_000_500L, engine.serverTimeNowMs())
    }

    @Test
    fun `drift correction evaluates deadband speed nudge and hard seek`() {
        val engine = ClockSyncEngine()

        val inSync = engine.evaluateDriftCorrection(actualPositionMs = 10_020L, expectedPositionMs = 10_000L, toleranceMs = 45L)
        assertTrue(inSync is ClockSyncEngine.DriftCorrectionAction.InSync)

        val nudgeAhead = engine.evaluateDriftCorrection(actualPositionMs = 9_900L, expectedPositionMs = 10_000L, toleranceMs = 45L)
        assertTrue(nudgeAhead is ClockSyncEngine.DriftCorrectionAction.SpeedNudge)
        assertTrue((nudgeAhead as ClockSyncEngine.DriftCorrectionAction.SpeedNudge).playbackSpeed > 1.0f)

        val hardSeek = engine.evaluateDriftCorrection(actualPositionMs = 9_500L, expectedPositionMs = 10_000L, toleranceMs = 45L)
        assertTrue(hardSeek is ClockSyncEngine.DriftCorrectionAction.HardSeek)
        assertEquals(10_000L, (hardSeek as ClockSyncEngine.DriftCorrectionAction.HardSeek).targetPositionMs)
    }

    @Test
    fun `qr invite and youtube url parsing work accurately`() {
        val url = QrCodeUtils.buildInviteWebUrl("192.168.1.25", 8765, "58321", "abc123")
        val parsed = QrCodeUtils.parseQrPayload(url)
        assertNotNull(parsed)
        assertEquals("58321", parsed?.roomCode)
        assertEquals("192.168.1.25", parsed?.hostIp)
        assertEquals(8765, parsed?.port)
        assertEquals("abc123", parsed?.joinToken)

        assertEquals("dQw4w9WgXcQ", MusicSourcesHelper.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=test"))
        assertEquals("dQw4w9WgXcQ", MusicSourcesHelper.extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `end to end room server websocket sync and http 206 audio range streaming`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val chime = AudioChimeGenerator.ensureTestChimeFile(context.cacheDir)
        assertTrue(chime.exists() && chime.length() > 1000L)

        var latestDevices = emptyList<ConnectedDevice>()
        val server = SyncRoomServer(context) { latestDevices = it }

        val admin = ConnectedDevice(
            deviceId = "admin-1",
            name = "Admin Phone",
            deviceType = DeviceType.ANDROID_PHONE,
            role = DeviceRole.ADMIN
        )
        val roomInfo = RoomInfo(
            roomCode = "58321",
            roomName = "QA Studio",
            adminDeviceName = "Admin Phone",
            hostIp = "127.0.0.1",
            port = 0,
            joinToken = "secret99"
        )
        val port = server.start(0, roomInfo, RoomSettings(), admin)
        assertTrue(port > 0)

        try {
            // 1. Verify unauthorized token rejection
            val badClient = SyncWebSocketClient()
            val rejectedDeferred = CompletableDeferred<String>()
            val badJob = launch(start = CoroutineStart.UNDISPATCHED) {
                badClient.events.collect { ev ->
                    if (ev is SyncWebSocketClient.ClientEvent.Rejected) {
                        rejectedDeferred.complete(ev.reason)
                    }
                }
            }
            badClient.connect(
                SyncWebSocketClient.ConnectParams(
                    wsUrl = "ws://127.0.0.1:$port/ws",
                    deviceId = "intruder",
                    deviceName = "Bad Device",
                    deviceType = DeviceType.ANDROID_PHONE,
                    roomCode = "58321",
                    joinToken = "wrongtoken"
                )
            )
            val rejectReason = withTimeout(5000L) { rejectedDeferred.await() }
            assertTrue(rejectReason.contains("Invalid", ignoreCase = true))
            badJob.cancel()
            badClient.destroy()

            // 2. Connect valid speaker & receive Welcome + SyncPong + PlaybackCommand
            val speakerClient = SyncWebSocketClient()
            val connectedDeferred = CompletableDeferred<SyncWebSocketClient.ClientEvent.Connected>()
            val playbackDeferred = CompletableDeferred<SyncWebSocketClient.ClientEvent.PlaybackCmd>()
            val goodJob = launch(start = CoroutineStart.UNDISPATCHED) {
                speakerClient.events.collect { ev ->
                    when (ev) {
                        is SyncWebSocketClient.ClientEvent.Connected -> connectedDeferred.complete(ev)
                        is SyncWebSocketClient.ClientEvent.PlaybackCmd -> playbackDeferred.complete(ev)
                        else -> Unit
                    }
                }
            }
            speakerClient.connect(
                SyncWebSocketClient.ConnectParams(
                    wsUrl = "ws://127.0.0.1:$port/ws",
                    deviceId = "spk-tablet-1",
                    deviceName = "Android Tablet",
                    deviceType = DeviceType.ANDROID_TABLET,
                    roomCode = "58321",
                    joinToken = "secret99"
                )
            )
            val welcomeEvent = withTimeout(5000L) { connectedDeferred.await() }
            assertTrue(welcomeEvent.welcome.accepted)
            assertEquals("QA Studio", welcomeEvent.welcome.roomInfo?.roomName)
            assertTrue(latestDevices.any { it.deviceId == "spk-tablet-1" && it.isOnline })

            // Wait for at least 1 NTP pong calibration
            withTimeout(5000L) {
                speakerClient.telemetry.first { it.sampleCount >= 1 }
            }

            // Broadcast scheduled playback command
            val track = TrackItem(
                id = "test_chime",
                title = "Calibration Chime",
                artist = "SyncSound",
                sourceType = MusicSourceType.LOCAL_AUDIO,
                streamUrl = "/audio/test_chime?token=secret99"
            )
            val snap = PlaybackSnapshot(
                track = track,
                queue = listOf(track),
                currentIndex = 0,
                isPlaying = true,
                scheduledServerTimeMs = System.currentTimeMillis() + 300L,
                startPositionMs = 0L,
                masterVolume = 0.85f,
                revision = 1L
            )
            server.broadcastPlayback(PlaybackCommandType.PLAY_SCHEDULED, snap)
            val receivedCmd = withTimeout(5000L) { playbackDeferred.await() }
            assertEquals("Calibration Chime", receivedCmd.cmd.snapshot.track?.title)
            assertEquals(0.85f, receivedCmd.cmd.snapshot.masterVolume)

            // 3. Test HTTP 206 Partial Content Range streaming for audio seeking & /join/{code} web page
            val http = OkHttpClient()
            val webReq = Request.Builder()
                .url("http://127.0.0.1:$port/join/58321?token=secret99")
                .build()
            http.newCall(webReq).execute().use { resp ->
                assertEquals(200, resp.code)
                val html = resp.body?.string().orEmpty()
                assertTrue(html.contains("Turn Every Device Into a Speaker."))
            }
            val manifestReq = Request.Builder()
                .url("http://127.0.0.1:$port/manifest.json")
                .build()
            http.newCall(manifestReq).execute().use { resp ->
                assertEquals(200, resp.code)
                assertTrue(resp.body?.string().orEmpty().contains("SyncSound"))
            }
            val rangeReq = Request.Builder()
                .url("http://127.0.0.1:$port/audio/test_chime?token=secret99")
                .header("Range", "bytes=0-127")
                .build()
            http.newCall(rangeReq).execute().use { resp ->
                assertEquals(206, resp.code)
                val bodyBytes = resp.body?.bytes()
                assertNotNull(bodyBytes)
                assertEquals(128, bodyBytes?.size)
            }

            goodJob.cancel()
            speakerClient.destroy()
        } finally {
            server.destroy()
        }
    }
}
