package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.SyncAudioController
import com.example.model.ConnectedDevice
import com.example.model.MusicSourceType
import com.example.model.PlaybackSnapshot
import com.example.model.PlaylistInfo
import com.example.model.RoomInfo
import com.example.model.RoomSettings
import com.example.model.TrackItem
import com.example.sources.SpotifySessionState
import com.example.ui.components.EqualizerWaveform
import com.example.ui.components.RoomQrShareDialog
import com.example.ui.components.YouTubeEmbedPlayer
import com.example.ui.components.formatDurationMs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    roomInfo: RoomInfo?,
    roomSettings: RoomSettings,
    devices: List<ConnectedDevice>,
    snapshot: PlaybackSnapshot,
    progress: SyncAudioController.PlayerUiProgress,
    playlists: List<PlaylistInfo>,
    youtubeSearchResults: List<TrackItem>,
    youtubeSearching: Boolean,
    youtubeSearchError: String?,
    spotifyState: SpotifySessionState,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onAddLocalUris: (List<Uri>) -> Unit,
    onAddStreamUrl: (String, String) -> Unit,
    onAddYouTubeUrl: (String) -> Unit,
    onSearchYouTube: (String) -> Unit,
    onAddTrack: (TrackItem) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onSavePlaylist: (String) -> Unit,
    onLoadPlaylist: (PlaylistInfo) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onConnectSpotify: () -> Unit,
    onSyncSpotifyTrack: () -> Unit,
    onDeviceVolumeChange: (String, Float) -> Unit,
    onToggleDeviceMute: (String) -> Unit,
    onDeviceDelayChange: (String, Long) -> Unit,
    onKickDevice: (String) -> Unit,
    onTestAllSpeakers: () -> Unit,
    onUpdateRoomSettings: ((RoomSettings) -> RoomSettings) -> Unit,
    onShareInvite: (String) -> Unit,
    onCloseRoom: () -> Unit
) {
    var showQrDialog by remember { mutableStateOf(false) }
    var sectionIndex by remember { mutableIntStateOf(0) }

    if (showQrDialog && roomInfo != null) {
        RoomQrShareDialog(
            roomInfo = roomInfo,
            onShareClick = { url ->
                onShareInvite("Join SyncSound Room \"${roomInfo.roomName}\" · Code: ${roomInfo.roomCode}\n$url")
            },
            onDismiss = { showQrDialog = false }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(roomInfo?.roomName ?: "Admin Studio", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Code ${roomInfo?.roomCode ?: "-----"} · ${devices.count { it.isOnline }}/${devices.size} online",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showQrDialog = true },
                        modifier = Modifier.testTag("admin_qr_btn")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Show room QR code")
                    }
                    TextButton(
                        onClick = onCloseRoom,
                        modifier = Modifier.testTag("close_room_btn")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Close", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 840.dp

            val playerCard: @Composable () -> Unit = {
                AdminNowPlayingCard(
                    snapshot = snapshot,
                    progress = progress,
                    onTogglePlayPause = onTogglePlayPause,
                    onStop = onStop,
                    onNext = onNext,
                    onPrev = onPrev,
                    onSeekTo = onSeekTo,
                    onSeekBy = onSeekBy,
                    onMasterVolumeChange = onMasterVolumeChange
                )
            }

            val sourcesSection: @Composable () -> Unit = {
                AdminSourcesAndQueueSection(
                    queue = snapshot.queue,
                    currentIndex = snapshot.currentIndex,
                    playlists = playlists,
                    youtubeSearchResults = youtubeSearchResults,
                    youtubeSearching = youtubeSearching,
                    youtubeSearchError = youtubeSearchError,
                    spotifyState = spotifyState,
                    onAddLocalUris = onAddLocalUris,
                    onAddStreamUrl = onAddStreamUrl,
                    onAddYouTubeUrl = onAddYouTubeUrl,
                    onSearchYouTube = onSearchYouTube,
                    onAddTrack = onAddTrack,
                    onSelectQueueIndex = onSelectQueueIndex,
                    onRemoveQueueIndex = onRemoveQueueIndex,
                    onSavePlaylist = onSavePlaylist,
                    onLoadPlaylist = onLoadPlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onConnectSpotify = onConnectSpotify,
                    onSyncSpotifyTrack = onSyncSpotifyTrack
                )
            }

            val devicesSection: @Composable () -> Unit = {
                AdminDevicesSection(
                    devices = devices,
                    roomSettings = roomSettings,
                    onDeviceVolumeChange = onDeviceVolumeChange,
                    onToggleDeviceMute = onToggleDeviceMute,
                    onDeviceDelayChange = onDeviceDelayChange,
                    onKickDevice = onKickDevice,
                    onTestAllSpeakers = onTestAllSpeakers,
                    onUpdateRoomSettings = onUpdateRoomSettings
                )
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        playerCard()
                        sourcesSection()
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        devicesSection()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    playerCard()

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = sectionIndex == 0,
                            onClick = { sectionIndex = 0 },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            modifier = Modifier.testTag("admin_tab_sources")
                        ) {
                            Text("Sources & Queue")
                        }
                        SegmentedButton(
                            selected = sectionIndex == 1,
                            onClick = { sectionIndex = 1 },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            modifier = Modifier.testTag("admin_tab_devices")
                        ) {
                            Text("Speakers (${devices.size})")
                        }
                    }

                    if (sectionIndex == 0) {
                        sourcesSection()
                    } else {
                        devicesSection()
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminNowPlayingCard(
    snapshot: PlaybackSnapshot,
    progress: SyncAudioController.PlayerUiProgress,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onMasterVolumeChange: (Float) -> Unit
) {
    val track = snapshot.track
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (track?.artworkUrl?.isNotBlank() == true) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = "Track artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_default_album),
                        contentDescription = "Default album artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = track?.sourceType?.name?.replace("_", " ") ?: "NO TRACK SELECTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = track?.title ?: "Add Music Below",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = track?.artist ?: "Local Audio · Stream URL · YouTube · Spotify",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            if (track?.sourceType == MusicSourceType.YOUTUBE && track.externalId.isNotBlank()) {
                YouTubeEmbedPlayer(
                    videoId = track.externalId,
                    isPlaying = snapshot.isPlaying,
                    startSeconds = (snapshot.startPositionMs / 1000L).toInt()
                )
            } else {
                EqualizerWaveform(
                    isPlaying = snapshot.isPlaying && progress.isPlaying,
                    modifier = Modifier.fillMaxWidth()
                )

                val duration = progress.durationMs.coerceAtLeast(track?.durationMs ?: 0L).coerceAtLeast(1L)
                Slider(
                    value = progress.positionMs.coerceIn(0L, duration).toFloat(),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    enabled = track != null,
                    modifier = Modifier.testTag("admin_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDurationMs(progress.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatDurationMs(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.testTag("admin_prev_btn")) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track")
                }
                IconButton(onClick = { onSeekBy(-10_000L) }, modifier = Modifier.testTag("admin_seek_back_btn")) {
                    Icon(Icons.Default.Replay10, contentDescription = "Seek back 10s")
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .testTag("admin_play_pause_btn")
                ) {
                    Icon(
                        if (snapshot.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (snapshot.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { onSeekBy(10_000L) }, modifier = Modifier.testTag("admin_seek_fwd_btn")) {
                    Icon(Icons.Default.Forward10, contentDescription = "Seek forward 10s")
                }
                IconButton(onClick = onNext, modifier = Modifier.testTag("admin_next_btn")) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next track")
                }
                IconButton(onClick = onStop, modifier = Modifier.testTag("admin_stop_btn")) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop playback")
                }
            }

            // Master Volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Master volume",
                    tint = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = snapshot.masterVolume,
                    onValueChange = onMasterVolumeChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_master_volume_slider")
                )
                Text(
                    "Master ${(snapshot.masterVolume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
