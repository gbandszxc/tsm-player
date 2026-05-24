package com.github.gbandszxc.tvmediaplayer.sleep

import android.content.Context

class SleepTimerStartupCoordinator {
    private var activeTimerCleared = false

    fun clearActiveTimerOnce(store: SleepTimerStoreContract): Boolean {
        if (activeTimerCleared) return false
        store.clear()
        activeTimerCleared = true
        return true
    }
}

object SleepTimerStartup {
    private val coordinator = SleepTimerStartupCoordinator()

    fun clearActiveTimerOnProcessStart(context: Context) {
        coordinator.clearActiveTimerOnce(SleepTimerStore(context))
    }
}
