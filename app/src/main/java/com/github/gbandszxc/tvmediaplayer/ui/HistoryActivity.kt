package com.github.gbandszxc.tvmediaplayer.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.history.PlayHistoryRepository
import com.github.gbandszxc.tvmediaplayer.history.PlayHistoryTrack
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {

    private val repository by lazy { PlayHistoryRepository(applicationContext) }
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var currentPage = 0
    private var currentQuery = ""
    private var currentTracks: List<PlayHistoryTrack> = emptyList()
    private var totalCount = 0

    private lateinit var btnBack: Button
    private lateinit var searchInput: EditText
    private lateinit var btnSearch: Button
    private lateinit var tvPage: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var tvStatus: TextView
    private lateinit var historyContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        UiSettingsApplier.applyAll(this)
        bindViews()
        bindActions()
        loadPage(0)
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyAll(this)
        loadPage(currentPage)
    }

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btn_history_back)
        searchInput = findViewById(R.id.et_history_search)
        btnSearch = findViewById(R.id.btn_history_search)
        tvPage = findViewById(R.id.tv_history_page)
        btnPrev = findViewById(R.id.btn_history_prev)
        btnNext = findViewById(R.id.btn_history_next)
        tvStatus = findViewById(R.id.tv_history_status)
        historyContainer = findViewById(R.id.container_history)
    }

    private fun bindActions() {
        btnBack.setOnClickListener { finish() }
        btnSearch.setOnClickListener { submitSearch() }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }
        btnPrev.setOnClickListener { loadPage(currentPage - 1) }
        btnNext.setOnClickListener { loadPage(currentPage + 1) }
        UiMotion.applyPressFeedback(btnBack, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnSearch, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnPrev, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnNext, R.color.ui_press_overlay_light)
    }

    private fun submitSearch() {
        currentQuery = searchInput.text?.toString().orEmpty().trim()
        loadPage(0)
    }

    private fun loadPage(page: Int) {
        val result = repository.query(page.coerceAtLeast(0), currentQuery)
        currentPage = result.page
        currentTracks = result.items
        totalCount = result.totalCount
        render()
    }

    private fun render() {
        historyContainer.removeAllViews()
        val totalPages = maxOf(1, (totalCount + PlayHistoryRepository.PAGE_SIZE - 1) / PlayHistoryRepository.PAGE_SIZE)
        tvPage.text = getString(R.string.history_page_status, currentPage + 1, totalPages, totalCount)
        btnPrev.isEnabled = currentPage > 0
        btnNext.isEnabled = currentPage + 1 < totalPages
        btnPrev.alpha = if (btnPrev.isEnabled) 1f else 0.55f
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.55f

        if (currentTracks.isEmpty()) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = if (currentQuery.isBlank()) {
                getString(R.string.history_empty)
            } else {
                getString(R.string.history_empty_search)
            }
            historyContainer.post {
                if (currentQuery.isBlank()) btnBack.requestFocus() else searchInput.requestFocus()
            }
            return
        }

        tvStatus.visibility = View.GONE
        currentTracks.forEachIndexed { index, track ->
            historyContainer.addView(createHistoryRow(track, index))
        }
        historyContainer.post {
            historyContainer.getChildAt(0)?.requestFocus() ?: btnBack.requestFocus()
        }
    }

    private fun createHistoryRow(track: PlayHistoryTrack, index: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = ContextCompat.getDrawable(this@HistoryActivity, R.drawable.bg_file_item)
            val paddingH = dimenPx(R.dimen.ui_space_3xl)
            val paddingV = dimenPx(R.dimen.ui_space_lg)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            setOnClickListener { playFrom(index) }
            layoutParams = LinearLayout.LayoutParams(matchParent(), wrapContent()).apply {
                bottomMargin = dimenPx(R.dimen.ui_space_md)
            }
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, wrapContent(), 1f)
        }
        textColumn.addView(createTitle(track.title))
        textColumn.addView(createSubtitle(track))
        row.addView(textColumn)
        row.addView(createPlayedAt(track.playedAt))
        UiMotion.applyPressFeedback(row, R.color.ui_press_overlay_light)
        return row
    }

    private fun createTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.ui_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ui_text_title))
            typeface = ResourcesCompat.getFont(this@HistoryActivity, R.font.misans_medium)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun createSubtitle(track: PlayHistoryTrack): TextView =
        TextView(this).apply {
            text = listOfNotNull(track.artist, track.album)
                .filter { it.isNotBlank() }
                .joinToString(" / ")
                .ifBlank { track.mediaId }
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.ui_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ui_text_body))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun createPlayedAt(playedAt: Long): TextView =
        TextView(this).apply {
            text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(playedAt))
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.ui_text_muted))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ui_text_body))
            gravity = Gravity.END
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.ui_browser_time_column_width), wrapContent()).apply {
                marginStart = dimenPx(R.dimen.ui_space_3xl)
            }
        }

    private fun playFrom(index: Int) {
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(this, R.string.history_player_init_failed, Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }
        val selected = currentTracks.getOrNull(index) ?: return
        if (selected.streamUri.isBlank()) {
            Toast.makeText(this, getString(R.string.history_play_failed, getString(R.string.history_empty_play_uri)), Toast.LENGTH_SHORT).show()
            return
        }

        val queue = currentTracks
            .filter { it.streamUri.isNotBlank() && sameSource(it.sourceConfig, selected.sourceConfig) }
        val startIndex = queue.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        PlaybackConfigStore.update(selected.sourceConfig ?: SmbConfig.Empty)

        lifecycleScope.launch {
            runCatching {
                controller.repeatMode = Player.REPEAT_MODE_OFF
                controller.setShuffleModeEnabled(false)
                controller.setMediaItems(queue.map(::toMediaItem), startIndex, 0L)
                controller.prepare()
                controller.play()
            }.onSuccess {
                startActivity(Intent(this@HistoryActivity, PlaybackActivity::class.java))
            }.onFailure { ex ->
                Toast.makeText(
                    this@HistoryActivity,
                    getString(R.string.history_play_failed, ex.message ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun sameSource(left: SmbConfig?, right: SmbConfig?): Boolean =
        (left ?: SmbConfig.Empty) == (right ?: SmbConfig.Empty)

    private fun toMediaItem(track: PlayHistoryTrack): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.mediaId)
            .setUri(track.streamUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .build()
            )
            .build()

    private fun ensureController() {
        if (mediaController != null || controllerFuture != null) return
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController = it }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(this, R.string.history_player_connect_failed, Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun releaseController() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    private fun dimenPx(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun matchParent(): Int = ViewGroup.LayoutParams.MATCH_PARENT

    private fun wrapContent(): Int = ViewGroup.LayoutParams.WRAP_CONTENT
}
