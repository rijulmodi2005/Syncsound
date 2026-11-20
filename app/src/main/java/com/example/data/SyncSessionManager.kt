package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.audio.SyncAudioController
import com.example.audio.SyncPlaybackService
import com.example.model.AppSettings
import com.example.model.ConnectedDevice
import com.example.model.ConnectionMode
import com.example.model.ConnectionQuality
import com.example.model.DeviceRole
import com.example.model.DeviceType
import com.example.model.DiscoveredRoom
import com.example.model.MusicSourceType
import com.example.model.PlaybackCommandType
import com.example.model.PlaybackSnapshot
import com.example.model.PlaylistInfo
import com.example.model.RecentRoomEntry
import com.example.model.RoomInfo
import com.example.model.RoomSettings
import com.example.model.SessionConnectionState
import com.example.model.SyncHealthStatus
import com.example.model.SyncTelemetryState
import com.example.model.TrackItem
import com.example.sources.MusicSourcesHelper
import com.example.sources.SpotifyPkceHelper
import com.example.sources.SpotifySessionState
import com.example.sync.AudioChimeGenerator
import com.example.sync.NsdDiscoveryManager
import com.example.sync.SyncRoomServer
import com.example.sync.SyncWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID

class SyncSessionManager(
    private val context: Context,
    private val repository: SettingsAndHistoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val wsClient = SyncWebSocketClient()
    private val audioController = SyncAudioController(context, wsClient.clockSyncEngine)
    private val nsdManager = NsdDiscoveryManager(context)
    private val roomServer = SyncRoomServer(context) { updatedDevices ->
        _devices.value = updatedDevices
    }

    private val _connectionState = MutableStateFlow(SessionConnectionState.IDLE)
    val connectionState: StateFlow<SessionConnectionState> = _connectionState.asStateFlow()

    private val _activeRole = MutableStateFlow<DeviceRole?>(null)
    val activeRole: StateFlow<DeviceRole?> = _activeRole.asStateFlow()

    private val _roomInfo = MutableStateFlow<RoomInfo?>(null)
    val roomInfo: StateFlow<RoomInfo?> = _roomInfo.asStateFlow()

    private val _roomSettings = MutableStateFlow(RoomSettings())
    val roomSettings: StateFlow<RoomSettings> = _roomSettings.asStateFlow()

    private val _devices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val devices: StateFlow<List<ConnectedDevice>> = _devices.asStateFlow()

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _speakerVolume = MutableStateFlow(1.0f)
    val speakerVolume: StateFlow<Float> = _speakerVolume.asStateFlow()

    private val _speakerMuted = MutableStateFlow(false)
    val speakerMuted: StateFlow<Boolean> = _speakerMuted.asStateFlow()

    private val _speakerDelayMs = MutableStateFlow(0L)
    val speakerDelayMs: StateFlow<Long> = _speakerDelayMs.asStateFlow()

    private val _spotifyState = MutableStateFlow(SpotifySessionState())
    val spotifyState: StateFlow<SpotifySessionState> = _spotifyState.asStateFlow()

    val playerProgress = audioController.progress
    val telemetry: StateFlow<SyncTelemetryState> = wsClient.telemetry
    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = nsdManager.discoveredRooms

    private var ownDeviceId: String = ""
    private var spotifyCodeVerifier: String = ""

    init {
        scope.launch {
            ownDeviceId = repository.getOrCreateDeviceId()
            val settings = repository.settingsFlow.first()
            _speakerDelayMs.value = settings.defaultDelayOffsetMs
        }

        audioController.onTrackCompletedByAdmin = {
            if (_activeRole.value == DeviceRole.ADMIN) {
                nextTrack()
            }
        }

        audioController.onSpeakerTelemetryTick = { pos, drift, playing, label ->
            if (_activeRole.value == DeviceRole.SPEAKER) {
                wsClient.sendTelemetryReport(
                    deviceId = ownDeviceId,
                    positionMs = pos,
                    driftMs = drift,
                    isPlaying = playing,
                    localVolume = _speakerVolume.value,
                    isMuted = _speakerMuted.value,
                    correctionLabel = label
                )
            }
        }

        observeWsClientEvents()
    }

    fun startDiscovery() {
        nsdManager.startDiscovery()
    }

    fun stopDiscovery() {
        nsdManager.stopDiscovery()
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun generateRoomCode(): String {
        val rnd = SecureRandom()
        return (10000 + rnd.nextInt(90000)).toString()
    }

    private fun generateJoinToken(): String {
        val bytes = ByteArray(6)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun detectDeviceType(): DeviceType {
        val sw = context.resources.configuration.smallestScreenWidthDp
        return if (sw >= 600) DeviceType.ANDROID_TABLET else DeviceType.ANDROID_PHONE
    }

    private fun startMediaService() {
        runCatching {
            context.startService(Intent(context, SyncPlaybackService::class.java))
        }
        audioController.initialize()
    }

    fun createAndHostRoom(
        roomName: String,
        roomCode: String,
        connectionMode: ConnectionMode,
        remoteRelayUrl: String,
        settings: AppSettings
    ) {
        scope.launch {
            if (ownDeviceId.isBlank()) ownDeviceId = repository.getOrCreateDeviceId()
            startMediaService()
            audioController.setCalibrationAndSettings(
                delayOffsetMs = settings.defaultDelayOffsetMs,
                toleranceMs = _roomSettings.value.driftToleranceMs,
                autoCorrect = true
            )

            val hostIp = NsdDiscoveryManager.getLocalIpv4Address()
            val token = generateJoinToken()
            val cleanName = roomName.trim().ifBlank { "SyncSound Studio" }
            val now = System.currentTimeMillis()
            val expiresAt = now + 6 * 3600_000L

            val adminDevice = ConnectedDevice(
                deviceId = ownDeviceId,
                name = settings.deviceName.ifBlank { Build.MODEL ?: "Admin Phone" },
                deviceType = detectDeviceType(),
                role = DeviceRole.ADMIN,
                isOnline = true,
                rttMs = 0L,
                clockOffsetMs = 0L,
                driftMs = 0L,
                volume = 1.0f,
                isMuted = false,
                delayCalibrationMs = settings.defaultDelayOffsetMs,
                lastSeenEpochMs = now,
                healthStatus = SyncHealthStatus.ADMIN,
                connectionQuality = ConnectionQuality.EXCELLENT
            )

            val initialInfo = RoomInfo(
                roomCode = roomCode,
                roomName = cleanName,
                adminDeviceName = adminDevice.name,
                hostIp = hostIp,
                port = 8765,
                joinToken = token,
                connectionMode = connectionMode,
                remoteRelayUrl = remoteRelayUrl.trim(),
                createdAtEpochMs = now,
                expiresAtEpochMs = expiresAt
            )

            val initialRoomSettings = RoomSettings(expiresAtEpochMs = expiresAt)
            val boundPort = roomServer.start(8765, initialInfo, initialRoomSettings, adminDevice)
            val finalInfo = initialInfo.copy(port = boundPort)

            _roomInfo.value = finalInfo
            _roomSettings.value = initialRoomSettings
            _activeRole.value = DeviceRole.ADMIN
            _connectionState.value = SessionConnectionState.HOSTING
            _snapshot.value = PlaybackSnapshot()
            _statusBanner.value = "Room $roomCode live on $hostIp:$boundPort"

            nsdManager.registerRoomService(roomCode, cleanName, boundPort)

            repository.recordRecentRoom(
                RecentRoomEntry(
                    roomCode = roomCode,
                    roomName = cleanName,
                    hostIp = hostIp,
                    port = boundPort,
                    joinToken = token,
                    role = DeviceRole.ADMIN,
                    connectionMode = connectionMode,
                    remoteRelayUrl = remoteRelayUrl.trim()
                )
            )
        }
    }

    fun joinRoom(
        roomCode: String,
        deviceName: String,
        hostIp: String,
        port: Int,
        joinToken: String,
        remoteRelayUrl: String = ""
    ) {
        scope.launch {
            if (ownDeviceId.isBlank()) ownDeviceId = repository.getOrCreateDeviceId()
            startMediaService()
            val cleanCode = roomCode.trim()
            val cleanName = deviceName.trim().ifBlank { Build.MODEL ?: "Speaker" }

            // Resolve host from NSD if user only entered 5-digit code
            val nsdMatch = discoveredRooms.value.firstOrNull { it.roomCode == cleanCode }
            val targetHost = hostIp.trim().ifBlank { nsdMatch?.hostAddress.orEmpty() }
            val targetPort = if (hostIp.isNotBlank()) port else (nsdMatch?.port ?: 8765)

            val wsUrl = when {
                remoteRelayUrl.isNotBlank() -> {
                    val base = remoteRelayUrl.trim().removeSuffix("/")
                    if (base.startsWith("ws://") || base.startsWith("wss://")) base else "wss://$base"
                }
                targetHost.isNotBlank() -> "ws://$targetHost:$targetPort/ws"
                else -> {
                    _connectionState.value = SessionConnectionState.ERROR
                    _statusBanner.value = "Room $cleanCode not found on Wi-Fi. Scan QR or enter Host IP."
                    return@launch
                }
            }

            _connectionState.value = SessionConnectionState.CONNECTING
            _activeRole.value = DeviceRole.SPEAKER
            _roomInfo.value = RoomInfo(
                roomCode = cleanCode,
                roomName = nsdMatch?.roomName ?: "Connecting...",
                adminDeviceName = "--",
                hostIp = targetHost,
                port = targetPort,
                joinToken = joinToken,
                remoteRelayUrl = remoteRelayUrl
            )

            repository.updateSettings { it.copy(deviceName = cleanName) }
            val appSettings = repository.settingsFlow.first()
            _speakerDelayMs.value = appSettings.defaultDelayOffsetMs
            audioController.setCalibrationAndSettings(
                delayOffsetMs = appSettings.defaultDelayOffsetMs,
                toleranceMs = 45L,
                autoCorrect = true
            )

            wsClient.connect(
                SyncWebSocketClient.ConnectParams(
                    wsUrl = wsUrl,
                    deviceId = ownDeviceId,
                    deviceName = cleanName,
                    deviceType = detectDeviceType(),
                    roomCode = cleanCode,
                    joinToken = joinToken
                )
            )
        }
    }

    private fun observeWsClientEvents() {
        scope.launch {
            wsClient.events.collect { event ->
                when (event) {
                    is SyncWebSocketClient.ClientEvent.Connected -> {
                        val info = event.welcome.roomInfo
                        _roomInfo.value = info
                        _roomSettings.value = event.welcome.settings
                        _devices.value = event.welcome.devices
                        _connectionState.value = SessionConnectionState.CONNECTED
                        _statusBanner.value = "Synchronized with ${info?.roomName ?: "Room"}"

                        event.welcome.snapshot?.let { snap ->
                            _snapshot.value = snap
                            audioController.applySnapshot(
                                snapshot = snap,
                                speakerMode = true,
                                hostBaseHttpUrl = buildHostHttpBaseUrl(info)
                            )
                        }

                        if (info != null) {
                            repository.recordRecentRoom(
                                RecentRoomEntry(
                                    roomCode = info.roomCode,
                                    roomName = info.roomName,
                                    hostIp = info.hostIp,
                                    port = info.port,
                                    joinToken = info.joinToken,
                                    role = DeviceRole.SPEAKER,
                                    connectionMode = info.connectionMode,
                                    remoteRelayUrl = info.remoteRelayUrl
                                )
                            )
                        }
                    }

                    is SyncWebSocketClient.ClientEvent.Rejected -> {
                        _connectionState.value = SessionConnectionState.ERROR
                        _activeRole.value = null
                        _statusBanner.value = event.reason
                    }

                    is SyncWebSocketClient.ClientEvent.PlaybackCmd -> {
                        _snapshot.value = event.cmd.snapshot
                        audioController.applySnapshot(
                            snapshot = event.cmd.snapshot,
                            speakerMode = true,
                            hostBaseHttpUrl = buildHostHttpBaseUrl(_roomInfo.value)
                        )
                    }

                    is SyncWebSocketClient.ClientEvent.DevicesUpdated -> {
                        _devices.value = event.msg.devices
                        _roomSettings.value = event.msg.settings
                        audioController.setCalibrationAndSettings(
                            delayOffsetMs = _speakerDelayMs.value,
                            toleranceMs = event.msg.settings.driftToleranceMs,
                            autoCorrect = event.msg.settings.autoCorrectDrift
                        )
                    }

                    is SyncWebSocketClient.ClientEvent.DeviceControl -> {
                        val ctrl = event.msg
                        if (ctrl.targetDeviceId == ownDeviceId) {
                            if (ctrl.kicked) {
                                leaveRoom()
                                _statusBanner.value = ctrl.reason.ifBlank { "Removed by Room Admin" }
                            } else {
                                ctrl.volume?.let { _speakerVolume.value = it }
                                ctrl.isMuted?.let { _speakerMuted.value = it }
                                ctrl.delayCalibrationMs?.let {
                                    _speakerDelayMs.value = it
                                    audioController.setCalibrationAndSettings(
                                        delayOffsetMs = it,
                                        toleranceMs = _roomSettings.value.driftToleranceMs,
                                        autoCorrect = _roomSettings.value.autoCorrectDrift
                                    )
                                }
                                audioController.setVolume(_speakerVolume.value, _speakerMuted.value)
                            }
                        }
                    }

                    is SyncWebSocketClient.ClientEvent.TestChime -> {
                        val base = buildHostHttpBaseUrl(_roomInfo.value)
                        val fullChimeUrl = if (event.msg.chimeUrl.startsWith("/")) "$base${event.msg.chimeUrl}" else event.msg.chimeUrl
                        audioController.playTestChime(fullChimeUrl, event.msg.scheduledServerTimeMs, speakerMode = true)
                    }

                    is SyncWebSocketClient.ClientEvent.RoomClosed -> {
                        leaveRoom()
                        _statusBanner.value = event.reason
                    }

                    is SyncWebSocketClient.ClientEvent.ConnectionLost -> {
                        _connectionState.value = SessionConnectionState.RECONNECTING
                        _statusBanner.value = "Connection interrupted (${event.error}). Reconnecting..."
                    }
                }
            }
        }
    }

    private fun buildHostHttpBaseUrl(info: RoomInfo?): String {
        if (info == null || info.hostIp.isBlank()) return ""
        return "http://${info.hostIp}:${info.port}"
    }

    // --- ADMIN MUSIC SOURCES & PLAYBACK ---

    fun addLocalAudioUris(uris: List<Uri>) {
        if (_activeRole.value != DeviceRole.ADMIN || uris.isEmpty()) return
        scope.launch {
            val token = _roomInfo.value?.joinToken.orEmpty()
            val added = uris.map { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val item = MusicSourcesHelper.extractLocalTrack(context, uri)
                roomServer.registerUriAudio(item.id, uri, item.mimeType)
                item.copy(streamUrl = "/audio/${item.id}?token=$token")
            }
            appendTracksToQueueAndAutoSelect(added)
            _statusBanner.value = "Added ${added.size} local track(s) to queue"
        }
    }

    fun addDirectStreamUrl(url: String, customTitle: String) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        scope.launch {
            val res = MusicSourcesHelper.validateAndBuildStreamTrack(url, customTitle)
            res.onSuccess { track ->
                appendTracksToQueueAndAutoSelect(listOf(track))
                _statusBanner.value = "Stream verified: ${track.title}"
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Invalid stream URL"
            }
        }
    }

    fun addYouTubeUrlOrId(urlOrId: String) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        scope.launch {
            val res = MusicSourcesHelper.fetchYouTubeTrackViaOEmbed(urlOrId)
            res.onSuccess { track ->
                appendTracksToQueueAndAutoSelect(listOf(track))
                _statusBanner.value = "Added YouTube: ${track.title}"
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Invalid YouTube URL"
            }
        }
    }

    fun addTrackDirectly(track: TrackItem) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        appendTracksToQueueAndAutoSelect(listOf(track))
    }

    private fun appendTracksToQueueAndAutoSelect(tracks: List<TrackItem>) {
        val current = _snapshot.value
        val newQueue = current.queue + tracks
        val newIndex = if (current.currentIndex < 0 && newQueue.isNotEmpty()) 0 else current.currentIndex
        val newTrack = if (current.track == null && newIndex >= 0) newQueue[newIndex] else current.track
        val updated = current.copy(
            queue = newQueue,
            currentIndex = newIndex,
            track = newTrack,
            revision = current.revision + 1
        )
        broadcastAdminSnapshot(PlaybackCommandType.TRACK_CHANGE, updated)
    }

    fun selectQueueIndex(index: Int, autoPlay: Boolean = true) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val current = _snapshot.value
        if (index !in current.queue.indices) return
        val leadTime = 360L
        val targetStart = if (autoPlay) System.currentTimeMillis() + leadTime else 0L
        val updated = current.copy(
            currentIndex = index,
            track = current.queue[index],
            isPlaying = autoPlay,
            scheduledServerTimeMs = targetStart,
            startPositionMs = 0L,
            revision = current.revision + 1
        )
        broadcastAdminSnapshot(
            if (autoPlay) PlaybackCommandType.PLAY_SCHEDULED else PlaybackCommandType.TRACK_CHANGE,
            updated
        )
    }

    fun removeQueueIndex(index: Int) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val current = _snapshot.value
        if (index !in current.queue.indices) return
        val mutable = current.queue.toMutableList()
        mutable.removeAt(index)
        val newIndex = when {
            mutable.isEmpty() -> -1
            index < current.currentIndex -> current.currentIndex - 1
            index == current.currentIndex -> minOf(index, mutable.lastIndex)
            else -> current.currentIndex
        }
        val newTrack = if (newIndex in mutable.indices) mutable[newIndex] else null
        val updated = current.copy(
            queue = mutable,
            currentIndex = newIndex,
            track = newTrack,
            isPlaying = if (newTrack == null) false else current.isPlaying,
            startPositionMs = if (index == current.currentIndex) 0L else audioController.currentPositionMs(),
            scheduledServerTimeMs = System.currentTimeMillis() + 300L,
            revision = current.revision + 1
        )
        broadcastAdminSnapshot(PlaybackCommandType.TRACK_CHANGE, updated)
    }

    fun loadPlaylistIntoQueue(playlist: PlaylistInfo, autoPlay: Boolean = true) {
        if (_activeRole.value != DeviceRole.ADMIN || playlist.tracks.isEmpty()) return
        val token = _roomInfo.value?.joinToken.orEmpty()
        val preparedTracks = playlist.tracks.map { track ->
            if (track.sourceType == MusicSourceType.LOCAL_AUDIO && track.localUriString.isNotBlank()) {
                roomServer.registerUriAudio(track.id, Uri.parse(track.localUriString), track.mimeType)
                track.copy(streamUrl = "/audio/${track.id}?token=$token")
            } else track
        }
        val updated = _snapshot.value.copy(
            queue = preparedTracks,
            currentIndex = 0,
            track = preparedTracks.first(),
            isPlaying = autoPlay,
            scheduledServerTimeMs = if (autoPlay) System.currentTimeMillis() + 380L else 0L,
            startPositionMs = 0L,
            revision = _snapshot.value.revision + 1
        )
        broadcastAdminSnapshot(PlaybackCommandType.PLAY_SCHEDULED, updated)
    }

    fun saveCurrentQueueAsPlaylist(name: String) {
        val clean = name.trim().ifBlank { return }
        val currentQueue = _snapshot.value.queue
        if (currentQueue.isEmpty()) return
        scope.launch {
            val existing = repository.playlistsFlow.first()
            val newPl = PlaylistInfo(
                id = "pl-${UUID.randomUUID().toString().take(8)}",
                name = clean,
                tracks = currentQueue
            )
            repository.savePlaylists(listOf(newPl) + existing)
            _statusBanner.value = "Saved playlist \"$clean\""
        }
    }

    fun togglePlayPause() {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val current = _snapshot.value
        if (current.track == null) return
        if (current.isPlaying) {
            val pausedPos = audioController.currentPositionMs()
            val updated = current.copy(
                isPlaying = false,
                startPositionMs = pausedPos,
                scheduledServerTimeMs = 0L,
                revision = current.revision + 1
            )
            broadcastAdminSnapshot(PlaybackCommandType.PAUSE, updated)
        } else {
            val resumePos = audioController.currentPositionMs().takeIf { it > 0L } ?: current.startPositionMs
            val updated = current.copy(
                isPlaying = true,
                startPositionMs = resumePos,
                scheduledServerTimeMs = System.currentTimeMillis() + 360L,
                revision = current.revision + 1
            )
            broadcastAdminSnapshot(PlaybackCommandType.PLAY_SCHEDULED, updated)
        }
    }

    fun stopPlayback() {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val current = _snapshot.value
        val updated = current.copy(
            isPlaying = false,
            startPositionMs = 0L,
            scheduledServerTimeMs = 0L,
            revision = current.revision + 1
        )
        broadcastAdminSnapshot(PlaybackCommandType.STOP, updated)
    }

    fun nextTrack() {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val current = _snapshot.value
        if (current.queue.isEmpty()) return
        val nextIdx = (current.currentIndex + 1) % current.queue.size
        selectQueueIndex(nextIdx, autoPlay = true)
    }

    fun previousTrack() {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val current = _snapshot.value
        if (current.queue.isEmpty()) return
        val prevIdx = if (current.currentIndex <= 0) current.queue.lastIndex else current.currentIndex - 1
        selectQueueIndex(prevIdx, autoPlay = true)
    }

    fun seekTo(positionMs: Long) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val current = _snapshot.value
        val clamped = positionMs.coerceAtLeast(0L)
        val updated = current.copy(
            startPositionMs = clamped,
            scheduledServerTimeMs = if (current.isPlaying) System.currentTimeMillis() + 320L else 0L,
            revision = current.revision + 1
        )
        broadcastAdminSnapshot(PlaybackCommandType.SEEK_SCHEDULED, updated)
    }

    fun seekBy(deltaMs: Long) {
        val currentPos = audioController.currentPositionMs()
        seekTo((currentPos + deltaMs).coerceAtLeast(0L))
    }

    fun setMasterVolume(volume: Float) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val clamped = volume.coerceIn(0f, 1f)
        val current = _snapshot.value
        val updated = current.copy(masterVolume = clamped, revision = current.revision + 1)
        broadcastAdminSnapshot(PlaybackCommandType.VOLUME_SYNC, updated)
    }

    fun setAdminLocalVolume(volume: Float, muted: Boolean) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        audioController.setVolume(volume, muted)
        _devices.value.firstOrNull { it.deviceId == ownDeviceId }?.let { admin ->
            roomServer.updateAdminDevice(admin.copy(volume = volume, isMuted = muted))
        }
    }

    fun setDeviceVolume(deviceId: String, volume: Float) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        if (deviceId == ownDeviceId) {
            val muted = _devices.value.firstOrNull { it.deviceId == ownDeviceId }?.isMuted ?: false
            setAdminLocalVolume(volume, muted)
        } else {
            roomServer.controlDevice(targetDeviceId = deviceId, volume = volume.coerceIn(0f, 1f))
        }
    }

    fun toggleDeviceMute(deviceId: String) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val dev = _devices.value.firstOrNull { it.deviceId == deviceId } ?: return
        val nextMuted = !dev.isMuted
        if (deviceId == ownDeviceId) {
            setAdminLocalVolume(dev.volume, nextMuted)
        } else {
            roomServer.controlDevice(targetDeviceId = deviceId, isMuted = nextMuted)
        }
    }

    fun setDeviceDelayCalibration(deviceId: String, delayMs: Long) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val clamped = delayMs.coerceIn(-250L, 250L)
        if (deviceId == ownDeviceId) {
            audioController.setCalibrationAndSettings(
                delayOffsetMs = clamped,
                toleranceMs = _roomSettings.value.driftToleranceMs,
                autoCorrect = _roomSettings.value.autoCorrectDrift
            )
            _devices.value.firstOrNull { it.deviceId == ownDeviceId }?.let { admin ->
                roomServer.updateAdminDevice(admin.copy(delayCalibrationMs = clamped))
            }
        } else {
            roomServer.controlDevice(targetDeviceId = deviceId, delayCalibrationMs = clamped)
        }
    }

    fun kickDevice(deviceId: String) {
        if (_activeRole.value != DeviceRole.ADMIN || deviceId == ownDeviceId) return
        roomServer.kickDevice(deviceId)
    }

    fun updateRoomSettings(transform: (RoomSettings) -> RoomSettings) {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val updated = transform(_roomSettings.value)
        _roomSettings.value = updated
        roomServer.updateRoomSettings(updated)
    }

    fun triggerSpeakerTestChime() {
        if (_activeRole.value != DeviceRole.ADMIN) return
        val scheduled = System.currentTimeMillis() + 400L
        val chimeFile = AudioChimeGenerator.ensureTestChimeFile(context.cacheDir)
        roomServer.registerFileAudio("test_chime", chimeFile, "audio/wav")
        roomServer.broadcastTestChime(scheduled)
        audioController.playTestChime(Uri.fromFile(chimeFile).toString(), scheduled, speakerMode = false)
        _statusBanner.value = "Playing synchronized speaker test chime across all devices"
    }

    private fun broadcastAdminSnapshot(command: PlaybackCommandType, snapshot: PlaybackSnapshot) {
        _snapshot.value = snapshot
        roomServer.broadcastPlayback(command, snapshot)
        audioController.applySnapshot(snapshot, speakerMode = false)
    }

    // --- SPEAKER CONTROLS ---

    fun setSpeakerLocalVolume(volume: Float) {
        if (_activeRole.value != DeviceRole.SPEAKER) return
        if (!_roomSettings.value.allowLocalVolumeOverride) {
            _statusBanner.value = "Volume locked by Room Admin"
            return
        }
        val clamped = volume.coerceIn(0f, 1f)
        _speakerVolume.value = clamped
        audioController.setVolume(clamped, _speakerMuted.value)
        wsClient.sendLocalVolumeUpdate(ownDeviceId, clamped, _speakerMuted.value)
    }

    fun toggleSpeakerMute() {
        if (_activeRole.value != DeviceRole.SPEAKER) return
        val next = !_speakerMuted.value
        _speakerMuted.value = next
        audioController.setVolume(_speakerVolume.value, next)
        wsClient.sendLocalVolumeUpdate(ownDeviceId, _speakerVolume.value, next)
    }

    fun setSpeakerLocalDelay(delayMs: Long) {
        val clamped = delayMs.coerceIn(-250L, 250L)
        _speakerDelayMs.value = clamped
        audioController.setCalibrationAndSettings(
            delayOffsetMs = clamped,
            toleranceMs = _roomSettings.value.driftToleranceMs,
            autoCorrect = _roomSettings.value.autoCorrectDrift
        )
    }

    fun resyncSpeakerNow() {
        if (_activeRole.value != DeviceRole.SPEAKER) return
        audioController.applySnapshot(
            snapshot = _snapshot.value,
            speakerMode = true,
            hostBaseHttpUrl = buildHostHttpBaseUrl(_roomInfo.value)
        )
        _statusBanner.value = "Re-synchronized playback timeline"
    }

    // --- SPOTIFY OAUTH PKCE ---

    fun prepareSpotifyLoginUri(clientId: String): Uri? {
        if (clientId.isBlank()) {
            _statusBanner.value = "Enter your Spotify Client ID in Settings first."
            return null
        }
        spotifyCodeVerifier = SpotifyPkceHelper.generateCodeVerifier()
        val challenge = SpotifyPkceHelper.generateCodeChallenge(spotifyCodeVerifier)
        return SpotifyPkceHelper.buildAuthorizeUri(clientId, challenge)
    }

    fun handleSpotifyCallbackUri(uri: Uri, clientId: String) {
        val code = uri.getQueryParameter("code") ?: return
        if (spotifyCodeVerifier.isBlank() || clientId.isBlank()) return
        scope.launch {
            val res = SpotifyPkceHelper.exchangeCodeAndLoadProfile(clientId, code, spotifyCodeVerifier)
            res.onSuccess { state ->
                _spotifyState.value = state
                _statusBanner.value = state.statusMessage
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Spotify authorization failed"
            }
        }
    }

    fun syncSpotifyCurrentTrack() {
        val token = _spotifyState.value.accessToken
        if (token.isBlank()) return
        scope.launch {
            val track = SpotifyPkceHelper.fetchCurrentlyPlaying(token)
            if (track != null) {
                addTrackDirectly(track)
                _statusBanner.value = "Synced Spotify track: ${track.title}"
            } else {
                _statusBanner.value = "No active Spotify playback found on your account."
            }
        }
    }

    fun closeOrLeaveRoom() {
        if (_activeRole.value == DeviceRole.ADMIN) {
            roomServer.closeRoom()
            nsdManager.unregisterRoomService()
        } else {
            wsClient.disconnect()
        }
        leaveRoom()
    }

    private fun leaveRoom() {
        audioController.applySnapshot(PlaybackSnapshot(), speakerMode = false)
        _activeRole.value = null
        _roomInfo.value = null
        _devices.value = emptyList()
        _snapshot.value = PlaybackSnapshot()
        _connectionState.value = SessionConnectionState.IDLE
    }

    fun destroy() {
        closeOrLeaveRoom()
        roomServer.destroy()
        wsClient.destroy()
        audioController.release()
        nsdManager.stopDiscovery()
        scope.cancel()
    }
}
