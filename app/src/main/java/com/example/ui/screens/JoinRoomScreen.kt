package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.DiscoveredRoom
import com.example.model.SessionConnectionState
import com.example.sync.QrCodeUtils
import com.example.ui.components.QrScannerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRoomScreen(
    defaultDeviceName: String,
    prefillCode: String,
    prefillHostIp: String,
    prefillPort: Int,
    prefillToken: String,
    prefillRelayUrl: String,
    discoveredRooms: List<DiscoveredRoom>,
    connectionState: SessionConnectionState,
    statusBanner: String?,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onJoinRoom: (code: String, deviceName: String, hostIp: String, port: Int, token: String, relayUrl: String) -> Unit,
    onBack: () -> Unit
) {
    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose { onStopDiscovery() }
    }

    var roomCode by remember(prefillCode) { mutableStateOf(prefillCode) }
    var deviceName by remember(defaultDeviceName) { mutableStateOf(defaultDeviceName) }
    var hostIp by remember(prefillHostIp) { mutableStateOf(prefillHostIp) }
    var portText by remember(prefillPort) { mutableStateOf(prefillPort.toString()) }
    var joinToken by remember(prefillToken) { mutableStateOf(prefillToken) }
    var relayUrl by remember(prefillRelayUrl) { mutableStateOf(prefillRelayUrl) }
    var showAdvanced by remember { mutableStateOf(prefillHostIp.isNotBlank() || prefillRelayUrl.isNotBlank()) }
    var showScanner by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    if (showScanner) {
        QrScannerDialog(
            onDismiss = { showScanner = false },
            onQrScanned = { raw ->
                showScanner = false
                val parsed = QrCodeUtils.parseQrPayload(raw)
                if (parsed != null) {
                    roomCode = parsed.roomCode
                    if (parsed.hostIp.isNotBlank()) hostIp = parsed.hostIp
                    portText = parsed.port.toString()
                    joinToken = parsed.joinToken
                    if (parsed.relayUrl.isNotBlank()) relayUrl = parsed.relayUrl
                    validationError = null
                    onJoinRoom(
                        parsed.roomCode,
                        deviceName.ifBlank { "Android Speaker" },
                        parsed.hostIp,
                        parsed.port,
                        parsed.joinToken,
                        parsed.relayUrl
                    )
                } else {
                    validationError = "QR code does not contain a valid SyncSound room invite."
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Join Room") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("join_back_btn")) {
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { input ->
                        roomCode = input.filter { it.isDigit() }.take(5)
                        validationError = null
                    },
                    label = { Text("5-Digit Room Code") },
                    placeholder = { Text("e.g. 58321") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_room_code_input")
                )

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Speaker Device Name") },
                    placeholder = { Text("e.g. Kitchen Tablet") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_device_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showScanner = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("scan_qr_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan QR")
                    }

                    Button(
                        onClick = {
                            if (roomCode.length != 5) {
                                validationError = "Enter a valid 5-digit room code."
                            } else {
                                validationError = null
                                onJoinRoom(
                                    roomCode,
                                    deviceName,
                                    hostIp,
                                    portText.toIntOrNull() ?: 8765,
                                    joinToken,
                                    relayUrl
                                )
                            }
                        },
                        enabled = connectionState != SessionConnectionState.CONNECTING,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .testTag("join_room_submit_btn")
                    ) {
                        if (connectionState == SessionConnectionState.CONNECTING) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(end = 8.dp).height(18.dp).width(18.dp)
                            )
                        }
                        Text("Join Room")
                    }
                }

                validationError?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (statusBanner != null && connectionState == SessionConnectionState.ERROR) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            statusBanner,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.testTag("toggle_advanced_network_btn")
                ) {
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Manual Host IP / Remote Relay")
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = hostIp,
                                onValueChange = { hostIp = it },
                                label = { Text("Host LAN IP") },
                                placeholder = { Text("192.168.1.42") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = joinToken,
                            onValueChange = { joinToken = it },
                            label = { Text("Access Token (Optional on LAN)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = relayUrl,
                            onValueChange = { relayUrl = it },
                            label = { Text("Remote Relay WebSocket URL") },
                            placeholder = { Text("wss://relay.example.com/ws") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (discoveredRooms.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text("Detected on This Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    }
                    discoveredRooms.forEach { room ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    roomCode = room.roomCode
                                    hostIp = room.hostAddress
                                    portText = room.port.toString()
                                    onJoinRoom(
                                        room.roomCode,
                                        deviceName,
                                        room.hostAddress,
                                        room.port,
                                        joinToken,
                                        ""
                                    )
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.SpeakerGroup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(room.roomName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Code ${room.roomCode} · ${room.hostAddress}:${room.port}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "TAP TO JOIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
