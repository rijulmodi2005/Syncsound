package com.example.sources

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.MusicSourceType
import com.example.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.util.Base64

object MusicSourcesHelper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun extractLocalTrack(context: Context, uri: Uri): TrackItem = withContext(Dispatchers.IO) {
        val trackId = "loc-${UUID.randomUUID().toString().take(8)}"
        var fileName = "Audio Track"
        var mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                }
            }
        }

        var title = fileName.substringBeforeLast(".")
        var artist = "Local Library"
        var durationMs = 0L
        var artworkPath = ""

        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
                title = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                artist = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                durationMs = it
            }
            retriever.embeddedPicture?.let { bytes ->
                val artFile = File(context.cacheDir, "art_$trackId.jpg")
                FileOutputStream(artFile).use { it.write(bytes) }
                artworkPath = artFile.toURI().toString()
            }
        }
        runCatching { retriever.release() }

        TrackItem(
            id = trackId,
            title = title,
            artist = artist,
            durationMs = durationMs,
            sourceType = MusicSourceType.LOCAL_AUDIO,
            localUriString = uri.toString(),
            artworkUrl = artworkPath,
            mimeType = mimeType
        )
    }

    suspend fun validateAndBuildStreamTrack(
        rawUrl: String,
        customTitle: String
    ): Result<TrackItem> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("Stream URL must start with http:// or https://"))
        }

        val check = runCatching {
            val req = Request.Builder().url(url).head().build()
            httpClient.newCall(req).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                val okCode = resp.isSuccessful || resp.code == 405
                okCode to contentType
            }
        }.getOrElse {
            return@withContext Result.failure(IllegalStateException("Could not reach stream URL: ${it.message}"))
        }

        if (!check.first) {
            return@withContext Result.failure(IllegalStateException("Stream endpoint returned an HTTP error."))
        }

        val ct = check.second
        val isSupportedType = ct.isBlank() ||
            ct.startsWith("audio/") ||
            ct.contains("ogg") ||
            ct.contains("mpegurl") ||
            ct.contains("octet-stream") ||
            ct.contains("mp4")

        if (!isSupportedType) {
            return@withContext Result.failure(IllegalArgumentException("URL returned non-audio content type ($ct)."))
        }

        val inferredTitle = customTitle.trim().ifBlank {
            Uri.parse(url).lastPathSegment?.substringBefore("?")?.ifBlank { "Live Audio Stream" } ?: "Live Audio Stream"
        }

        Result.success(
            TrackItem(
                id = "str-${UUID.randomUUID().toString().take(8)}",
                title = inferredTitle,
                artist = Uri.parse(url).host ?: "Internet Audio Stream",
                sourceType = MusicSourceType.DIRECT_STREAM,
                streamUrl = url,
                mimeType = ct.ifBlank { "audio/mpeg" }
            )
        )
    }

    fun extractYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return trimmed
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        return when {
            host.contains("youtu.be") -> uri.lastPathSegment?.take(11)
            host.contains("youtube.com") -> {
                uri.getQueryParameter("v")?.take(11)
                    ?: uri.pathSegments.dropWhile { it != "embed" && it != "shorts" && it != "live" }
                        .getOrNull(1)?.take(11)
            }
            else -> null
        }?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{11}$")) }
    }

    suspend fun fetchYouTubeTrackViaOEmbed(videoIdOrUrl: String): Result<TrackItem> = withContext(Dispatchers.IO) {
        val videoId = extractYouTubeVideoId(videoIdOrUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Enter a valid YouTube URL or 11-character Video ID."))
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val oembedUrl = "https://www.youtube.com/oembed?url=${Uri.encode(watchUrl)}&format=json"

        return@withContext runCatching {
            val req = Request.Builder().url(oembedUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@use TrackItem(
                        id = "yt-$videoId",
                        title = "YouTube Video ($videoId)",
                        artist = "YouTube Official Embed",
                        sourceType = MusicSourceType.YOUTUBE,
                        externalId = videoId,
                        artworkUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    )
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                TrackItem(
                    id = "yt-$videoId",
                    title = json.optString("title", "YouTube Video"),
                    artist = json.optString("author_name", "YouTube"),
                    sourceType = MusicSourceType.YOUTUBE,
                    externalId = videoId,
                    artworkUrl = json.optString("thumbnail_url", "https://img.youtube.com/vi/$videoId/hqdefault.jpg")
                )
            }
        }
    }

    suspend fun searchYouTubeDataApi(query: String, apiKey: String): Result<List<TrackItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Add a YouTube Data API v3 key in Settings to search, or paste any YouTube URL directly."))
        }
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=10&q=${Uri.encode(query)}&key=${Uri.encode(apiKey)}"
        return@withContext runCatching {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("YouTube API error (${resp.code})")
                }
                val items = JSONObject(body).optJSONArray("items") ?: return@use emptyList()
                buildList {
                    for (i in 0 until items.length()) {
                        val obj = items.getJSONObject(i)
                        val vid = obj.getJSONObject("id").optString("videoId")
                        val snippet = obj.getJSONObject("snippet")
                        val thumb = snippet.optJSONObject("thumbnails")
                            ?.optJSONObject("medium")
                            ?.optString("url")
                            ?: "https://img.youtube.com/vi/$vid/hqdefault.jpg"
                        if (vid.isNotBlank()) {
                            add(
                                TrackItem(
                                    id = "yt-$vid",
                                    title = snippet.optString("title", "YouTube Video"),
                                    artist = snippet.optString("channelTitle", "YouTube"),
                                    sourceType = MusicSourceType.YOUTUBE,
                                    externalId = vid,
                                    artworkUrl = thumb
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

data class SpotifySessionState(
    val isConnected: Boolean = false,
    val displayName: String = "",
    val productTier: String = "",
    val accessToken: String = "",
    val playlists: List<Pair<String, String>> = emptyList(),
    val statusMessage: String = ""
)

object SpotifyPkceHelper {
    private const val REDIRECT_URI = "syncsound://spotify-callback"
    private val httpClient = OkHttpClient()

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun buildAuthorizeUri(clientId: String, codeChallenge: String): Uri {
        val scopes = "user-read-private user-read-playback-state user-modify-playback-state user-read-currently-playing playlist-read-private"
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .build()
    }

    suspend fun exchangeCodeAndLoadProfile(
        clientId: String,
        code: String,
        codeVerifier: String
    ): Result<SpotifySessionState> = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val formBody = okhttp3.FormBody.Builder()
                .add("client_id", clientId.trim())
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()

            val tokenReq = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(formBody)
                .build()

            val tokenJson = httpClient.newCall(tokenReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Spotify token exchange failed (${resp.code})")
                JSONObject(raw)
            }
            val accessToken = tokenJson.getString("access_token")

            val meReq = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $accessToken")
                .build()
            val meJson = httpClient.newCall(meReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Failed to fetch Spotify profile")
                JSONObject(resp.body?.string().orEmpty())
            }

            val displayName = meJson.optString("display_name", "Spotify User")
            val product = meJson.optString("product", "free")

            val plReq = Request.Builder()
                .url("https://api.spotify.com/v1/me/playlists?limit=15")
                .header("Authorization", "Bearer $accessToken")
                .build()
            val playlists = runCatching {
                httpClient.newCall(plReq).execute().use { resp ->
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("items")
                    buildList {
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.getJSONObject(i)
                                add(item.optString("id") to item.optString("name", "Playlist"))
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())

            SpotifySessionState(
                isConnected = true,
                displayName = displayName,
                productTier = product,
                accessToken = accessToken,
                playlists = playlists,
                statusMessage = if (product.equals("premium", ignoreCase = true)) {
                    "Spotify Premium connected"
                } else {
                    "Connected ($product). Playback control requires Spotify Premium."
                }
            )
        }
    }

    suspend fun fetchCurrentlyPlaying(accessToken: String): TrackItem? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me/player/currently-playing")
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.code == 204 || !resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string().orEmpty())
                val item = json.optJSONObject("item") ?: return@use null
                val artistsArr = item.optJSONArray("artists")
                val artistNames = buildList {
                    if (artistsArr != null) {
                        for (i in 0 until artistsArr.length()) {
                            add(artistsArr.getJSONObject(i).optString("name"))
                        }
                    }
                }.joinToString(", ").ifBlank { "Spotify Artist" }
                val artwork = item.optJSONObject("album")
                    ?.optJSONArray("images")
                    ?.optJSONObject(0)
                    ?.optString("url")
                    .orEmpty()
                TrackItem(
                    id = "sp-${item.optString("id")}",
                    title = item.optString("name", "Spotify Track"),
                    artist = artistNames,
                    durationMs = item.optLong("duration_ms", 0L),
                    sourceType = MusicSourceType.SPOTIFY,
                    externalId = item.optString("uri"),
                    artworkUrl = artwork
                )
            }
        }.getOrNull()
    }
}
