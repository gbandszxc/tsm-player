package com.github.gbandszxc.tvmediaplayer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerManagerTest {

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

    @Test
    fun `start stores target time and duration`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)

        manager.start(durationMinutes = 30, nowMs = 1_000L)

        assertEquals(SleepTimerState.Enabled(1_801_000L, 30), store.state)
    }

    @Test
    fun `remaining minutes rounds up while positive`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)
        store.state = SleepTimerState.Enabled(targetEpochMillis = 1_801_000L, durationMinutes = 30)

        assertEquals(30, manager.remainingMinutesCeil(nowMs = 1_000L))
        assertEquals(30, manager.remainingMinutesCeil(nowMs = 60_001L))
        assertEquals(29, manager.remainingMinutesCeil(nowMs = 61_000L))
    }

    @Test
    fun `remaining minutes returns null when disabled or expired`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)

        assertNull(manager.remainingMinutesCeil(nowMs = 1_000L))

        store.state = SleepTimerState.Enabled(targetEpochMillis = 2_000L, durationMinutes = 1)

        assertNull(manager.remainingMinutesCeil(nowMs = 2_000L))
        assertNull(manager.remainingMinutesCeil(nowMs = 3_000L))
    }

    @Test
    fun `execute if due clears once and calls action`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)
        var executed = false
        store.state = SleepTimerState.Enabled(targetEpochMillis = 2_000L, durationMinutes = 1)

        val first = manager.executeIfDue(nowMs = 2_000L) { executed = true }
        val second = manager.executeIfDue(nowMs = 2_001L) { error("should not run twice") }

        assertTrue(first)
        assertFalse(second)
        assertTrue(executed)
        assertEquals(SleepTimerState.Disabled, store.state)
    }

    @Test
    fun `cancel clears state`() {
        val store = FakeStore()
        val manager = SleepTimerManager(store)
        store.state = SleepTimerState.Enabled(targetEpochMillis = 2_000L, durationMinutes = 1)

        manager.cancel()

        assertEquals(SleepTimerState.Disabled, store.state)
    }
}
