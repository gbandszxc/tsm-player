package com.github.gbandszxc.tvmediaplayer.sleep

import android.app.Activity
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object SleepAppExitController {
    private val activities = CopyOnWriteArrayList<WeakReference<Activity>>()

    fun register(activity: Activity) {
        activities.removeAll { it.get() == null || it.get() === activity }
        activities.add(WeakReference(activity))
    }

    fun unregister(activity: Activity) {
        activities.removeAll { it.get() == null || it.get() === activity }
    }

    fun finishAll() {
        activities.mapNotNull { it.get() }
            .filterNot { it.isFinishing }
            .forEach { activity -> activity.runOnUiThread { activity.finish() } }
        activities.removeAll { it.get() == null || it.get()?.isFinishing == true }
    }
}
