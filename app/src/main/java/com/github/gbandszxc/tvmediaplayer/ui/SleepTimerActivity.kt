package com.github.gbandszxc.tvmediaplayer.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerManager
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerState
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerStore

class SleepTimerActivity : BaseActivity() {
    private lateinit var manager: SleepTimerManager
    private lateinit var tvStatus: TextView
    private lateinit var btnHours: Button
    private lateinit var btnMinutes: Button
    private lateinit var btnStart: Button
    private lateinit var btnCancel: Button
    private var selectedHours = 0
    private var selectedMinutes = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_timer)
        UiSettingsApplier.applyAll(this)
        manager = SleepTimerManager(SleepTimerStore(this))
        bindViews()
        bindActions()
        render()
        findViewById<Button>(R.id.btn_sleep_30).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun bindViews() {
        tvStatus = findViewById(R.id.tv_sleep_status)
        btnHours = findViewById(R.id.btn_sleep_hours)
        btnMinutes = findViewById(R.id.btn_sleep_minutes)
        btnStart = findViewById(R.id.btn_sleep_start)
        btnCancel = findViewById(R.id.btn_sleep_cancel)
    }

    private fun bindActions() {
        findViewById<Button>(R.id.btn_sleep_15).setOnClickListener { setDurationAndStart(15) }
        findViewById<Button>(R.id.btn_sleep_30).setOnClickListener { setDurationAndStart(30) }
        findViewById<Button>(R.id.btn_sleep_60).setOnClickListener { setDurationAndStart(60) }
        findViewById<Button>(R.id.btn_sleep_120).setOnClickListener { setDurationAndStart(120) }
        btnHours.setOnClickListener {
            selectedHours = (selectedHours + 1) % 24
            renderManualSelection()
        }
        btnMinutes.setOnClickListener {
            selectedMinutes = (selectedMinutes + 5) % 60
            renderManualSelection()
        }
        btnStart.setOnClickListener {
            val duration = selectedHours * 60 + selectedMinutes
            if (duration <= 0) {
                Toast.makeText(this, getString(R.string.sleep_timer_toast_min_duration), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setDurationAndStart(duration)
        }
        btnCancel.setOnClickListener {
            manager.cancel()
            Toast.makeText(this, getString(R.string.sleep_timer_toast_cancelled), Toast.LENGTH_SHORT).show()
            render()
        }
        findViewById<Button>(R.id.btn_sleep_back).setOnClickListener { finish() }
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
        renderManualSelection()
    }

    private fun renderManualSelection() {
        btnHours.text = getString(R.string.sleep_timer_hours, selectedHours)
        btnMinutes.text = getString(R.string.sleep_timer_minutes, selectedMinutes)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
