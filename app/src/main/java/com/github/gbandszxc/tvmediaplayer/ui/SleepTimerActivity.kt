package com.github.gbandszxc.tvmediaplayer.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerManager
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerState
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerStore

class SleepTimerActivity : BaseActivity() {
    private lateinit var manager: SleepTimerManager
    private lateinit var tvStatus: TextView
    private lateinit var layoutPresets: LinearLayout
    private lateinit var layoutWheel: LinearLayout
    private lateinit var layoutWheelHours: LinearLayout
    private lateinit var layoutWheelMinutes: LinearLayout
    private lateinit var tvHoursPrev: TextView
    private lateinit var tvHoursCurrent: TextView
    private lateinit var tvHoursNext: TextView
    private lateinit var tvMinutesPrev: TextView
    private lateinit var tvMinutesCurrent: TextView
    private lateinit var tvMinutesNext: TextView
    private lateinit var btnStart: Button
    private lateinit var btnCancel: Button
    private var selectedHours = 0
    private var selectedMinutes = 30
    private var customMode = false
    private var lastKeyDownTime = 0L
    private var keyRepeatCount = 0
    private var currentWheelTarget: LinearLayout? = null
    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // 触屏反向：上滑增加，下滑减少
                val target = currentWheelTarget ?: return false
                if (distanceY < -20f) {
                    if (target == layoutWheelHours) {
                        selectedHours = (selectedHours + 1) % 24
                    } else {
                        selectedMinutes = (selectedMinutes + 1) % 60
                    }
                    renderWheel()
                    return true
                }
                if (distanceY > 20f) {
                    if (target == layoutWheelHours) {
                        selectedHours = (selectedHours - 1 + 24) % 24
                    } else {
                        selectedMinutes = (selectedMinutes - 1 + 60) % 60
                    }
                    renderWheel()
                    return true
                }
                return false
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_timer)
        UiSettingsApplier.applyAll(this)
        manager = SleepTimerManager(SleepTimerStore(this))
        bindViews()
        bindActions()
        restoreFromCurrentTimer()
        render()
        findViewById<Button>(R.id.btn_sleep_30).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun bindViews() {
        tvStatus = findViewById(R.id.tv_sleep_status)
        layoutPresets = findViewById(R.id.layout_presets)
        layoutWheel = findViewById(R.id.layout_wheel)
        layoutWheelHours = findViewById(R.id.layout_wheel_hours)
        layoutWheelMinutes = findViewById(R.id.layout_wheel_minutes)
        tvHoursPrev = findViewById(R.id.tv_wheel_hours_prev)
        tvHoursCurrent = findViewById(R.id.tv_wheel_hours_current)
        tvHoursNext = findViewById(R.id.tv_wheel_hours_next)
        tvMinutesPrev = findViewById(R.id.tv_wheel_minutes_prev)
        tvMinutesCurrent = findViewById(R.id.tv_wheel_minutes_current)
        tvMinutesNext = findViewById(R.id.tv_wheel_minutes_next)
        btnStart = findViewById(R.id.btn_sleep_start)
        btnCancel = findViewById(R.id.btn_sleep_cancel)

        val touchListener = View.OnTouchListener { view, event ->
            currentWheelTarget = view as LinearLayout
            gestureDetector.onTouchEvent(event)
        }
        layoutWheelHours.setOnTouchListener(touchListener)
        layoutWheelMinutes.setOnTouchListener(touchListener)
    }

    private fun bindActions() {
        findViewById<Button>(R.id.btn_sleep_15).setOnClickListener { setDurationAndStart(15) }
        findViewById<Button>(R.id.btn_sleep_30).setOnClickListener { setDurationAndStart(30) }
        findViewById<Button>(R.id.btn_sleep_60).setOnClickListener { setDurationAndStart(60) }
        findViewById<Button>(R.id.btn_sleep_120).setOnClickListener { setDurationAndStart(120) }
        findViewById<Button>(R.id.btn_sleep_custom).setOnClickListener { toggleCustomMode() }
        btnStart.setOnClickListener {
            if (customMode) {
                val duration = selectedHours * 60 + selectedMinutes
                if (duration <= 0) {
                    Toast.makeText(this, getString(R.string.sleep_timer_toast_min_duration), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                setDurationAndStart(duration)
            } else {
                // 非自定义模式下，如果已有定时则重新用当前值更新
                val duration = selectedHours * 60 + selectedMinutes
                if (duration > 0) {
                    setDurationAndStart(duration)
                }
            }
        }
        btnCancel.setOnClickListener {
            manager.cancel()
            Toast.makeText(this, getString(R.string.sleep_timer_toast_cancelled), Toast.LENGTH_SHORT).show()
            render()
        }
        findViewById<Button>(R.id.btn_sleep_back).setOnClickListener { finish() }
    }

    private fun toggleCustomMode() {
        customMode = !customMode
        if (customMode) {
            layoutPresets.visibility = View.GONE
            layoutWheel.visibility = View.VISIBLE
            renderWheel()
            layoutWheelHours.requestFocus()
        } else {
            layoutWheel.visibility = View.GONE
            layoutPresets.visibility = View.VISIBLE
            findViewById<Button>(R.id.btn_sleep_custom).requestFocus()
        }
        render()
    }

    private fun restoreFromCurrentTimer() {
        val state = manager.currentState()
        if (state is SleepTimerState.Enabled) {
            val totalMinutes = state.durationMinutes
            selectedHours = totalMinutes / 60
            selectedMinutes = totalMinutes % 60
        }
    }

    private fun setDurationAndStart(durationMinutes: Int) {
        manager.start(durationMinutes)
        Toast.makeText(this, getString(R.string.sleep_timer_toast_started, durationMinutes), Toast.LENGTH_SHORT).show()
        render()
    }

    private fun render() {
        val remaining = manager.remainingMinutesCeil()
        val state = manager.currentState()
        tvStatus.text = when {
            remaining != null -> getString(R.string.sleep_timer_status_on, remaining)
            state is SleepTimerState.Disabled -> getString(R.string.sleep_timer_status_off)
            else -> getString(R.string.sleep_timer_status_off)
        }
        btnStart.text = if (remaining != null) getString(R.string.sleep_timer_update) else getString(R.string.sleep_timer_start)
        btnCancel.isEnabled = remaining != null
        if (customMode) {
            renderWheel()
        }
    }

    private fun renderWheel() {
        val hoursPrev = (selectedHours - 1 + 24) % 24
        val hoursNext = (selectedHours + 1) % 24
        val minutesPrev = (selectedMinutes - 1 + 60) % 60
        val minutesNext = (selectedMinutes + 1) % 60

        tvHoursPrev.text = String.format("%02d", hoursPrev)
        tvHoursCurrent.text = String.format("%02d", selectedHours)
        tvHoursNext.text = String.format("%02d", hoursNext)
        tvMinutesPrev.text = String.format("%02d", minutesPrev)
        tvMinutesCurrent.text = String.format("%02d", selectedMinutes)
        tvMinutesNext.text = String.format("%02d", minutesNext)
    }

    private fun adjustWheelValue(direction: Int) {
        val focusedView = currentFocus
        if (focusedView == layoutWheelHours) {
            selectedHours = (selectedHours + direction + 24) % 24
            renderWheel()
        } else if (focusedView == layoutWheelMinutes) {
            selectedMinutes = (selectedMinutes + direction + 60) % 60
            renderWheel()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!customMode) {
            return super.onKeyDown(keyCode, event)
        }

        val focusedView = currentFocus
        val isWheelFocused = focusedView == layoutWheelHours || focusedView == layoutWheelMinutes

        if (!isWheelFocused) {
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastKeyDownTime < 200) {
                    keyRepeatCount++
                } else {
                    keyRepeatCount = 0
                }
                lastKeyDownTime = now
                val step = if (keyRepeatCount > 3) 5 else 1
                // 遥控器正向：下键增加、上键减少
                adjustWheelValue(-step)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastKeyDownTime < 200) {
                    keyRepeatCount++
                } else {
                    keyRepeatCount = 0
                }
                lastKeyDownTime = now
                val step = if (keyRepeatCount > 3) 5 else 1
                adjustWheelValue(step)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (focusedView == layoutWheelMinutes) {
                    layoutWheelHours.requestFocus()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (focusedView == layoutWheelHours) {
                    layoutWheelMinutes.requestFocus()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customMode) {
                toggleCustomMode()
                return true
            }
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
