package com.example.sync

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

data class ParsedQrInvite(
    val roomCode: String,
    val hostIp: String = "",
    val port: Int = 8765,
    val joinToken: String = "",
    val relayUrl: String = ""
)

object QrCodeUtils {

    fun buildInviteWebUrl(
        hostIp: String,
        port: Int,
        roomCode: String,
        joinToken: String
    ): String {
        val base = "http://$hostIp:$port/join/${Uri.encode(roomCode)}"
        return if (joinToken.isNotBlank()) "$base?token=${Uri.encode(joinToken)}" else base
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            val dark = 0xFF090C12.toInt()
            val light = 0xFFFFFFFF.toInt()
            for (y in 0 until sizePx) {
                val offset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (bitMatrix[x, y]) dark else light
                }
            }
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            bitmap.asImageBitmap()
        }.getOrNull()
    }

    fun parseQrPayload(raw: String): ParsedQrInvite? {
        val trimmed = raw.trim()
        if (trimmed.length == 5 && trimmed.all { it.isDigit() }) {
            return ParsedQrInvite(roomCode = trimmed)
        }
        return runCatching {
            val uri = Uri.parse(trimmed)
            val pathSegments = uri.pathSegments.orEmpty()
            val pathCode = if (pathSegments.size >= 2 && pathSegments[0].equals("join", ignoreCase = true)) {
                pathSegments[1]
            } else null

            val room = uri.getQueryParameter("room")
                ?: uri.getQueryParameter("code")
                ?: pathCode
                ?: return null
            if (room.length != 5 || !room.all { it.isDigit() }) return null
            val token = uri.getQueryParameter("token").orEmpty()
            val host = uri.getQueryParameter("host") ?: uri.host.orEmpty()
            val port = uri.getQueryParameter("port")?.toIntOrNull()
                ?: if (uri.port > 0) uri.port else 8765
            val relay = uri.getQueryParameter("relay").orEmpty()
            ParsedQrInvite(
                roomCode = room,
                hostIp = host,
                port = port,
                joinToken = token,
                relayUrl = relay
            )
        }.getOrNull()
    }
}
