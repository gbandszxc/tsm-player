package com.github.gbandszxc.tvmediaplayer.ui

import android.view.KeyEvent
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseInteractionGuardsTest {

    @Test
    fun `fast locate confirm guard swallows repeated confirm until release after long press enter`() {
        val guard = FastLocateConfirmGuard()

        guard.arm(KeyEvent.KEYCODE_DPAD_CENTER)

        assertTrue(
            guard.shouldConsumeWhileFastLocate(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_DOWN
            )
        )
        assertTrue(
            guard.shouldConsumeWhileFastLocate(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_UP
            )
        )
        assertFalse(
            guard.shouldConsumeWhileFastLocate(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_DOWN
            )
        )
    }

    @Test
    fun `fast locate confirm guard ignores non confirm keys`() {
        val guard = FastLocateConfirmGuard()

        guard.arm(KeyEvent.KEYCODE_ENTER)

        assertFalse(
            guard.shouldConsumeWhileFastLocate(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                action = KeyEvent.ACTION_DOWN
            )
        )
    }

    @Test
    fun `browse list render gate only rebuilds when directory content changes`() {
        val gate = BrowseListRenderGate()
        val entries = listOf(
            SmbEntry(name = "Track 01", fullPath = "Music/Track 01.flac", isDirectory = false),
            SmbEntry(name = "Track 02", fullPath = "Music/Track 02.flac", isDirectory = false)
        )

        assertTrue(gate.shouldRebuild(directoryPath = "Music", entries = entries))
        assertFalse(gate.shouldRebuild(directoryPath = "Music", entries = entries))
        assertTrue(
            gate.shouldRebuild(
                directoryPath = "Music",
                entries = entries.reversed()
            )
        )
    }

    @Test
    fun `browse list render gate rebuilds when switching directory even if entries repeat`() {
        val gate = BrowseListRenderGate()
        val entries = listOf(
            SmbEntry(name = "Track 01", fullPath = "Track 01.flac", isDirectory = false)
        )

        assertTrue(gate.shouldRebuild(directoryPath = "Album A", entries = entries))
        assertTrue(gate.shouldRebuild(directoryPath = "Album B", entries = entries))
    }
}
