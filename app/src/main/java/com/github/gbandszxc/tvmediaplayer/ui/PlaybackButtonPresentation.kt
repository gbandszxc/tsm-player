package com.github.gbandszxc.tvmediaplayer.ui

import com.github.gbandszxc.tvmediaplayer.R

data class PlaybackButtonSpec(
    val text: String,
    val contentDescription: String,
    val iconResId: Int,
    val expandsOnFocus: Boolean,
)

object PlaybackButtonPresentation {

    fun previous(): PlaybackButtonSpec {
        return iconOnly(
            label = "上一首",
            iconResId = R.drawable.ic_skip_previous,
        )
    }

    fun playPause(isPlaying: Boolean): PlaybackButtonSpec {
        return iconOnly(
            label = if (isPlaying) "暂停" else "播放",
            iconResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )
    }

    fun next(): PlaybackButtonSpec {
        return iconOnly(
            label = "下一首",
            iconResId = R.drawable.ic_skip_next,
        )
    }

    fun lyricsFullscreen(focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = "歌词全屏",
            iconResId = R.drawable.ic_lyrics_fullscreen,
            focused = focused,
        )
    }

    fun backToBrowser(focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = "返回文件页",
            iconResId = R.drawable.ic_back_to_folder,
            focused = focused,
        )
    }

    fun shouldDrawCenteredIcon(spec: PlaybackButtonSpec, hasFocus: Boolean): Boolean {
        return !spec.expandsOnFocus || !hasFocus
    }

    private fun iconOnly(
        label: String,
        iconResId: Int,
    ): PlaybackButtonSpec {
        return PlaybackButtonSpec(
            text = "",
            contentDescription = label,
            iconResId = iconResId,
            expandsOnFocus = false,
        )
    }

    private fun expandable(
        label: String,
        iconResId: Int,
        focused: Boolean,
    ): PlaybackButtonSpec {
        return PlaybackButtonSpec(
            text = if (focused) label else "",
            contentDescription = label,
            iconResId = iconResId,
            expandsOnFocus = true,
        )
    }
}
