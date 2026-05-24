package com.github.gbandszxc.tvmediaplayer.sleep

import android.content.Context

interface SleepTimerStoreContract {
    fun load(nowMs: Long = System.currentTimeMillis()): SleepTimerState
    fun loadRaw(): SleepTimerState
    fun saveEnabled(targetEpochMillis: Long, durationMinutes: Int)
    fun clear()
}

class SleepTimerStore(context: Context) : SleepTimerStoreContract {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun loadRaw(): SleepTimerState {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        if (!enabled) return SleepTimerState.Disabled
        val target = prefs.getLong(KEY_TARGET_EPOCH_MILLIS, 0L)
        val duration = prefs.getInt(KEY_DURATION_MINUTES, 0)
        if (target <= 0L || duration <= 0) return SleepTimerState.Disabled
        return SleepTimerState.Enabled(target, duration)
    }

    override fun load(nowMs: Long): SleepTimerState {
        val state = loadRaw()
        if (state is SleepTimerState.Enabled && state.targetEpochMillis <= nowMs) {
            return SleepTimerState.Disabled
        }
        return state
    }

    override fun saveEnabled(targetEpochMillis: Long, durationMinutes: Int) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_TARGET_EPOCH_MILLIS, targetEpochMillis)
            .putInt(KEY_DURATION_MINUTES, durationMinutes.coerceAtLeast(1))
            .apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_ENABLED)
            .remove(KEY_TARGET_EPOCH_MILLIS)
            .remove(KEY_DURATION_MINUTES)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "sleep_timer"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TARGET_EPOCH_MILLIS = "target_epoch_millis"
        private const val KEY_DURATION_MINUTES = "duration_minutes"
    }
}
