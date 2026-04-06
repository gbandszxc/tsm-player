package com.github.gbandszxc.tvmediaplayer.ui

import android.view.KeyEvent
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry

class FastLocateConfirmGuard {

    private var armedConfirmKeyCode: Int? = null
    private var consumeAnyConfirmKey = false

    fun arm(triggerKeyCode: Int? = null) {
        if (triggerKeyCode != null && !isConfirmKey(triggerKeyCode)) return
        armedConfirmKeyCode = triggerKeyCode
        consumeAnyConfirmKey = triggerKeyCode == null
    }

    fun shouldConsumeWhileFastLocate(keyCode: Int, action: Int): Boolean {
        if (!isConfirmKey(keyCode)) return false
        if (!consumeAnyConfirmKey && armedConfirmKeyCode != keyCode) return false

        val hasGuard = consumeAnyConfirmKey || armedConfirmKeyCode != null
        if (!hasGuard) return false

        if (action == KeyEvent.ACTION_UP) {
            reset()
            return true
        }
        return action == KeyEvent.ACTION_DOWN
    }

    fun reset() {
        armedConfirmKeyCode = null
        consumeAnyConfirmKey = false
    }

    private fun isConfirmKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    }
}

class BrowseListRenderGate {

    private var lastFingerprint: String? = null

    fun shouldRebuild(directoryPath: String, entries: List<SmbEntry>): Boolean {
        val fingerprint = buildString(entries.size * 24 + directoryPath.length + 16) {
            append(directoryPath)
            append('#')
            entries.forEach { entry ->
                append(entry.fullPath)
                append('|')
                append(entry.name)
                append('|')
                append(entry.isDirectory)
                append(';')
            }
        }
        if (fingerprint == lastFingerprint) return false
        lastFingerprint = fingerprint
        return true
    }

    fun reset() {
        lastFingerprint = null
    }
}
