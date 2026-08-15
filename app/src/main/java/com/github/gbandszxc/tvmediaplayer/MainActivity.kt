package com.github.gbandszxc.tvmediaplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLocationResolver
import com.github.gbandszxc.tvmediaplayer.sleep.SleepDeviceController
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerStartup
import com.github.gbandszxc.tvmediaplayer.ui.BaseActivity
import com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragment
import com.github.gbandszxc.tvmediaplayer.ui.UiSettingsApplier
import com.github.gbandszxc.tvmediaplayer.ui.UiSettingsStore
import com.github.gbandszxc.tvmediaplayer.ui.modal.ActionModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalAction
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinator
import com.github.gbandszxc.tvmediaplayer.update.AppUpdateManager

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SleepTimerStartup.clearActiveTimerOnProcessStart(this)
        setContentView(R.layout.activity_main)
        UiSettingsApplier.applyAll(this)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.root_container, TvBrowseFragment())
                .commitNow()
        }
        deliverPlaybackLocateTarget()
        if (!maybePromptSleepDeviceAdmin()) {
            AppUpdateManager.maybeCheckOnAppStart(this)
        }
    }

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyAll(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverPlaybackLocateTarget()
    }

    private fun maybePromptSleepDeviceAdmin(): Boolean {
        val controller = SleepDeviceController(this)
        if (controller.isDeviceAdminActive()) return false
        if (UiSettingsStore.sleepAdminPromptShown(this)) return false
        UiSettingsStore.setSleepAdminPromptShown(this, true)
        TsmModalCoordinator(this).showActionModal(
            ActionModalSpec(
                sectionLabel = getString(R.string.permission_section),
                title = getString(R.string.sleep_permission_title),
                message = getString(R.string.sleep_permission_message),
                actions = listOf(
                    ModalAction(getString(R.string.sleep_permission_skip)),
                    ModalAction(getString(R.string.sleep_permission_open), isPrimary = true) {
                        controller.openDeviceAdminSettings(this)
                    },
                ),
                onDismiss = { AppUpdateManager.maybeCheckOnAppStart(this) },
            )
        )
        return true
    }

    private fun deliverPlaybackLocateTarget() {
        val target = readPlaybackLocateTarget(intent) ?: return
        (supportFragmentManager.findFragmentById(R.id.root_container) as? TvBrowseFragment)
            ?.handlePlaybackLocateTarget(target)
        clearPlaybackLocateExtras(intent)
    }

    companion object {
        private const val EXTRA_LOCATE_TOKEN = "playback_locate_token"

        /**
         * MainActivity 是 exported 组件，SMB 连接（含密码）不再放入 intent extras，
         * 改为进程内单槽暂存 + 随机 token 关联：外部应用伪造的 locate intent
         * 因 token 不匹配而被直接忽略，应用内定位流程不受影响。
         */
        @Volatile
        private var pendingLocateTarget: PlaybackLocationResolver.Target? = null
        @Volatile
        private var pendingLocateToken: String? = null

        fun createLocateIntent(context: Context, target: PlaybackLocationResolver.Target): Intent {
            val token = java.util.UUID.randomUUID().toString()
            pendingLocateTarget = target
            pendingLocateToken = token
            return Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_LOCATE_TOKEN, token)
            }
        }

        private fun readPlaybackLocateTarget(intent: Intent?): PlaybackLocationResolver.Target? {
            val locateIntent = intent ?: return null
            val token = locateIntent.getStringExtra(EXTRA_LOCATE_TOKEN)
            if (token.isNullOrBlank() || token != pendingLocateToken) return null
            pendingLocateToken = null
            return pendingLocateTarget.also { pendingLocateTarget = null }
        }

        private fun clearPlaybackLocateExtras(intent: Intent?) {
            intent?.removeExtra(EXTRA_LOCATE_TOKEN)
        }
    }
}
