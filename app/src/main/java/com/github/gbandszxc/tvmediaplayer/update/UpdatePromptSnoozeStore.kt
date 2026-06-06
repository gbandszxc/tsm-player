package com.github.gbandszxc.tvmediaplayer.update

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore

class UpdatePromptSnoozeStore(context: Context) {
    private val db = AppSettingsDbStore(context.applicationContext)

    fun snoozeOnce(versionName: String) {
        onceVersion = normalize(versionName)
    }

    fun snoozeForSevenDays(versionName: String, nowMs: Long = System.currentTimeMillis()) {
        val normalized = normalize(versionName)
        db.putString(KEY_MODE, MODE_SEVEN_DAYS)
        db.putString(KEY_VERSION, normalized)
        db.putLong(KEY_UNTIL_MS, nowMs + SEVEN_DAYS_MS)
    }

    fun snoozeUntilNextVersion(versionName: String) {
        db.putString(KEY_MODE, MODE_UNTIL_NEXT_VERSION)
        db.putString(KEY_VERSION, normalize(versionName))
        db.remove(KEY_UNTIL_MS)
    }

    fun shouldSkipAutomaticPrompt(
        versionName: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val normalized = normalize(versionName)
        if (onceVersion == normalized) {
            onceVersion = null
            return true
        }

        return when (db.getString(KEY_MODE)) {
            MODE_SEVEN_DAYS -> shouldSkipSevenDayPrompt(nowMs)
            MODE_UNTIL_NEXT_VERSION -> db.getString(KEY_VERSION) == normalized
            else -> false
        }
    }

    fun clear() {
        onceVersion = null
        db.removePrefix(KEY_PREFIX)
    }

    private fun shouldSkipSevenDayPrompt(nowMs: Long): Boolean {
        val untilMs = db.getLong(KEY_UNTIL_MS) ?: return false
        if (nowMs < untilMs) return true
        clear()
        return false
    }

    private fun normalize(versionName: String): String =
        versionName.trim().removePrefix("v").removePrefix("V")

    companion object {
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        private var onceVersion: String? = null
        private const val KEY_PREFIX = "update_snooze_"
        private const val KEY_MODE = "${KEY_PREFIX}mode"
        private const val KEY_VERSION = "${KEY_PREFIX}version"
        private const val KEY_UNTIL_MS = "${KEY_PREFIX}until_ms"
        private const val MODE_SEVEN_DAYS = "seven_days"
        private const val MODE_UNTIL_NEXT_VERSION = "until_next_version"
    }
}
