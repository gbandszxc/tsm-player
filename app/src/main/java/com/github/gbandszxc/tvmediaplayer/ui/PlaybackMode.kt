package com.github.gbandszxc.tvmediaplayer.ui

import androidx.media3.common.Player
import com.github.gbandszxc.tvmediaplayer.R

enum class PlaybackMode(
    val label: String,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val iconResId: Int,
) {
    ORDER(
        label = "顺序播放",
        repeatMode = Player.REPEAT_MODE_OFF,
        shuffleEnabled = false,
        iconResId = R.drawable.ic_play_order,
    ),
    REPEAT_ONE(
        label = "单曲循环",
        repeatMode = Player.REPEAT_MODE_ONE,
        shuffleEnabled = false,
        iconResId = R.drawable.ic_repeat_one,
    ),
    REPEAT_ALL(
        label = "列表循环",
        repeatMode = Player.REPEAT_MODE_ALL,
        shuffleEnabled = false,
        iconResId = R.drawable.ic_repeat,
    ),
    SHUFFLE(
        label = "随机播放",
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
