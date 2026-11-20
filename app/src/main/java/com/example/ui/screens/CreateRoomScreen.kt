package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionMode
import com.example.sync.NsdDiscoveryManager
import com.example.sync.QrCodeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(
    initialCode: String,
    defaultRelayUrl: String,
    onGenerateNewCode: () -> String,
    onShareInvite: (String) -> Unit,
    onLaunchRoom: (roomName: String, roomCode: String, mode: ConnectionMode, relayUrl: String) -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var roomName by remember { mutableStateOf("Living Room Music") }
    var roomCode by remember { mutableStateOf(initialCode) }
    var connectionMode by remember { mutableStateOf(ConnectionMode.LAN_HOST) }
    var relayUrl by remember { mutableStateOf(defaultRelayUrl) }
    var copiedNotice by remember { mutableStateOf(false) }

    val localIp = remember { NsdDiscoveryManager.getLocalIpv4Address() }
    val previewInviteUrl = remember(localIp, roomCode) {
        QrCodeUtils.buildInviteWebUrl(localIp, 8765, roomCode, "")
    }
    val qrBitmap = remember(previewInviteUrl) {
        QrCodeUtils.generateQrBitmap(previewInviteUrl, 400)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Create Music Room") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_back_btn")) {
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
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    placeholder = { Text("e.g. Living Room Music") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_room_name_input")
                )

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "5-DIGIT ROOM CODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = roomCode,
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("generated_room_code_text")
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(roomCode))
                                    copiedNotice = true
                                },
                                modifier = Modifier.testTag("copy_room_code_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy room code")
                            }
                            IconButton(
                                onClick = {
                                    roomCode = onGenerateNewCode()
                                    copiedNotice = false
                                },
                                modifier = Modifier.testTag("regenerate_code_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Generate new room code")
                            }
                        }

                        if (copiedNotice) {
                            Text(
                                "Room code $roomCode copied",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (qrBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White
                            ) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Preview QR Code",
                                    modifier = Modifier
                                        .size(170.dp)
                                        .padding(10.dp)
                                )
                            }
                        }

                        Text(
                            "LAN Host IP: $localIp · Port 8765",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connection Topology", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = connectionMode == ConnectionMode.LAN_HOST,
                            onClick = { connectionMode = ConnectionMode.LAN_HOST },
                            label = { Text("Same Wi-Fi LAN (Lowest Latency)") },
                            leadingIcon = {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        FilterChip(
                            selected = connectionMode == ConnectionMode.REMOTE_RELAY,
                            onClick = { connectionMode = ConnectionMode.REMOTE_RELAY },
                            label = { Text("Remote Relay") },
                            leadingIcon = {
                                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }

                if (connectionMode == ConnectionMode.REMOTE_RELAY) {
                    OutlinedTextField(
                        value = relayUrl,
                        onValueChange = { relayUrl = it },
                        label = { Text("Remote WebSocket Relay URL (wss://...)") },
                        placeholder = { Text("wss://relay.example.com/ws") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onShareInvite("Join my SyncSound room \"$roomName\" with code $roomCode ($previewInviteUrl)")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("create_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }

                    Button(
                        onClick = { onLaunchRoom(roomName, roomCode, connectionMode, relayUrl) },
                        modifier = Modifier
                            .weight(1.6f)
                            .height(52.dp)
                            .testTag("launch_room_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create Room")
                    }
                }
            }
        }
    }
}
