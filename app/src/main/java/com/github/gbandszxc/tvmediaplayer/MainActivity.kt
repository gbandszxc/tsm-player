package com.github.gbandszxc.tvmediaplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
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
        AppUpdateManager.maybeCheckOnAppStart(this)
        maybePromptSleepDeviceAdmin()
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

    private fun maybePromptSleepDeviceAdmin() {
        val controller = SleepDeviceController(this)
        if (controller.isDeviceAdminActive()) return
        if (UiSettingsStore.sleepAdminPromptShown(this)) return
        UiSettingsStore.setSleepAdminPromptShown(this, true)
        TsmModalCoordinator(this).showActionModal(
            ActionModalSpec(
                sectionLabel = "权限",
                title = "开启睡眠权限",
                message = "授权后，睡眠定时结束时可以让电视进入睡眠或息屏。暂不授权也可以继续使用播放器，并可之后在设置页重新授权。",
                actions = listOf(
                    ModalAction("暂不授权"),
                    ModalAction("去授权", isPrimary = true) { controller.openDeviceAdminSettings(this) },
                ),
            )
        )
    }

    private fun deliverPlaybackLocateTarget() {
        val target = readPlaybackLocateTarget(intent) ?: return
        (supportFragmentManager.findFragmentById(R.id.root_container) as? TvBrowseFragment)
            ?.handlePlaybackLocateTarget(target)
        clearPlaybackLocateExtras(intent)
    }

    companion object {
        private const val EXTRA_LOCATE_MEDIA_ID = "playback_locate_media_id"
        private const val EXTRA_LOCATE_DIRECTORY_PATH = "playback_locate_directory_path"
        private const val EXTRA_LOCATE_CONNECTION_ID = "playback_locate_connection_id"
        private const val EXTRA_LOCATE_HOST = "playback_locate_host"
        private const val EXTRA_LOCATE_SHARE = "playback_locate_share"
        private const val EXTRA_LOCATE_PATH = "playback_locate_path"
        private const val EXTRA_LOCATE_USERNAME = "playback_locate_username"
        private const val EXTRA_LOCATE_PASSWORD = "playback_locate_password"
        private const val EXTRA_LOCATE_GUEST = "playback_locate_guest"
        private const val EXTRA_LOCATE_SMB1 = "playback_locate_smb1"

        fun createLocateIntent(context: Context, target: PlaybackLocationResolver.Target): Intent {
            return Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_LOCATE_MEDIA_ID, target.mediaId)
                putExtra(EXTRA_LOCATE_DIRECTORY_PATH, target.directoryPath)
                putExtra(EXTRA_LOCATE_CONNECTION_ID, target.sourceConnectionId)
                putExtra(EXTRA_LOCATE_HOST, target.sourceConfig.host)
                putExtra(EXTRA_LOCATE_SHARE, target.sourceConfig.share)
                putExtra(EXTRA_LOCATE_PATH, target.sourceConfig.path)
                putExtra(EXTRA_LOCATE_USERNAME, target.sourceConfig.username)
                putExtra(EXTRA_LOCATE_PASSWORD, target.sourceConfig.password)
                putExtra(EXTRA_LOCATE_GUEST, target.sourceConfig.guest)
                putExtra(EXTRA_LOCATE_SMB1, target.sourceConfig.smb1Enabled)
            }
        }

        private fun readPlaybackLocateTarget(intent: Intent?): PlaybackLocationResolver.Target? {
            val locateIntent = intent ?: return null
            val directoryPath = locateIntent.getStringExtra(EXTRA_LOCATE_DIRECTORY_PATH).orEmpty()
            if (directoryPath.isBlank()) return null
            return PlaybackLocationResolver.Target(
                mediaId = locateIntent.getStringExtra(EXTRA_LOCATE_MEDIA_ID).orEmpty(),
                directoryPath = directoryPath,
                sourceConnectionId = locateIntent.getStringExtra(EXTRA_LOCATE_CONNECTION_ID),
                sourceConfig = SmbConfig(
                    host = locateIntent.getStringExtra(EXTRA_LOCATE_HOST).orEmpty(),
                    share = locateIntent.getStringExtra(EXTRA_LOCATE_SHARE).orEmpty(),
                    path = locateIntent.getStringExtra(EXTRA_LOCATE_PATH).orEmpty(),
                    username = locateIntent.getStringExtra(EXTRA_LOCATE_USERNAME).orEmpty(),
                    password = locateIntent.getStringExtra(EXTRA_LOCATE_PASSWORD).orEmpty(),
                    guest = locateIntent.getBooleanExtra(EXTRA_LOCATE_GUEST, true),
                    smb1Enabled = locateIntent.getBooleanExtra(EXTRA_LOCATE_SMB1, false)
                )
            )
        }

        private fun clearPlaybackLocateExtras(intent: Intent?) {
            intent?.removeExtra(EXTRA_LOCATE_MEDIA_ID)
            intent?.removeExtra(EXTRA_LOCATE_DIRECTORY_PATH)
            intent?.removeExtra(EXTRA_LOCATE_CONNECTION_ID)
            intent?.removeExtra(EXTRA_LOCATE_HOST)
            intent?.removeExtra(EXTRA_LOCATE_SHARE)
            intent?.removeExtra(EXTRA_LOCATE_PATH)
            intent?.removeExtra(EXTRA_LOCATE_USERNAME)
            intent?.removeExtra(EXTRA_LOCATE_PASSWORD)
            intent?.removeExtra(EXTRA_LOCATE_GUEST)
            intent?.removeExtra(EXTRA_LOCATE_SMB1)
        }
    }
}
