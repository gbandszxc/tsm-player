package com.github.gbandszxc.tvmediaplayer.sleep

import kotlin.math.ceil

class SleepTimerManager(
    private val store: SleepTimerStoreContract
) {
    fun start(durationMinutes: Int, nowMs: Long = System.currentTimeMillis()): SleepTimerState.Enabled {
        val safeDuration = durationMinutes.coerceAtLeast(1)
        val target = nowMs + safeDuration * MILLIS_PER_MINUTE
        store.saveEnabled(targetEpochMillis = target, durationMinutes = safeDuration)
        return SleepTimerState.Enabled(target, safeDuration)
    }

    fun cancel() {
        store.clear()
    }

    fun currentState(nowMs: Long = System.currentTimeMillis()): SleepTimerState {
        return store.load(nowMs)
    }

    fun remainingMillis(nowMs: Long = System.currentTimeMillis()): Long? {
        val state = store.load(nowMs) as? SleepTimerState.Enabled ?: return null
        return (state.targetEpochMillis - nowMs).takeIf { it > 0L }
    }

    fun remainingMinutesCeil(nowMs: Long = System.currentTimeMillis()): Int? {
        val remaining = remainingMillis(nowMs) ?: return null
        return ceil(remaining / MILLIS_PER_MINUTE.toDouble()).toInt().coerceAtLeast(1)
    }

    fun executeIfDue(nowMs: Long = System.currentTimeMillis(), action: () -> Unit): Boolean {
        val state = store.loadRaw() as? SleepTimerState.Enabled ?: return false
        if (state.targetEpochMillis > nowMs) return false
        store.clear()
        action()
        return true
    }

    companion object {
        const val MILLIS_PER_MINUTE: Long = 60_000L
    }
}
