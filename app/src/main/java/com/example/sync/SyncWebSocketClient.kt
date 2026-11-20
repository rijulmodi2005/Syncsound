package com.example.sync

import com.example.model.DeviceType
import com.example.model.SyncHealthStatus
import com.example.model.SyncJson
import com.example.model.SyncTelemetryState
import com.example.model.SyncWsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SyncWebSocketClient(
    val clockSyncEngine: ClockSyncEngine = ClockSyncEngine()
) {
    sealed class ClientEvent {
        data class Connected(val welcome: SyncWsMessage.Welcome) : ClientEvent()
        data class Rejected(val reason: String) : ClientEvent()
        data class PlaybackCmd(val cmd: SyncWsMessage.PlaybackCommand) : ClientEvent()
        data class DevicesUpdated(val msg: SyncWsMessage.DevicesUpdate) : ClientEvent()
        data class DeviceControl(val msg: SyncWsMessage.DeviceControl) : ClientEvent()
        data class TestChime(val msg: SyncWsMessage.TestChime) : ClientEvent()
        data class RoomClosed(val reason: String) : ClientEvent()
        data class ConnectionLost(val willRetry: Boolean, val error: String) : ClientEvent()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private val pingSeq = AtomicLong(0L)

    @Volatile
    private var manualDisconnect = false

    @Volatile
    private var activeParams: ConnectParams? = null

    data class ConnectParams(
        val wsUrl: String,
        val deviceId: String,
        val deviceName: String,
        val deviceType: DeviceType,
        val roomCode: String,
        val joinToken: String
    )

    private val _events = MutableSharedFlow<ClientEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ClientEvent> = _events.asSharedFlow()

    private val _telemetry = MutableStateFlow(SyncTelemetryState())
    val telemetry: StateFlow<SyncTelemetryState> = _telemetry.asStateFlow()

    fun connect(params: ConnectParams) {
        manualDisconnect = false
        activeParams = params
        reconnectJob?.cancel()
        clockSyncEngine.reset()
        openSocket(params)
    }

    private fun openSocket(params: ConnectParams) {
        pingJob?.cancel()
        runCatching { webSocket?.close(1000, "Reconnecting") }

        val request = Request.Builder().url(params.wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = SyncWsMessage.Hello(
                    deviceId = params.deviceId,
                    deviceName = params.deviceName,
                    deviceType = params.deviceType,
                    roomCode = params.roomCode,
                    joinToken = params.joinToken
                )
                webSocket.send(SyncJson.encodeToString(SyncWsMessage.serializer(), hello))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { SyncJson.decodeFromString<SyncWsMessage>(text) }.getOrNull() ?: return
                handleServerMessage(msg)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                pingJob?.cancel()
                if (!manualDisconnect) {
                    _telemetry.value = _telemetry.value.copy(
                        healthStatus = SyncHealthStatus.DISCONNECTED,
                        lastCorrectionLabel = "Reconnecting..."
                    )
                    _events.tryEmit(
                        ClientEvent.ConnectionLost(
                            willRetry = true,
                            error = t.message ?: "Network interrupted"
                        )
                    )
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                pingJob?.cancel()
                if (!manualDisconnect && code != 1000) {
                    _events.tryEmit(ClientEvent.ConnectionLost(willRetry = true, error = reason))
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleServerMessage(msg: SyncWsMessage) {
        when (msg) {
            is SyncWsMessage.Welcome -> {
                if (msg.accepted) {
                    _events.tryEmit(ClientEvent.Connected(msg))
                    startPingLoop()
                } else {
                    manualDisconnect = true
                    _events.tryEmit(ClientEvent.Rejected(msg.reason))
                }
            }

            is SyncWsMessage.SyncPong -> {
                clockSyncEngine.recordPong(
                    t1ClientMs = msg.t1ClientMs,
                    t2ServerRecvMs = msg.t2ServerRecvMs,
                    t3ServerSendMs = msg.t3ServerSendMs
                )
                val rtt = clockSyncEngine.estimatedRttMs
                val quality = ClockSyncEngine.classifyConnectionQuality(rtt, isOnline = true)
                _telemetry.value = _telemetry.value.copy(
                    rttMs = rtt,
                    clockOffsetMs = clockSyncEngine.clockOffsetMs,
                    sampleCount = clockSyncEngine.sampleCount,
                    connectionQuality = quality,
                    healthStatus = SyncHealthStatus.SYNCHRONIZED
                )
            }

            is SyncWsMessage.PlaybackCommand -> {
                _events.tryEmit(ClientEvent.PlaybackCmd(msg))
            }

            is SyncWsMessage.DevicesUpdate -> {
                _events.tryEmit(ClientEvent.DevicesUpdated(msg))
            }

            is SyncWsMessage.DeviceControl -> {
                if (msg.kicked) {
                    manualDisconnect = true
                }
                _events.tryEmit(ClientEvent.DeviceControl(msg))
            }

            is SyncWsMessage.TestChime -> {
                _events.tryEmit(ClientEvent.TestChime(msg))
            }

            is SyncWsMessage.RoomClosed -> {
                manualDisconnect = true
                _events.tryEmit(ClientEvent.RoomClosed(msg.reason))
            }

            else -> Unit
        }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            // Burst 5 rapid pings for sub-second clock calibration on join
            repeat(5) {
                if (!isActive) return@launch
                sendSyncPing()
                delay(120L)
            }
            while (isActive) {
                delay(3000L)
                sendSyncPing()
            }
        }
    }

    private fun sendSyncPing() {
        val ping = SyncWsMessage.SyncPing(
            seq = pingSeq.incrementAndGet(),
            t1ClientMs = System.currentTimeMillis()
        )
        webSocket?.send(SyncJson.encodeToString(SyncWsMessage.serializer(), ping))
    }

    fun sendTelemetryReport(
        deviceId: String,
        positionMs: Long,
        driftMs: Long,
        isPlaying: Boolean,
        localVolume: Float,
        isMuted: Boolean,
        correctionLabel: String
    ) {
        val rtt = clockSyncEngine.estimatedRttMs
        _telemetry.value = _telemetry.value.copy(
            currentDriftMs = driftMs,
            rttMs = rtt,
            clockOffsetMs = clockSyncEngine.clockOffsetMs,
            connectionQuality = ClockSyncEngine.classifyConnectionQuality(rtt, true),
            healthStatus = ClockSyncEngine.classifyHealthStatus(
                isOnline = true,
                isAdmin = false,
                driftMs = driftMs
            ),
            lastCorrectionLabel = correctionLabel
        )
        val report = SyncWsMessage.TelemetryReport(
            deviceId = deviceId,
            positionMs = positionMs,
            driftMs = driftMs,
            rttMs = rtt,
            clockOffsetMs = clockSyncEngine.clockOffsetMs,
            isPlaying = isPlaying,
            localVolume = localVolume,
            isMuted = isMuted
        )
        webSocket?.send(SyncJson.encodeToString(SyncWsMessage.serializer(), report))
    }

    fun sendLocalVolumeUpdate(deviceId: String, volume: Float, isMuted: Boolean) {
        val msg = SyncWsMessage.LocalVolumeUpdate(deviceId = deviceId, volume = volume, isMuted = isMuted)
        webSocket?.send(SyncJson.encodeToString(SyncWsMessage.serializer(), msg))
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        val params = activeParams ?: return
        reconnectJob = scope.launch {
            val delays = listOf(1000L, 2000L, 4000L, 8000L)
            var attempt = 0
            while (isActive && !manualDisconnect) {
                delay(delays[minOf(attempt, delays.lastIndex)])
                attempt++
                if (!manualDisconnect) {
                    openSocket(params)
                    break
                }
            }
        }
    }

    fun disconnect() {
        manualDisconnect = true
        pingJob?.cancel()
        reconnectJob?.cancel()
        runCatching { webSocket?.close(1000, "User left room") }
        webSocket = null
        _telemetry.value = SyncTelemetryState()
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
