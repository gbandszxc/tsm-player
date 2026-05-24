package com.github.gbandszxc.tvmediaplayer.ui

import androidx.media3.common.Player
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
    fun labelsAreShortChineseModeNames() {
        assertEquals("顺序播放", PlaybackMode.ORDER.label)
        assertEquals("单曲循环", PlaybackMode.REPEAT_ONE.label)
        assertEquals("列表循环", PlaybackMode.REPEAT_ALL.label)
        assertEquals("随机播放", PlaybackMode.SHUFFLE.label)
    }
}
