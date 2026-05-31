package com.github.gbandszxc.tvmediaplayer.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteInvalidTrackPolicy
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritePlaybackErrorTarget
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritePlaylist
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrack
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackIdentity
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackMediaItems
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackQueueFilter
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class FavoritesActivity : BaseActivity() {

    private val repository by lazy { FavoritesRepository(applicationContext) }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var currentPlaylist: FavoritePlaylist? = null
    private var currentTracks: List<FavoriteTrack> = emptyList()
    private var activeFavoritePlaybackMediaIds: Set<String> = emptySet()
    private var invalidDialogTrackId: String? = null

    private lateinit var tvTitle: TextView
    private lateinit var btnBack: Button
    private lateinit var scrollPlaylists: ScrollView
    private lateinit var gridPlaylists: GridLayout
    private lateinit var scrollTracks: ScrollView
    private lateinit var tracksContainer: LinearLayout

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val controller = mediaController ?: return
            val playlist = currentPlaylist ?: return
            val track = FavoritePlaybackErrorTarget.resolve(
                currentTracks = currentTracks,
                currentMediaItemIndex = controller.currentMediaItemIndex,
                currentMediaId = controller.currentMediaItem?.mediaId,
                activeMediaIds = activeFavoritePlaybackMediaIds,
            ) ?: return
            if (
                !FavoriteInvalidTrackPolicy.isBlankStreamUriInvalid(track.streamUri) &&
                !FavoriteInvalidTrackPolicy.shouldOfferRemoval(error)
            ) {
                return
            }
            confirmRemoveInvalidTrack(playlist, track)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)
        UiSettingsApplier.applyAll(this)
        bindViews()
        bindActions()
        bindBackHandling()
        showPlaylistGrid()
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyAll(this)
    }

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tv_favorites_title)
        btnBack = findViewById(R.id.btn_favorites_back)
        scrollPlaylists = findViewById(R.id.scroll_playlists)
        gridPlaylists = findViewById(R.id.grid_playlists)
        scrollTracks = findViewById(R.id.scroll_tracks)
        tracksContainer = findViewById(R.id.container_tracks)
    }

    private fun bindActions() {
        btnBack.setOnClickListener {
            if (scrollTracks.visibility == View.VISIBLE) {
                showPlaylistGrid()
            } else {
                finish()
            }
        }
    }

    private fun bindBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (scrollTracks.visibility == View.VISIBLE) {
                        showPlaylistGrid()
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    private fun showPlaylistGrid() {
        currentPlaylist = null
        currentTracks = emptyList()
        activeFavoritePlaybackMediaIds = emptySet()
        tvTitle.text = getString(R.string.favorites_title)
        scrollPlaylists.visibility = View.VISIBLE
        scrollTracks.visibility = View.GONE
        gridPlaylists.removeAllViews()

        gridPlaylists.addView(createAddPlaylistTile())
        repository.getPlaylists().forEach { playlist ->
            gridPlaylists.addView(createPlaylistTile(playlist))
        }
        gridPlaylists.post {
            gridPlaylists.getChildAt(0)?.requestFocus() ?: btnBack.requestFocus()
        }
    }

    private fun showTracks(playlist: FavoritePlaylist) {
        currentPlaylist = playlist
        currentTracks = repository.getTracks(playlist.id)
        activeFavoritePlaybackMediaIds = emptySet()
        tvTitle.text = playlist.name
        scrollPlaylists.visibility = View.GONE
        scrollTracks.visibility = View.VISIBLE
        renderTracks(playlist, currentTracks)
    }

    private fun renderTracks(playlist: FavoritePlaylist, tracks: List<FavoriteTrack>) {
        tracksContainer.removeAllViews()
        if (tracks.isEmpty()) {
            tracksContainer.addView(createEmptyTrackText())
            FavoritesEmptyTrackFocus.requestFallbackFocus(tracks, btnBack)
            return
        }

        tracks.forEachIndexed { index, track ->
            tracksContainer.addView(createTrackRow(playlist, track, index))
        }
        tracksContainer.post {
            tracksContainer.getChildAt(0)?.requestFocus() ?: btnBack.requestFocus()
        }
    }

    private fun createAddPlaylistTile(): View {
        val tile = createTileShell()
        tile.contentDescription = getString(R.string.favorites_add_playlist)
        tile.setOnClickListener { showCreatePlaylistDialog() }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_add_playlist)
            setColorFilter(ContextCompat.getColor(this@FavoritesActivity, R.color.ui_accent_blue_stroke_soft))
            adjustViewBounds = true
        }
        tile.addView(icon, LinearLayout.LayoutParams(matchParent(), 0, 1f))
        tile.addView(createTileTitle(getString(R.string.favorites_add_playlist)))
        return tile
    }

    private fun createPlaylistTile(playlist: FavoritePlaylist): View {
        val tile = createTileShell()
        tile.contentDescription = playlist.name
        tile.setOnClickListener { showTracks(playlist) }

        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, R.color.ui_bg_artwork))
            loadArtworkOrDefault(playlist.coverArtworkUri)
        }
        tile.addView(cover, LinearLayout.LayoutParams(matchParent(), 0, 1f))
        tile.addView(createTileTitle(playlist.name))
        tile.addView(createTileSubtitle(getString(R.string.favorites_track_count, playlist.trackCount)))
        return tile
    }

    private fun createTileShell(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = ContextCompat.getDrawable(this@FavoritesActivity, R.drawable.bg_playlist_tile)
            val padding = dimenPx(R.dimen.ui_space_lg)
            setPadding(padding, padding, padding, padding)
            layoutParams = GridLayout.LayoutParams().apply {
                width = dimenPx(R.dimen.ui_playlist_tile_width)
                height = dimenPx(R.dimen.ui_playlist_tile_height)
                setMargins(0, 0, dimenPx(R.dimen.ui_space_3xl), 0)
            }
        }

    private fun createTileTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.ui_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ui_text_body_large))
            typeface = ResourcesCompat.getFont(this@FavoritesActivity, R.font.misans_medium)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(matchParent(), wrapContent()).apply {
                topMargin = dimenPx(R.dimen.ui_space_md)
            }
        }

    private fun createTileSubtitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.ui_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ui_text_caption))
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun createTrackRow(
        playlist: FavoritePlaylist,
        track: FavoriteTrack,
        index: Int,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = ContextCompat.getDrawable(this@FavoritesActivity, R.drawable.bg_file_item)
            val paddingH = dimenPx(R.dimen.ui_space_3xl)
            val paddingV = dimenPx(R.dimen.ui_space_lg)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            setOnClickListener { playPlaylistFrom(playlist, index) }
            layoutParams = LinearLayout.LayoutParams(matchParent(), wrapContent()).apply {
                bottomMargin = dimenPx(R.dimen.ui_space_md)
            }
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, wrapContent(), 1f)
        }
        textColumn.addView(createTrackTitle(track.title))
        textColumn.addView(createTrackSubtitle(track))

        row.addView(textColumn)
        row.addView(createDeleteButton(playlist, track))
        return row
    }

    private fun createTrackTitle(title: String): TextView =
        TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.ui_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ui_text_title))
            typeface = ResourcesCompat.getFont(this@FavoritesActivity, R.font.misans_medium)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun createTrackSubtitle(track: FavoriteTrack): TextView =
        TextView(this).apply {
            text = listOfNotNull(track.artist, track.album)
                .filter { it.isNotBlank() }
                .joinToString(" / ")
                .ifBlank { track.mediaId }
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.ui_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ui_text_body))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun createDeleteButton(playlist: FavoritePlaylist, track: FavoriteTrack): Button =
        Button(this).apply {
            text = ""
            contentDescription = getString(R.string.favorites_remove)
            background = ContextCompat.getDrawable(this@FavoritesActivity, R.drawable.bg_button_red)
            minWidth = dimenPx(R.dimen.ui_favorites_delete_button_width)
            minimumWidth = dimenPx(R.dimen.ui_favorites_delete_button_width)
            setPadding(0, 0, 0, 0)
            setCompoundDrawablesWithIntrinsicBounds(tintedDeleteIcon(), null, null, null)
            gravity = Gravity.CENTER
            setOnClickListener {
                repository.removeTrack(playlist.id, track)
                Toast.makeText(this@FavoritesActivity, R.string.favorites_removed_track, Toast.LENGTH_SHORT).show()
                showTracks(playlist)
            }
            layoutParams = LinearLayout.LayoutParams(
                dimenPx(R.dimen.ui_favorites_delete_button_width),
                matchParent(),
            ).apply {
                marginStart = dimenPx(R.dimen.ui_space_3xl)
            }
        }

    private fun createEmptyTrackText(): TextView =
        TextView(this).apply {
            text = getString(R.string.favorites_empty_playlist)
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, R.color.ui_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ui_text_title))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(matchParent(), wrapContent())
        }

    private fun playPlaylistFrom(playlist: FavoritePlaylist, startIndex: Int) {
        val tracks = repository.getTracks(playlist.id)
        if (tracks.isEmpty()) return

        val controller = mediaController
        if (controller == null) {
            Toast.makeText(this, R.string.favorites_player_init_failed, Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val selectedTrack = tracks[safeIndex]
        if (FavoriteInvalidTrackPolicy.isBlankStreamUriInvalid(selectedTrack.streamUri)) {
            confirmRemoveInvalidTrack(playlist, selectedTrack)
            return
        }

        val queue = FavoriteTrackQueueFilter.sameSourceQueue(tracks, safeIndex)
        PlaybackConfigStore.update(queue.sourceConfig ?: SmbConfig.Empty)
        currentPlaylist = playlist
        currentTracks = queue.tracks
        activeFavoritePlaybackMediaIds = queue.tracks.map { it.mediaId }.toSet()

        lifecycleScope.launch {
            runCatching {
                val mediaItems = FavoriteTrackMediaItems.fromTracks(queue.tracks)
                controller.repeatMode = Player.REPEAT_MODE_OFF
                controller.setShuffleModeEnabled(false)
                controller.setMediaItems(mediaItems, queue.startIndex, 0L)
                controller.prepare()
                controller.play()
            }.onSuccess {
                startActivity(
                    Intent(this@FavoritesActivity, PlaybackActivity::class.java).apply {
                        putExtra(PlaybackActivity.EXTRA_FAVORITES_PLAYLIST_ID, playlist.id)
                    }
                )
            }.onFailure { ex ->
                Toast.makeText(
                    this@FavoritesActivity,
                    getString(R.string.favorites_play_failed, ex.message ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun confirmRemoveInvalidTrack(playlist: FavoritePlaylist, track: FavoriteTrack) {
        val trackKey = FavoriteTrackIdentity.keyOf(track)
        if (invalidDialogTrackId == trackKey) return
        invalidDialogTrackId = trackKey
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.favorites_invalid_track_title))
            .setMessage(getString(R.string.favorites_invalid_track_message))
            .setPositiveButton(getString(R.string.favorites_invalid_track_remove)) { _, _ ->
                repository.removeTrack(playlist.id, track)
                if (currentPlaylist?.id == playlist.id) {
                    showTracks(playlist)
                }
            }
            .setNegativeButton(getString(R.string.favorites_invalid_track_keep), null)
            .setOnDismissListener { invalidDialogTrackId = null }
            .show()
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.favorites_playlist_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.favorites_new_playlist))
            .setView(input)
            .setPositiveButton(getString(R.string.favorites_create_playlist), null)
            .setNegativeButton(getString(R.string.favorites_dialog_cancel), null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = input.text?.toString().orEmpty().trim()
                        if (name.isBlank()) {
                            input.error = getString(R.string.favorites_playlist_name_empty)
                            return@setOnClickListener
                        }
                        if (repository.getPlaylists().any { it.name == name }) {
                            input.error = getString(R.string.favorites_playlist_name_duplicate)
                            return@setOnClickListener
                        }
                        if (repository.createPlaylist(name) == null) {
                            input.error = getString(R.string.favorites_playlist_name_duplicate)
                            return@setOnClickListener
                        }
                        dismiss()
                        showPlaylistGrid()
                    }
                }
            }
            .show()
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
                    }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(this, R.string.favorites_player_connect_failed, Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun releaseController() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    private fun ImageView.loadArtworkOrDefault(artworkUri: String?) {
        if (artworkUri.isNullOrBlank()) {
            setImageResource(R.drawable.default_cover)
            return
        }
        runCatching { setImageURI(Uri.parse(artworkUri)) }
            .onFailure { setImageResource(R.drawable.default_cover) }
        if (drawable == null) {
            setImageResource(R.drawable.default_cover)
        }
    }

    private fun tintedDeleteIcon() =
        ContextCompat.getDrawable(this, R.drawable.ic_delete)?.let { icon ->
            DrawableCompat.wrap(icon).mutate().apply {
                DrawableCompat.setTint(this, ContextCompat.getColor(this@FavoritesActivity, R.color.ui_text_on_accent))
            }
        }

    private fun dimenPx(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun matchParent(): Int = ViewGroup.LayoutParams.MATCH_PARENT

    private fun wrapContent(): Int = ViewGroup.LayoutParams.WRAP_CONTENT
}
