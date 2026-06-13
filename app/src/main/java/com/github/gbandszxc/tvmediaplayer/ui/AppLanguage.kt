package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val storageValue: String, val localeTag: String?) {
    SYSTEM("system", null),
    CHINESE("zh", "zh-CN"),
    ENGLISH("en", "en");

    companion object {
        fun fromStorageValue(value: String?): AppLanguage =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object AppLocaleApplier {
    fun wrap(base: Context): Context {
        val language = UiSettingsStore.appLanguage(base)
        val localeTag = language.localeTag ?: return base
        val locale = Locale.forLanguageTag(localeTag)

        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return base.createConfigurationContext(config)
    }
}
