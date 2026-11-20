package com.example.sync

import com.example.model.ConnectionQuality
import com.example.model.PlaybackSnapshot
import com.example.model.SyncHealthStatus
import kotlin.math.abs

/**
 * NTP-style clock offset and playback drift synchronization engine.
 *
 * Uses symmetric exchange timestamps:
 *  t1 = client send time
 *  t2 = server receive time
 *  t3 = server send time
 *  t4 = client receive time
 *
 *  RTT = (t4 - t1) - (t3 - t2)
 *  offset = ((t2 - t1) + (t3 - t4)) / 2
 *  such that serverTime ~= localTime + offset.
 */
class ClockSyncEngine(
    private val clockProvider: () -> Long = { System.currentTimeMillis() },
    private val maxSamples: Int = 10
) {
    data class SyncSample(
        val rttMs: Long,
        val offsetMs: Long,
        val recordedAtLocalMs: Long
    )

    sealed class DriftCorrectionAction {
        data object InSync : DriftCorrectionAction()
        data class SpeedNudge(val playbackSpeed: Float, val driftMs: Long) : DriftCorrectionAction()
        data class HardSeek(val targetPositionMs: Long, val driftMs: Long) : DriftCorrectionAction()
    }

    private val samples = mutableListOf<SyncSample>()
    @Volatile
    var clockOffsetMs: Long = 0L
        private set

    @Volatile
    var estimatedRttMs: Long = 0L
        private set

    val sampleCount: Int
        @Synchronized get() = samples.size

    @Synchronized
    fun reset() {
        samples.clear()
        clockOffsetMs = 0L
        estimatedRttMs = 0L
    }

    @Synchronized
    fun recordPong(t1ClientMs: Long, t2ServerRecvMs: Long, t3ServerSendMs: Long, t4ClientRecvMs: Long = clockProvider()): SyncSample {
        val rawRtt = ((t4ClientRecvMs - t1ClientMs) - (t3ServerSendMs - t2ServerRecvMs)).coerceAtLeast(0L)
        val rawOffset = ((t2ServerRecvMs - t1ClientMs) + (t3ServerSendMs - t4ClientRecvMs)) / 2L
        val sample = SyncSample(rttMs = rawRtt, offsetMs = rawOffset, recordedAtLocalMs = t4ClientRecvMs)

        samples.add(sample)
        if (samples.size > maxSamples) {
            samples.removeAt(0)
        }

        recalculateFilteredEstimate()
        return sample
    }

    private fun recalculateFilteredEstimate() {
        if (samples.isEmpty()) return

        // Sort by lowest RTT (lowest queuing jitter gives the truest clock offset).
        val sortedByRtt = samples.sortedBy { it.rttMs }
        val medianRtt = sortedByRtt[sortedByRtt.size / 2].rttMs.coerceAtLeast(1L)

        // Filter out network spike outliers (> 2.5x median RTT), keeping at least top 3 lowest-RTT samples.
        val bestSamples = sortedByRtt.filterIndexed { index, sample ->
            index < 3 || sample.rttMs <= (medianRtt * 2.5).toLong() + 10L
        }

        // Take up to the 5 lowest-RTT samples and average their offsets.
        val core = bestSamples.take(5)
        clockOffsetMs = core.map { it.offsetMs }.average().toLong()
        estimatedRttMs = core.map { it.rttMs }.average().toLong()
    }

    fun serverTimeNowMs(): Long = clockProvider() + clockOffsetMs

    fun computeLocalDelayUntilServerTimeMs(scheduledServerTimeMs: Long): Long {
        return (scheduledServerTimeMs - serverTimeNowMs()).coerceAtLeast(0L)
    }

    /**
     * Computes where the playback head should be right now in milliseconds,
     * factoring in the scheduled start timestamp and this speaker's hardware delay calibration.
     * Positive [deviceDelayCalibrationMs] shifts playback slightly earlier (reduces audio delay),
     * negative shifts slightly later.
     */
    fun computeExpectedPositionMs(
        snapshot: PlaybackSnapshot,
        deviceDelayCalibrationMs: Long = 0L
    ): Long {
        if (!snapshot.isPlaying || snapshot.scheduledServerTimeMs <= 0L) {
            return snapshot.startPositionMs.coerceAtLeast(0L)
        }
        val elapsedSinceSchedule = serverTimeNowMs() - snapshot.scheduledServerTimeMs
        return (snapshot.startPositionMs + elapsedSinceSchedule + deviceDelayCalibrationMs).coerceAtLeast(0L)
    }

    /**
     * Decides whether to leave playback untouched, apply a smooth micro-speed nudge (35..180ms),
     * or execute a hard seek (>180ms).
     *
     * @param actualPositionMs Current player position.
     * @param expectedPositionMs Ideal synced position.
     * @param toleranceMs Acceptable deadband (default ~45ms).
     */
    fun evaluateDriftCorrection(
        actualPositionMs: Long,
        expectedPositionMs: Long,
        toleranceMs: Long = 45L
    ): DriftCorrectionAction {
        // driftMs > 0 means player is behind expected position (needs to catch up).
        val driftMs = expectedPositionMs - actualPositionMs
        val absDrift = abs(driftMs)

        return when {
            absDrift <= toleranceMs -> DriftCorrectionAction.InSync
            absDrift <= 180L -> {
                val speed = if (driftMs > 0) 1.035f else 0.965f
                DriftCorrectionAction.SpeedNudge(playbackSpeed = speed, driftMs = driftMs)
            }
            else -> DriftCorrectionAction.HardSeek(targetPositionMs = expectedPositionMs, driftMs = driftMs)
        }
    }

    companion object {
        fun classifyConnectionQuality(rttMs: Long, isOnline: Boolean): ConnectionQuality {
            if (!isOnline) return ConnectionQuality.OFFLINE
            return when {
                rttMs <= 25L -> ConnectionQuality.EXCELLENT
                rttMs <= 65L -> ConnectionQuality.GOOD
                rttMs <= 150L -> ConnectionQuality.FAIR
                else -> ConnectionQuality.POOR
            }
        }

        fun classifyHealthStatus(
            isOnline: Boolean,
            isAdmin: Boolean,
            driftMs: Long,
            toleranceMs: Long = 45L
        ): SyncHealthStatus {
            if (isAdmin) return SyncHealthStatus.ADMIN
            if (!isOnline) return SyncHealthStatus.DISCONNECTED
            return if (abs(driftMs) <= toleranceMs * 2L) {
                SyncHealthStatus.SYNCHRONIZED
            } else {
                SyncHealthStatus.DRIFT_WARNING
            }
        }
    }
}
