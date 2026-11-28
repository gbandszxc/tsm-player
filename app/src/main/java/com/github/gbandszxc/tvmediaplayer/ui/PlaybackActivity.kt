package com.github.gbandszxc.tvmediaplayer.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.MainActivity
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStoreState
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteInvalidTrackPolicy
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritePlaylist
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrack
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackIdentity
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesRepository
import com.github.gbandszxc.tvmediaplayer.history.PlayHistoryRepository
import com.github.gbandszxc.tvmediaplayer.history.PlayHistoryTrackFactory
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
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinator
import com.github.gbandszxc.tvmediaplayer.ui.modal.ConfirmModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormFieldSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ListModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalAction
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalListRow
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalFormValidators
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
import java.util.UUID

internal data class FavoritePlaylistChoice(
    val playlistId: String?,
    val label: String,
    val disabled: Boolean,
    val createNew: Boolean = false,
) {
    override fun toString(): String = label
}

class PlaybackActivity : BaseActivity() {

    private data class LyricsLoadOutcome(
        val timeline: LrcTimeline?,
        val isMiss: Boolean,
    )

    private val modalCoordinator by lazy { TsmModalCoordinator(this) }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var seekIdleCommitJob: Job? = null
    private var playbackToastJob: Job? = null
    private var lyricsScrollAnimator: ObjectAnimator? = null
    private var playerProgressHoldUntilMs: Long = 0L
    private val playbackSeekController = PlaybackSeekController()
    private val lyricsRepository = SmbLyricsRepository()
    private val favoritesRepository by lazy { FavoritesRepository(applicationContext) }
    private val playHistoryRepository by lazy { PlayHistoryRepository(applicationContext) }
    private lateinit var configStore: SmbConfigStore

    private var currentTimeline: LrcTimeline? = null
    private var currentLyricKey: String? = null
    // 歌词重渲染去重：timeline 实例与高亮行都没变时跳过整段 Spannable 重建
    private var renderedLyricsTimeline: LrcTimeline? = null
    private var renderedLyricsLineIndex: Int = -1
    // 进度时间文本去重：内容不变时不触发 TextView 重排
    private var renderedProgressText: String? = null
    private var currentArtworkKey: String? = null
    private var currentArtworkBitmap: Bitmap? = null
    private var currentArtworkUri: String? = null
    private var ambientBackdrop: AmbientBackdrop.Controller? = null
    private var currentTagKey: String? = null
    private val trackInfoStore = PlaybackTrackInfoStore.shared
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
    private lateinit var layoutArtworkFullscreenHost: FrameLayout
    private lateinit var layoutArtworkFullscreenStage: FrameLayout
    private lateinit var ivArtworkFullscreenBlur: ImageView
    private lateinit var ivArtworkFullscreen: ImageView
    private lateinit var viewArtworkFullscreenRim: View
    private lateinit var pbArtworkFullscreenProgress: ProgressBar
    private var fullscreenHudHideJob: Job? = null
    private var fullscreenBlurBreatheAnimator: AnimatorSet? = null
    private var fullscreenBlurBreathing: Boolean = false
    private lateinit var btnPrevious: Button
    private lateinit var btnPlayPause: Button
    private lateinit var btnNext: Button
    private lateinit var btnPlayMode: Button
    private lateinit var btnFavorite: Button
    private lateinit var btnLyricsFullscreen: Button
    private lateinit var btnBack: Button
    private lateinit var btnLocate: Button
    private lateinit var tvPlaybackToast: TextView
    private var playbackMode: PlaybackMode = PlaybackMode.ORDER
    private var renderedPlaybackMode: PlaybackMode? = null
    private var renderedPlaybackModeFocused: Boolean? = null
    private var currentTrackInDefaultFavorites: Boolean = false
    private var renderedFavoriteInDefault: Boolean? = null
    private var favoriteStateCheckKey: String? = null
    private var renderedFavoriteFocused: Boolean? = null
    private var renderedLocateFocused: Boolean? = null
    private var renderedPlayPausePlaying: Boolean? = null
    private var renderedLyricsFullscreenFocused: Boolean? = null
    private var renderedBackFocused: Boolean? = null
    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var btnSleepTimer: Button
    private var renderedSleepTimerLabel: String? = null
    private var renderedSleepTimerFocused: Boolean? = null
    private var favoritesPlaybackPlaylistId: String? = null
    private var invalidFavoriteDialogTrackKey: String? = null

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

        override fun onPlayerError(error: PlaybackException) {
            if (!FavoriteInvalidTrackPolicy.shouldOfferRemoval(error)) return
            confirmRemoveInvalidFavoriteTrack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configStore = SmbConfigStore(applicationContext)
        sleepTimerManager = SleepTimerManager(SleepTimerStore(applicationContext))
        favoritesPlaybackPlaylistId = intent.getStringExtra(EXTRA_FAVORITES_PLAYLIST_ID)
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
        lyricsScrollAnimator?.cancel()
        lyricsScrollAnimator = null
        fullscreenHudHideJob?.cancel()
        fullscreenHudHideJob = null
        setFullscreenBlurBreathing(false)
        ambientBackdrop?.setBreathing(false)
        savePlaybackSnapshot()
        releaseController()
        super.onStop()
    }

    override fun onDestroy() {
        fullscreenBlurBreatheAnimator?.cancel()
        fullscreenBlurBreatheAnimator = null
        ambientBackdrop?.dispose()
        ambientBackdrop = null
        super.onDestroy()
    }

