package com.github.gbandszxc.tvmediaplayer.sleep

sealed class SleepTimerState {
    data object Disabled : SleepTimerState()
    data class Enabled(
        val targetEpochMillis: Long,
        val durationMinutes: Int
    ) : SleepTimerState()
}
