package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore

object UiSettingsStore {
    private const val PREF_NAME = "ui_settings"
    private const val DB_PREFIX = "ui."
    private const val KEY_GLOBAL_SCALE_PERCENT = "global_scale_percent"
    private const val KEY_PLAYBACK_LYRICS_FONT_SP = "playback_lyrics_font_sp"
    private const val KEY_FULLSCREEN_LYRICS_FONT_SP = "fullscreen_lyrics_font_sp"
    private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
    private const val KEY_REMEMBER_LAST_PLAYBACK = "remember_last_playback"
    private const val KEY_PLAYBACK_LYRICS_LINE_SPACING = "playback_lyrics_line_spacing"
    private const val KEY_FULLSCREEN_LYRICS_LINE_SPACING = "fullscreen_lyrics_line_spacing"
    private const val KEY_SLEEP_ADMIN_PROMPT_SHOWN = "sleep_admin_prompt_shown"

    val globalScalePresets: IntArray = intArrayOf(90, 95, 100, 105, 110)
    const val defaultGlobalScalePercent: Int = 100
    const val defaultPlaybackLyricsFontSp: Int = 20
    const val defaultFullscreenLyricsFontSp: Int = 28
    const val minLyricsFontSp: Int = 14
    const val maxLyricsFontSp: Int = 56
    const val defaultPlaybackLyricsLineSpacing: Float = 1.2f
    const val defaultFullscreenLyricsLineSpacing: Float = 1.5f
    const val minLyricsLineSpacing: Float = 1.0f
    const val maxLyricsLineSpacing: Float = 3.0f

    fun globalScalePercent(context: Context): Int {
        val value = db(context).getInt(dbKey(KEY_GLOBAL_SCALE_PERCENT))
            ?: prefs(context).takeIf { it.contains(KEY_GLOBAL_SCALE_PERCENT) }
                ?.getInt(KEY_GLOBAL_SCALE_PERCENT, defaultGlobalScalePercent)
                ?.also { db(context).putInt(dbKey(KEY_GLOBAL_SCALE_PERCENT), it) }
            ?: defaultGlobalScalePercent
        return if (globalScalePresets.contains(value)) value else defaultGlobalScalePercent
    }

    fun setGlobalScalePercent(context: Context, value: Int) {
        if (!globalScalePresets.contains(value)) return
        db(context).putInt(dbKey(KEY_GLOBAL_SCALE_PERCENT), value)
    }

    fun cycleGlobalScalePreset(context: Context): Int {
        val current = globalScalePercent(context)
        val index = globalScalePresets.indexOf(current).coerceAtLeast(0)
        val nextIndex = (index + 1) % globalScalePresets.size
        val next = globalScalePresets[nextIndex]
        setGlobalScalePercent(context, next)
        return next
    }

    fun playbackLyricsFontSp(context: Context): Int {
        val value = db(context).getInt(dbKey(KEY_PLAYBACK_LYRICS_FONT_SP))
            ?: prefs(context).takeIf { it.contains(KEY_PLAYBACK_LYRICS_FONT_SP) }
                ?.getInt(KEY_PLAYBACK_LYRICS_FONT_SP, defaultPlaybackLyricsFontSp)
                ?.also { db(context).putInt(dbKey(KEY_PLAYBACK_LYRICS_FONT_SP), it) }
            ?: defaultPlaybackLyricsFontSp
        return value.coerceIn(minLyricsFontSp, maxLyricsFontSp)
    }

    fun setPlaybackLyricsFontSp(context: Context, value: Int) {
        db(context).putInt(dbKey(KEY_PLAYBACK_LYRICS_FONT_SP), value.coerceIn(minLyricsFontSp, maxLyricsFontSp))
    }

    fun fullscreenLyricsFontSp(context: Context): Int {
        val value = db(context).getInt(dbKey(KEY_FULLSCREEN_LYRICS_FONT_SP))
            ?: prefs(context).takeIf { it.contains(KEY_FULLSCREEN_LYRICS_FONT_SP) }
                ?.getInt(KEY_FULLSCREEN_LYRICS_FONT_SP, defaultFullscreenLyricsFontSp)
                ?.also { db(context).putInt(dbKey(KEY_FULLSCREEN_LYRICS_FONT_SP), it) }
            ?: defaultFullscreenLyricsFontSp
        return value.coerceIn(minLyricsFontSp, maxLyricsFontSp)
    }

