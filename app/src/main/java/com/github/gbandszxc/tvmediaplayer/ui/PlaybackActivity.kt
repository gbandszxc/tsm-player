package com.github.gbandszxc.tvmediaplayer.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.MainActivity
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStoreState
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcParser
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcTimeline
import com.github.gbandszxc.tvmediaplayer.lyrics.SmbLyricsRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackArtworkCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLyricsCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLocationResolver
import com.github.gbandszxc.tvmediaplayer.playback.SmbAudioMetadataProbe
import com.github.gbandszxc.tvmediaplayer.playback.SmbContextFactory
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerManager
import com.github.gbandszxc.tvmediaplayer.sleep.SleepTimerStore
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackActivity : BaseActivity() {

    private data class AudioTagInfo(
        val title: String?,
        val artist: String?,
        val albumTitle: String?
    )

    private data class LyricsLoadOutcome(
        val timeline: LrcTimeline?,
        val isMiss: Boolean,
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var seekIdleCommitJob: Job? = null
    private var playbackToastJob: Job? = null
    private var playerProgressHoldUntilMs: Long = 0L
    private val playbackSeekController = PlaybackSeekController()
    private val lyricsRepository = SmbLyricsRepository()
    private lateinit var configStore: SmbConfigStore

    private var currentTimeline: LrcTimeline? = null
    private var currentLyricKey: String? = null
    private var currentArtworkKey: String? = null
    private var currentArtworkBitmap: Bitmap? = null
    private var currentTagKey: String? = null
    private val tagInfoCache = mutableMapOf<String, AudioTagInfo>()
    private var fallbackConfig: SmbConfig = SmbConfig.Empty

    private lateinit var layoutArtworkFrame: FrameLayout
    private lateinit var ivArtwork: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var tvTime: TextView
    private lateinit var pbProgress: SeekBar
    private lateinit var scrollLyrics: ScrollView
    private lateinit var tvLyricContent: TextView
    private lateinit var layoutArtworkFullscreen: FrameLayout
    private lateinit var ivArtworkFullscreenBlur: ImageView
    private lateinit var ivArtworkFullscreen: ImageView
    private lateinit var btnPrevious: Button
    private lateinit var btnPlayPause: Button
    private lateinit var btnNext: Button
    private lateinit var btnPlayMode: Button
    private lateinit var btnLyricsFullscreen: Button
    private lateinit var btnBack: Button
    private lateinit var btnLocate: Button
    private lateinit var tvPlaybackToast: TextView
    private var playbackMode: PlaybackMode = PlaybackMode.ORDER
    private var renderedPlaybackMode: PlaybackMode? = null
    private var renderedPlaybackModeFocused: Boolean? = null
    private var renderedLocateFocused: Boolean? = null
    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var btnSleepTimer: Button
    private var renderedSleepTimerLabel: String? = null
    private var renderedSleepTimerFocused: Boolean? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
            ) {
                renderPlayerState(player)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configStore = SmbConfigStore(applicationContext)
        sleepTimerManager = SleepTimerManager(SleepTimerStore(applicationContext))
        setContentView(R.layout.activity_playback)
        bindViews()
        applyUiSettings()
        bindActions()
        bindBackHandling()
        pbProgress.post { pbProgress.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        applyUiSettings()
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onStop() {
        savePlaybackSnapshot()
        releaseController()
        super.onStop()
    }

    private fun bindViews() {
        layoutArtworkFrame = findViewById(R.id.layout_artwork_frame)
        ivArtwork = findViewById(R.id.iv_artwork)
        tvTitle = findViewById(R.id.tv_playback_title)
        tvArtist = findViewById(R.id.tv_playback_artist)
        tvAlbum = findViewById(R.id.tv_playback_album)
        tvTime = findViewById(R.id.tv_playback_time)
        pbProgress = findViewById(R.id.pb_playback)
        scrollLyrics = findViewById(R.id.scroll_lyrics)
        tvLyricContent = findViewById(R.id.tv_lyric_content)
        layoutArtworkFullscreen = findViewById(R.id.layout_artwork_fullscreen)
        ivArtworkFullscreenBlur = findViewById(R.id.iv_artwork_fullscreen_blur)
        ivArtworkFullscreen = findViewById(R.id.iv_artwork_fullscreen)
        btnPrevious = findViewById(R.id.btn_prev)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnPlayMode = findViewById(R.id.btn_play_mode)
        btnLyricsFullscreen = findViewById(R.id.btn_lyrics_fullscreen)
        btnBack = findViewById(R.id.btn_back_to_browser)
        btnLocate = findViewById(R.id.btn_locate)
        btnSleepTimer = findViewById(R.id.btn_sleep_timer)
        tvPlaybackToast = findViewById(R.id.tv_playback_toast)
        renderPlaybackModeButton()
        renderLocateButton()
        renderSleepTimerButton()
    }

    private fun bindActions() {
        layoutArtworkFrame.setOnClickListener { showArtworkFullscreen() }
        layoutArtworkFrame.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                showArtworkFullscreen()
                true
            } else {
                false
            }
        }
        layoutArtworkFullscreen.setOnClickListener { hideArtworkFullscreen() }
        layoutArtworkFullscreen.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                hideArtworkFullscreen()
                true
            } else {
                false
            }
        }
        btnPrevious.setOnClickListener { mediaController?.seekToPreviousMediaItem() }
        btnPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            if (controller.isPlaying) controller.pause() else controller.play()
            renderPlayerState(controller)
        }
        btnNext.setOnClickListener { mediaController?.seekToNextMediaItem() }
        btnPlayMode.setOnClickListener {
            applyPlaybackMode(playbackMode.next(), showNotice = true)
        }
        btnPlayMode.setOnFocusChangeListener { _, _ -> renderPlaybackModeButton() }
        btnLyricsFullscreen.setOnClickListener {
            startActivity(Intent(this, LyricsFullscreenActivity::class.java))
        }
        btnBack.setOnClickListener { finish() }
        btnLocate.setOnClickListener { locateCurrentPlayback() }
        btnLocate.setOnFocusChangeListener { _, _ -> renderLocateButton() }
        btnSleepTimer.setOnClickListener {
            startActivity(Intent(this, SleepTimerActivity::class.java))
        }
        btnSleepTimer.setOnFocusChangeListener { _, _ -> renderSleepTimerButton(force = true) }

        pbProgress.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val controller = mediaController ?: return@setOnKeyListener false
                if (controller.isPlaying) controller.pause() else controller.play()
                renderPlayerState(controller)
                return@setOnKeyListener true
            }
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                return@setOnKeyListener false
            }
            val controller = mediaController ?: return@setOnKeyListener false
            val duration = controller.duration
            if (duration <= 0 || duration == C.TIME_UNSET) return@setOnKeyListener true

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    handleProgressDirectionKeyDown(controller, keyCode, event.repeatCount, duration)
                    true
                }

                KeyEvent.ACTION_UP -> {
                    commitPendingProgressSeek(controller, duration, restartTicker = true)
                    true
                }

                else -> true
            }
        }

        pbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // 拖动开始时暂停进度自动刷新，避免进度条被播放器覆盖
                seekIdleCommitJob?.cancel()
                seekIdleCommitJob = null
                playbackSeekController.reset()
                progressJob?.cancel()
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val controller = mediaController ?: return
                val duration = controller.duration
                if (duration <= 0 || duration == C.TIME_UNSET) return
                val targetMs = (progress.toLong() * duration) / seekBar.max.toLong()
                renderProgress(targetMs, duration)
                renderLyrics(targetMs)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val controller = mediaController ?: run {
                    startProgressTicker(); return
                }
                val duration = controller.duration
                if (duration > 0 && duration != C.TIME_UNSET) {
                    val targetMs = (seekBar.progress.toLong() * duration) / seekBar.max.toLong()
                    controller.seekTo(targetMs)
                }
                startProgressTicker()
            }
        })
    }

    private fun bindBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (layoutArtworkFullscreen.visibility == View.VISIBLE) {
                        hideArtworkFullscreen()
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    private fun applyUiSettings() {
        UiSettingsApplier.applyAll(this)
        tvLyricContent.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            UiSettingsStore.playbackLyricsFontSp(this).toFloat()
        )
        val spacing = UiSettingsStore.playbackLyricsLineSpacing(this).coerceAtLeast(1.0f)
        tvLyricContent.setLineSpacing(0f, spacing)
    }

    private fun ensureController() {
        if (mediaController != null || controllerFuture != null) return

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        controller.addListener(playerListener)
                        updatePlaybackModeFromPlayer(controller)
                        renderPlayerState(controller)
                        startProgressTicker()
                    }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(this, "播放器连接失败", Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun renderPlayerState(player: Player) {
        updatePlaybackModeFromPlayer(player)

        val title = player.mediaMetadata.title?.toString().orEmpty()
        tvTitle.text = "歌曲：" + if (title.isBlank()) "暂无播放内容" else title

        // 先用 mediaMetadata 回退值填充，等 tag 异步读完后再覆盖
        val artist = player.mediaMetadata.artist?.toString().orEmpty().ifBlank { "-" }
        val album = player.mediaMetadata.albumTitle?.toString().orEmpty().ifBlank { "-" }
        tvArtist.text = "艺术家：$artist"
        tvAlbum.text = "专辑：$album"
        if (player.isPlaying) {
            btnPlayPause.text = "暂停"
            btnPlayPause.setBackgroundResource(R.drawable.bg_button_amber)
        } else {
            btnPlayPause.text = "播放"
            btnPlayPause.setBackgroundResource(R.drawable.bg_button_green)
        }

        maybeLoadLyrics(player)
        maybeLoadArtwork(player)
        maybeLoadTagInfo(player)
        if (shouldDeferPlayerProgressRender()) return
        renderProgress(player.currentPosition, player.duration)
        renderLyrics(player.currentPosition)
    }

    private fun applyPlaybackMode(mode: PlaybackMode, showNotice: Boolean) {
        playbackMode = mode
        renderedPlaybackMode = null
        mediaController?.let { controller ->
            controller.setShuffleModeEnabled(mode.shuffleEnabled)
            controller.repeatMode = mode.repeatMode
        }
        renderPlaybackModeButton()
        if (showNotice) {
            showPlaybackToast("已切换为：${mode.label}")
        }
    }

    private fun updatePlaybackModeFromPlayer(player: Player) {
        val mode = PlaybackMode.fromPlayer(player)
        if (mode == playbackMode && renderedPlaybackMode == mode) return
        playbackMode = mode
        renderPlaybackModeButton()
    }

    private fun renderPlaybackModeButton() {
        val focused = btnPlayMode.hasFocus()
        if (renderedPlaybackMode == playbackMode && renderedPlaybackModeFocused == focused) {
            return
        }
        renderedPlaybackMode = playbackMode
        renderedPlaybackModeFocused = focused
        renderCompactIconButton(
            button = btnPlayMode,
            label = playbackMode.label,
            iconResId = playbackMode.iconResId,
            focused = focused,
            backgroundResId = R.drawable.bg_button_amber,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_mode_button_expanded_min_width,
        )
    }

    private fun renderLocateButton() {
        val focused = btnLocate.hasFocus()
        if (renderedLocateFocused == focused) return
        renderedLocateFocused = focused
        renderCompactIconButton(
            button = btnLocate,
            label = "定位",
            iconResId = R.drawable.ic_locate_crosshair,
            focused = focused,
            backgroundResId = R.drawable.bg_button_light_yellow,
            iconColorResId = R.color.ui_text_warning_dark,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_locate_button_expanded_min_width,
        )
    }

    private fun renderSleepTimerButton(force: Boolean = false) {
        val remaining = sleepTimerManager.remainingMinutesCeil()
        val label = if (remaining != null) "睡眠 ${remaining}分" else "睡眠定时"
        val focused = btnSleepTimer.hasFocus()
        if (!force && renderedSleepTimerLabel == label && renderedSleepTimerFocused == focused) return
        renderedSleepTimerLabel = label
        renderedSleepTimerFocused = focused

        btnSleepTimer.text = if (focused) label else ""
        btnSleepTimer.contentDescription = label
        btnSleepTimer.setBackgroundResource(R.drawable.bg_button_dark)
        btnSleepTimer.minWidth = resources.getDimensionPixelSize(
            if (focused) R.dimen.ui_playback_sleep_button_expanded_min_width
            else R.dimen.ui_playback_mode_button_collapsed_width
        )
        val layoutParams = btnSleepTimer.layoutParams
        val targetWidth = if (focused) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            resources.getDimensionPixelSize(R.dimen.ui_playback_mode_button_collapsed_width)
        }
        if (layoutParams.width != targetWidth) {
            layoutParams.width = targetWidth
            btnSleepTimer.layoutParams = layoutParams
        }
        btnSleepTimer.overlay.clear()
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_sleep_timer)?.mutate() ?: return
        val wrapped = DrawableCompat.wrap(icon)
        DrawableCompat.setTint(wrapped, ContextCompat.getColor(this, R.color.ui_text_primary))
        wrapped.setBounds(0, 0, wrapped.intrinsicWidth, wrapped.intrinsicHeight)
        if (focused) {
            btnSleepTimer.setCompoundDrawables(wrapped, null, null, null)
        } else {
            btnSleepTimer.setCompoundDrawables(null, null, null, null)
            btnSleepTimer.post { drawCenteredButtonIcon(btnSleepTimer, wrapped) }
        }
    }

    private fun renderCompactIconButton(
        button: Button,
        label: String,
        iconResId: Int,
        focused: Boolean,
        backgroundResId: Int,
        iconColorResId: Int,
        collapsedWidthResId: Int,
        expandedMinWidthResId: Int,
    ) {
        button.text = if (focused) label else ""
        button.contentDescription = label
        button.setBackgroundResource(backgroundResId)
        button.minWidth = resources.getDimensionPixelSize(
            if (focused) expandedMinWidthResId else collapsedWidthResId
        )
        val layoutParams = button.layoutParams
        val targetWidth = if (focused) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            resources.getDimensionPixelSize(collapsedWidthResId)
        }
        if (layoutParams.width != targetWidth) {
            layoutParams.width = targetWidth
            button.layoutParams = layoutParams
        }
        button.overlay.clear()
        val icon = ContextCompat.getDrawable(this, iconResId)?.mutate() ?: return
        val wrapped = DrawableCompat.wrap(icon)
        DrawableCompat.setTint(
            wrapped,
            ContextCompat.getColor(this, iconColorResId)
        )
        wrapped.setBounds(0, 0, wrapped.intrinsicWidth, wrapped.intrinsicHeight)
        if (focused) {
            button.setCompoundDrawables(wrapped, null, null, null)
        } else {
            button.setCompoundDrawables(null, null, null, null)
            button.post { drawCenteredButtonIcon(button, wrapped) }
        }
    }

    private fun drawCenteredButtonIcon(button: Button, icon: Drawable) {
        if (button.hasFocus()) return
        button.overlay.clear()
        val iconWidth = icon.intrinsicWidth.coerceAtLeast(1)
        val iconHeight = icon.intrinsicHeight.coerceAtLeast(1)
        val left = (button.width - iconWidth) / 2
        val top = (button.height - iconHeight) / 2
        icon.setBounds(left, top, left + iconWidth, top + iconHeight)
        button.overlay.add(icon)
    }

    private fun showPlaybackToast(message: String) {
        playbackToastJob?.cancel()
        tvPlaybackToast.text = message
        tvPlaybackToast.visibility = View.VISIBLE
        playbackToastJob = lifecycleScope.launch {
            delay(PLAYBACK_TOAST_DURATION_MS)
            tvPlaybackToast.visibility = View.GONE
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                val player = mediaController
                if (player != null && !shouldDeferPlayerProgressRender()) {
                    renderProgress(player.currentPosition, player.duration)
                    renderLyrics(player.currentPosition)
                    renderSleepTimerButton()
                }
                delay(300)
            }
        }
    }

    private fun handleProgressDirectionKeyDown(
        controller: MediaController,
        keyCode: Int,
        repeatCount: Int,
        durationMs: Long,
    ) {
        progressJob?.cancel()
        val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
        val update = playbackSeekController.onDirectionKeyDown(
            direction = direction,
            repeatCount = repeatCount,
            currentPositionMs = controller.currentPosition,
            durationMs = durationMs,
            nowMs = SystemClock.elapsedRealtime(),
        )
        renderProgress(update.previewPositionMs, durationMs)
        update.commitPositionMs?.let { position ->
            controller.seekTo(position)
            renderLyrics(position)
        }
        schedulePendingProgressSeekCommit(controller, durationMs)
    }

    private fun schedulePendingProgressSeekCommit(controller: MediaController, durationMs: Long) {
        seekIdleCommitJob?.cancel()
        seekIdleCommitJob = lifecycleScope.launch {
            delay(PlaybackSeekController.DEFAULT_IDLE_COMMIT_DELAY_MS)
            val commit = playbackSeekController.commitIfIdle(SystemClock.elapsedRealtime()) ?: return@launch
            holdPlayerProgressRender()
            controller.seekTo(commit.positionMs)
            renderProgress(commit.positionMs, durationMs)
            renderLyrics(commit.positionMs)
            startProgressTicker()
        }
    }

    private fun commitPendingProgressSeek(
        controller: MediaController,
        durationMs: Long,
        restartTicker: Boolean,
    ) {
        seekIdleCommitJob?.cancel()
        seekIdleCommitJob = null
        val commit = playbackSeekController.commitPending()
        if (commit != null) {
            holdPlayerProgressRender()
            controller.seekTo(commit.positionMs)
            renderProgress(commit.positionMs, durationMs)
            renderLyrics(commit.positionMs)
        }
        if (restartTicker) {
            startProgressTicker()
        }
    }

    private fun shouldDeferPlayerProgressRender(): Boolean {
        return playbackSeekController.isPreviewActive ||
            SystemClock.elapsedRealtime() < playerProgressHoldUntilMs
    }

    private fun holdPlayerProgressRender() {
        playerProgressHoldUntilMs = SystemClock.elapsedRealtime() + PLAYER_PROGRESS_RENDER_HOLD_MS
    }

    private fun renderProgress(positionMs: Long, durationMs: Long) {
        val safeDuration = if (durationMs <= 0 || durationMs == C.TIME_UNSET) 0L else durationMs
        tvTime.text = "${formatMs(positionMs)} / ${formatMs(safeDuration)}"
        pbProgress.progress = if (safeDuration <= 0L) {
            0
        } else {
            ((positionMs.coerceAtLeast(0L) * pbProgress.max.toLong()) / safeDuration)
                .toInt()
                .coerceIn(0, pbProgress.max)
        }
    }

    private fun maybeLoadLyrics(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val key = mediaCacheKey(mediaItem)
        if (key == currentLyricKey) return
        currentLyricKey = key
        currentTimeline = null
        PlaybackLyricsCache.get(key)?.let {
            PlaybackLyricsCache.clearMiss(applicationContext, key)
            currentTimeline = it
            renderLyrics(player.currentPosition)
            return
        }
        if (PlaybackLyricsCache.isMissCached(applicationContext, key)) {
            tvLyricContent.text = "暂无歌词"
            return
        }
        tvLyricContent.text = "歌词加载中..."

        val config = PlaybackConfigStore.current()
        if (config.host.isBlank()) {
            lifecycleScope.launch {
                refreshFallbackConfigIfNeeded()
                if (currentLyricKey == key) {
                    // Trigger a fresh attempt once fallback config is restored.
                    currentLyricKey = null
                }
            }
        }
        val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val fullPath = mediaItem.mediaId
        val fileName = fullPath.substringAfterLast('/').ifBlank {
            mediaItem.mediaMetadata.title?.toString().orEmpty()
        }
        val entry = SmbEntry(
            name = fileName,
            fullPath = fullPath,
            isDirectory = false,
            streamUri = uri
        )

        lifecycleScope.launch {
            // 先查磁盘缓存
            val diskHit = withContext(Dispatchers.IO) {
                PlaybackLyricsCache.loadFromDisk(applicationContext, key)
            }
            if (diskHit != null) {
                if (currentLyricKey != key) return@launch
                PlaybackLyricsCache.put(key, diskHit)
                PlaybackLyricsCache.clearMiss(applicationContext, key)
                currentTimeline = diskHit
                renderLyrics(player.currentPosition)
                return@launch
            }

            // 磁盘未命中，从 SMB 加载
            val outcome = withContext(Dispatchers.IO) {
                loadLyricsWithRetry(entry)
            }
            if (currentLyricKey != key) return@launch
            currentTimeline = outcome.timeline
            if (outcome.timeline == null || outcome.timeline.lines.isEmpty()) {
                if (outcome.isMiss) {
                    PlaybackLyricsCache.markMissAsync(applicationContext, key)
                }
                tvLyricContent.text = "暂无歌词"
                return@launch
            }
            PlaybackLyricsCache.put(key, outcome.timeline)
            PlaybackLyricsCache.clearMiss(applicationContext, key)
            PlaybackLyricsCache.saveAsync(applicationContext, key, outcome.timeline)
            renderLyrics(player.currentPosition)
        }
    }

    private fun renderLyrics(positionMs: Long) {
        val timeline = currentTimeline ?: return
        if (timeline.lines.isEmpty()) return

        val currentIndex = LrcParser.findCurrentLineIndex(
            lines = timeline.lines,
            playbackPositionMs = positionMs,
            offsetMs = timeline.offsetMs
        )

        val normalColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        val highlightColor = ContextCompat.getColor(this, android.R.color.white)
        val builder = SpannableStringBuilder()
        var highlightStart = -1

        timeline.lines.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line.text.ifBlank { "..." })
            val end = builder.length
            if (index == currentIndex) {
                builder.setSpan(ForegroundColorSpan(highlightColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                highlightStart = start
            } else {
                builder.setSpan(ForegroundColorSpan(normalColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (index != timeline.lines.lastIndex) builder.append('\n')
        }
        tvLyricContent.text = builder

        if (highlightStart >= 0) {
            scrollLyrics.post {
                val layout = tvLyricContent.layout ?: return@post
                val lineIndex = layout.getLineForOffset(highlightStart)
                val lineTop = layout.getLineTop(lineIndex)
                val targetY = (lineTop - scrollLyrics.height / 3).coerceAtLeast(0)
                scrollLyrics.smoothScrollTo(0, targetY)
            }
        }
    }

    private fun maybeLoadArtwork(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val artworkKey = mediaCacheKey(mediaItem)
        if (artworkKey == currentArtworkKey) return
        currentArtworkKey = artworkKey
        PlaybackArtworkCache.get(artworkKey)?.let {
            setCurrentArtworkBitmap(it)
            return
        }
        setDefaultArtwork()

        lifecycleScope.launch {
            // 先查磁盘缓存
            val diskHit = withContext(Dispatchers.IO) {
                PlaybackArtworkCache.loadFromDisk(applicationContext, artworkKey)
            }
            if (diskHit != null) {
                if (currentArtworkKey != artworkKey) return@launch
                setCurrentArtworkBitmap(diskHit)
                PlaybackArtworkCache.put(artworkKey, diskHit)
                return@launch
            }

            // 磁盘未命中，从 SMB 加载
            val bitmap = withContext(Dispatchers.IO) {
                loadArtworkBitmap(resolvePlaybackConfig(), mediaItem)
            }
            if (currentArtworkKey != artworkKey) return@launch
            if (bitmap != null) {
                setCurrentArtworkBitmap(bitmap)
                PlaybackArtworkCache.put(artworkKey, bitmap)
                PlaybackArtworkCache.saveAsync(applicationContext, artworkKey, bitmap)
            } else {
                setDefaultArtwork()
            }
        }
    }

    private fun setCurrentArtworkBitmap(bitmap: Bitmap) {
        currentArtworkBitmap = bitmap
        ivArtwork.setImageBitmap(bitmap)
        if (layoutArtworkFullscreen.visibility == View.VISIBLE) {
            renderArtworkFullscreen(bitmap)
        }
    }

    private fun setDefaultArtwork() {
        currentArtworkBitmap = null
        ivArtwork.setImageResource(R.drawable.default_cover)
        if (layoutArtworkFullscreen.visibility == View.VISIBLE) {
            hideArtworkFullscreen()
        }
    }

    private fun showArtworkFullscreen() {
        val bitmap = currentArtworkBitmap ?: defaultArtworkBitmap()
        renderArtworkFullscreen(bitmap)
        layoutArtworkFullscreen.visibility = View.VISIBLE
        layoutArtworkFullscreen.bringToFront()
        layoutArtworkFullscreen.requestFocus()
    }

    private fun renderArtworkFullscreen(bitmap: Bitmap) {
        ivArtworkFullscreen.setImageBitmap(bitmap)
        val blurSource = createScaledBlurSource(bitmap)
        val blurred = BitmapBlur.blur(blurSource, radius = 14)
        if (blurSource !== bitmap) {
            blurSource.recycle()
        }
        ivArtworkFullscreenBlur.setImageBitmap(blurred)
    }

    private fun hideArtworkFullscreen() {
        layoutArtworkFullscreen.visibility = View.GONE
        layoutArtworkFrame.requestFocus()
    }

    private fun createScaledBlurSource(bitmap: Bitmap): Bitmap {
        val maxEdge = 320
        val largestEdge = maxOf(bitmap.width, bitmap.height)
        if (largestEdge <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / largestEdge.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun defaultArtworkBitmap(): Bitmap {
        val drawable = ContextCompat.getDrawable(this, R.drawable.default_cover)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun maybeLoadTagInfo(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val key = mediaCacheKey(mediaItem)
        if (key == currentTagKey) return
        currentTagKey = key

        tagInfoCache[key]?.let { info ->
            applyTagInfo(info)
            return
        }

        lifecycleScope.launch {
            val config = resolvePlaybackConfig()
            val mediaUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            val info = withContext(Dispatchers.IO) { loadAudioTagInfo(mediaUri, config) }
            if (currentTagKey != key) return@launch
            if (info != null) {
                tagInfoCache[key] = info
                applyTagInfo(info)
            }
        }
    }

    private fun applyTagInfo(info: AudioTagInfo) {
        if (!info.title.isNullOrBlank()) {
            tvTitle.text = "歌曲：${info.title}"
        }
        if (!info.artist.isNullOrBlank()) {
            tvArtist.text = "艺术家：${info.artist}"
        }
        if (!info.albumTitle.isNullOrBlank()) {
            tvAlbum.text = "专辑：${info.albumTitle}"
        }
    }

    private suspend fun loadAudioTagInfo(mediaUri: String, config: SmbConfig): AudioTagInfo? = runCatching {
        val metadata = SmbAudioMetadataProbe.probe(config, mediaUri) ?: return@runCatching null
        if (metadata.title == null && metadata.artist == null && metadata.album == null) return@runCatching null
        AudioTagInfo(
            title = metadata.title,
            artist = metadata.artist,
            albumTitle = metadata.album,
        )
    }.getOrNull()

    private suspend fun loadArtworkBitmap(config: SmbConfig, mediaItem: MediaItem) = runCatching {
        val artworkUri = mediaItem.mediaMetadata.artworkUri?.toString().orEmpty()
        if (artworkUri.isNotBlank()) {
            loadSmbBitmap(artworkUri, config)?.let { return@runCatching it }
        }

        val mediaUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        if (mediaUri.startsWith("smb://", ignoreCase = true)) {
            loadEmbeddedArtwork(mediaUri, config)?.let { return@runCatching it }
            loadSiblingArtwork(mediaUri, config)?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    private fun loadSiblingArtwork(mediaSmbUrl: String, config: SmbConfig) = runCatching {
        val context = SmbContextFactory.build(config)
        val parentPath = mediaSmbUrl.substringBeforeLast('/', "").trimEnd('/') + "/"
        val parentDir = SmbFile(parentPath, context)
        val candidates = listOf(
            "folder.jpg", "folder.png",
            "cover.jpg", "cover.png",
            "front.jpg", "front.png",
        )
        for (name in candidates) {
            val candidate = SmbFile(parentDir, name)
            if (!candidate.exists() || candidate.isDirectory) continue
            SmbFileInputStream(candidate).use { stream ->
                BitmapFactory.decodeStream(stream)?.let { return@runCatching it }
            }
        }
        null
    }.getOrNull()

    private fun loadSmbBitmap(smbUrl: String, config: SmbConfig) = runCatching {
        val smbFile = SmbFile(smbUrl, SmbContextFactory.build(config))
        if (!smbFile.exists() || smbFile.isDirectory) return@runCatching null
        SmbFileInputStream(smbFile).use { stream -> BitmapFactory.decodeStream(stream) }
    }.getOrNull()

    private suspend fun loadEmbeddedArtwork(mediaSmbUrl: String, config: SmbConfig) = runCatching {
        val artwork = SmbAudioMetadataProbe.probe(config, mediaSmbUrl)?.artworkData ?: return@runCatching null
        BitmapFactory.decodeByteArray(artwork, 0, artwork.size)
    }.getOrNull()

    private fun savePlaybackSnapshot() {
        if (!UiSettingsStore.rememberLastPlayback(this)) return
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return
        val currentMediaId = controller.currentMediaItem?.mediaId
            ?.let(PlaybackLocationResolver::normalizePath)
        val currentDirectoryPath = currentMediaId?.let(PlaybackLocationResolver::parentDirectory)
        val sourceConfig = PlaybackConfigStore.current()
            .takeIf { it.host.isNotBlank() }
            ?: fallbackConfig.takeIf { it.host.isNotBlank() }
        val uris = buildList {
            repeat(controller.mediaItemCount) { i ->
                controller.getMediaItemAt(i).localConfiguration?.uri?.toString()?.let(::add)
            }
        }
        val ids = buildList {
            repeat(controller.mediaItemCount) { i ->
                add(controller.getMediaItemAt(i).mediaId)
            }
        }
        if (uris.isEmpty()) return
        LastPlaybackStore.save(
            this,
            LastPlaybackStore.Snapshot(
                queueUris = uris,
                queueMediaIds = ids,
                currentIndex = controller.currentMediaItemIndex,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                title = controller.mediaMetadata.title?.toString().orEmpty(),
                currentMediaId = currentMediaId,
                currentDirectoryPath = currentDirectoryPath,
                sourceConnectionId = null,
                sourceConfig = sourceConfig
            )
        )
    }

    private fun locateCurrentPlayback() {
        lifecycleScope.launch {
            val storeState = withContext(Dispatchers.IO) { configStore.loadState() }
            val target = resolveActivePlaybackTarget(storeState)
                ?: LastPlaybackStore.load(this@PlaybackActivity)?.let(PlaybackLocationResolver::fromSnapshot)

            if (target == null) {
                Toast.makeText(this@PlaybackActivity, "无法定位当前播放目录", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (requiresLocateConfirmation(storeState, target)) {
                showLocateConfirmationDialog(target)
            } else {
                openBrowserAtPlaybackDirectory(target)
            }
        }
    }

    private fun requiresLocateConfirmation(
        storeState: SmbConfigStoreState,
        target: PlaybackLocationResolver.Target
    ): Boolean {
        if (target.sourceConnectionId != null && storeState.activeConnectionId != null) {
            return target.sourceConnectionId != storeState.activeConnectionId
        }
        return !PlaybackLocationResolver.matchesConnection(storeState.activeConfig, target.sourceConfig)
    }

    private fun showLocateConfirmationDialog(target: PlaybackLocationResolver.Target) {
        val targetLabel = target.sourceConfig.rootUrl().ifBlank { "目标 SMB 连接" }
        AlertDialog.Builder(this)
            .setTitle("切换 SMB 连接")
            .setMessage("当前播放文件位于另一个 SMB 连接，是否切换到 $targetLabel 并定位到该目录？")
            .setPositiveButton("切换并定位") { _, _ ->
                openBrowserAtPlaybackDirectory(target)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openBrowserAtPlaybackDirectory(target: PlaybackLocationResolver.Target) {
        startActivity(MainActivity.createLocateIntent(this, target))
        finish()
    }

    private fun resolveActivePlaybackTarget(storeState: SmbConfigStoreState): PlaybackLocationResolver.Target? {
        val currentMediaId = mediaController?.currentMediaItem?.mediaId
            ?.let(PlaybackLocationResolver::normalizePath)
            ?: return null
        val sourceConfig = PlaybackConfigStore.current()
            .takeIf { it.host.isNotBlank() }
            ?: storeState.activeConfig.takeIf { it.host.isNotBlank() }
            ?: SmbConfig.Empty
        return PlaybackLocationResolver.Target(
            mediaId = currentMediaId,
            directoryPath = PlaybackLocationResolver.parentDirectory(currentMediaId).orEmpty(),
            sourceConnectionId = storeState.activeConnectionId,
            sourceConfig = sourceConfig
        )
    }

    private fun releaseController() {
        playbackToastJob?.cancel()
        playbackToastJob = null
        tvPlaybackToast.visibility = View.GONE
        seekIdleCommitJob?.cancel()
        seekIdleCommitJob = null
        playerProgressHoldUntilMs = 0L
        playbackSeekController.reset()
        progressJob?.cancel()
        progressJob = null
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun formatMs(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private suspend fun loadLyricsWithRetry(entry: SmbEntry): LyricsLoadOutcome {
        repeat(3) { attempt ->
            val result = runCatching {
                lyricsRepository.loadDetailed(resolvePlaybackConfig(), entry)
            }.getOrNull()
            when (result?.status) {
                SmbLyricsRepository.Status.FOUND -> {
                    return LyricsLoadOutcome(result.timeline, isMiss = false)
                }

                SmbLyricsRepository.Status.MISS -> {
                    return LyricsLoadOutcome(null, isMiss = true)
                }

                SmbLyricsRepository.Status.ERROR, null -> {
                    if (attempt < 2) delay(250L * (attempt + 1))
                }
            }
        }
        return LyricsLoadOutcome(null, isMiss = false)
    }

    private suspend fun resolvePlaybackConfig(): SmbConfig {
        val active = PlaybackConfigStore.current()
        if (active.host.isNotBlank()) return active
        refreshFallbackConfigIfNeeded()
        return fallbackConfig
    }

    private suspend fun refreshFallbackConfigIfNeeded() {
        if (fallbackConfig.host.isNotBlank()) return
        val loaded = runCatching { configStore.loadState().activeConfig }.getOrNull() ?: return
        if (loaded.host.isBlank()) return
        fallbackConfig = loaded
        PlaybackConfigStore.update(loaded)
    }

    private fun mediaCacheKey(mediaItem: MediaItem): String {
        val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        return if (uri.isNotBlank()) uri else mediaItem.mediaId
    }

    private companion object {
        const val PLAYER_PROGRESS_RENDER_HOLD_MS = 350L
        const val PLAYBACK_TOAST_DURATION_MS = 2_000L
    }
}
