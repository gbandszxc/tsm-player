package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.github.gbandszxc.tvmediaplayer.sleep.SleepAppExitController

/**
 * 所有 Activity 的公共基类，统一管理沉浸式全屏逻辑。
 * 继承此类的 Activity 无需手动处理 onWindowFocusChanged。
 */
abstract class BaseActivity : FragmentActivity() {
    private var attachedLanguage: AppLanguage = AppLanguage.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        attachedLanguage = UiSettingsStore.appLanguage(newBase)
        super.attachBaseContext(AppLocaleApplier.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiSettingsApplier.applyFullscreenWindowLayout(this)
        SleepAppExitController.register(this)
    }

    override fun onDestroy() {
        SleepAppExitController.unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (UiSettingsStore.appLanguage(this) != attachedLanguage) {
            recreate()
            return
        }
        UiSettingsApplier.applyImmersiveFullscreen(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) UiSettingsApplier.applyImmersiveFullscreen(this)
    }
}
