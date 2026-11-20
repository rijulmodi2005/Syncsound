package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ConnectedDevice
import com.example.model.DeviceRole
import com.example.model.RoomSettings
import com.example.ui.components.HealthStatusPill
import com.example.ui.components.deviceTypeIcon
import com.example.ui.components.qualityLabel
import kotlin.math.roundToInt

@Composable
fun AdminDevicesSection(
    devices: List<ConnectedDevice>,
    roomSettings: RoomSettings,
    onDeviceVolumeChange: (String, Float) -> Unit,
    onToggleDeviceMute: (String) -> Unit,
    onDeviceDelayChange: (String, Long) -> Unit,
    onKickDevice: (String) -> Unit,
    onTestAllSpeakers: () -> Unit,
    onUpdateRoomSettings: ((RoomSettings) -> RoomSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    Text("Connected Speakers (${devices.size})", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = onTestAllSpeakers,
                        modifier = Modifier.testTag("test_all_speakers_btn")
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Chime")
                    }
                }

                devices.forEach { device ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("device_card_${device.deviceId}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        deviceTypeIcon(device.deviceType),
                                        contentDescription = device.deviceType.name,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (device.role == DeviceRole.ADMIN) {
                                                "Host Admin · Reference Clock"
                                            } else {
                                                "${qualityLabel(device.connectionQuality)} · RTT ${device.rttMs}ms · Drift ${device.driftMs}ms"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    HealthStatusPill(status = device.healthStatus)
                                    if (device.role != DeviceRole.ADMIN) {
                                        IconButton(
                                            onClick = { onKickDevice(device.deviceId) },
                                            modifier = Modifier.testTag("kick_device_${device.deviceId}")
                                        ) {
                                            Icon(
                                                Icons.Default.PersonRemove,
                                                contentDescription = "Remove device",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            // Volume Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { onToggleDeviceMute(device.deviceId) },
                                    modifier = Modifier.testTag("mute_device_${device.deviceId}")
                                ) {
                                    Icon(
                                        if (device.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Mute device"
                                    )
                                }
                                Slider(
                                    value = device.volume,
                                    onValueChange = { onDeviceVolumeChange(device.deviceId, it) },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (device.isMuted) "MUTE" else "${(device.volume * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(44.dp)
                                )
                            }

                            // Delay Calibration Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Delay calibration",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = device.delayCalibrationMs.toFloat(),
                                    onValueChange = { onDeviceDelayChange(device.deviceId, it.roundToInt().toLong()) },
                                    valueRange = -250f..250f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${device.delayCalibrationMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Room Policy Settings
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Room Session Rules", style = MaterialTheme.typography.titleLarge)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Local Speaker Volume", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Let connected speakers adjust their own device volume",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = roomSettings.allowLocalVolumeOverride,
                        onCheckedChange = { allowed ->
                            onUpdateRoomSettings { it.copy(allowLocalVolumeOverride = allowed) }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Drift Correction", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Smooth speed nudging & hard resync beyond ${roomSettings.driftToleranceMs}ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = roomSettings.autoCorrectDrift,
                        onCheckedChange = { enabled ->
                            onUpdateRoomSettings { it.copy(autoCorrectDrift = enabled) }
                        }
                    )
                }
            }
        }
    }
}
