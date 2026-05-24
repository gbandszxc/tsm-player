package com.github.gbandszxc.tvmediaplayer.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.github.gbandszxc.tvmediaplayer.sleep.SleepAppExitController

/**
 * 所有 Activity 的公共基类，统一管理沉浸式全屏逻辑。
 * 继承此类的 Activity 无需手动处理 onWindowFocusChanged。
 */
abstract class BaseActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SleepAppExitController.register(this)
    }

    override fun onDestroy() {
        SleepAppExitController.unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyImmersiveFullscreen(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) UiSettingsApplier.applyImmersiveFullscreen(this)
    }
}
