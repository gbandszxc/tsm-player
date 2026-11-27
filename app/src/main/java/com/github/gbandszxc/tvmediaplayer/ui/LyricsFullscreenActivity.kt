package com.github.gbandszxc.tvmediaplayer.ui

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcTimeline
import com.github.gbandszxc.tvmediaplayer.lyrics.SmbLyricsRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackArtworkCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLyricsCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsFullscreenActivity : BaseActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var refreshJob: Job? = null
    private var lyricsScrollAnimator: ObjectAnimator? = null
    private var ambientBackdrop: AmbientBackdrop.Controller? = null
    private val lyricsRepository = SmbLyricsRepository()
    private val trackInfoStore = PlaybackTrackInfoStore.shared

    private var currentTimeline: LrcTimeline? = null
    private var currentLyricKey: String? = null
    private var currentArtworkKey: String? = null
    private var renderedLyricsTimeline: LrcTimeline? = null
    private var renderedLyricsLineIndex: Int = -1
    private var renderedPlaying: Boolean? = null
    private var renderedTimeText: String? = null
    private var lastScrollViewportHeight: Int = 0

    private lateinit var layoutArtworkStage: View
    private lateinit var ivArtwork: ImageView
    private lateinit var viewArtworkRim: View
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var pbProgress: ProgressBar
    private lateinit var tvTime: TextView
    private lateinit var btnPlayPause: Button
    private lateinit var btnClose: Button
    private lateinit var scrollLyrics: ScrollView
    private lateinit var tvLyrics: TextView

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                maybeLoadLyrics(player)
                maybeLoadArtwork(player)
                updateAmbientFromPlayback()
                ambientBackdrop?.setBreathing(player.isPlaying)
                renderTrackMetaAndProgress(player)
                renderLyrics(player.currentPosition)
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateAmbientFromPlayback()
            mediaController?.let(::renderTrackMetaAndProgress)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics_fullscreen)
        ambientBackdrop = AmbientBackdrop.bind(
            findViewById(R.id.iv_ambient),
            findViewById(R.id.view_ambient_scrim),
        )
        layoutArtworkStage = findViewById(R.id.layout_fullscreen_artwork_stage)
        ivArtwork = findViewById(R.id.iv_fullscreen_artwork)
        viewArtworkRim = findViewById(R.id.view_fullscreen_artwork_rim)
        tvTitle = findViewById(R.id.tv_fullscreen_title)
        tvArtist = findViewById(R.id.tv_fullscreen_artist)
        tvAlbum = findViewById(R.id.tv_fullscreen_album)
        pbProgress = findViewById(R.id.pb_fullscreen_progress)
        tvTime = findViewById(R.id.tv_fullscreen_time)
        btnPlayPause = findViewById(R.id.btn_fullscreen_play_pause)
        btnClose = findViewById(R.id.btn_close_fullscreen_lyrics)
        scrollLyrics = findViewById(R.id.scroll_lyrics)
        tvLyrics = findViewById(R.id.tv_fullscreen_lyrics)

        LiquidGlassSurface.applyRoundedClip(layoutArtworkStage, R.dimen.ui_radius_panel)
        layoutArtworkStage.elevation = 18f * resources.displayMetrics.density
        LiquidGlassSurface.applyGlassFrameOverlay(viewArtworkRim, R.dimen.ui_radius_panel)

        btnPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            if (controller.isPlaying) controller.pause() else controller.play()
            renderTrackMetaAndProgress(controller)
        }
        btnClose.setOnClickListener { finish() }
        UiMotion.applyPressFeedback(btnPlayPause, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnClose, R.color.ui_press_overlay_light)
        renderPlayPauseButton(isPlaying = false)

        scrollLyrics.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val viewportHeight = bottom - top
            if (viewportHeight > 0 && viewportHeight != lastScrollViewportHeight) {
                lastScrollViewportHeight = viewportHeight
                tvLyrics.setPadding(
                    tvLyrics.paddingLeft,
                    (viewportHeight * CENTER_BIAS_RATIO).toInt(),
                    tvLyrics.paddingRight,
                    (viewportHeight * BOTTOM_PADDING_RATIO).toInt(),
                )
            }
        }

        applyUiSettings()
        btnPlayPause.post { btnPlayPause.requestFocus() }
    }

    override fun onDestroy() {
        ambientBackdrop?.dispose()
        ambientBackdrop = null
        super.onDestroy()
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
        ambientBackdrop?.setBreathing(false)
        refreshJob?.cancel()
        refreshJob = null
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            mediaController?.let { controller ->
                if (controller.isPlaying) controller.pause() else controller.play()
                renderTrackMetaAndProgress(controller)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateAmbientFromPlayback() {
        ambientBackdrop?.updateFromMediaItem(mediaController?.currentMediaItem)
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
                if (controllerFuture !== future) return@addListener
                mediaController = controller
                controller.addListener(playerListener)
                maybeLoadLyrics(controller)
                maybeLoadArtwork(controller)
                updateAmbientFromPlayback()
                ambientBackdrop?.setBreathing(controller.isPlaying)
                renderTrackMetaAndProgress(controller)
                renderLyrics(controller.currentPosition)
                startTicker()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun applyUiSettings() {
        UiSettingsApplier.applyAll(this)
        tvLyrics.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            UiSettingsStore.fullscreenLyricsFontSp(this).toFloat()
        )
        val spacing = UiSettingsStore.fullscreenLyricsLineSpacing(this).coerceAtLeast(1.0f)
        tvLyrics.setLineSpacing(0f, spacing)
    }

    private fun startTicker() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    renderTrackMetaAndProgress(controller)
                    renderLyrics(controller.currentPosition)
                }
                delay(250)
            }
        }
    }

    private fun renderTrackMetaAndProgress(player: Player) {
        val mediaItem = player.currentMediaItem
        val display = PlaybackTrackInfoResolver.resolve(
            store = trackInfoStore,
            key = mediaItem?.let(::mediaCacheKey),
            mediaItemTitle = mediaItem?.mediaMetadata?.title?.toString(),
            mediaItemArtist = mediaItem?.mediaMetadata?.artist?.toString(),
            mediaItemAlbumTitle = mediaItem?.mediaMetadata?.albumTitle?.toString(),
            playerTitle = player.mediaMetadata.title?.toString(),
            playerArtist = player.mediaMetadata.artist?.toString(),
            playerAlbumTitle = player.mediaMetadata.albumTitle?.toString(),
            emptyTitleFallback = getString(R.string.playback_empty_title),
        )
        tvTitle.text = display.title
        val artist = display.artist
        if (artist.isNullOrBlank() || artist == "-") {
            tvArtist.visibility = View.GONE
        } else {
            tvArtist.text = artist
            tvArtist.visibility = View.VISIBLE
        }
        val album = display.albumTitle
        if (album.isNullOrBlank() || album == "-") {
            tvAlbum.visibility = View.GONE
        } else {
            tvAlbum.text = album
            tvAlbum.visibility = View.VISIBLE
        }

        val duration = player.duration
        val safeDuration = if (duration <= 0L || duration == C.TIME_UNSET) 0L else duration
        val position = player.currentPosition.coerceAtLeast(0L)
        val timeText = "${formatMs(position)} / ${formatMs(safeDuration)}"
        if (timeText != renderedTimeText) {
            renderedTimeText = timeText
            tvTime.text = timeText
        }
        pbProgress.progress = if (safeDuration <= 0L) {
            0
        } else {
            ((position * pbProgress.max.toLong()) / safeDuration)
                .toInt()
                .coerceIn(0, pbProgress.max)
        }
        renderPlayPauseButton(player.isPlaying)
    }

    private fun renderPlayPauseButton(isPlaying: Boolean) {
        if (renderedPlaying == isPlaying) return
        renderedPlaying = isPlaying
        btnPlayPause.text = getString(if (isPlaying) R.string.common_pause else R.string.common_play)
        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val icon = ContextCompat.getDrawable(this, iconRes)?.mutate()
        if (icon != null) {
            DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.ui_text_on_accent))
            icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
            btnPlayPause.setCompoundDrawables(icon, null, null, null)
        }
    }

    private fun maybeLoadArtwork(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val key = mediaCacheKey(mediaItem)
        if (key == currentArtworkKey) return
        val hadPrevious = currentArtworkKey != null
        currentArtworkKey = key

        PlaybackArtworkCache.get(key)?.let { bitmap ->
            setArtworkBitmap(bitmap, animate = hadPrevious)
            return
        }
        ivArtwork.setImageResource(R.drawable.default_cover)

        lifecycleScope.launch {
            val diskHit = withContext(Dispatchers.IO) {
                PlaybackArtworkCache.loadFromDisk(applicationContext, key)
            }
            if (currentArtworkKey != key) return@launch
            if (diskHit != null) {
                PlaybackArtworkCache.put(key, diskHit)
                setArtworkBitmap(diskHit, animate = true)
            }
        }
    }

    private fun setArtworkBitmap(bitmap: Bitmap, animate: Boolean) {
        ivArtwork.setImageBitmap(bitmap)
        if (!animate || layoutArtworkStage.width <= 0) return
        layoutArtworkStage.animate().cancel()
        layoutArtworkStage.alpha = 0.45f
        layoutArtworkStage.scaleX = 0.96f
        layoutArtworkStage.scaleY = 0.96f
        layoutArtworkStage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(UiMotion.springGlideInterpolator())
            .withEndAction {
                (viewArtworkRim.background as? LiquidGlassDrawable)?.triggerSheen()
            }
            .start()
    }

    private fun maybeLoadLyrics(player: Player) {
        val mediaItem = player.currentMediaItem ?: return

        // key 与 PlaybackActivity 保持一致：uri 优先，否则 mediaId
        val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val key = if (uri.isNotBlank()) uri else mediaItem.mediaId
        if (key == currentLyricKey) return
        currentLyricKey = key
        currentTimeline = null

        // 内存缓存命中
        PlaybackLyricsCache.get(key)?.let {
            // 文件删除放后台，避免主线程磁盘 IO
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

        val fullPath = mediaItem.mediaId
        val entry = SmbEntry(
            name = fullPath.substringAfterLast('/'),
            fullPath = fullPath,
            isDirectory = false,
            streamUri = uri
        )

        lifecycleScope.launch {
            // 磁盘缓存命中
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

            // 从 SMB 加载
            val config = PlaybackConfigStore.current()
            val timeline = withContext(Dispatchers.IO) {
                runCatching { lyricsRepository.loadDetailed(config, entry) }.getOrNull()
            }
            if (currentLyricKey != key) return@launch
            val loadedTimeline = timeline?.timeline
            currentTimeline = loadedTimeline
            if (loadedTimeline == null || loadedTimeline.lines.isEmpty()) {
                if (timeline?.status == SmbLyricsRepository.Status.MISS) {
                    PlaybackLyricsCache.markMissAsync(applicationContext, key)
                }
                showLyricsStatus(getString(R.string.playback_lyrics_empty))
                return@launch
            }
            PlaybackLyricsCache.put(key, loadedTimeline)
            PlaybackLyricsCache.clearMiss(applicationContext, key)
            PlaybackLyricsCache.saveAsync(applicationContext, key, loadedTimeline)
            renderLyrics(player.currentPosition)
        }
    }

    private fun showLyricsStatus(text: String) {
        tvLyrics.text = text
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
                builder.setSpan(RelativeSizeSpan(ACTIVE_LINE_SCALE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        tvLyrics.text = builder

        if (renderPlan.shouldResetScrollToTop) {
            resetLyricsScrollToTop()
        } else if (highlightStart >= 0) {
            scrollLyrics.post {
                val layout = tvLyrics.layout ?: return@post
                val lineIndex = layout.getLineForOffset(highlightStart)
                val lineTopInParent = tvLyrics.paddingTop + layout.getLineTop(lineIndex)
                val targetY = (lineTopInParent - (scrollLyrics.height * CENTER_BIAS_RATIO).toInt()).coerceAtLeast(0)
                lyricsScrollAnimator?.cancel()
                lyricsScrollAnimator = ObjectAnimator.ofInt(scrollLyrics, "scrollY", targetY).apply {
                    duration = LYRICS_SCROLL_DURATION_MS
                    interpolator = UiMotion.springGlideInterpolator()
                    start()
                }
            }
        }
    }

    private fun mediaCacheKey(mediaItem: MediaItem): String {
        val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        return if (uri.isNotBlank()) uri else mediaItem.mediaId
    }

    private fun formatMs(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    companion object {
        private const val CENTER_BIAS_RATIO = 0.34f
        private const val BOTTOM_PADDING_RATIO = 0.60f
        private const val ACTIVE_LINE_SCALE = 1.08f
        private const val LYRICS_SCROLL_DURATION_MS = 340L
    }
}
