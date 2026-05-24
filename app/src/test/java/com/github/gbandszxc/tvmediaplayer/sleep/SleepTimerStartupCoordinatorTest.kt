package com.github.gbandszxc.tvmediaplayer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SleepTimerStartupCoordinatorTest {

    @Test
    fun `process startup clears active sleep timer once`() {
        val store = FakeStore()
        val coordinator = SleepTimerStartupCoordinator()
        store.saveEnabled(targetEpochMillis = 120_000L, durationMinutes = 2)

        val first = coordinator.clearActiveTimerOnce(store)
        store.saveEnabled(targetEpochMillis = 180_000L, durationMinutes = 3)
        val second = coordinator.clearActiveTimerOnce(store)

        assertTrue(first)
        assertFalse(second)
        assertEquals(SleepTimerState.Enabled(180_000L, 3), store.state)
    }

    @Test
    fun `process startup keeps latest custom duration`() {
        val context = RuntimeEnvironment.getApplication()
        val timerStore = SleepTimerStore(context)
        val customStore = SleepTimerCustomDurationStore(context)
        val coordinator = SleepTimerStartupCoordinator()
        timerStore.clear()
        customStore.clear()
        timerStore.saveEnabled(targetEpochMillis = 120_000L, durationMinutes = 2)
        customStore.saveDurationMinutes(75)

        coordinator.clearActiveTimerOnce(timerStore)

        assertEquals(SleepTimerState.Disabled, timerStore.loadRaw())
        assertEquals(75, customStore.loadDurationMinutes())
    }

    private class FakeStore : SleepTimerStoreContract {
        var state: SleepTimerState = SleepTimerState.Disabled

        override fun load(nowMs: Long): SleepTimerState = state

        override fun loadRaw(): SleepTimerState = state

        override fun saveEnabled(targetEpochMillis: Long, durationMinutes: Int) {
            state = SleepTimerState.Enabled(targetEpochMillis, durationMinutes)
        }

        override fun clear() {
            state = SleepTimerState.Disabled
        }
    }
}
