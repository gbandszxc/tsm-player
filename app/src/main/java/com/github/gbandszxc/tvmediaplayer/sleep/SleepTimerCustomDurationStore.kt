package com.github.gbandszxc.tvmediaplayer.sleep

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore

class SleepTimerCustomDurationStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val db = AppSettingsDbStore(appContext)

    fun loadDurationMinutes(): Int {
        return (db.getInt(DB_KEY_DURATION_MINUTES)
            ?: prefs.takeIf { it.contains(KEY_DURATION_MINUTES) }
                ?.getInt(KEY_DURATION_MINUTES, DEFAULT_DURATION_MINUTES)
                ?.also { db.putInt(DB_KEY_DURATION_MINUTES, it) }
            ?: DEFAULT_DURATION_MINUTES)
            .coerceAtLeast(MIN_DURATION_MINUTES)
    }

    fun saveDurationMinutes(durationMinutes: Int) {
        db.putInt(DB_KEY_DURATION_MINUTES, durationMinutes.coerceAtLeast(MIN_DURATION_MINUTES))
    }

    fun clear() {
        db.remove(DB_KEY_DURATION_MINUTES)
    }

    private companion object {
        const val PREF_NAME = "sleep_timer_custom_duration"
        const val KEY_DURATION_MINUTES = "duration_minutes"
        const val DB_KEY_DURATION_MINUTES = "sleep.custom_duration_minutes"
        const val DEFAULT_DURATION_MINUTES = 30
        const val MIN_DURATION_MINUTES = 1
    }
}
