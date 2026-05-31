package com.github.gbandszxc.tvmediaplayer.ui

import com.github.gbandszxc.tvmediaplayer.lyrics.LrcParser
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcTimeline

internal data class LyricsRenderPlan(
    val currentLineIndex: Int,
    val shouldResetScrollToTop: Boolean,
)

internal object LyricsViewRenderer {
    fun buildRenderPlan(timeline: LrcTimeline, positionMs: Long): LyricsRenderPlan {
        val currentLineIndex = LrcParser.findCurrentLineIndex(
            lines = timeline.lines,
            playbackPositionMs = positionMs,
            offsetMs = timeline.offsetMs
        )
        return LyricsRenderPlan(
            currentLineIndex = currentLineIndex,
            shouldResetScrollToTop = currentLineIndex < 0,
        )
    }
}
