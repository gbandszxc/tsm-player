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
            url = db.getString(DB_KEY_URL).orEmpty(),
            username = db.getString(DB_KEY_USERNAME).orEmpty(),
            password = db.getString(DB_KEY_PASSWORD).orEmpty()
        )
        }

    fun save(config: WebDavConfig) {
        db.putString(DB_KEY_URL, config.url.trim())
        db.putString(DB_KEY_USERNAME, config.username.trim())
        db.putString(DB_KEY_PASSWORD, config.password)
        db.remove(DB_KEY_REMOTE_DIRECTORY)
    }

    private fun migrateLegacyIfNeeded() {
        if (db.getString(DB_KEY_URL) != null) return
        db.getString(DB_KEY_SERVER_URL)?.let { serverUrl ->
            val remoteDirectory = db.getString(DB_KEY_REMOTE_DIRECTORY).orEmpty()
            db.putString(DB_KEY_URL, mergeUrlAndDirectory(serverUrl, remoteDirectory))
            db.remove(DB_KEY_SERVER_URL)
            db.remove(DB_KEY_REMOTE_DIRECTORY)
            return
        }
        if (!prefs.contains(KEY_SERVER_URL) && !prefs.contains(KEY_REMOTE_DIRECTORY)) return
        db.putString(
            DB_KEY_URL,
            mergeUrlAndDirectory(
                prefs.getString(KEY_SERVER_URL, "").orEmpty(),
                prefs.getString(KEY_REMOTE_DIRECTORY, "").orEmpty()
            )
        )
        db.putString(DB_KEY_USERNAME, prefs.getString(KEY_USERNAME, "").orEmpty())
        db.putString(DB_KEY_PASSWORD, prefs.getString(KEY_PASSWORD, "").orEmpty())
    }

    private fun mergeUrlAndDirectory(serverUrl: String, remoteDirectory: String): String {
        val base = serverUrl.trim().trimEnd('/')
        val dir = remoteDirectory.trim().trim('/').takeIf { it.isNotBlank() }
        return if (base.isBlank()) "" else if (dir == null) base else "$base/$dir"
    }

    companion object {
        private const val PREF_NAME = "webdav_backup_config"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMOTE_DIRECTORY = "remote_directory"
        private const val DB_KEY_URL = "webdav.url"
        private const val DB_KEY_SERVER_URL = "webdav.server_url"
        private const val DB_KEY_USERNAME = "webdav.username"
        private const val DB_KEY_PASSWORD = "webdav.password"
        private const val DB_KEY_REMOTE_DIRECTORY = "webdav.remote_directory"
    }
}
