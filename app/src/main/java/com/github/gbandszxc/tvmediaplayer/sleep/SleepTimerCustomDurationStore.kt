package com.github.gbandszxc.tvmediaplayer.sleep

import android.content.Context

class SleepTimerCustomDurationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadDurationMinutes(): Int {
        return prefs.getInt(KEY_DURATION_MINUTES, DEFAULT_DURATION_MINUTES)
            .coerceAtLeast(MIN_DURATION_MINUTES)
    }

    fun saveDurationMinutes(durationMinutes: Int) {
        prefs.edit()
            .putInt(KEY_DURATION_MINUTES, durationMinutes.coerceAtLeast(MIN_DURATION_MINUTES))
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_DURATION_MINUTES)
            .apply()
    }

    private companion object {
        const val PREF_NAME = "sleep_timer_custom_duration"
        const val KEY_DURATION_MINUTES = "duration_minutes"
        const val DEFAULT_DURATION_MINUTES = 30
        const val MIN_DURATION_MINUTES = 1
    }
}
