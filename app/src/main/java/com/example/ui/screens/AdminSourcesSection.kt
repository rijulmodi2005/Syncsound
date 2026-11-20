package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.PlaylistInfo
import com.example.model.TrackItem
import com.example.sources.SpotifySessionState
import com.example.ui.components.formatDurationMs

@Composable
fun AdminSourcesAndQueueSection(
    queue: List<TrackItem>,
    currentIndex: Int,
    playlists: List<PlaylistInfo>,
    youtubeSearchResults: List<TrackItem>,
    youtubeSearching: Boolean,
    youtubeSearchError: String?,
    spotifyState: SpotifySessionState,
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
    onSyncSpotifyTrack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Local", "Stream URL", "YouTube", "Spotify")

    var streamUrl by remember { mutableStateOf("") }
    var streamTitle by remember { mutableStateOf("") }
    var ytInput by remember { mutableStateOf("") }
    var ytSearchQuery by remember { mutableStateOf("") }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onAddLocalUris(uris)
        }
    )

    if (showSavePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showSavePlaylistDialog = false },
            title = { Text("Save Queue as Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistNameInput.isNotBlank()) {
                        onSavePlaylist(playlistNameInput)
                        playlistNameInput = ""
                        showSavePlaylistDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Music Sources", style = MaterialTheme.typography.titleLarge)

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    tabs.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label) },
                            modifier = Modifier.testTag("source_tab_$index")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        Button(
                            onClick = {
                                audioPicker.launch(arrayOf("audio/*"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("pick_local_audio_btn")
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select MP3 / WAV / M4A / FLAC Files")
                        }
                        Text(
                            "Audio files are streamed directly from this device over LAN HTTP 206 Range requests so connected Android & Web speakers buffer and seek in sync.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    1 -> {
                        OutlinedTextField(
                            value = streamUrl,
                            onValueChange = { streamUrl = it },
                            label = { Text("Direct Audio Stream URL (HTTP/HTTPS)") },
                            placeholder = { Text("https://example.com/stream.mp3") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stream_url_input")
                        )
                        OutlinedTextField(
                            value = streamTitle,
                            onValueChange = { streamTitle = it },
                            label = { Text("Track / Station Name (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (streamUrl.isNotBlank()) {
                                    onAddStreamUrl(streamUrl, streamTitle)
                                    streamUrl = ""
                                    streamTitle = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("add_stream_url_btn")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Validate & Add Stream")
                        }
                    }

                    2 -> {
                        OutlinedTextField(
                            value = ytInput,
                            onValueChange = { ytInput = it },
                            label = { Text("YouTube Video URL or 11-Char ID") },
                            placeholder = { Text("https://youtu.be/...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("youtube_url_input")
                        )
                        Button(
                            onClick = {
                                if (ytInput.isNotBlank()) {
                                    onAddYouTubeUrl(ytInput)
                                    ytInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("add_youtube_url_btn")
                        ) {
                            Icon(Icons.Default.OndemandVideo, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add YouTube Video to Queue")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = ytSearchQuery,
                                onValueChange = { ytSearchQuery = it },
                                label = { Text("Search YouTube (API Key in Settings)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { onSearchYouTube(ytSearchQuery) },
                                enabled = !youtubeSearching,
                                modifier = Modifier.height(52.dp)
                            ) {
                                if (youtubeSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = "Search YouTube")
                                }
                            }
                        }

                        youtubeSearchError?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }

                        youtubeSearchResults.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onAddTrack(item) }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = item.artworkUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(item.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Icon(Icons.Default.Add, contentDescription = "Add to queue")
                            }
                        }

                        Text(
                            stringResource(R.string.youtube_policy_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    3 -> {
                        if (!spotifyState.isConnected) {
                            Button(
                                onClick = onConnectSpotify,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("connect_spotify_btn")
                            ) {
                                Icon(Icons.Default.LibraryMusic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Authorize Spotify (OAuth PKCE)")
                            }
                        } else {
                            Text(
                                "${spotifyState.displayName} (${spotifyState.productTier.uppercase()})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Button(
                                onClick = onSyncSpotifyTrack,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sync Active Spotify Track")
                            }
                            if (spotifyState.playlists.isNotEmpty()) {
                                Text("Your Spotify Playlists", style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    spotifyState.playlists.take(4).forEach { (_, name) ->
                                        FilterChip(selected = false, onClick = onSyncSpotifyTrack, label = { Text(name) })
                                    }
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.spotify_policy_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Queue & Saved Playlists
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Session Queue (${queue.size})", style = MaterialTheme.typography.titleLarge)
                    }
                    if (queue.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showSavePlaylistDialog = true },
                            modifier = Modifier.testTag("save_playlist_btn")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                }

                if (queue.isEmpty()) {
                    Text(
                        "Queue is empty. Pick local MP3/WAV files, add a stream URL, or paste a YouTube link above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    queue.forEachIndexed { idx, track ->
                        val isCurrent = idx == currentIndex
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectQueueIndex(idx) }
                                .testTag("queue_item_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isCurrent) Icons.Default.PlayArrow else Icons.Default.AudioFile,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${track.artist} · ${track.sourceType.name} · ${formatDurationMs(track.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = { onRemoveQueueIndex(idx) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove from queue")
                                }
                            }
                        }
                    }
                }

                if (playlists.isNotEmpty()) {
                    Text("Saved Playlists", style = MaterialTheme.typography.titleMedium)
                    playlists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onLoadPlaylist(pl) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium)
                                Text("${pl.tracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                TextButton(onClick = { onLoadPlaylist(pl) }) { Text("Play") }
                                IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete playlist")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
