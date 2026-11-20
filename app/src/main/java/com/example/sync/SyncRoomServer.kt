package com.example.sync

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.model.ConnectedDevice
import com.example.model.DeviceRole
import com.example.model.DeviceType
import com.example.model.PlaybackCommandType
import com.example.model.PlaybackSnapshot
import com.example.model.RoomInfo
import com.example.model.RoomSettings
import com.example.model.SyncJson
import com.example.model.SyncWsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure-Kotlin Coroutines HTTP/1.1 + HTTP 206 Partial Content + RFC 6455 WebSocket server
 * running inside the Admin Android device.
 *
 * Zero external server dependencies, full support for LAN audio streaming,
 * NTP clock-sync pings, scheduled playback broadcasts, and the Web Speaker PWA.
 */
class SyncRoomServer(
    private val context: Context,
    private val onDevicesChanged: (List<ConnectedDevice>) -> Unit
) {
    data class RegisteredAudioSource(
        val trackId: String,
        val mimeType: String,
        val contentUri: Uri? = null,
        val file: File? = null
    )

    private class ClientSession(
        val socket: Socket,
        val out: OutputStream,
        val writeMutex: Mutex = Mutex(),
        var deviceId: String = "",
        var authenticated: Boolean = false
    )

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var heartbeatJob: Job? = null

    private val clients = ConcurrentHashMap<String, ClientSession>()
    private val devicesMap = ConcurrentHashMap<String, ConnectedDevice>()
    private val audioSources = ConcurrentHashMap<String, RegisteredAudioSource>()
    private val failedAttemptsByIp = ConcurrentHashMap<String, Pair<Int, Long>>()

    @Volatile
    var roomInfo: RoomInfo? = null
        private set

    @Volatile
    var roomSettings: RoomSettings = RoomSettings()
        private set

    @Volatile
    var latestSnapshot: PlaybackSnapshot = PlaybackSnapshot()
        private set

    val boundPort: Int
        get() = serverSocket?.localPort ?: 0

    fun start(
        preferredPort: Int = 8765,
        initialRoomInfo: RoomInfo,
        initialSettings: RoomSettings,
        adminDevice: ConnectedDevice
    ): Int {
        stop()
        roomSettings = initialSettings
        devicesMap[adminDevice.deviceId] = adminDevice

        val sock = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(preferredPort))
            }
        } catch (_: Exception) {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
            }
        }
        serverSocket = sock
        roomInfo = initialRoomInfo.copy(port = sock.localPort)

        // Ensure test chime WAV is always registered
        val chimeFile = AudioChimeGenerator.ensureTestChimeFile(context.cacheDir)
        registerFileAudio("test_chime", chimeFile, "audio/wav")

        acceptJob = serverScope.launch {
            while (isActive && !sock.isClosed) {
                try {
                    val clientSocket = sock.accept()
                    clientSocket.tcpNoDelay = true
                    launch { handleConnection(clientSocket) }
                } catch (_: Exception) {
                    if (sock.isClosed) break
                }
            }
        }

        heartbeatJob = serverScope.launch {
            while (isActive) {
                delay(3000L)
                pruneStaleDevices()
            }
        }

        onDevicesChanged(sortedDevices())
        return sock.localPort
    }

    fun registerUriAudio(trackId: String, uri: Uri, mimeType: String) {
        audioSources[trackId] = RegisteredAudioSource(
            trackId = trackId,
            mimeType = mimeType.ifBlank { "audio/mpeg" },
            contentUri = uri
        )
    }

    fun registerFileAudio(trackId: String, file: File, mimeType: String) {
        audioSources[trackId] = RegisteredAudioSource(
            trackId = trackId,
            mimeType = mimeType.ifBlank { "audio/wav" },
            file = file
        )
    }

    fun updateAdminDevice(admin: ConnectedDevice) {
        devicesMap[admin.deviceId] = admin
        broadcastDevicesUpdate()
    }

    fun updateRoomSettings(settings: RoomSettings) {
        roomSettings = settings
        broadcastDevicesUpdate()
    }

    fun broadcastPlayback(command: PlaybackCommandType, snapshot: PlaybackSnapshot) {
        latestSnapshot = snapshot
        val msg = SyncWsMessage.PlaybackCommand(
            command = command,
            snapshot = snapshot,
            serverTimeMs = System.currentTimeMillis()
        )
        broadcastWsMessage(msg)
    }

    fun broadcastTestChime(scheduledServerTimeMs: Long) {
        val token = roomInfo?.joinToken.orEmpty()
        val msg = SyncWsMessage.TestChime(
            scheduledServerTimeMs = scheduledServerTimeMs,
            chimeUrl = "/audio/test_chime?token=$token"
        )
        broadcastWsMessage(msg)
    }

    fun controlDevice(
        targetDeviceId: String,
        volume: Float? = null,
        isMuted: Boolean? = null,
        delayCalibrationMs: Long? = null
    ) {
        devicesMap[targetDeviceId]?.let { existing ->
            devicesMap[targetDeviceId] = existing.copy(
                volume = volume ?: existing.volume,
                isMuted = isMuted ?: existing.isMuted,
                delayCalibrationMs = delayCalibrationMs ?: existing.delayCalibrationMs
            )
        }
        val controlMsg = SyncWsMessage.DeviceControl(
            targetDeviceId = targetDeviceId,
            volume = volume,
            isMuted = isMuted,
            delayCalibrationMs = delayCalibrationMs
        )
        sendToDevice(targetDeviceId, controlMsg)
        broadcastDevicesUpdate()
    }

    fun kickDevice(targetDeviceId: String, reason: String = "Removed by Room Admin") {
        val msg = SyncWsMessage.DeviceControl(
            targetDeviceId = targetDeviceId,
            kicked = true,
            reason = reason
        )
        sendToDevice(targetDeviceId, msg)
        clients.remove(targetDeviceId)?.let { runCatching { it.socket.close() } }
        devicesMap.remove(targetDeviceId)
        broadcastDevicesUpdate()
    }

    fun closeRoom(reason: String = "Session ended by Admin") {
        broadcastWsMessage(SyncWsMessage.RoomClosed(reason))
        stop()
    }

    fun stop() {
        acceptJob?.cancel()
        heartbeatJob?.cancel()
        clients.values.forEach { runCatching { it.socket.close() } }
        clients.clear()
        devicesMap.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        roomInfo = null
    }

    fun destroy() {
        stop()
        serverScope.cancel()
    }

    private suspend fun handleConnection(socket: Socket) {
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val headerText = readHttpHeaderBlock(input) ?: run {
                socket.close()
                return
            }
            val lines = headerText.split("\r\n")
            if (lines.isEmpty()) {
                socket.close()
                return
            }
            val requestParts = lines[0].split(" ")
            if (requestParts.size < 2) {
                socket.close()
                return
            }
            val method = requestParts[0].uppercase()
            val fullPath = requestParts[1]
            val path = fullPath.substringBefore("?")
            val queryParams = parseQueryParams(fullPath.substringAfter("?", ""))
            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val idx = lines[i].indexOf(":")
                if (idx > 0) {
                    headers[lines[i].substring(0, idx).trim().lowercase()] =
                        lines[i].substring(idx + 1).trim()
                }
            }

            if (headers["upgrade"]?.equals("websocket", ignoreCase = true) == true && path == "/ws") {
                val wsKey = headers["sec-websocket-key"]
                if (wsKey.isNullOrBlank()) {
                    writeSimpleResponse(output, 400, "text/plain", "Missing WebSocket key".toByteArray())
                    socket.close()
                    return
                }
                val acceptKey = computeWebSocketAccept(wsKey)
                val handshake = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"
                output.write(handshake.toByteArray(Charsets.US_ASCII))
                output.flush()
                handleWebSocketLoop(socket, input, output)
                return
            }

            when {
                path == "/" || path == "/index.html" || path.startsWith("/join/") || path == "/join" -> {
                    val html = WebSpeakerHtmlProvider.getSpeakerHtml().toByteArray(Charsets.UTF_8)
                    writeSimpleResponse(output, 200, "text/html; charset=utf-8", html)
                }
                path == "/manifest.json" -> {
                    val name = roomInfo?.roomName ?: "SyncSound"
                    val json = WebSpeakerHtmlProvider.getManifestJson(name).toByteArray(Charsets.UTF_8)
                    writeSimpleResponse(output, 200, "application/manifest+json", json)
                }
                path == "/sw.js" -> {
                    writeSimpleResponse(
                        output, 200, "application/javascript",
                        WebSpeakerHtmlProvider.serviceWorkerJs.toByteArray(Charsets.UTF_8)
                    )
                }
                path == "/icon.svg" -> {
                    writeSimpleResponse(
                        output, 200, "image/svg+xml",
                        WebSpeakerHtmlProvider.iconSvg.toByteArray(Charsets.UTF_8)
                    )
                }
                path == "/api/info" -> {
                    val info = roomInfo
                    if (info == null) {
                        writeSimpleResponse(output, 404, "application/json", """{"error":"Room offline"}""".toByteArray())
                    } else {
                        val payload = """{"roomCode":"${info.roomCode}","roomName":"${info.roomName}","admin":"${info.adminDeviceName}","devices":${devicesMap.size}}"""
                        writeSimpleResponse(output, 200, "application/json", payload.toByteArray(Charsets.UTF_8))
                    }
                }
                path.startsWith("/audio/") -> {
                    val trackId = path.removePrefix("/audio/")
                    val token = queryParams["token"].orEmpty()
                    val expectedToken = roomInfo?.joinToken.orEmpty()
                    if (expectedToken.isNotEmpty() && token != expectedToken) {
                        writeSimpleResponse(output, 403, "text/plain", "Unauthorized token".toByteArray())
                    } else {
                        serveAudioResource(trackId, method == "HEAD", headers["range"], output)
                    }
                }
                else -> {
                    writeSimpleResponse(output, 404, "text/plain", "Not Found".toByteArray())
                }
            }
            socket.close()
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }

    private suspend fun handleWebSocketLoop(
        socket: Socket,
        input: BufferedInputStream,
        output: OutputStream
    ) {
        val session = ClientSession(socket = socket, out = output)
        val remoteIp = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
        try {
            while (!socket.isClosed) {
                val frame = readWebSocketFrame(input) ?: break
                when (frame.opcode) {
                    0x8 -> break // Close
                    0x9 -> sendWebSocketFrame(session, 0xA, frame.payload) // Pong
                    0x1 -> { // Text message
                        val text = frame.payload.toString(Charsets.UTF_8)
                        processWsTextMessage(session, remoteIp, text)
                    }
                }
            }
        } catch (_: Exception) {
            // Client disconnected
        } finally {
            if (session.deviceId.isNotBlank()) {
                clients.remove(session.deviceId)
                devicesMap[session.deviceId]?.let { existing ->
                    devicesMap[session.deviceId] = existing.copy(
                        isOnline = false,
                        healthStatus = com.example.model.SyncHealthStatus.DISCONNECTED,
                        connectionQuality = com.example.model.ConnectionQuality.OFFLINE
                    )
                }
                broadcastDevicesUpdate()
            }
            runCatching { socket.close() }
        }
    }

    private suspend fun processWsTextMessage(
        session: ClientSession,
        remoteIp: String,
        rawJson: String
    ) {
        val msg = runCatching { SyncJson.decodeFromString<SyncWsMessage>(rawJson) }.getOrNull() ?: return
        when (msg) {
            is SyncWsMessage.Hello -> {
                val info = roomInfo
                val now = System.currentTimeMillis()
                val blockedInfo = failedAttemptsByIp[remoteIp]
                if (blockedInfo != null && blockedInfo.first >= 5 && now - blockedInfo.second < 60_000L) {
                    sendToSession(
                        session,
                        SyncWsMessage.Welcome(accepted = false, reason = "Too many failed attempts. Wait 60s.")
                    )
                    return
                }

                if (info == null) {
                    sendToSession(session, SyncWsMessage.Welcome(accepted = false, reason = "Room is no longer active."))
                    return
                }
                if (now > roomSettings.expiresAtEpochMs) {
                    sendToSession(session, SyncWsMessage.Welcome(accepted = false, reason = "Room session has expired."))
                    return
                }
                if (msg.roomCode != info.roomCode || (msg.joinToken.isNotBlank() && msg.joinToken != info.joinToken)) {
                    val prev = failedAttemptsByIp[remoteIp]?.first ?: 0
                    failedAttemptsByIp[remoteIp] = (prev + 1) to now
                    sendToSession(session, SyncWsMessage.Welcome(accepted = false, reason = "Invalid room code or access token."))
                    return
                }
                if (devicesMap.size >= roomSettings.maxDevices && !devicesMap.containsKey(msg.deviceId)) {
                    sendToSession(session, SyncWsMessage.Welcome(accepted = false, reason = "Room capacity (${roomSettings.maxDevices}) reached."))
                    return
                }

                failedAttemptsByIp.remove(remoteIp)
                val assignedId = msg.deviceId.ifBlank { "spk-${System.nanoTime()}" }
                session.deviceId = assignedId
                session.authenticated = true
                clients[assignedId] = session

                val existing = devicesMap[assignedId]
                val device = ConnectedDevice(
                    deviceId = assignedId,
                    name = msg.deviceName.take(40).ifBlank { "Speaker" },
                    deviceType = msg.deviceType,
                    role = DeviceRole.SPEAKER,
                    isOnline = true,
                    volume = existing?.volume ?: 1.0f,
                    isMuted = existing?.isMuted ?: false,
                    delayCalibrationMs = existing?.delayCalibrationMs ?: 0L,
                    lastSeenEpochMs = now,
                    healthStatus = com.example.model.SyncHealthStatus.SYNCHRONIZED,
                    connectionQuality = com.example.model.ConnectionQuality.EXCELLENT
                )
                devicesMap[assignedId] = device

                sendToSession(
                    session,
                    SyncWsMessage.Welcome(
                        accepted = true,
                        assignedDeviceId = assignedId,
                        serverTimeMs = System.currentTimeMillis(),
                        roomInfo = info,
                        snapshot = latestSnapshot,
                        devices = sortedDevices(),
                        settings = roomSettings
                    )
                )
                broadcastDevicesUpdate()
            }

            is SyncWsMessage.SyncPing -> {
                if (!session.authenticated) return
                val t2 = System.currentTimeMillis()
                val t3 = System.currentTimeMillis()
                sendToSession(
                    session,
                    SyncWsMessage.SyncPong(
                        seq = msg.seq,
                        t1ClientMs = msg.t1ClientMs,
                        t2ServerRecvMs = t2,
                        t3ServerSendMs = t3
                    )
                )
            }

            is SyncWsMessage.TelemetryReport -> {
                if (!session.authenticated) return
                val existing = devicesMap[session.deviceId] ?: return
                val now = System.currentTimeMillis()
                devicesMap[session.deviceId] = existing.copy(
                    isOnline = true,
                    rttMs = msg.rttMs,
                    clockOffsetMs = msg.clockOffsetMs,
                    driftMs = msg.driftMs,
                    volume = if (roomSettings.allowLocalVolumeOverride) msg.localVolume else existing.volume,
                    isMuted = msg.isMuted,
                    lastSeenEpochMs = now,
                    connectionQuality = ClockSyncEngine.classifyConnectionQuality(msg.rttMs, true),
                    healthStatus = ClockSyncEngine.classifyHealthStatus(
                        isOnline = true,
                        isAdmin = false,
                        driftMs = msg.driftMs,
                        toleranceMs = roomSettings.driftToleranceMs
                    )
                )
                onDevicesChanged(sortedDevices())
            }

            is SyncWsMessage.LocalVolumeUpdate -> {
                if (!session.authenticated || !roomSettings.allowLocalVolumeOverride) return
                val existing = devicesMap[session.deviceId] ?: return
                devicesMap[session.deviceId] = existing.copy(
                    volume = msg.volume.coerceIn(0f, 1f),
                    isMuted = msg.isMuted,
                    lastSeenEpochMs = System.currentTimeMillis()
                )
                broadcastDevicesUpdate()
            }

            else -> Unit
        }
    }

    private fun pruneStaleDevices() {
        val now = System.currentTimeMillis()
        var changed = false
        devicesMap.entries.forEach { (id, dev) ->
            if (dev.role == DeviceRole.SPEAKER && dev.isOnline && now - dev.lastSeenEpochMs > 15_000L) {
                devicesMap[id] = dev.copy(
                    isOnline = false,
                    healthStatus = com.example.model.SyncHealthStatus.DISCONNECTED,
                    connectionQuality = com.example.model.ConnectionQuality.OFFLINE
                )
                changed = true
            }
        }
        if (changed) broadcastDevicesUpdate()
    }

    private fun sortedDevices(): List<ConnectedDevice> =
        devicesMap.values.sortedWith(
            compareBy<ConnectedDevice> { it.role != DeviceRole.ADMIN }
                .thenByDescending { it.isOnline }
                .thenBy { it.name.lowercase() }
        )

    private fun broadcastDevicesUpdate() {
        val list = sortedDevices()
        onDevicesChanged(list)
        broadcastWsMessage(SyncWsMessage.DevicesUpdate(devices = list, settings = roomSettings))
    }

    private fun broadcastWsMessage(message: SyncWsMessage) {
        val json = SyncJson.encodeToString(SyncWsMessage.serializer(), message)
        val payload = json.toByteArray(Charsets.UTF_8)
        clients.values.filter { it.authenticated }.forEach { client ->
            serverScope.launch {
                runCatching { sendWebSocketFrame(client, 0x1, payload) }
            }
        }
    }

    private fun sendToDevice(deviceId: String, message: SyncWsMessage) {
        val client = clients[deviceId] ?: return
        val payload = SyncJson.encodeToString(SyncWsMessage.serializer(), message).toByteArray(Charsets.UTF_8)
        serverScope.launch {
            runCatching { sendWebSocketFrame(client, 0x1, payload) }
        }
    }

    private suspend fun sendToSession(session: ClientSession, message: SyncWsMessage) {
        val payload = SyncJson.encodeToString(SyncWsMessage.serializer(), message).toByteArray(Charsets.UTF_8)
        sendWebSocketFrame(session, 0x1, payload)
    }

    private fun serveAudioResource(
        trackId: String,
        headOnly: Boolean,
        rangeHeader: String?,
        output: OutputStream
    ) {
        val source = audioSources[trackId]
        if (source == null) {
            writeSimpleResponse(output, 404, "text/plain", "Track not found".toByteArray())
            return
        }

        val totalLength = resolveAudioLength(source)
        if (totalLength <= 0L) {
            writeSimpleResponse(output, 404, "text/plain", "Audio stream unreadable".toByteArray())
            return
        }

        var startByte = 0L
        var endByte = totalLength - 1L
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val spec = rangeHeader.removePrefix("bytes=").substringBefore(",")
            val parts = spec.split("-")
            val parsedStart = parts.getOrNull(0)?.toLongOrNull()
            val parsedEnd = parts.getOrNull(1)?.toLongOrNull()
            if (parsedStart != null) {
                startByte = parsedStart.coerceIn(0L, totalLength - 1L)
                endByte = (parsedEnd ?: (totalLength - 1L)).coerceIn(startByte, totalLength - 1L)
                isPartial = true
            }
        }

        val contentLength = (endByte - startByte) + 1L
        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"
        val headers = buildString {
            append("$statusLine\r\n")
            append("Content-Type: ${source.mimeType}\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Content-Length: $contentLength\r\n")
            if (isPartial) {
                append("Content-Range: bytes $startByte-$endByte/$totalLength\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.US_ASCII))
        if (!headOnly) {
            openAudioInputStream(source)?.use { inputStream ->
                skipFully(inputStream, startByte)
                val buffer = ByteArray(16 * 1024)
                var remaining = contentLength
                while (remaining > 0L) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = inputStream.read(buffer, 0, toRead)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        output.flush()
    }

    private fun resolveAudioLength(source: RegisteredAudioSource): Long {
        source.file?.let { return it.length() }
        val uri = source.contentUri ?: return -1L
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0) afd.length else -1L
            } ?: -1L
        }.getOrDefault(-1L).let { len ->
            if (len > 0L) len else {
                // Fallback byte count if descriptor length unknown
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        var total = 0L
                        val buf = ByteArray(8192)
                        while (true) {
                            val r = stream.read(buf)
                            if (r <= 0) break
                            total += r
                        }
                        total
                    } ?: -1L
                }.getOrDefault(-1L)
            }
        }
    }

    private fun openAudioInputStream(source: RegisteredAudioSource): InputStream? {
        source.file?.let { return FileInputStream(it) }
        val uri = source.contentUri ?: return null
        return context.contentResolver.openInputStream(uri)
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val scratch = ByteArray(8192)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (read <= 0) break
                remaining -= read
            }
        }
    }

    private fun writeSimpleResponse(output: OutputStream, status: Int, contentType: String, body: ByteArray) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val header = "HTTP/1.1 $status $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun readHttpHeaderBlock(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev1 = 0
        var prev2 = 0
        var prev3 = 0
        var count = 0
        while (count < 16_384) {
            val b = input.read()
            if (b == -1) return null
            baos.write(b)
            count++
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                return baos.toString(Charsets.US_ASCII.name())
            }
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        return null
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx > 0) part.substring(0, idx) to Uri.decode(part.substring(idx + 1)) else null
        }.toMap()
    }

    private fun computeWebSocketAccept(key: String): String {
        val magic = key.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1").digest(magic.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(sha1, Base64.NO_WRAP)
    }

    private data class WsFrame(val opcode: Int, val payload: ByteArray)

    private fun readWebSocketFrame(input: InputStream): WsFrame? {
        val b0 = input.read()
        if (b0 == -1) return null
        val b1 = input.read()
        if (b1 == -1) return null

        val opcode = b0 and 0x0F
        val masked = (b1 and 0x80) != 0
        var payloadLen = (b1 and 0x7F).toLong()

        if (payloadLen == 126L) {
            val hi = input.read()
            val lo = input.read()
            if (hi == -1 || lo == -1) return null
            payloadLen = ((hi shl 8) or lo).toLong()
        } else if (payloadLen == 127L) {
            var len = 0L
            for (i in 0 until 8) {
                val next = input.read()
                if (next == -1) return null
                len = (len shl 8) or next.toLong()
            }
            payloadLen = len
        }

        if (payloadLen > 2_000_000L) return null

        val maskKey = if (masked) {
            ByteArray(4).also { if (!readExact(input, it)) return null }
        } else null

        val payload = ByteArray(payloadLen.toInt())
        if (!readExact(input, payload)) return null
        if (maskKey != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }
        return WsFrame(opcode, payload)
    }

    private fun readExact(input: InputStream, dest: ByteArray): Boolean {
        var offset = 0
        while (offset < dest.size) {
            val r = input.read(dest, offset, dest.size - offset)
            if (r == -1) return false
            offset += r
        }
        return true
    }

    private suspend fun sendWebSocketFrame(session: ClientSession, opcode: Int, payload: ByteArray) {
        session.writeMutex.withLock {
            val out = session.out
            out.write(0x80 or opcode)
            val len = payload.size
            when {
                len < 126 -> out.write(len)
                len <= 65535 -> {
                    out.write(126)
                    out.write((len shr 8) and 0xFF)
                    out.write(len and 0xFF)
                }
                else -> {
                    out.write(127)
                    for (shift in 56 downTo 0 step 8) {
                        out.write(((len.toLong() shr shift) and 0xFFL).toInt())
                    }
                }
            }
            out.write(payload)
            out.flush()
        }
    }
}
