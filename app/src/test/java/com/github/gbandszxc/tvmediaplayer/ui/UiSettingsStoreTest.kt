package com.github.gbandszxc.tvmediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UiSettingsStoreTest {

    @Test
    fun `app language defaults to system and persists supported manual choices`() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("tsm-player.db")

        assertEquals(AppLanguage.SYSTEM, UiSettingsStore.appLanguage(context))

        UiSettingsStore.setAppLanguage(context, AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, UiSettingsStore.appLanguage(context))

        UiSettingsStore.setAppLanguage(context, AppLanguage.CHINESE)
        assertEquals(AppLanguage.CHINESE, UiSettingsStore.appLanguage(context))

        UiSettingsStore.setAppLanguage(context, AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, UiSettingsStore.appLanguage(context))
    }
}