    fun setFullscreenLyricsFontSp(context: Context, value: Int) {
        db(context).putInt(dbKey(KEY_FULLSCREEN_LYRICS_FONT_SP), value.coerceIn(minLyricsFontSp, maxLyricsFontSp))
    }

    fun keepScreenAwake(context: Context): Boolean {
        return db(context).getBoolean(dbKey(KEY_KEEP_SCREEN_AWAKE))
            ?: prefs(context).takeIf { it.contains(KEY_KEEP_SCREEN_AWAKE) }
                ?.getBoolean(KEY_KEEP_SCREEN_AWAKE, true)
                ?.also { db(context).putBoolean(dbKey(KEY_KEEP_SCREEN_AWAKE), it) }
            ?: true
    }

    fun setKeepScreenAwake(context: Context, enabled: Boolean) {
        db(context).putBoolean(dbKey(KEY_KEEP_SCREEN_AWAKE), enabled)
    }

    fun rememberLastPlayback(context: Context): Boolean {
        return db(context).getBoolean(dbKey(KEY_REMEMBER_LAST_PLAYBACK))
            ?: prefs(context).takeIf { it.contains(KEY_REMEMBER_LAST_PLAYBACK) }
                ?.getBoolean(KEY_REMEMBER_LAST_PLAYBACK, true)
                ?.also { db(context).putBoolean(dbKey(KEY_REMEMBER_LAST_PLAYBACK), it) }
            ?: true
    }

    fun setRememberLastPlayback(context: Context, enabled: Boolean) {
        db(context).putBoolean(dbKey(KEY_REMEMBER_LAST_PLAYBACK), enabled)
    }

    fun playbackLyricsLineSpacing(context: Context): Float {
        val value = db(context).getFloat(dbKey(KEY_PLAYBACK_LYRICS_LINE_SPACING))
            ?: prefs(context).takeIf { it.contains(KEY_PLAYBACK_LYRICS_LINE_SPACING) }
                ?.getFloat(KEY_PLAYBACK_LYRICS_LINE_SPACING, defaultPlaybackLyricsLineSpacing)
                ?.also { db(context).putFloat(dbKey(KEY_PLAYBACK_LYRICS_LINE_SPACING), it) }
            ?: defaultPlaybackLyricsLineSpacing
        return value.coerceIn(minLyricsLineSpacing, maxLyricsLineSpacing)
    }

    fun setPlaybackLyricsLineSpacing(context: Context, value: Float) {
        db(context).putFloat(dbKey(KEY_PLAYBACK_LYRICS_LINE_SPACING), value.coerceIn(minLyricsLineSpacing, maxLyricsLineSpacing))
    }

    fun fullscreenLyricsLineSpacing(context: Context): Float {
        val value = db(context).getFloat(dbKey(KEY_FULLSCREEN_LYRICS_LINE_SPACING))
            ?: prefs(context).takeIf { it.contains(KEY_FULLSCREEN_LYRICS_LINE_SPACING) }
                ?.getFloat(KEY_FULLSCREEN_LYRICS_LINE_SPACING, defaultFullscreenLyricsLineSpacing)
                ?.also { db(context).putFloat(dbKey(KEY_FULLSCREEN_LYRICS_LINE_SPACING), it) }
            ?: defaultFullscreenLyricsLineSpacing
        return value.coerceIn(minLyricsLineSpacing, maxLyricsLineSpacing)
    }

    fun setFullscreenLyricsLineSpacing(context: Context, value: Float) {
        db(context).putFloat(dbKey(KEY_FULLSCREEN_LYRICS_LINE_SPACING), value.coerceIn(minLyricsLineSpacing, maxLyricsLineSpacing))
    }

    fun sleepAdminPromptShown(context: Context): Boolean {
        return db(context).getBoolean(dbKey(KEY_SLEEP_ADMIN_PROMPT_SHOWN))
            ?: prefs(context).takeIf { it.contains(KEY_SLEEP_ADMIN_PROMPT_SHOWN) }
                ?.getBoolean(KEY_SLEEP_ADMIN_PROMPT_SHOWN, false)
                ?.also { db(context).putBoolean(dbKey(KEY_SLEEP_ADMIN_PROMPT_SHOWN), it) }
            ?: false
    }

    fun setSleepAdminPromptShown(context: Context, shown: Boolean) {
        db(context).putBoolean(dbKey(KEY_SLEEP_ADMIN_PROMPT_SHOWN), shown)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun db(context: Context) = AppSettingsDbStore(context)

    private fun dbKey(key: String): String = "$DB_PREFIX$key"
}
