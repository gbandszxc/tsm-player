package com.github.gbandszxc.tvmediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UiSettingsStoreTest {

    @Test
    fun `global scale presets cover 80 to 120 in five percent steps`() {
        assertArrayEquals(
            intArrayOf(80, 85, 90, 95, 100, 105, 110, 115, 120),
            UiSettingsStore.globalScalePresets,
        )
    }

    @Test
    fun `global scale stepper clamps at range edges`() {
        assertEquals(80, UiSettingsStore.previousGlobalScalePreset(80))
        assertEquals(95, UiSettingsStore.previousGlobalScalePreset(100))
        assertEquals(105, UiSettingsStore.nextGlobalScalePreset(100))
        assertEquals(120, UiSettingsStore.nextGlobalScalePreset(120))
    }

    @Test
    fun `global scale stepper recovers unsupported values from default preset`() {
        assertEquals(95, UiSettingsStore.previousGlobalScalePreset(77))
        assertEquals(105, UiSettingsStore.nextGlobalScalePreset(77))
    }

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
