package com.github.gbandszxc.tvmediaplayer.ui

import androidx.media3.common.Player
import com.github.gbandszxc.tvmediaplayer.R

enum class PlaybackMode(
    val labelResId: Int,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val iconResId: Int,
) {
    ORDER(
        labelResId = R.string.playback_mode_order,
        repeatMode = Player.REPEAT_MODE_OFF,
        shuffleEnabled = false,
        iconResId = R.drawable.ic_play_order,
    ),
    REPEAT_ONE(
        labelResId = R.string.playback_mode_repeat_one,
        repeatMode = Player.REPEAT_MODE_ONE,
        shuffleEnabled = false,
        iconResId = R.drawable.ic_repeat_one,
    ),
    REPEAT_ALL(
        labelResId = R.string.playback_mode_repeat_all,
        repeatMode = Player.REPEAT_MODE_ALL,
        shuffleEnabled = false,
        iconResId = R.drawable.ic_repeat,
    ),
    SHUFFLE(
        labelResId = R.string.playback_mode_shuffle,
        repeatMode = Player.REPEAT_MODE_OFF,
        shuffleEnabled = true,
        iconResId = R.drawable.ic_shuffle,
    );

    fun next(): PlaybackMode {
        return when (this) {
            ORDER -> REPEAT_ONE
            REPEAT_ONE -> REPEAT_ALL
            REPEAT_ALL -> SHUFFLE
            SHUFFLE -> ORDER
        }
    }

    companion object {
        fun fromPlayer(player: Player): PlaybackMode {
            return when {
                player.shuffleModeEnabled -> SHUFFLE
                player.repeatMode == Player.REPEAT_MODE_ONE -> REPEAT_ONE
                player.repeatMode == Player.REPEAT_MODE_ALL -> REPEAT_ALL
                else -> ORDER
            }
        }
    }
}
