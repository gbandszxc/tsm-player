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

    /** legacy SharedPreferences 迁移只需探测一次 */
    @Volatile
    private var legacyMigrated = false

    /**
     * 原始状态短缓存：播放页进度 ticker 每 300ms 轮询剩余分钟，
     * 避免因此对 SQLite 产生每秒多次同步查询；写入时同步刷新缓存。
     */
    @Volatile
    private var cachedRaw: SleepTimerState? = null
    @Volatile
    private var cachedRawAtMs = 0L

    override fun loadRaw(): SleepTimerState {
        val cached = cachedRaw
        if (cached != null && System.currentTimeMillis() - cachedRawAtMs < RAW_CACHE_TTL_MS) {
            return cached
        }
        val state = loadRawFromDb()
        cachedRaw = state
        cachedRawAtMs = System.currentTimeMillis()
        return state
    }

    private fun loadRawFromDb(): SleepTimerState {
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
        val duration = durationMinutes.coerceAtLeast(1)
        db.inTransaction {
            db.putBoolean(DB_KEY_ENABLED, true)
            db.putLong(DB_KEY_TARGET_EPOCH_MILLIS, targetEpochMillis)
            db.putInt(DB_KEY_DURATION_MINUTES, duration)
        }
        updateCache(SleepTimerState.Enabled(targetEpochMillis, duration))
    }

    override fun clear() {
        db.inTransaction {
            db.remove(DB_KEY_ENABLED)
            db.remove(DB_KEY_TARGET_EPOCH_MILLIS)
            db.remove(DB_KEY_DURATION_MINUTES)
        }
        updateCache(SleepTimerState.Disabled)
    }

    private fun updateCache(state: SleepTimerState) {
        cachedRaw = state
        cachedRawAtMs = System.currentTimeMillis()
    }

    private fun migrateLegacyIfNeeded() {
        if (legacyMigrated) return
        if (db.getString(DB_KEY_ENABLED) != null) {
            legacyMigrated = true
            return
        }
        if (!prefs.contains(KEY_ENABLED)) return
        db.inTransaction {
            db.putBoolean(DB_KEY_ENABLED, prefs.getBoolean(KEY_ENABLED, false))
            db.putLong(DB_KEY_TARGET_EPOCH_MILLIS, prefs.getLong(KEY_TARGET_EPOCH_MILLIS, 0L))
            db.putInt(DB_KEY_DURATION_MINUTES, prefs.getInt(KEY_DURATION_MINUTES, 0))
        }
        legacyMigrated = true
    }

    companion object {
        private const val PREF_NAME = "sleep_timer"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TARGET_EPOCH_MILLIS = "target_epoch_millis"
        private const val KEY_DURATION_MINUTES = "duration_minutes"
        private const val DB_KEY_ENABLED = "sleep.timer.enabled"
        private const val DB_KEY_TARGET_EPOCH_MILLIS = "sleep.timer.target_epoch_millis"
        private const val DB_KEY_DURATION_MINUTES = "sleep.timer.duration_minutes"
        private const val RAW_CACHE_TTL_MS = 1000L
    }
}
