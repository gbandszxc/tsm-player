package com.github.gbandszxc.tvmediaplayer.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdatePromptSnoozeStoreTest {

    @Test
    fun `once snooze suppresses only current process prompt`() {
        val store = UpdatePromptSnoozeStore(RuntimeEnvironment.getApplication())
        store.clear()

        store.snoozeOnce("1.0.10")

        assertTrue(store.shouldSkipAutomaticPrompt("1.0.10", nowMs = 1_000L))
        assertFalse(store.shouldSkipAutomaticPrompt("1.0.10", nowMs = 1_000L))
    }

    @Test
    fun `seven day snooze suppresses any version until window expires`() {
        val store = UpdatePromptSnoozeStore(RuntimeEnvironment.getApplication())
        store.clear()

        store.snoozeForSevenDays("1.0.10", nowMs = 1_000L)

        assertTrue(store.shouldSkipAutomaticPrompt("1.0.10", nowMs = 2_000L))
        assertTrue(store.shouldSkipAutomaticPrompt("1.0.11", nowMs = 2_000L))
        assertFalse(store.shouldSkipAutomaticPrompt("1.0.11", nowMs = 1_000L + UpdatePromptSnoozeStore.SEVEN_DAYS_MS))
    }

    @Test
    fun `until next version snooze suppresses only selected version`() {
        val store = UpdatePromptSnoozeStore(RuntimeEnvironment.getApplication())
        store.clear()

        store.snoozeUntilNextVersion("1.0.10")

        assertTrue(store.shouldSkipAutomaticPrompt("1.0.10", nowMs = 1_000L))
        assertFalse(store.shouldSkipAutomaticPrompt("1.0.11", nowMs = 1_000L))
    }
}
