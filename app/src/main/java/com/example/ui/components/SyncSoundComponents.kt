package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.ConnectionQuality
import com.example.model.DeviceType
import com.example.model.RoomInfo
import com.example.model.SyncHealthStatus
import com.example.sync.QrCodeUtils
import com.example.ui.theme.AdminAzure
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.WarningAmber
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SyncSoundBrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.linearGradient(
                    listOf(ElectricCyan.copy(alpha = 0.22f), SignalEmerald.copy(alpha = 0.22f))
                )
            )
            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = ElectricCyan.copy(alpha = 0.4f),
                radius = w * 0.46f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
            )
            drawCircle(
                color = SignalEmerald,
                radius = w * 0.26f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
            )
            drawCircle(
                color = ElectricCyan,
                radius = w * 0.1f,
                center = Offset(w / 2f, h / 2f)
            )
        }
    }
}

@Composable
fun EqualizerWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eqPhase"
    )

    Canvas(modifier = modifier.height(24.dp)) {
        val bars = 12
        val barWidth = (size.width / (bars * 2f)).coerceAtLeast(3f)
        val spacing = (size.width - bars * barWidth) / (bars - 1).coerceAtLeast(1)
        for (i in 0 until bars) {
            val normalized = if (isPlaying) {
                0.28f + 0.72f * ((sin(phase + i * 0.75f) + 1f) / 2f)
            } else {
                0.22f
            }
            val barH = size.height * normalized
            val x = i * (barWidth + spacing)
            val y = (size.height - barH) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

fun healthColor(status: SyncHealthStatus): Color = when (status) {
    SyncHealthStatus.ADMIN -> AdminAzure
    SyncHealthStatus.SYNCHRONIZED -> SignalEmerald
    SyncHealthStatus.DRIFT_WARNING -> WarningAmber
    SyncHealthStatus.DISCONNECTED -> AlertCoral
}

fun healthLabel(status: SyncHealthStatus): String = when (status) {
    SyncHealthStatus.ADMIN -> "ADMIN"
    SyncHealthStatus.SYNCHRONIZED -> "SYNCED"
    SyncHealthStatus.DRIFT_WARNING -> "SYNC ISSUE"
    SyncHealthStatus.DISCONNECTED -> "OFFLINE"
}

@Composable
fun HealthStatusPill(status: SyncHealthStatus, modifier: Modifier = Modifier) {
    val color = healthColor(status)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = healthLabel(status),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun deviceTypeIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.ANDROID_PHONE -> Icons.Default.PhoneAndroid
    DeviceType.ANDROID_TABLET -> Icons.Default.TabletAndroid
    DeviceType.WEB_BROWSER -> Icons.Default.Language
    DeviceType.DESKTOP -> Icons.Default.Computer
}

fun qualityLabel(quality: ConnectionQuality): String = when (quality) {
    ConnectionQuality.EXCELLENT -> "Excellent"
    ConnectionQuality.GOOD -> "Good"
    ConnectionQuality.FAIR -> "Fair"
    ConnectionQuality.POOR -> "Weak"
    ConnectionQuality.OFFLINE -> "Offline"
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return "%d:%02d".format(mins, secs)
}

@Composable
fun RoomQrShareDialog(
    roomInfo: RoomInfo,
    onShareClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    val inviteUrl = remember(roomInfo) {
        QrCodeUtils.buildInviteWebUrl(
            hostIp = roomInfo.hostIp,
            port = roomInfo.port,
            roomCode = roomInfo.roomCode,
            joinToken = roomInfo.joinToken
        )
    }
    val qrBitmap = remember(inviteUrl) {
        QrCodeUtils.generateQrBitmap(inviteUrl, 512)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invite Speakers", style = MaterialTheme.typography.titleLarge)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_qr_share_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        roomInfo.roomCode,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(roomInfo.roomCode))
                            copiedLabel = "Copied room code ${roomInfo.roomCode}"
                        },
                        modifier = Modifier.testTag("dialog_copy_code_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy room code")
                    }
                }

                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Room QR Code",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(12.dp)
                        )
                    }
                }

                Text(
                    text = "Join URL:\n$inviteUrl",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                copiedLabel?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalEmerald
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(inviteUrl))
                            copiedLabel = "Join link copied to clipboard"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_room_link_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Link")
                    }

                    Button(
                        onClick = { onShareClick(inviteUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_room_invite_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}