    private fun bindViews() {
        ambientBackdrop = AmbientBackdrop.bind(
            findViewById(R.id.iv_ambient),
            findViewById(R.id.view_ambient_scrim),
        )
        layoutArtworkFrame = findViewById(R.id.layout_artwork_frame)
        LiquidGlassSurface.applyRoundedClip(layoutArtworkFrame, R.dimen.ui_radius_card)
        layoutArtworkFrame.elevation = 14f * resources.displayMetrics.density
        findViewById<View>(R.id.layout_playback_dock)?.let(LiquidGlassSurface::applyDock)
        ivArtwork = findViewById(R.id.iv_artwork)
        tvTitle = findViewById(R.id.tv_playback_title)
        tvArtist = findViewById(R.id.tv_playback_artist)
        tvAlbum = findViewById(R.id.tv_playback_album)
        tvTime = findViewById(R.id.tv_playback_time)
        pbProgress = findViewById(R.id.pb_playback)
        scrollLyrics = findViewById(R.id.scroll_lyrics)
        tvLyricContent = findViewById(R.id.tv_lyric_content)
        layoutArtworkFullscreen = findViewById(R.id.layout_artwork_fullscreen)
        layoutArtworkFullscreenHost = findViewById(R.id.layout_artwork_fullscreen_host)
        layoutArtworkFullscreenStage = findViewById(R.id.layout_artwork_fullscreen_stage)
        ivArtworkFullscreenBlur = findViewById(R.id.iv_artwork_fullscreen_blur)
        ivArtworkFullscreen = findViewById(R.id.iv_artwork_fullscreen)
        viewArtworkFullscreenRim = findViewById(R.id.view_artwork_fullscreen_rim)
        pbArtworkFullscreenProgress = findViewById(R.id.pb_artwork_fullscreen_progress)
        LiquidGlassSurface.applyRoundedClip(layoutArtworkFullscreenStage, R.dimen.ui_radius_panel)
        layoutArtworkFullscreenStage.elevation = 24f * resources.displayMetrics.density
        LiquidGlassSurface.applyGlassFrameOverlay(viewArtworkFullscreenRim, R.dimen.ui_radius_panel)
        layoutArtworkFullscreenHost.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (((r - l) != (or - ol) || (b - t) != (ob - ot)) &&
                layoutArtworkFullscreen.visibility == View.VISIBLE
            ) {
                updateFullscreenArtworkStageBounds(currentArtworkBitmap ?: defaultArtworkBitmap())
            }
        }
        btnPrevious = findViewById(R.id.btn_prev)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnPlayMode = findViewById(R.id.btn_play_mode)
        btnFavorite = findViewById(R.id.btn_favorite)
        btnLyricsFullscreen = findViewById(R.id.btn_lyrics_fullscreen)
        btnBack = findViewById(R.id.btn_back_to_browser)
        btnLocate = findViewById(R.id.btn_locate)
        btnSleepTimer = findViewById(R.id.btn_sleep_timer)
        tvPlaybackToast = findViewById(R.id.tv_playback_toast)
        renderPreviousButton()
        renderPlayPauseButton(isPlaying = false)
        renderNextButton()
        renderPlaybackModeButton()
        refreshFavoriteState()
        renderLyricsFullscreenButton()
        renderBackButton()
        renderLocateButton()
        renderSleepTimerButton()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        favoritesPlaybackPlaylistId = intent.getStringExtra(EXTRA_FAVORITES_PLAYLIST_ID)
    }

    private fun bindActions() {
        layoutArtworkFrame.setOnClickListener { showArtworkFullscreen() }
        layoutArtworkFrame.setOnFocusChangeListener { v, hasFocus ->
            UiMotion.animateFocusScale(v, hasFocus)
        }
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
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    hideArtworkFullscreen()
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    val controller = mediaController ?: return@setOnKeyListener false
                    if (controller.isPlaying) controller.pause() else controller.play()
                    renderPlayerState(controller)
                    showFullscreenHud()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    mediaController?.seekToPreviousMediaItem()
                    showFullscreenHud()
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    mediaController?.seekToNextMediaItem()
                    showFullscreenHud()
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    showFullscreenHud()
                    true
                }
                else -> false
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
        btnFavorite.setOnClickListener { toggleDefaultFavorite() }
        btnFavorite.setOnLongClickListener {
            showFavoritePlaylistDialog()
            true
        }
        btnFavorite.setOnFocusChangeListener { _, _ -> updateFavoriteButtonPresentation() }
        btnLyricsFullscreen.setOnClickListener {
            startActivity(Intent(this, LyricsFullscreenActivity::class.java))
        }
        btnLyricsFullscreen.setOnFocusChangeListener { _, _ -> renderLyricsFullscreenButton() }
        btnBack.setOnClickListener { finish() }
        btnBack.setOnFocusChangeListener { _, _ -> renderBackButton() }
        btnLocate.setOnClickListener { locateCurrentPlayback() }
        btnLocate.setOnFocusChangeListener { _, _ -> renderLocateButton() }
        btnSleepTimer.setOnClickListener {
            startActivity(Intent(this, SleepTimerActivity::class.java))
        }
        btnSleepTimer.setOnFocusChangeListener { _, _ -> renderSleepTimerButton(force = true) }
        bindTouchFeedback()

        pbProgress.setOnFocusChangeListener { view, hasFocus ->
            if (view.isInTouchMode || view.width <= 0) {
                view.scaleY = 1f
                return@setOnFocusChangeListener
            }
            view.animate().cancel()
            view.animate()
                .scaleY(if (hasFocus) 1.35f else 1f)
                .setDuration(if (hasFocus) 190L else 140L)
                .setInterpolator(UiMotion.springGlideInterpolator())
                .start()
        }

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

    private fun bindTouchFeedback() {
        // 暗色按钮（上一首/下一首/睡眠）用提亮 overlay；彩色按钮（播放暂停/模式/收藏/歌词/返回/定位）用加深 overlay。
        UiMotion.applyPressFeedback(btnPrevious, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnNext, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnSleepTimer, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnPlayPause, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnPlayMode, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnFavorite, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnLyricsFullscreen, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnBack, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnLocate, R.color.ui_press_overlay_dark)
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
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    if (controllerFuture === future) controllerFuture = null
                    Toast.makeText(this, R.string.playback_player_connect_failed, Toast.LENGTH_SHORT).show()
                    return@addListener
                }
                if (controllerFuture !== future) {
                    // onStop 已释放该 future，不要持有已释放的 controller
                    return@addListener
                }
                mediaController = controller
                controller.addListener(playerListener)
                updatePlaybackModeFromPlayer(controller)
                renderPlayerState(controller)
                startProgressTicker()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun renderPlayerState(player: Player) {
        updatePlaybackModeFromPlayer(player)
        renderTrackInfo(player)
        refreshFavoriteState()
        renderPlayPauseButton(player.isPlaying)
        ambientBackdrop?.setBreathing(player.isPlaying)
        setFullscreenBlurBreathing(layoutArtworkFullscreen.visibility == View.VISIBLE && player.isPlaying)

        maybeLoadLyrics(player)
        maybeLoadArtwork(player)
        maybeLoadTagInfo(player)
        if (shouldDeferPlayerProgressRender()) return
        renderProgress(player.currentPosition, player.duration)
        renderLyrics(player.currentPosition)
    }

    private fun renderTrackInfo(player: Player) {
        val mediaItem = player.currentMediaItem
        val key = mediaItem?.let(::mediaCacheKey)
        val display = PlaybackTrackInfoResolver.resolve(
            store = trackInfoStore,
            key = key,
            mediaItemTitle = mediaItem?.mediaMetadata?.title?.toString(),
            mediaItemArtist = mediaItem?.mediaMetadata?.artist?.toString(),
            mediaItemAlbumTitle = mediaItem?.mediaMetadata?.albumTitle?.toString(),
            playerTitle = player.mediaMetadata.title?.toString(),
            playerArtist = player.mediaMetadata.artist?.toString(),
            playerAlbumTitle = player.mediaMetadata.albumTitle?.toString(),
            emptyTitleFallback = getString(R.string.playback_empty_title),
        )
        tvTitle.text = getString(R.string.playback_title_format, display.title)
        tvArtist.text = getString(R.string.playback_artist_format, display.artist)
        tvAlbum.text = getString(R.string.playback_album_format, display.albumTitle)
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
            showPlaybackToast(getString(R.string.playback_mode_changed, getString(mode.labelResId)))
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
            label = getString(playbackMode.labelResId),
            iconResId = playbackMode.iconResId,
            focused = focused,
            backgroundResId = R.drawable.bg_button_amber,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_mode_button_expanded_min_width,
        )
    }

    private fun refreshFavoriteState() {
        val track = currentFavoriteTrack()
        val checkKey = track?.let(FavoriteTrackIdentity::keyOf)
        // 同一曲目已查询过收藏状态时直接复用结果，避免每次播放器事件都查库；
        // 收藏变更路径会重置 checkKey 触发重查
        if (checkKey != null && checkKey == favoriteStateCheckKey) {
            updateFavoriteButtonPresentation()
            return
        }
        favoriteStateCheckKey = checkKey
        val inDefault = track?.let {
            favoritesRepository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, it)
        } ?: false
        if (currentTrackInDefaultFavorites != inDefault) {
            currentTrackInDefaultFavorites = inDefault
            renderedFavoriteInDefault = null
        }
        updateFavoriteButtonPresentation()
    }

    private fun updateFavoriteButtonPresentation() {
        val focused = btnFavorite.hasFocus()
        if (
            renderedFavoriteInDefault == currentTrackInDefaultFavorites &&
            renderedFavoriteFocused == focused
        ) {
            return
        }
        renderedFavoriteInDefault = currentTrackInDefaultFavorites
        renderedFavoriteFocused = focused
        renderPlaybackButton(
            button = btnFavorite,
            spec = PlaybackButtonPresentation.favorite(
                context = this,
                inDefaultFavorites = currentTrackInDefaultFavorites,
                focused = focused,
            ),
            backgroundResId = R.drawable.bg_button_red,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_favorite_button_expanded_min_width,
        )
    }

    private fun toggleDefaultFavorite() {
        val track = currentFavoriteTrack() ?: run {
            showPlaybackToast(getString(R.string.favorites_empty_current_track))
            return
        }
        if (currentTrackInDefaultFavorites) {
            favoritesRepository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track)
            currentTrackInDefaultFavorites = false
            showPlaybackToast(getString(R.string.favorites_removed_default))
        } else {
            favoritesRepository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track)
            currentTrackInDefaultFavorites = true
            showPlaybackToast(getString(R.string.favorites_added_default))
        }
        renderedFavoriteInDefault = null
        updateFavoriteButtonPresentation()
    }

    private fun renderPreviousButton() {
        renderPlaybackButton(
            button = btnPrevious,
            spec = PlaybackButtonPresentation.previous(this),
            backgroundResId = R.drawable.bg_button_dark,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
        )
    }

    private fun renderPlayPauseButton(isPlaying: Boolean) {
        if (renderedPlayPausePlaying == isPlaying) return
        renderedPlayPausePlaying = isPlaying
        renderPlaybackButton(
            button = btnPlayPause,
            spec = PlaybackButtonPresentation.playPause(this, isPlaying = isPlaying),
            backgroundResId = if (isPlaying) R.drawable.bg_button_amber else R.drawable.bg_button_green,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
        )
    }

    private fun renderNextButton() {
        renderPlaybackButton(
            button = btnNext,
            spec = PlaybackButtonPresentation.next(this),
            backgroundResId = R.drawable.bg_button_dark,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
        )
    }

    private fun renderLocateButton() {
        val focused = btnLocate.hasFocus()
        if (renderedLocateFocused == focused) return
        renderedLocateFocused = focused
        renderCompactIconButton(
            button = btnLocate,
            label = getString(R.string.playback_locate),
            iconResId = R.drawable.ic_locate_crosshair,
            focused = focused,
            backgroundResId = R.drawable.bg_button_light_yellow,
            iconColorResId = R.color.ui_text_warning_glass,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_locate_button_expanded_min_width,
        )
    }

    private fun renderLyricsFullscreenButton() {
        val focused = btnLyricsFullscreen.hasFocus()
        if (renderedLyricsFullscreenFocused == focused) return
        renderedLyricsFullscreenFocused = focused
        renderPlaybackButton(
            button = btnLyricsFullscreen,
            spec = PlaybackButtonPresentation.lyricsFullscreen(this, focused = focused),
            backgroundResId = R.drawable.bg_button_primary,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_mode_button_expanded_min_width,
        )
    }

    private fun renderBackButton() {
        val focused = btnBack.hasFocus()
        if (renderedBackFocused == focused) return
        renderedBackFocused = focused
        renderPlaybackButton(
            button = btnBack,
            spec = PlaybackButtonPresentation.backToBrowser(this, focused = focused),
            backgroundResId = R.drawable.bg_button_primary,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_mode_button_expanded_min_width,
        )
    }

    private fun renderSleepTimerButton(force: Boolean = false) {
        val remaining = sleepTimerManager.remainingMinutesCeil()
        val label = if (remaining != null) {
            getString(R.string.sleep_timer_button_remaining, remaining)
        } else {
            getString(R.string.sleep_timer_button_inactive)
        }
        val focused = btnSleepTimer.hasFocus()
        if (!force && renderedSleepTimerLabel == label && renderedSleepTimerFocused == focused) return
        renderedSleepTimerLabel = label
        renderedSleepTimerFocused = focused

        btnSleepTimer.text = if (focused) label else ""
        btnSleepTimer.contentDescription = label
        btnSleepTimer.setSingleLine(true)
        btnSleepTimer.ellipsize = TextUtils.TruncateAt.END
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
        val iconSize = resources.getDimensionPixelSize(R.dimen.ui_button_icon_size)
        val iconRes = if (remaining != null) R.drawable.ic_sleep_timer_active else R.drawable.ic_sleep_timer
        val icon = ContextCompat.getDrawable(this, iconRes)?.mutate() ?: return
        DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.ui_text_primary))
        icon.setBounds(0, 0, iconSize, iconSize)
        val basePadding = resources.getDimensionPixelSize(R.dimen.ui_space_3xl)
        if (focused) {
            btnSleepTimer.text = label
            btnSleepTimer.setCompoundDrawables(icon, null, null, null)
            btnSleepTimer.setPaddingRelative(basePadding, btnSleepTimer.paddingTop, basePadding, btnSleepTimer.paddingBottom)
        } else {
            btnSleepTimer.text = ""
            btnSleepTimer.setCompoundDrawables(icon, null, null, null)
            val collapsedWidth = resources.getDimensionPixelSize(R.dimen.ui_playback_mode_button_collapsed_width)
            val startPad = ((collapsedWidth - iconSize) / 2).coerceAtLeast(0)
            val endPad = (collapsedWidth - iconSize - startPad).coerceAtLeast(0)
            btnSleepTimer.setPaddingRelative(startPad, btnSleepTimer.paddingTop, endPad, btnSleepTimer.paddingBottom)
        }
        btnSleepTimer.compoundDrawablePadding = if (focused) {
            resources.getDimensionPixelSize(R.dimen.ui_space_sm)
        } else {
            0
        }
        UiMotion.animateWidthTo(btnSleepTimer, targetWidth, expand = focused)
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
        renderPlaybackButton(
            button = button,
            spec = PlaybackButtonSpec(
                text = if (focused) label else "",
                contentDescription = label,
                iconResId = iconResId,
                expandsOnFocus = true,
            ),
            backgroundResId = backgroundResId,
            iconColorResId = iconColorResId,
            collapsedWidthResId = collapsedWidthResId,
            expandedMinWidthResId = expandedMinWidthResId,
        )
    }

    private fun renderPlaybackButton(
        button: Button,
        spec: PlaybackButtonSpec,
        backgroundResId: Int,
        iconColorResId: Int,
        collapsedWidthResId: Int,
        expandedMinWidthResId: Int,
    ) {
        val expanded = spec.expandsOnFocus && button.hasFocus()
        button.text = spec.text
        button.contentDescription = spec.contentDescription
        button.setSingleLine(true)
        button.ellipsize = TextUtils.TruncateAt.END
        button.setBackgroundResource(backgroundResId)
        button.background?.jumpToCurrentState()
        button.minWidth = resources.getDimensionPixelSize(
            if (expanded) expandedMinWidthResId else collapsedWidthResId
        )
        val layoutParams = button.layoutParams
        val targetWidth = if (expanded) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            resources.getDimensionPixelSize(collapsedWidthResId)
        }
        if (layoutParams.width != targetWidth) {
            layoutParams.width = targetWidth
            button.layoutParams = layoutParams
        }
        val iconSize = resources.getDimensionPixelSize(R.dimen.ui_button_icon_size)
        val icon = ContextCompat.getDrawable(this, spec.iconResId)?.mutate() ?: return
        DrawableCompat.setTint(
            icon,
            ContextCompat.getColor(this, iconColorResId)
        )
        icon.setBounds(0, 0, iconSize, iconSize)
        val showsLabel = expanded && spec.text.isNotEmpty()
        val basePadding = resources.getDimensionPixelSize(R.dimen.ui_space_3xl)
        if (showsLabel) {
            button.text = spec.text
            button.setCompoundDrawables(icon, null, null, null)
            button.setPaddingRelative(basePadding, button.paddingTop, basePadding, button.paddingBottom)
        } else {
            button.text = ""
            button.setCompoundDrawables(icon, null, null, null)
            val collapsedWidth = resources.getDimensionPixelSize(collapsedWidthResId)
            val startPad = ((collapsedWidth - iconSize) / 2).coerceAtLeast(0)
            val endPad = (collapsedWidth - iconSize - startPad).coerceAtLeast(0)
            button.setPaddingRelative(startPad, button.paddingTop, endPad, button.paddingBottom)
        }
        button.compoundDrawablePadding = if (showsLabel) {
            resources.getDimensionPixelSize(R.dimen.ui_space_sm)
        } else {
            0
        }
        // 图标与文字都就位后再测量展开宽度；单行约束保证中间帧不改变按钮高度。
        UiMotion.animateWidthTo(button, targetWidth, expand = expanded)
    }

    private fun showPlaybackToast(message: String) {
        playbackToastJob?.cancel()
        tvPlaybackToast.text = message
        UiMotion.animateToastEnter(tvPlaybackToast)
        playbackToastJob = lifecycleScope.launch {
            delay(PLAYBACK_TOAST_DURATION_MS)
            UiMotion.animateToastExit(tvPlaybackToast)
        }
    }

    private fun currentFavoriteTrack(): FavoriteTrack? {
        val mediaItem = mediaController?.currentMediaItem ?: return null
        val streamUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val mediaId = mediaItem.mediaId.ifBlank { streamUri }
        if (mediaId.isBlank() || streamUri.isBlank()) return null

        val key = mediaCacheKey(mediaItem)
        val fallbackTitle = mediaItem.mediaMetadata.title?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: mediaId.substringAfterLast('/').ifBlank { streamUri.substringAfterLast('/') }
        val display = trackInfoStore.displayFor(
            key = key,
            fallbackTitle = fallbackTitle,
            fallbackArtist = mediaItem.mediaMetadata.artist?.toString().orEmpty().ifBlank { "-" },
            fallbackAlbumTitle = mediaItem.mediaMetadata.albumTitle?.toString().orEmpty().ifBlank { "-" }
        )
        val sourceConfig = PlaybackConfigStore.current().takeIf { it.host.isNotBlank() }
            ?: fallbackConfig.takeIf { it.host.isNotBlank() }
        return FavoriteTrack(
            id = UUID.randomUUID().toString(),
            playlistId = FavoritesRepository.DEFAULT_PLAYLIST_ID,
            mediaId = mediaId,
            streamUri = streamUri,
            title = display.title.orEmpty(),
            artist = display.artist.takeUnless { it == "-" },
            album = display.albumTitle.takeUnless { it == "-" },
            artworkUri = mediaItem.mediaMetadata.artworkUri?.toString(),
            sourceConnectionId = null,
            sourceConfig = sourceConfig,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun showFavoritePlaylistDialog() {
        val track = currentFavoriteTrack() ?: run {
            showPlaybackToast(getString(R.string.favorites_empty_current_track))
            return
        }
        lateinit var listDialog: Dialog
        fun refreshDialog() {
            refreshFavoritePlaylistDialog(listDialog, track)
        }
        fun rebuildRows(): List<ModalListRow> = buildFavoritePlaylistRows(
            track = track,
            onPlaylistChanged = ::refreshDialog,
            onCreateNew = {
                showCreatePlaylistAndAddDialog(track) {
                    refreshDialog()
                }
            }
        )

        listDialog = modalCoordinator.showListModal(
            ListModalSpec(
                sectionLabel = getString(R.string.favorites_title),
                title = getString(R.string.favorites_select_playlist),
                message = getString(R.string.playback_add_to_playlist_message),
                rows = rebuildRows(),
            )
        )
    }

    private fun buildFavoritePlaylistRows(
        track: FavoriteTrack,
        onPlaylistChanged: () -> Unit,
        onCreateNew: () -> Unit,
    ): List<ModalListRow> {
        val playlists = favoritesRepository.getPlaylists()
        val choices = buildFavoritePlaylistChoices(playlists, track)
        return choices.map { choice ->
            ModalListRow(
                key = choice.playlistId ?: "create_new",
                label = choice.label,
                enabled = !choice.disabled,
                onClick = {
                    when {
                        choice.createNew -> onCreateNew()
                        choice.disabled -> showPlaybackToast(getString(R.string.favorites_already_in_playlist))
                        choice.playlistId != null -> {
                            addTrackToPlaylist(choice.playlistId, track)
                            onPlaylistChanged()
                        }
                    }
                },
            )
        }
    }

    private fun refreshFavoritePlaylistDialog(
        dialog: Dialog,
        track: FavoriteTrack,
    ) {
        modalCoordinator.updateListRows(
            dialog = dialog,
            rows = buildFavoritePlaylistRows(
                track = track,
                onPlaylistChanged = {
                    refreshFavoritePlaylistDialog(dialog, track)
                },
                onCreateNew = {
                    showCreatePlaylistAndAddDialog(track) {
                        refreshFavoritePlaylistDialog(dialog, track)
                    }
                },
            ),
        )
    }

    private fun buildFavoritePlaylistChoices(
        playlists: List<FavoritePlaylist>,
        track: FavoriteTrack,
    ): List<FavoritePlaylistChoice> {
        return playlists.map { playlist ->
            val contains = favoritesRepository.containsTrack(playlist.id, track)
            FavoritePlaylistChoice(
                playlistId = playlist.id,
                label = if (contains) {
                    getString(R.string.favorites_playlist_already_added, favoritePlaylistDisplayName(playlist))
                } else {
                    favoritePlaylistDisplayName(playlist)
                },
                disabled = contains,
            )
        } + FavoritePlaylistChoice(
            playlistId = null,
            label = "+ " + getString(R.string.favorites_new_playlist),
            disabled = false,
            createNew = true,
        )
    }

    private fun favoritePlaylistDisplayName(playlist: FavoritePlaylist): String =
        if (playlist.isDefault) getString(R.string.favorites_default_playlist) else playlist.name

    private fun addTrackToPlaylist(playlistId: String, track: FavoriteTrack) {
        val added = favoritesRepository.addTrack(
            playlistId,
            track.copy(
                id = UUID.randomUUID().toString(),
                playlistId = playlistId,
                addedAt = System.currentTimeMillis(),
            )
        )
        showPlaybackToast(
            if (added) {
                if (playlistId == FavoritesRepository.DEFAULT_PLAYLIST_ID) {
                    getString(R.string.favorites_added_default)
                } else {
                    getString(R.string.favorites_added_playlist)
                }
            }
            else getString(R.string.favorites_already_in_playlist)
        )
        favoriteStateCheckKey = null
        refreshFavoriteState()
    }

    private fun showCreatePlaylistAndAddDialog(
        track: FavoriteTrack,
        onPlaylistCreated: (() -> Unit)? = null,
    ) {
        val existing = favoritesRepository.getPlaylists().map { it.name }.toSet()
        val dialog = modalCoordinator.showFormModal(
            FormModalSpec(
                sectionLabel = getString(R.string.favorites_title),
                title = getString(R.string.favorites_new_playlist),
                fields = listOf(
                    FormFieldSpec(
                        key = "playlist_name",
                        label = getString(R.string.favorites_name_label),
                        initialValue = "",
                        hint = getString(R.string.favorites_playlist_name_hint),
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                    )
                ),
                primaryAction = ModalAction(getString(R.string.favorites_dialog_confirm), isPrimary = true),
                secondaryAction = ModalAction(getString(R.string.favorites_dialog_cancel)),
            )
        )
        modalCoordinator.bindFormPrimaryAction(dialog, "playlist_name") { values ->
            val name = values.getValue("playlist_name")
            val error = TsmModalFormValidators.validatePlaylistName(
                name,
                existing,
                emptyMessage = getString(R.string.favorites_playlist_name_empty),
                duplicateMessage = getString(R.string.favorites_playlist_name_duplicate),
            )
            if (error != null) {
                modalCoordinator.updateFieldError(dialog, "playlist_name", error)
                return@bindFormPrimaryAction false
            }
            val playlistId = favoritesRepository.createPlaylist(name.trim())?.id ?: run {
                modalCoordinator.updateFieldError(dialog, "playlist_name", getString(R.string.favorites_playlist_name_duplicate))
                return@bindFormPrimaryAction false
            }
            addTrackToPlaylist(playlistId, track)
            onPlaylistCreated?.invoke()
            true
        }
    }

    private fun confirmRemoveInvalidFavoriteTrack() {
        val playlistId = favoritesPlaybackPlaylistId ?: return
        val track = currentFavoriteTrack() ?: return
        val trackKey = FavoriteTrackIdentity.keyOf(track)
        if (invalidFavoriteDialogTrackKey == trackKey) return
        invalidFavoriteDialogTrackKey = trackKey

        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = getString(R.string.favorites_title),
                title = getString(R.string.favorites_invalid_track_title),
                message = getString(R.string.favorites_invalid_track_message),
                confirmAction = ModalAction(
                    getString(R.string.favorites_invalid_track_remove),
                    isDanger = true,
                    onClick = {
                        favoritesRepository.removeTrack(playlistId, track)
                        favoriteStateCheckKey = null
                        refreshFavoriteState()
                        invalidFavoriteDialogTrackKey = null
                    },
                ),
                cancelAction = ModalAction(
                    getString(R.string.favorites_invalid_track_keep),
                    onClick = { invalidFavoriteDialogTrackKey = null },
                ),
            )
        )
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
        val timeText = "${formatMs(positionMs)} / ${formatMs(safeDuration)}"
        if (timeText != renderedProgressText) {
            renderedProgressText = timeText
            tvTime.text = timeText
        }
        val progressValue = if (safeDuration <= 0L) {
            0
        } else {
            ((positionMs.coerceAtLeast(0L) * pbProgress.max.toLong()) / safeDuration)
                .toInt()
                .coerceIn(0, pbProgress.max)
        }
        pbProgress.progress = progressValue
        pbArtworkFullscreenProgress.progress = progressValue
    }

    private fun maybeLoadLyrics(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val key = mediaCacheKey(mediaItem)
        if (key == currentLyricKey) return
        currentLyricKey = key
        currentTimeline = null
        PlaybackLyricsCache.get(key)?.let {
            // 文件删除是低频小操作，放后台避免在主线程做磁盘 IO
            lifecycleScope.launch(Dispatchers.IO) {
                PlaybackLyricsCache.clearMiss(applicationContext, key)
            }
            currentTimeline = it
            renderLyrics(player.currentPosition)
            return
        }
        if (PlaybackLyricsCache.isMissCached(applicationContext, key)) {
            showLyricsStatus(getString(R.string.playback_lyrics_empty))
            return
        }
        showLyricsStatus(getString(R.string.playback_lyrics_loading))

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
                showLyricsStatus(getString(R.string.playback_lyrics_empty))
                return@launch
            }
            PlaybackLyricsCache.put(key, outcome.timeline)
            PlaybackLyricsCache.clearMiss(applicationContext, key)
            PlaybackLyricsCache.saveAsync(applicationContext, key, outcome.timeline)
            renderLyrics(player.currentPosition)
        }
    }

    private fun showLyricsStatus(text: String) {
        tvLyricContent.text = text
        resetLyricsScrollToTop()
    }

    private fun resetLyricsScrollToTop() {
        lyricsScrollAnimator?.cancel()
        scrollLyrics.post { scrollLyrics.scrollTo(0, 0) }
    }

    private fun renderLyrics(positionMs: Long) {
        val timeline = currentTimeline ?: return
        if (timeline.lines.isEmpty()) return

        val renderPlan = LyricsViewRenderer.buildRenderPlan(
            timeline = timeline,
            positionMs = positionMs,
        )
        val currentIndex = renderPlan.currentLineIndex
        if (
            timeline === renderedLyricsTimeline &&
            currentIndex == renderedLyricsLineIndex &&
            !renderPlan.shouldResetScrollToTop
        ) {
            return
        }
        renderedLyricsTimeline = timeline
        renderedLyricsLineIndex = currentIndex

        val activeColor = ContextCompat.getColor(this, R.color.ui_lyric_active)
        val nearColor = ContextCompat.getColor(this, R.color.ui_lyric_near)
        val midColor = ContextCompat.getColor(this, R.color.ui_lyric_mid)
        val farColor = ContextCompat.getColor(this, R.color.ui_lyric_far)
        val builder = SpannableStringBuilder()
        var highlightStart = -1

        timeline.lines.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line.text.ifBlank { "..." })
            val end = builder.length
            if (index == currentIndex) {
                builder.setSpan(ForegroundColorSpan(activeColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                highlightStart = start
            } else {
                val color = if (currentIndex < 0) {
                    midColor
                } else {
                    when (kotlin.math.abs(index - currentIndex)) {
                        1 -> nearColor
                        2 -> midColor
                        else -> farColor
                    }
                }
                builder.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (index != timeline.lines.lastIndex) builder.append('\n')
        }
        tvLyricContent.text = builder

        if (renderPlan.shouldResetScrollToTop) {
            resetLyricsScrollToTop()
        } else if (highlightStart >= 0) {
            scrollLyrics.post {
                val layout = tvLyricContent.layout ?: return@post
                val lineIndex = layout.getLineForOffset(highlightStart)
                val lineTop = layout.getLineTop(lineIndex)
                val targetY = (lineTop - scrollLyrics.height / 3).coerceAtLeast(0)
                lyricsScrollAnimator?.cancel()
                lyricsScrollAnimator = ObjectAnimator.ofInt(scrollLyrics, "scrollY", targetY).apply {
                    duration = 280L
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        }
    }

    private fun maybeLoadArtwork(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val artworkKey = mediaCacheKey(mediaItem)
        if (artworkKey == currentArtworkKey) return
        currentArtworkKey = artworkKey
        currentArtworkUri = mediaItem.mediaMetadata.artworkUri?.toString()
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
        ambientBackdrop?.update(currentArtworkUri, bitmap)
        if (layoutArtworkFullscreen.visibility == View.VISIBLE) {
            renderArtworkFullscreen(bitmap, animateChange = true)
        }
    }

    private fun setDefaultArtwork() {
        currentArtworkBitmap = null
        currentArtworkUri = null
        ivArtwork.setImageResource(R.drawable.default_cover)
        ambientBackdrop?.update(null)
        if (layoutArtworkFullscreen.visibility == View.VISIBLE) {
            renderArtworkFullscreen(defaultArtworkBitmap(), animateChange = false)
        }
    }

    private fun showArtworkFullscreen() {
        val bitmap = currentArtworkBitmap ?: defaultArtworkBitmap()
        renderArtworkFullscreen(bitmap, animateChange = false)
        layoutArtworkFullscreen.animate().cancel()
        layoutArtworkFullscreenStage.animate().cancel()
        pbArtworkFullscreenProgress.animate().cancel()
        layoutArtworkFullscreen.visibility = View.VISIBLE
        layoutArtworkFullscreen.alpha = 1f
        layoutArtworkFullscreenStage.alpha = 1f
        layoutArtworkFullscreenStage.scaleX = 1f
        layoutArtworkFullscreenStage.scaleY = 1f
        pbArtworkFullscreenProgress.alpha = 0f
        pbArtworkFullscreenProgress.translationY = 4f * resources.displayMetrics.density
        layoutArtworkFullscreen.bringToFront()
        layoutArtworkFullscreen.requestFocus()
        setFullscreenBlurBreathing(mediaController?.isPlaying == true)
        showFullscreenHud()
        layoutArtworkFullscreen.post {
            if (!layoutArtworkFullscreen.isAttachedToWindow ||
                layoutArtworkFullscreen.visibility != View.VISIBLE ||
                layoutArtworkFullscreen.width <= 0
            ) {
                return@post
            }
            updateFullscreenArtworkStageBounds(bitmap)
            layoutArtworkFullscreen.alpha = 0f
            layoutArtworkFullscreenStage.scaleX = 0.93f
            layoutArtworkFullscreenStage.scaleY = 0.93f
            layoutArtworkFullscreen.animate()
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(UiMotion.springGlideInterpolator())
                .start()
            layoutArtworkFullscreenStage.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260L)
                .setInterpolator(UiMotion.springGlideInterpolator())
                .withEndAction {
                    (viewArtworkFullscreenRim.background as? LiquidGlassDrawable)?.triggerSheen()
                }
                .start()
        }
    }

    private fun renderArtworkFullscreen(bitmap: Bitmap, animateChange: Boolean) {
        updateFullscreenArtworkStageBounds(bitmap)
        ivArtworkFullscreen.setImageBitmap(bitmap)
        val blurSource = createScaledBlurSource(bitmap)
        val blurred = BitmapBlur.blur(blurSource, radius = 14)
        if (blurSource !== bitmap) {
            blurSource.recycle()
        }
        ivArtworkFullscreenBlur.setImageBitmap(blurred)
        if (animateChange && layoutArtworkFullscreenStage.width > 0) {
            layoutArtworkFullscreenStage.animate().cancel()
            layoutArtworkFullscreenStage.alpha = 0.35f
            layoutArtworkFullscreenStage.scaleX = 0.96f
            layoutArtworkFullscreenStage.scaleY = 0.96f
            layoutArtworkFullscreenStage.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240L)
                .setInterpolator(UiMotion.springGlideInterpolator())
                .withEndAction {
                    (viewArtworkFullscreenRim.background as? LiquidGlassDrawable)?.triggerSheen()
                }
                .start()
            showFullscreenHud()
        }
    }

    private fun updateFullscreenArtworkStageBounds(bitmap: Bitmap) {
        val padH = layoutArtworkFullscreenHost.paddingLeft + layoutArtworkFullscreenHost.paddingRight
        val padV = layoutArtworkFullscreenHost.paddingTop + layoutArtworkFullscreenHost.paddingBottom
        val measuredW = layoutArtworkFullscreenHost.width - padH
        val measuredH = layoutArtworkFullscreenHost.height - padV
        val availW = if (measuredW > 0) {
            measuredW
        } else {
            (resources.displayMetrics.widthPixels -
                resources.getDimensionPixelSize(R.dimen.ui_artwork_fullscreen_padding_h) * 2).coerceAtLeast(1)
        }
        val availH = if (measuredH > 0) {
            measuredH
        } else {
            (resources.displayMetrics.heightPixels -
                resources.getDimensionPixelSize(R.dimen.ui_artwork_fullscreen_padding_top) -
                resources.getDimensionPixelSize(R.dimen.ui_artwork_fullscreen_padding_bottom)).coerceAtLeast(1)
        }
        val bmpW = bitmap.width.coerceAtLeast(1)
        val bmpH = bitmap.height.coerceAtLeast(1)
        val scale = minOf(availW.toFloat() / bmpW.toFloat(), availH.toFloat() / bmpH.toFloat())
        val targetW = (bmpW * scale).toInt().coerceAtLeast(1)
        val targetH = (bmpH * scale).toInt().coerceAtLeast(1)
        val lp = layoutArtworkFullscreenStage.layoutParams
        if (lp.width != targetW || lp.height != targetH) {
            lp.width = targetW
            lp.height = targetH
            layoutArtworkFullscreenStage.layoutParams = lp
        }
    }

    private fun showFullscreenHud() {
        fullscreenHudHideJob?.cancel()
        if (layoutArtworkFullscreen.visibility != View.VISIBLE) return
        pbArtworkFullscreenProgress.animate().cancel()
        pbArtworkFullscreenProgress.visibility = View.VISIBLE
        if (pbArtworkFullscreenProgress.alpha < 1f || pbArtworkFullscreenProgress.translationY != 0f) {
            pbArtworkFullscreenProgress.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(UiMotion.springGlideInterpolator())
                .start()
        }
        fullscreenHudHideJob = lifecycleScope.launch {
            delay(FULLSCREEN_HUD_AUTO_HIDE_MS)
            hideFullscreenHud()
        }
    }

    private fun hideFullscreenHud() {
        pbArtworkFullscreenProgress.animate().cancel()
        pbArtworkFullscreenProgress.animate()
            .alpha(0f)
            .translationY(4f * resources.displayMetrics.density)
            .setDuration(260L)
            .start()
    }

    private fun setFullscreenBlurBreathing(enabled: Boolean) {
        if (fullscreenBlurBreathing == enabled) return
        fullscreenBlurBreathing = enabled
        fullscreenBlurBreatheAnimator?.cancel()
        fullscreenBlurBreatheAnimator = null
        if (!enabled) {
            if (ivArtworkFullscreenBlur.width > 0) {
                ivArtworkFullscreenBlur.animate().scaleX(1f).scaleY(1f).setDuration(300L).start()
            } else {
                ivArtworkFullscreenBlur.scaleX = 1f
                ivArtworkFullscreenBlur.scaleY = 1f
            }
            return
        }
        val sx = ObjectAnimator.ofFloat(ivArtworkFullscreenBlur, View.SCALE_X, 1.05f, 1.11f).apply {
            duration = 14_000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
        }
        val sy = ObjectAnimator.ofFloat(ivArtworkFullscreenBlur, View.SCALE_Y, 1.05f, 1.11f).apply {
            duration = 14_000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
        }
        fullscreenBlurBreatheAnimator = AnimatorSet().apply {
            playTogether(sx, sy)
            start()
        }
    }

    private fun hideArtworkFullscreen() {
        fullscreenHudHideJob?.cancel()
        fullscreenHudHideJob = null
        setFullscreenBlurBreathing(false)
        layoutArtworkFullscreen.animate().cancel()
        layoutArtworkFullscreenStage.animate().cancel()
        pbArtworkFullscreenProgress.animate().cancel()
        if (layoutArtworkFullscreen.visibility == View.VISIBLE && layoutArtworkFullscreen.width > 0) {
            layoutArtworkFullscreen.animate()
                .alpha(0f)
                .setDuration(150L)
                .withEndAction {
                    layoutArtworkFullscreen.visibility = View.GONE
                    layoutArtworkFullscreen.alpha = 1f
                    layoutArtworkFullscreenStage.scaleX = 1f
                    layoutArtworkFullscreenStage.scaleY = 1f
                    layoutArtworkFrame.requestFocus()
                }
                .start()
            return
        }
        layoutArtworkFullscreen.visibility = View.GONE
        layoutArtworkFullscreen.alpha = 1f
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

        lifecycleScope.launch {
            val config = resolvePlaybackConfig()
            val mediaUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            val info = withContext(Dispatchers.IO) { loadAudioTagInfo(mediaUri, config) }
            if (currentTagKey != key) return@launch
            if (info != null) {
                trackInfoStore.remember(key, info)
                updateHistoryTagInfo(mediaItem, config, info)
                renderTrackInfo(player)
            }
        }
    }

    private fun updateHistoryTagInfo(
        mediaItem: MediaItem,
        config: SmbConfig,
        info: PlaybackTrackInfo,
    ) {
        val base = PlayHistoryTrackFactory.fromMediaItem(mediaItem, config)
        val updated = base.copy(
            title = info.title?.takeIf { it.isNotBlank() } ?: base.title,
            artist = info.artist?.takeIf { it.isNotBlank() } ?: base.artist,
            album = info.albumTitle?.takeIf { it.isNotBlank() } ?: base.album,
        )
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { playHistoryRepository.record(updated) }
        }
    }

    private suspend fun loadAudioTagInfo(mediaUri: String, config: SmbConfig): PlaybackTrackInfo? = runCatching {
        val metadata = SmbAudioMetadataProbe.probe(config, mediaUri) ?: return@runCatching null
        if (metadata.title == null && metadata.artist == null && metadata.album == null) return@runCatching null
        PlaybackTrackInfo(
            title = metadata.title,
            artist = metadata.artist,
            albumTitle = metadata.album,
        )
    }.getOrNull()

    private suspend fun loadArtworkBitmap(config: SmbConfig, mediaItem: MediaItem) = runCatching {
        val artworkUri = mediaItem.mediaMetadata.artworkUri?.toString().orEmpty()
        if (artworkUri.isNotBlank()) {
            if (artworkUri.startsWith("smb://", ignoreCase = true)) {
                loadSmbBitmap(artworkUri, config)?.let { return@runCatching it }
            } else {
                contentResolver.openInputStream(Uri.parse(artworkUri))?.use(PlaybackArtworkCache::decodeSampled)
                    ?.let { return@runCatching it }
            }
        }

        val mediaUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        if (mediaUri.startsWith("smb://", ignoreCase = true)) {
            loadEmbeddedArtwork(mediaUri, config)?.let { return@runCatching it }
            loadSiblingArtwork(mediaUri, config)?.let { return@runCatching it }
        } else if (Uri.parse(mediaUri).scheme.equals("file", ignoreCase = true)) {
            loadEmbeddedArtwork(mediaUri, config)?.let { return@runCatching it }
            val file = Uri.parse(mediaUri).path?.let(::File)
            listOf("folder.jpg", "folder.png", "cover.jpg", "cover.png", "front.jpg", "front.png")
                .asSequence()
                .mapNotNull { name -> file?.parentFile?.let { File(it, name) } }
                .firstOrNull(File::isFile)
                ?.let(PlaybackArtworkCache::decodeSampled)
                ?.let { return@runCatching it }
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
                PlaybackArtworkCache.decodeSampled(stream)?.let { return@runCatching it }
            }
        }
        null
    }.getOrNull()

    private fun loadSmbBitmap(smbUrl: String, config: SmbConfig) = runCatching {
        val smbFile = SmbFile(smbUrl, SmbContextFactory.build(config))
        if (!smbFile.exists() || smbFile.isDirectory) return@runCatching null
        SmbFileInputStream(smbFile).use(PlaybackArtworkCache::decodeSampled)
    }.getOrNull()

    private suspend fun loadEmbeddedArtwork(mediaSmbUrl: String, config: SmbConfig) = runCatching {
        val artwork = SmbAudioMetadataProbe.probe(config, mediaSmbUrl)?.artworkData ?: return@runCatching null
        PlaybackArtworkCache.decodeSampled(artwork)
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
        val currentMediaItem = controller.currentMediaItem
        val display = PlaybackTrackInfoResolver.resolve(
            store = trackInfoStore,
            key = currentMediaItem?.let(::mediaCacheKey),
            mediaItemTitle = currentMediaItem?.mediaMetadata?.title?.toString(),
            mediaItemArtist = currentMediaItem?.mediaMetadata?.artist?.toString(),
            mediaItemAlbumTitle = currentMediaItem?.mediaMetadata?.albumTitle?.toString(),
            playerTitle = controller.mediaMetadata.title?.toString(),
            playerArtist = controller.mediaMetadata.artist?.toString(),
            playerAlbumTitle = controller.mediaMetadata.albumTitle?.toString(),
            emptyTitleFallback = getString(R.string.playback_empty_title),
        )
        LastPlaybackStore.save(
            this,
            LastPlaybackStore.Snapshot(
                queueUris = uris,
                queueMediaIds = ids,
                currentIndex = controller.currentMediaItemIndex,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                title = display.title.orEmpty(),
                artist = display.artist.orEmpty(),
                albumTitle = display.albumTitle.orEmpty(),
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
                Toast.makeText(this@PlaybackActivity, R.string.playback_locate_failed, Toast.LENGTH_SHORT).show()
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
        val targetLabel = target.sourceConfig.rootUrl().ifBlank { getString(R.string.playback_target_smb_connection) }
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = getString(R.string.playback_locate),
                title = getString(R.string.playback_switch_smb_connection),
                message = getString(R.string.playback_switch_smb_message, targetLabel),
                confirmAction = ModalAction(getString(R.string.playback_switch_and_locate), isPrimary = true) {
                    openBrowserAtPlaybackDirectory(target)
                },
                cancelAction = ModalAction(getString(R.string.common_cancel)),
            )
        )
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

    companion object {
        const val PLAYER_PROGRESS_RENDER_HOLD_MS = 350L
        const val PLAYBACK_TOAST_DURATION_MS = 2_000L
        private const val FULLSCREEN_HUD_AUTO_HIDE_MS = 3_500L
        const val EXTRA_FAVORITES_PLAYLIST_ID = "extra_favorites_playlist_id"

        /**
         * 测试用途：根据播放列表列表和已包含集合构建选择项。
         * 不依赖 Activity 实例，可在纯 JUnit 中调用。
         */
        @VisibleForTesting
        internal fun buildFavoritePlaylistChoicesForTest(
            playlists: List<FavoritePlaylist>,
            containedPlaylists: Set<String>,
        ): List<FavoritePlaylistChoice> {
            return playlists.map { playlist ->
                val contains = playlist.id in containedPlaylists
                FavoritePlaylistChoice(
                    playlistId = playlist.id,
                    label = if (contains) {
                        "${playlist.name} (already in playlist)"
                    } else {
                        playlist.name
                    },
                    disabled = contains,
                )
            } + FavoritePlaylistChoice(
                playlistId = null,
                label = "+ New Playlist",
                disabled = false,
                createNew = true,
            )
        }
    }
}
