package com.github.gbandszxc.tvmediaplayer.backup

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore

class WebDavConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val db = AppSettingsDbStore(appContext)

    fun load(): WebDavConfig =
        migrateLegacyIfNeeded().let {
        WebDavConfig(
            serverUrl = db.getString(DB_KEY_SERVER_URL).orEmpty(),
            username = db.getString(DB_KEY_USERNAME).orEmpty(),
            password = db.getString(DB_KEY_PASSWORD).orEmpty(),
            remoteDirectory = db.getString(DB_KEY_REMOTE_DIRECTORY) ?: DEFAULT_REMOTE_DIRECTORY
        )
        }

    fun save(config: WebDavConfig) {
        db.putString(DB_KEY_SERVER_URL, config.serverUrl.trim())
        db.putString(DB_KEY_USERNAME, config.username.trim())
        db.putString(DB_KEY_PASSWORD, config.password)
        db.putString(DB_KEY_REMOTE_DIRECTORY, config.remoteDirectory.trim().ifBlank { DEFAULT_REMOTE_DIRECTORY })
    }

    private fun migrateLegacyIfNeeded() {
        if (db.getString(DB_KEY_SERVER_URL) != null) return
        if (!prefs.contains(KEY_SERVER_URL) && !prefs.contains(KEY_REMOTE_DIRECTORY)) return
        db.putString(DB_KEY_SERVER_URL, prefs.getString(KEY_SERVER_URL, "").orEmpty())
        db.putString(DB_KEY_USERNAME, prefs.getString(KEY_USERNAME, "").orEmpty())
        db.putString(DB_KEY_PASSWORD, prefs.getString(KEY_PASSWORD, "").orEmpty())
        db.putString(
            DB_KEY_REMOTE_DIRECTORY,
            prefs.getString(KEY_REMOTE_DIRECTORY, DEFAULT_REMOTE_DIRECTORY).orEmpty()
                .ifBlank { DEFAULT_REMOTE_DIRECTORY }
        )
    }

    companion object {
        const val DEFAULT_REMOTE_DIRECTORY = "tsm-player"
        private const val PREF_NAME = "webdav_backup_config"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMOTE_DIRECTORY = "remote_directory"
        private const val DB_KEY_SERVER_URL = "webdav.server_url"
        private const val DB_KEY_USERNAME = "webdav.username"
        private const val DB_KEY_PASSWORD = "webdav.password"
        private const val DB_KEY_REMOTE_DIRECTORY = "webdav.remote_directory"
    }
}
