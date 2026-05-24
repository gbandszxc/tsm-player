package com.github.gbandszxc.tvmediaplayer.sleep

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SleepTimerCustomDurationStoreTest {

    @Test
    fun `load returns default custom duration when never saved`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SleepTimerCustomDurationStore(context)
        store.clear()

        assertEquals(30, store.loadDurationMinutes())
    }

    @Test
    fun `save keeps only latest custom duration`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SleepTimerCustomDurationStore(context)
        store.clear()

        store.saveDurationMinutes(45)
        store.saveDurationMinutes(125)

        assertEquals(125, store.loadDurationMinutes())
    }

    @Test
    fun `save coerces invalid custom duration to minimum`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SleepTimerCustomDurationStore(context)
        store.clear()

        store.saveDurationMinutes(0)

        assertEquals(1, store.loadDurationMinutes())
    }
}
