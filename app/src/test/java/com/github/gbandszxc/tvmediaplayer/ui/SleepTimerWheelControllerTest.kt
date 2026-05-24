package com.github.gbandszxc.tvmediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerWheelControllerTest {

    @Test
    fun `swiping up increases selected value`() {
        val controller = SleepTimerWheelController(hours = 2, minutes = 30)

        controller.adjust(SleepTimerWheelController.Target.Hours, direction = 1)
        controller.adjust(SleepTimerWheelController.Target.Minutes, direction = 1)

        assertEquals(3, controller.hours)
        assertEquals(31, controller.minutes)
    }

    @Test
    fun `swiping down decreases selected value`() {
        val controller = SleepTimerWheelController(hours = 2, minutes = 30)

        controller.adjust(SleepTimerWheelController.Target.Hours, direction = -1)
        controller.adjust(SleepTimerWheelController.Target.Minutes, direction = -1)

        assertEquals(1, controller.hours)
        assertEquals(29, controller.minutes)
    }

    @Test
    fun `wheel values wrap within hour and minute ranges`() {
        val controller = SleepTimerWheelController(hours = 0, minutes = 0)

        controller.adjust(SleepTimerWheelController.Target.Hours, direction = -1)
        controller.adjust(SleepTimerWheelController.Target.Minutes, direction = -1)
        assertEquals(23, controller.hours)
        assertEquals(59, controller.minutes)

        controller.adjust(SleepTimerWheelController.Target.Hours, direction = 1)
        controller.adjust(SleepTimerWheelController.Target.Minutes, direction = 1)
        assertEquals(0, controller.hours)
        assertEquals(0, controller.minutes)
    }
}
