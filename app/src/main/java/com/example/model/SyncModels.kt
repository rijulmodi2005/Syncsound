package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceRole {
    ADMIN,
    SPEAKER
}

@Serializable
enum class DeviceType {
    ANDROID_PHONE,
    ANDROID_TABLET,
    WEB_BROWSER,
    DESKTOP
}

@Serializable
enum class SyncHealthStatus {
    ADMIN,          // Blue
    SYNCHRONIZED,   // Green
    DRIFT_WARNING,  // Yellow
    DISCONNECTED    // Red
}

@Serializable
enum class ConnectionQuality {
    EXCELLENT,  // RTT < 25ms
    GOOD,       // RTT < 60ms
    FAIR,       // RTT < 150ms
    POOR,       // RTT >= 150ms
    OFFLINE
}

@Serializable
enum class MusicSourceType {
    LOCAL_AUDIO,
    DIRECT_STREAM,
    YOUTUBE,
    SPOTIFY
}

@Serializable
enum class ConnectionMode {
    LAN_HOST,
    REMOTE_RELAY
}

@Serializable
data class TrackItem(
    val id: String,
    val title: String,
    val artist: String = "Unknown Artist",
    val durationMs: Long = 0L,
    val sourceType: MusicSourceType = MusicSourceType.LOCAL_AUDIO,
    val streamUrl: String = "",
    val localUriString: String = "",
    val externalId: String = "",
    val artworkUrl: String = "",
    val mimeType: String = "audio/mpeg",
    val isTestChime: Boolean = false
)

@Serializable
data class PlaylistInfo(
    val id: String,
    val name: String,
    val tracks: List<TrackItem> = emptyList(),
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
data class ConnectedDevice(
    val deviceId: String,
    val name: String,
    val deviceType: DeviceType,
    val role: DeviceRole,
    val isOnline: Boolean = true,
    val rttMs: Long = 0L,
    val clockOffsetMs: Long = 0L,
    val driftMs: Long = 0L,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val delayCalibrationMs: Long = 0L,
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val healthStatus: SyncHealthStatus = if (role == DeviceRole.ADMIN) SyncHealthStatus.ADMIN else SyncHealthStatus.SYNCHRONIZED,
    val connectionQuality: ConnectionQuality = ConnectionQuality.EXCELLENT
)

@Serializable
data class RoomSettings(
    val allowLocalVolumeOverride: Boolean = true,
    val driftToleranceMs: Long = 45L,
    val autoCorrectDrift: Boolean = true,
    val maxDevices: Int = 20,
    val expiresAtEpochMs: Long = System.currentTimeMillis() + 6 * 3600_000L
)

@Serializable
data class RoomInfo(
    val roomCode: String,
    val roomName: String,
    val adminDeviceName: String,
    val hostIp: String,
    val port: Int,
    val joinToken: String,
    val connectionMode: ConnectionMode = ConnectionMode.LAN_HOST,
    val remoteRelayUrl: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long = System.currentTimeMillis() + 6 * 3600_000L
)

@Serializable
data class PlaybackSnapshot(
    val track: TrackItem? = null,
    val queue: List<TrackItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val scheduledServerTimeMs: Long = 0L,
    val startPositionMs: Long = 0L,
    val masterVolume: Float = 1.0f,
    val revision: Long = 0L
)

@Serializable
data class RecentRoomEntry(
    val roomCode: String,
    val roomName: String,
    val hostIp: String,
    val port: Int,
    val joinToken: String,
    val role: DeviceRole,
    val connectionMode: ConnectionMode = ConnectionMode.LAN_HOST,
    val remoteRelayUrl: String = "",
    val lastJoinedEpochMs: Long = System.currentTimeMillis()
)

data class DiscoveredRoom(
    val roomCode: String,
    val roomName: String,
    val hostAddress: String,
    val port: Int,
    val serviceName: String
)

@Serializable
data class AppSettings(
    val isDarkTheme: Boolean = true,
    val deviceName: String = "Android Speaker",
    val defaultDelayOffsetMs: Long = 0L,
    val scheduleLeadTimeMs: Long = 350L,
    val preferLan: Boolean = true,
    val remoteRelayUrl: String = "",
    val spotifyClientId: String = "",
    val youtubeApiKey: String = ""
)

enum class SessionConnectionState {
    IDLE,
    HOSTING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class SyncTelemetryState(
    val rttMs: Long = 0L,
    val clockOffsetMs: Long = 0L,
    val currentDriftMs: Long = 0L,
    val sampleCount: Int = 0,
    val connectionQuality: ConnectionQuality = ConnectionQuality.OFFLINE,
    val healthStatus: SyncHealthStatus = SyncHealthStatus.DISCONNECTED,
    val lastCorrectionLabel: String = "Standby",
    val scheduledCountdownMs: Long = 0L
)
