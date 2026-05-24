package com.github.gbandszxc.tvmediaplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.github.gbandszxc.tvmediaplayer.sleep.SleepAppExitController
import com.github.gbandszxc.tvmediaplayer.sleep.SleepDeviceController
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerManager
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerStore
import com.github.gbandszxc.tvmediaplayer.ui.PlaybackActivity
import com.github.gbandszxc.tvmediaplayer.ui.UiSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepTimerJob: Job? = null
    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var sleepDeviceController: SleepDeviceController

    /**
     * 监听歌曲切换事件，在服务侧（player 状态永远最新）立即保存快照。
     * 解决 MediaController 在 IPC 传播延迟期间 Activity.onStop() 读到旧 index 的问题。
     */
    private val snapshotListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveSnapshotFromPlayer()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sleepTimerManager = SleepTimerManager(SleepTimerStore(this))
        sleepDeviceController = SleepDeviceController(this)
        startSleepTimerChecker()

        val mediaSourceFactory = DefaultMediaSourceFactory(
            SmbDataSource.Factory { PlaybackConfigStore.current() }
        )
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            addListener(snapshotListener)
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionActivity())
            .build()
    }

    private fun saveSnapshotFromPlayer() {
        if (!UiSettingsStore.rememberLastPlayback(this)) return
        val player = mediaSession?.player ?: return
        if (player.mediaItemCount == 0) return
        val currentMediaId = player.currentMediaItem?.mediaId
            ?.let(PlaybackLocationResolver::normalizePath)
        val currentDirectoryPath = currentMediaId?.let(PlaybackLocationResolver::parentDirectory)
        val sourceConfig = PlaybackConfigStore.current().takeIf { it.host.isNotBlank() }
        val uris = buildList {
            repeat(player.mediaItemCount) { i ->
                player.getMediaItemAt(i).localConfiguration?.uri?.toString()?.let(::add)
            }
        }
        val ids = buildList {
            repeat(player.mediaItemCount) { i ->
                add(player.getMediaItemAt(i).mediaId)
            }
        }
        if (uris.isEmpty()) return
        LastPlaybackStore.save(
            this,
            LastPlaybackStore.Snapshot(
                queueUris = uris,
                queueMediaIds = ids,
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                title = player.mediaMetadata.title?.toString().orEmpty(),
                currentMediaId = currentMediaId,
                currentDirectoryPath = currentDirectoryPath,
                sourceConnectionId = null,
                sourceConfig = sourceConfig
            )
        )
    }

    private fun startSleepTimerChecker() {
        sleepTimerJob?.cancel()
        sleepTimerJob = serviceScope.launch {
            while (isActive) {
                sleepTimerManager.executeIfDue {
                    executeSleepTimerAction()
                }
                delay(SLEEP_TIMER_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun executeSleepTimerAction() {
        saveSnapshotFromPlayer()
        mediaSession?.player?.run {
            pause()
            stop()
        }
        SleepAppExitController.finishAll()
        sleepDeviceController.sleepNow()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepTimerJob?.cancel()
        serviceScope.coroutineContext.cancelChildren()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun buildSessionActivity(): PendingIntent {
        val intent = Intent(this, PlaybackActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val SLEEP_TIMER_CHECK_INTERVAL_MS = 30_000L
    }
}
