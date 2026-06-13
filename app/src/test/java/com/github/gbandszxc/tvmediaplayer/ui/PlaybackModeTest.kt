package com.github.gbandszxc.tvmediaplayer.ui

import androidx.media3.common.Player
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeTest {

    @Test
    fun nextCyclesThroughModesInPlaybackOrder() {
        assertEquals(PlaybackMode.REPEAT_ONE, PlaybackMode.ORDER.next())
        assertEquals(PlaybackMode.REPEAT_ALL, PlaybackMode.REPEAT_ONE.next())
        assertEquals(PlaybackMode.SHUFFLE, PlaybackMode.REPEAT_ALL.next())
        assertEquals(PlaybackMode.ORDER, PlaybackMode.SHUFFLE.next())
    }

    @Test
    fun media3MappingMatchesPlaybackBehavior() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackMode.ORDER.repeatMode)
        assertFalse(PlaybackMode.ORDER.shuffleEnabled)

        assertEquals(Player.REPEAT_MODE_ONE, PlaybackMode.REPEAT_ONE.repeatMode)
        assertFalse(PlaybackMode.REPEAT_ONE.shuffleEnabled)

        assertEquals(Player.REPEAT_MODE_ALL, PlaybackMode.REPEAT_ALL.repeatMode)
        assertFalse(PlaybackMode.REPEAT_ALL.shuffleEnabled)

        assertEquals(Player.REPEAT_MODE_OFF, PlaybackMode.SHUFFLE.repeatMode)
        assertTrue(PlaybackMode.SHUFFLE.shuffleEnabled)
    }

    @Test
    fun labelsUseStringResources() {
        assertEquals(R.string.playback_mode_order, PlaybackMode.ORDER.labelResId)
        assertEquals(R.string.playback_mode_repeat_one, PlaybackMode.REPEAT_ONE.labelResId)
        assertEquals(R.string.playback_mode_repeat_all, PlaybackMode.REPEAT_ALL.labelResId)
        assertEquals(R.string.playback_mode_shuffle, PlaybackMode.SHUFFLE.labelResId)
    }
}
