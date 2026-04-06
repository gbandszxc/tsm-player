package com.github.gbandszxc.tvmediaplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLocationResolver
import com.github.gbandszxc.tvmediaplayer.ui.BaseActivity
import com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragment
import com.github.gbandszxc.tvmediaplayer.ui.UiSettingsApplier

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        UiSettingsApplier.applyAll(this)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.root_container, TvBrowseFragment())
                .commitNow()
        }
        deliverPlaybackLocateTarget()
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
