package com.github.gbandszxc.tvmediaplayer.ui

class PlaybackSeekController(
    private val minCommitIntervalMs: Long = DEFAULT_MIN_COMMIT_INTERVAL_MS,
    private val idleCommitDelayMs: Long = DEFAULT_IDLE_COMMIT_DELAY_MS,
) {

    data class SeekUpdate(
        val previewPositionMs: Long,
        val commitPositionMs: Long?,
    )

    data class PendingCommit(
        val positionMs: Long,
    )

    private var pendingPositionMs: Long? = null
    private var lastCommitAtMs: Long? = null
    private var lastInputAtMs: Long? = null

    val isPreviewActive: Boolean
        get() = pendingPositionMs != null

    fun onDirectionKeyDown(
        direction: Int,
        repeatCount: Int,
        currentPositionMs: Long,
        durationMs: Long,
        nowMs: Long,
    ): SeekUpdate {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val basePosition = pendingPositionMs ?: currentPositionMs
        val delta = stepMs(repeatCount) * direction.coerceIn(-1, 1)
        val nextPosition = (basePosition + delta).coerceIn(0L, safeDuration)
        pendingPositionMs = nextPosition
        lastInputAtMs = nowMs

        val shouldCommit = lastCommitAtMs?.let { nowMs - it >= minCommitIntervalMs } ?: true
        val commitPosition = if (shouldCommit) {
            lastCommitAtMs = nowMs
            nextPosition
        } else {
            null
        }
        return SeekUpdate(previewPositionMs = nextPosition, commitPositionMs = commitPosition)
    }

    fun commitPending(): PendingCommit? {
        val position = pendingPositionMs ?: return null
        reset()
        return PendingCommit(position)
    }

    fun commitIfIdle(nowMs: Long): PendingCommit? {
        val lastInput = lastInputAtMs ?: return null
        if (nowMs - lastInput < idleCommitDelayMs) return null
        return commitPending()
    }

    fun reset() {
        pendingPositionMs = null
        lastCommitAtMs = null
        lastInputAtMs = null
    }

    companion object {
        const val DEFAULT_MIN_COMMIT_INTERVAL_MS = 250L
        const val DEFAULT_IDLE_COMMIT_DELAY_MS = 180L

        fun stepMs(repeatCount: Int): Long {
            return when {
                repeatCount < 5 -> 5_000L
                repeatCount < 12 -> 10_000L
                repeatCount < 25 -> 30_000L
                repeatCount < 40 -> 60_000L
                else -> 90_000L
            }
        }
    }
}
