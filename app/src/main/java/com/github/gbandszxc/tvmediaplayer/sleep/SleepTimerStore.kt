package com.github.gbandszxc.tvmediaplayer.sleep

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore

interface SleepTimerStoreContract {
    fun load(nowMs: Long = System.currentTimeMillis()): SleepTimerState
    fun loadRaw(): SleepTimerState
    fun saveEnabled(targetEpochMillis: Long, durationMinutes: Int)
    fun clear()
}

class SleepTimerStore(context: Context) : SleepTimerStoreContract {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val db = AppSettingsDbStore(appContext)

    override fun loadRaw(): SleepTimerState {
        migrateLegacyIfNeeded()
        val enabled = db.getBoolean(DB_KEY_ENABLED) ?: false
        if (!enabled) return SleepTimerState.Disabled
        val target = db.getLong(DB_KEY_TARGET_EPOCH_MILLIS) ?: 0L
        val duration = db.getInt(DB_KEY_DURATION_MINUTES) ?: 0
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
        db.putBoolean(DB_KEY_ENABLED, true)
        db.putLong(DB_KEY_TARGET_EPOCH_MILLIS, targetEpochMillis)
        db.putInt(DB_KEY_DURATION_MINUTES, durationMinutes.coerceAtLeast(1))
    }

    override fun clear() {
        db.remove(DB_KEY_ENABLED)
        db.remove(DB_KEY_TARGET_EPOCH_MILLIS)
        db.remove(DB_KEY_DURATION_MINUTES)
    }

    private fun migrateLegacyIfNeeded() {
        if (db.getString(DB_KEY_ENABLED) != null) return
        if (!prefs.contains(KEY_ENABLED)) return
        db.putBoolean(DB_KEY_ENABLED, prefs.getBoolean(KEY_ENABLED, false))
        db.putLong(DB_KEY_TARGET_EPOCH_MILLIS, prefs.getLong(KEY_TARGET_EPOCH_MILLIS, 0L))
        db.putInt(DB_KEY_DURATION_MINUTES, prefs.getInt(KEY_DURATION_MINUTES, 0))
    }

    companion object {
        private const val PREF_NAME = "sleep_timer"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TARGET_EPOCH_MILLIS = "target_epoch_millis"
        private const val KEY_DURATION_MINUTES = "duration_minutes"
        private const val DB_KEY_ENABLED = "sleep.timer.enabled"
        private const val DB_KEY_TARGET_EPOCH_MILLIS = "sleep.timer.target_epoch_millis"
        private const val DB_KEY_DURATION_MINUTES = "sleep.timer.duration_minutes"
    }
}
