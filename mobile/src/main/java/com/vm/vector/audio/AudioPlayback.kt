package com.vm.vector.audio

/**
 * Discrete playback speeds and the podcast-style resume point.
 * Recordings of at least 45s rewind 15s on a cold start so the sentence before
 * the pause is still there. Shorter voice notes resume at the exact position.
 * Finishing a take (or stopping within 2s of the end) starts over next time.
 */
object AudioPlayback {
    val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
    val SEEK_CHOICES = listOf(1, 5, 10, 15, 30, 60)

    const val REWIND_MS = 15_000L
    const val LONG_RECORDING_MS = 45_000L
    private const val END_SLACK_MS = 2_000L

    fun nearestSpeed(speed: Float): Float = SPEEDS.minBy { kotlin.math.abs(it - speed) }

    fun speedLabel(speed: Float): String {
        val level = nearestSpeed(speed)
        val text = if (level % 1f == 0f) level.toInt().toString() else level.toString()
        return "${text}x"
    }

    fun startPosition(positionMs: Long, durationMs: Long, completed: Boolean): Long {
        if (completed) return 0L
        if (durationMs > 0L && positionMs >= durationMs - END_SLACK_MS) return 0L
        val safe = positionMs.coerceAtLeast(0L)
        val longEnough = durationMs >= LONG_RECORDING_MS ||
            (durationMs <= 0L && safe >= LONG_RECORDING_MS)
        val rewound = if (longEnough) (safe - REWIND_MS).coerceAtLeast(0L) else safe
        return if (durationMs > 0L) rewound.coerceAtMost(durationMs) else rewound
    }

    /** Saved timeline was the broken MediaRecorder duration, not the real audio. */
    fun timelineWasInflated(savedDurationMs: Long, actualDurationMs: Long): Boolean {
        if (savedDurationMs <= 0L || actualDurationMs <= 0L) return false
        return savedDurationMs > actualDurationMs * 3 / 2 &&
            savedDurationMs - actualDurationMs > 5_000L
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSec = (ms / 1000L).toInt()
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
