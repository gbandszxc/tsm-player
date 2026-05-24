package com.github.gbandszxc.tvmediaplayer.ui

class SleepTimerWheelController(
    var hours: Int = 0,
    var minutes: Int = 30
) {
    enum class Target {
        Hours,
        Minutes
    }

    fun adjust(target: Target, direction: Int) {
        when (target) {
            Target.Hours -> hours = wrap(hours + direction, HOURS_RANGE)
            Target.Minutes -> minutes = wrap(minutes + direction, MINUTES_RANGE)
        }
    }

    fun setDuration(totalMinutes: Int) {
        hours = totalMinutes / MINUTES_RANGE
        minutes = totalMinutes % MINUTES_RANGE
    }

    fun durationMinutes(): Int = hours * MINUTES_RANGE + minutes

    private fun wrap(value: Int, range: Int): Int = (value % range + range) % range

    private companion object {
        const val HOURS_RANGE = 24
        const val MINUTES_RANGE = 60
    }
}
