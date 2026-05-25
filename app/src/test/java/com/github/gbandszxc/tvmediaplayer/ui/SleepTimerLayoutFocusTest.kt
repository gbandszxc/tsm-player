package com.github.gbandszxc.tvmediaplayer.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SleepTimerLayoutFocusTest {

    @Test
    fun `right key from minutes wheel is routed to start button`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.activity_sleep_timer, FrameLayout(context), false)
        val minutesWheel = root.findViewById<LinearLayout>(R.id.layout_wheel_minutes)

        assertEquals(R.id.btn_sleep_start, minutesWheel.nextFocusRightId)
    }
}
