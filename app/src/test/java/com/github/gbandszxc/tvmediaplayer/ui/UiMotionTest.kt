package com.github.gbandszxc.tvmediaplayer.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class UiMotionTest {

    @Test
    fun `losing focus before first frame cancels pending expansion`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val button = laidOutButton(activity, width = 100)

        UiMotion.animateWidthTo(button, targetSpec = 220, expand = true)
        UiMotion.animateWidthTo(button, targetSpec = 100, expand = false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

        assertEquals(100, button.layoutParams.width)
        assertEquals(100, button.width)
    }

    @Test
    fun `touch ripple is clipped to rounded background outline`() {
        val context = RuntimeEnvironment.getApplication()
        val button = Button(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(0xff336699.toInt())
            }
        }
        UiMotion.applyPressFeedback(button, R.color.ui_press_overlay_dark)

        MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0).run {
            button.dispatchTouchEvent(this)
            recycle()
        }

        assertFalse(button.clipToOutline)
        val ripple = button.foreground as RippleDrawable
        assertNotNull(ripple.findDrawableByLayerId(android.R.id.mask))
    }

    private fun laidOutButton(activity: Activity, width: Int): Button {
        val root = FrameLayout(activity)
        val button = Button(activity).apply {
            layoutParams = FrameLayout.LayoutParams(width, 80)
        }
        root.addView(button)
        activity.setContentView(root)
        root.measure(
            ViewGroup.LayoutParams.MATCH_PARENT.toExactlyMeasureSpec(),
            ViewGroup.LayoutParams.MATCH_PARENT.toExactlyMeasureSpec(),
        )
        root.layout(0, 0, 800, 600)
        return button
    }

    private fun Int.toExactlyMeasureSpec(): Int =
        android.view.View.MeasureSpec.makeMeasureSpec(if (this > 0) this else 800, android.view.View.MeasureSpec.EXACTLY)
}
