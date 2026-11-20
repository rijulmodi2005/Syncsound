package com.example.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.model.MusicSourceType
import com.example.model.PlaybackSnapshot
import com.example.model.TrackItem
import com.example.sync.ClockSyncEngine
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncAudioController(
    private val context: Context,
    private val clockSyncEngine: ClockSyncEngine
) {
    data class PlayerUiProgress(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val isPlaying: Boolean = false,
        val currentDriftMs: Long = 0L,
        val correctionState: String = "In Sync"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var scheduledPlayJob: Job? = null
    private var monitorJob: Job? = null
    private var chimeJob: Job? = null

    private var currentLoadedTrackId: String? = null
    private var activeSnapshot: PlaybackSnapshot? = null
    private var isSpeakerMode: Boolean = false
    private var localDelayCalibrationMs: Long = 0L
    private var driftToleranceMs: Long = 45L
    private var autoCorrectDrift: Boolean = true

    private var currentVolume: Float = 1.0f
    private var currentMuted: Boolean = false

    private val _progress = MutableStateFlow(PlayerUiProgress())
    val progress: StateFlow<PlayerUiProgress> = _progress.asStateFlow()

    var onTrackCompletedByAdmin: (() -> Unit)? = null
    var onSpeakerTelemetryTick: ((positionMs: Long, driftMs: Long, isPlaying: Boolean, label: String) -> Unit)? = null

    fun initialize() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, SyncPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val controller = future.get()
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED && !isSpeakerMode) {
                            onTrackCompletedByAdmin?.invoke()
                        }
                    }
                })
                applyEffectiveVolume()
                activeSnapshot?.let { applySnapshot(it, isSpeakerMode) }
                startProgressMonitor()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setCalibrationAndSettings(
        delayOffsetMs: Long,
        toleranceMs: Long,
        autoCorrect: Boolean
    ) {
        localDelayCalibrationMs = delayOffsetMs
        driftToleranceMs = toleranceMs
        autoCorrectDrift = autoCorrect
    }

    fun setVolume(volume: Float, muted: Boolean) {
        currentVolume = volume.coerceIn(0f, 1f)
        currentMuted = muted
        applyEffectiveVolume()
    }

    private fun applyEffectiveVolume() {
        val master = activeSnapshot?.masterVolume ?: 1f
        val effective = if (currentMuted) 0f else (currentVolume * master).coerceIn(0f, 1f)
        mediaController?.volume = effective
    }

    fun applySnapshot(snapshot: PlaybackSnapshot, speakerMode: Boolean, hostBaseHttpUrl: String = "") {
        isSpeakerMode = speakerMode
        activeSnapshot = snapshot
        applyEffectiveVolume()

        val track = snapshot.track
        if (track == null || track.sourceType == MusicSourceType.YOUTUBE || track.sourceType == MusicSourceType.SPOTIFY) {
            scheduledPlayJob?.cancel()
            mediaController?.pause()
            currentLoadedTrackId = track?.id
            return
        }

        val controller = mediaController ?: return
        val playableUri = resolvePlayableUri(track, speakerMode, hostBaseHttpUrl) ?: return

        if (currentLoadedTrackId != track.id) {
            currentLoadedTrackId = track.id
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .build()
            val item = MediaItem.Builder()
                .setUri(playableUri)
                .setMediaId(track.id)
                .setMediaMetadata(metadata)
                .build()
            controller.setMediaItem(item)
            controller.prepare()
        }

        scheduledPlayJob?.cancel()
        if (!snapshot.isPlaying) {
            controller.pause()
            controller.setPlaybackSpeed(1.0f)
            controller.seekTo(snapshot.startPositionMs.coerceAtLeast(0L))
            _progress.value = _progress.value.copy(
                positionMs = snapshot.startPositionMs,
                isPlaying = false,
                currentDriftMs = 0L,
                correctionState = "Paused"
            )
            return
        }

        val delayUntilStartMs = if (speakerMode) {
            clockSyncEngine.computeLocalDelayUntilServerTimeMs(snapshot.scheduledServerTimeMs)
        } else {
            (snapshot.scheduledServerTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)
        }

        val expectedTargetPos = if (delayUntilStartMs == 0L) {
            if (speakerMode) {
                clockSyncEngine.computeExpectedPositionMs(snapshot, localDelayCalibrationMs)
            } else {
                snapshot.startPositionMs + (System.currentTimeMillis() - snapshot.scheduledServerTimeMs).coerceAtLeast(0L)
            }
        } else {
            (snapshot.startPositionMs + localDelayCalibrationMs).coerceAtLeast(0L)
        }

        controller.pause()
        controller.seekTo(expectedTargetPos)

        scheduledPlayJob = scope.launch {
            if (delayUntilStartMs > 0L) {
                _progress.value = _progress.value.copy(correctionState = "Scheduled (${delayUntilStartMs}ms)")
                delay(delayUntilStartMs)
            }
            controller.setPlaybackSpeed(1.0f)
            controller.play()
        }
    }

    fun playTestChime(chimeUrlOrUri: String, scheduledServerTimeMs: Long, speakerMode: Boolean) {
        val controller = mediaController ?: return
        chimeJob?.cancel()
        val delayMs = if (speakerMode) {
            clockSyncEngine.computeLocalDelayUntilServerTimeMs(scheduledServerTimeMs)
        } else {
            (scheduledServerTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        chimeJob = scope.launch {
            currentLoadedTrackId = "test_chime"
            controller.setMediaItem(MediaItem.fromUri(chimeUrlOrUri))
            controller.prepare()
            controller.seekTo(0L)
            if (delayMs > 0L) delay(delayMs)
            controller.setPlaybackSpeed(1.0f)
            controller.play()
        }
    }

    fun currentPositionMs(): Long = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun currentDurationMs(): Long = mediaController?.duration?.coerceAtLeast(0L) ?: 0L

    private fun resolvePlayableUri(track: TrackItem, speakerMode: Boolean, hostBaseHttpUrl: String): Uri? {
        if (!speakerMode && track.localUriString.isNotBlank()) {
            return Uri.parse(track.localUriString)
        }
        val url = track.streamUrl
        if (url.isBlank()) {
            return if (track.localUriString.isNotBlank()) Uri.parse(track.localUriString) else null
        }
        val fullUrl = if (url.startsWith("/") && hostBaseHttpUrl.isNotBlank()) {
            "$hostBaseHttpUrl$url"
        } else {
            url
        }
        return Uri.parse(fullUrl)
    }

    private fun startProgressMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(500L)
                val controller = mediaController ?: continue
                val snap = activeSnapshot
                val pos = controller.currentPosition.coerceAtLeast(0L)
                val dur = controller.duration.coerceAtLeast(snap?.track?.durationMs ?: 0L)
                val playing = controller.isPlaying

                var driftMs = 0L
                var label = if (playing) "Synchronized" else "Standby"

                if (isSpeakerMode && snap != null && snap.isPlaying &&
                    snap.track?.sourceType != MusicSourceType.YOUTUBE &&
                    snap.track?.sourceType != MusicSourceType.SPOTIFY &&
                    scheduledPlayJob?.isActive != true
                ) {
                    val expected = clockSyncEngine.computeExpectedPositionMs(snap, localDelayCalibrationMs)
                    driftMs = expected - pos
                    if (autoCorrectDrift) {
                        when (val action = clockSyncEngine.evaluateDriftCorrection(pos, expected, driftToleranceMs)) {
                            is ClockSyncEngine.DriftCorrectionAction.InSync -> {
                                if (controller.playbackParameters.speed != 1.0f) {
                                    controller.setPlaybackSpeed(1.0f)
                                }
                                label = "In Sync (${driftMs}ms)"
                            }
                            is ClockSyncEngine.DriftCorrectionAction.SpeedNudge -> {
                                controller.setPlaybackSpeed(action.playbackSpeed)
                                label = "Nudging ${action.playbackSpeed}x (${action.driftMs}ms)"
                            }
                            is ClockSyncEngine.DriftCorrectionAction.HardSeek -> {
                                controller.setPlaybackSpeed(1.0f)
                                controller.seekTo(action.targetPositionMs)
                                label = "Hard Resync (${action.driftMs}ms)"
                            }
                        }
                    }
                    onSpeakerTelemetryTick?.invoke(pos, driftMs, playing, label)
                }

                _progress.value = PlayerUiProgress(
                    positionMs = pos,
                    durationMs = dur,
                    isPlaying = playing,
                    currentDriftMs = driftMs,
                    correctionState = label
                )
            }
        }
    }

    fun release() {
        scheduledPlayJob?.cancel()
        monitorJob?.cancel()
        chimeJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        scope.cancel()
    }
}
