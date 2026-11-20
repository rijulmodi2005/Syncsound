package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                SettingsSectionCard("Appearance & Identity") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dark Acoustic Theme", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Recommended for low-glare studio & party sessions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.isDarkTheme,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(isDarkTheme = checked) }
                            },
                            modifier = Modifier.testTag("theme_switch")
                        )
                    }

                    OutlinedTextField(
                        value = settings.deviceName,
                        onValueChange = { name -> onUpdateSettings { it.copy(deviceName = name) } },
                        label = { Text("Default Device Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_device_name_input")
                    )
                }

                SettingsSectionCard("Audio & Synchronization Engine") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Default Audio Offset", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${settings.defaultDelayOffsetMs} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.defaultDelayOffsetMs.toFloat(),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(defaultDelayOffsetMs = v.roundToInt().toLong()) }
                        },
                        valueRange = -250f..250f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prioritize Local Wi-Fi LAN", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Uses NSD mDNS & direct LAN sockets for 50–100ms target sync",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.preferLan,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(preferLan = checked) }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = settings.remoteRelayUrl,
                        onValueChange = { url -> onUpdateSettings { it.copy(remoteRelayUrl = url) } },
                        label = { Text("Default Remote Relay WebSocket URL") },
                        placeholder = { Text("wss://your-syncsound-relay.example.com/ws") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SettingsSectionCard("External Music Integrations (Optional)") {
                    OutlinedTextField(
                        value = settings.youtubeApiKey,
                        onValueChange = { key -> onUpdateSettings { it.copy(youtubeApiKey = key) } },
                        label = { Text("YouTube Data API v3 Key") },
                        placeholder = { Text("Required only for keyword search; URL paste works without key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = settings.spotifyClientId,
                        onValueChange = { cid -> onUpdateSettings { it.copy(spotifyClientId = cid) } },
                        label = { Text("Spotify App Client ID (OAuth PKCE)") },
                        placeholder = { Text("Redirect URI: syncsound://spotify-callback") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SettingsSectionCard("About SyncSound") {
                    Text(
                        "SyncSound v1.0 · Native Android + Embedded Web Speaker PWA",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Multi-speaker synchronization schedules future playback timestamps using NTP-filtered RTT clock offsets and continuous Media3 playback speed nudging. Actual synchronization precision depends on Wi-Fi congestion, Bluetooth speaker latency, and device audio HAL buffering.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}
