package com.github.gbandszxc.tvmediaplayer.sleep

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.github.gbandszxc.tvmediaplayer.R

class SleepDeviceController(private val context: Context) {
    private val appContext = context.applicationContext
    private val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(appContext, SleepDeviceAdminReceiver::class.java)

    fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    fun openDeviceAdminSettings(activity: Activity) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                appContext.getString(R.string.sleep_device_admin_description)
            )
        }
        activity.startActivity(intent)
    }

    fun sleepNow(): Boolean {
        if (!isDeviceAdminActive()) return false
        return runCatching {
            devicePolicyManager.lockNow()
            true
        }.getOrDefault(false)
    }
}
