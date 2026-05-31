package com.github.gbandszxc.tvmediaplayer.ui

import com.github.gbandszxc.tvmediaplayer.lyrics.LrcTimeline
import com.github.gbandszxc.tvmediaplayer.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsViewRendererTest {

    @Test
    fun `before first line viewport resets to top`() {
        val timeline = LrcTimeline(
            lines = listOf(
                LyricLine(timestampMs = 10_000L, text = "第一句"),
                LyricLine(timestampMs = 30_000L, text = "第二句"),
            ),
            offsetMs = 0L,
        )

        val plan = LyricsViewRenderer.buildRenderPlan(
            timeline = timeline,
            positionMs = 0L,
        )

        assertEquals(-1, plan.currentLineIndex)
        assertTrue(plan.shouldResetScrollToTop)
    }

    @Test
    fun `active line keeps viewport following highlighted lyric`() {
        val timeline = LrcTimeline(
            lines = listOf(
                LyricLine(timestampMs = 10_000L, text = "第一句"),
                LyricLine(timestampMs = 30_000L, text = "第二句"),
            ),
            offsetMs = 0L,
        )

        val plan = LyricsViewRenderer.buildRenderPlan(
            timeline = timeline,
            positionMs = 31_000L,
        )

        assertEquals(1, plan.currentLineIndex)
        assertFalse(plan.shouldResetScrollToTop)
    }
}
