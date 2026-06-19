package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.R

data class PlaybackButtonSpec(
    val text: String,
    val contentDescription: String,
    val iconResId: Int,
    val expandsOnFocus: Boolean,
    val browserExpandedWidthResId: Int? = null,
)

object PlaybackButtonPresentation {

    fun previous(context: Context): PlaybackButtonSpec {
        return iconOnly(
            label = context.getString(R.string.common_previous),
            iconResId = R.drawable.ic_skip_previous,
        )
    }

    fun playPause(context: Context, isPlaying: Boolean): PlaybackButtonSpec {
        return iconOnly(
            label = context.getString(if (isPlaying) R.string.common_pause else R.string.common_play),
            iconResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )
    }

    fun next(context: Context): PlaybackButtonSpec {
        return iconOnly(
            label = context.getString(R.string.common_next),
            iconResId = R.drawable.ic_skip_next,
        )
    }

    fun favorite(context: Context, inDefaultFavorites: Boolean, focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = context.getString(R.string.playback_favorite),
            iconResId = if (inDefaultFavorites) {
                R.drawable.ic_favorite_filled
            } else {
                R.drawable.ic_favorite_outline
            },
            focused = focused,
        )
    }

    fun lyricsFullscreen(context: Context, focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = context.getString(R.string.playback_lyrics_fullscreen),
            iconResId = R.drawable.ic_lyrics_fullscreen,
            focused = focused,
        )
    }

    fun backToBrowser(context: Context, focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = context.getString(R.string.playback_back_to_browser),
            iconResId = R.drawable.ic_back_to_folder,
            focused = focused,
        )
    }

    fun browserFavorites(context: Context, focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = context.getString(R.string.playback_favorite),
            iconResId = R.drawable.ic_favorite_filled,
            focused = focused,
            browserExpandedWidthResId = R.dimen.ui_playback_favorite_button_expanded_min_width,
        )
    }

    fun browserHistory(context: Context, focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = context.getString(R.string.history_title),
            iconResId = R.drawable.ic_history,
            focused = focused,
            browserExpandedWidthResId = R.dimen.ui_playback_favorite_button_expanded_min_width,
        )
    }

    fun browserPlayOrder(context: Context, focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = context.getString(R.string.playback_mode_order),
            iconResId = R.drawable.ic_play_order,
            focused = focused,
            browserExpandedWidthResId = R.dimen.ui_playback_mode_button_expanded_min_width,
        )
    }

    fun browserPlayShuffle(context: Context, focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = context.getString(R.string.playback_mode_shuffle),
            iconResId = R.drawable.ic_shuffle,
            focused = focused,
            browserExpandedWidthResId = R.dimen.ui_playback_mode_button_expanded_min_width,
        )
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
        browserExpandedWidthResId: Int? = null,
    ): PlaybackButtonSpec {
        return PlaybackButtonSpec(
            text = if (focused) label else "",
            contentDescription = label,
            iconResId = iconResId,
            expandsOnFocus = true,
            browserExpandedWidthResId = browserExpandedWidthResId,
        )
    }
}
