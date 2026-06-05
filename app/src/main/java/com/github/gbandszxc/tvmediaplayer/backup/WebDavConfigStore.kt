package com.github.gbandszxc.tvmediaplayer.backup

import android.content.Context

class WebDavConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): WebDavConfig =
        WebDavConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            remoteDirectory = prefs.getString(KEY_REMOTE_DIRECTORY, DEFAULT_REMOTE_DIRECTORY).orEmpty()
        )

    fun save(config: WebDavConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, config.serverUrl.trim())
            .putString(KEY_USERNAME, config.username.trim())
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_REMOTE_DIRECTORY, config.remoteDirectory.trim().ifBlank { DEFAULT_REMOTE_DIRECTORY })
            .apply()
    }

    companion object {
        const val DEFAULT_REMOTE_DIRECTORY = "tsm-player"
        private const val PREF_NAME = "webdav_backup_config"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMOTE_DIRECTORY = "remote_directory"
    }
}
