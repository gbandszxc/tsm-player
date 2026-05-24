package com.github.gbandszxc.tvmediaplayer.sleep

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SleepTimerStoreTest {

    @Test
    fun `save and load enabled timer`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SleepTimerStore(context)
        store.clear()

        store.saveEnabled(targetEpochMillis = 120_000L, durationMinutes = 2)

        assertEquals(SleepTimerState.Enabled(120_000L, 2), store.load(nowMs = 1_000L))
    }

    @Test
    fun `load hides expired timer without consuming due action state`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SleepTimerStore(context)
        store.clear()
        store.saveEnabled(targetEpochMillis = 120_000L, durationMinutes = 2)

        assertEquals(SleepTimerState.Disabled, store.load(nowMs = 120_000L))
        assertEquals(SleepTimerState.Enabled(120_000L, 2), store.loadRaw())
    }
}
