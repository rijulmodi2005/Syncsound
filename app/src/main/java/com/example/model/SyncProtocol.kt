package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val SyncJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    classDiscriminator = "type"
}

@Serializable
sealed class SyncWsMessage {
    // Client -> Server
    @Serializable
    @SerialName("hello")
    data class Hello(
        val deviceId: String,
        val deviceName: String,
        val deviceType: DeviceType,
        val roomCode: String,
        val joinToken: String
    ) : SyncWsMessage()

    @Serializable
    @SerialName("sync_ping")
    data class SyncPing(
        val seq: Long,
        val t1ClientMs: Long
    ) : SyncWsMessage()

    @Serializable
    @SerialName("telemetry")
    data class TelemetryReport(
        val deviceId: String,
        val positionMs: Long,
        val driftMs: Long,
        val rttMs: Long,
        val clockOffsetMs: Long,
        val isPlaying: Boolean,
        val localVolume: Float,
        val isMuted: Boolean
    ) : SyncWsMessage()

    @Serializable
    @SerialName("local_volume")
    data class LocalVolumeUpdate(
        val deviceId: String,
        val volume: Float,
        val isMuted: Boolean
    ) : SyncWsMessage()

    // Server -> Client
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val accepted: Boolean,
        val reason: String = "",
        val assignedDeviceId: String = "",
        val serverTimeMs: Long = 0L,
        val roomInfo: RoomInfo? = null,
        val snapshot: PlaybackSnapshot? = null,
        val devices: List<ConnectedDevice> = emptyList(),
        val settings: RoomSettings = RoomSettings()
    ) : SyncWsMessage()

    @Serializable
    @SerialName("sync_pong")
    data class SyncPong(
        val seq: Long,
        val t1ClientMs: Long,
        val t2ServerRecvMs: Long,
        val t3ServerSendMs: Long
    ) : SyncWsMessage()

    @Serializable
    @SerialName("playback_cmd")
    data class PlaybackCommand(
        val command: PlaybackCommandType,
        val snapshot: PlaybackSnapshot,
        val serverTimeMs: Long
    ) : SyncWsMessage()

    @Serializable
    @SerialName("devices_update")
    data class DevicesUpdate(
        val devices: List<ConnectedDevice>,
        val settings: RoomSettings
    ) : SyncWsMessage()

    @Serializable
    @SerialName("device_control")
    data class DeviceControl(
        val targetDeviceId: String,
        val volume: Float? = null,
        val isMuted: Boolean? = null,
        val delayCalibrationMs: Long? = null,
        val kicked: Boolean = false,
        val reason: String = ""
    ) : SyncWsMessage()

    @Serializable
    @SerialName("room_closed")
    data class RoomClosed(
        val reason: String
    ) : SyncWsMessage()

    @Serializable
    @SerialName("test_chime")
    data class TestChime(
        val scheduledServerTimeMs: Long,
        val chimeUrl: String
    ) : SyncWsMessage()
}

@Serializable
enum class PlaybackCommandType {
    PLAY_SCHEDULED,
    PAUSE,
    SEEK_SCHEDULED,
    TRACK_CHANGE,
    STOP,
    VOLUME_SYNC
}
