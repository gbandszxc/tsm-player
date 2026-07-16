package com.github.gbandszxc.tvmediaplayer.data.repo

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore

enum class BrowseMode { NAS, LOCAL }

class BrowserModeStore(context: Context) {
    private val settings = AppSettingsDbStore(context.applicationContext)

    fun loadMode(): BrowseMode = runCatching {
        BrowseMode.valueOf(settings.getString(KEY_MODE) ?: BrowseMode.NAS.name)
    }.getOrDefault(BrowseMode.NAS)

    fun saveMode(mode: BrowseMode) = settings.putString(KEY_MODE, mode.name)

    fun loadLocalPath(): String = settings.getString(KEY_LOCAL_PATH).orEmpty().trim('/')

    fun saveLocalPath(path: String) = settings.putString(KEY_LOCAL_PATH, path.trim('/'))

    companion object {
        private const val KEY_MODE = "browser.mode"
        private const val KEY_LOCAL_PATH = "browser.local.path"
    }
}
