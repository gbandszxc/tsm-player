package com.github.gbandszxc.tvmediaplayer.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
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
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackArtworkCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.github.gbandszxc.tvmediaplayer.playback.SmbAudioMetadataProbe
import com.github.gbandszxc.tvmediaplayer.playback.SmbContextFactory
import com.github.gbandszxc.tvmediaplayer.ui.modal.ConfirmModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormFieldSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalAction
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinator
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalFormValidators
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoritesActivity : BaseActivity() {

    private val repository by lazy { FavoritesRepository(applicationContext) }
    private val modalCoordinator by lazy { TsmModalCoordinator(this) }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var currentPlaylist: FavoritePlaylist? = null
    private var currentTracks: List<FavoriteTrack> = emptyList()
    private var activeFavoritePlaybackMediaIds: Set<String> = emptySet()
    private var invalidDialogTrackId: String? = null

    private lateinit var tvTitle: TextView
    private lateinit var btnBack: Button
    private lateinit var btnDeletePlaylist: Button
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
        btnDeletePlaylist = findViewById(R.id.btn_favorites_delete_playlist)
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
        btnDeletePlaylist.setOnClickListener {
            currentPlaylist?.let(::confirmDeletePlaylist)
        }
        UiMotion.applyPressFeedback(btnBack, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnDeletePlaylist, R.color.ui_press_overlay_dark)
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
        updateDeletePlaylistButton(null)
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
        tvTitle.text = playlistDisplayName(playlist)
        updateDeletePlaylistButton(playlist)
        scrollPlaylists.visibility = View.GONE
        scrollTracks.visibility = View.VISIBLE
        renderTracks(playlist, currentTracks)
    }

    private fun updateDeletePlaylistButton(playlist: FavoritePlaylist?) {
        val showDelete = playlist != null && !playlist.isDefault
        btnDeletePlaylist.visibility = if (showDelete) View.VISIBLE else View.GONE
        btnDeletePlaylist.isEnabled = showDelete
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
            (tracksContainer.getChildAt(0)?.tag as? View)?.requestFocus() ?: btnBack.requestFocus()
        }
    }

    private fun createAddPlaylistTile(): View {
        val tile = createTileShell()
        tile.contentDescription = getString(R.string.favorites_add_playlist)
        tile.setOnClickListener { showCreatePlaylistDialog() }
        UiMotion.applyPressFeedback(tile, R.color.ui_press_overlay_light)

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
        tile.contentDescription = playlistDisplayName(playlist)
        tile.setOnClickListener { showTracks(playlist) }
        UiMotion.applyPressFeedback(tile, R.color.ui_press_overlay_light)

        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, R.color.ui_bg_artwork))
            loadPlaylistArtworkOrDefault(playlist)
        }
        tile.addView(cover, LinearLayout.LayoutParams(matchParent(), 0, 1f))
        tile.addView(createTileTitle(playlistDisplayName(playlist)))
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

    private fun playlistDisplayName(playlist: FavoritePlaylist): String =
        if (playlist.isDefault) getString(R.string.favorites_default_playlist) else playlist.name

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
            isFocusable = false
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

        val playButton = createPlayButton(playlist, index)
        row.addView(textColumn)
        row.addView(playButton)
        row.addView(createDeleteButton(playlist, track))
        UiMotion.applyPressFeedback(row, R.color.ui_press_overlay_light)
        row.tag = playButton
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

    private fun createPlayButton(playlist: FavoritePlaylist, index: Int): Button =
        createTrackActionButton(
            contentDescription = getString(R.string.favorites_play),
            backgroundResId = R.drawable.bg_button_green,
            iconResId = R.drawable.ic_play,
            marginStartPx = dimenPx(R.dimen.ui_space_3xl),
        ) {
            playPlaylistFrom(playlist, index)
        }

    private fun createDeleteButton(playlist: FavoritePlaylist, track: FavoriteTrack): Button =
        createTrackActionButton(
            contentDescription = getString(R.string.favorites_remove),
            backgroundResId = R.drawable.bg_button_red,
            iconResId = R.drawable.ic_delete,
            marginStartPx = dimenPx(R.dimen.ui_space_lg),
        ) {
            confirmRemoveTrack(playlist, track)
        }

    private fun createTrackActionButton(
        contentDescription: String,
        backgroundResId: Int,
        iconResId: Int,
        marginStartPx: Int,
        onClick: () -> Unit,
    ): Button {
        val size = dimenPx(R.dimen.ui_playback_mode_button_collapsed_width)
        return Button(this).apply {
            text = ""
            this.contentDescription = contentDescription
            background = ContextCompat.getDrawable(this@FavoritesActivity, backgroundResId)
            minWidth = size
            minimumWidth = size
            minHeight = size
            minimumHeight = size
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setCompoundDrawables(null, null, null, null)
            setOnClickListener { onClick() }
            UiMotion.applyPressFeedback(this, R.color.ui_press_overlay_dark)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = marginStartPx
            }
            post { renderCenteredActionIcon(this, iconResId) }
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
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = getString(R.string.favorites_title),
                title = getString(R.string.favorites_invalid_track_title),
                message = getString(R.string.favorites_invalid_track_message),
                confirmAction = ModalAction(
                    getString(R.string.favorites_invalid_track_remove),
                    isDanger = true,
                    onClick = {
                        repository.removeTrack(playlist.id, track)
                        invalidDialogTrackId = null
                        if (currentPlaylist?.id == playlist.id) {
                            showTracks(playlist)
                        }
                    },
                ),
                cancelAction = ModalAction(
                    getString(R.string.favorites_invalid_track_keep),
                    onClick = { invalidDialogTrackId = null },
                ),
            )
        )
    }

    private fun confirmRemoveTrack(playlist: FavoritePlaylist, track: FavoriteTrack) {
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = getString(R.string.favorites_title),
                title = getString(R.string.favorites_remove_track_confirm_title),
                message = getString(R.string.favorites_remove_track_confirm_message),
                confirmAction = ModalAction(
                    getString(R.string.favorites_remove),
                    isDanger = true,
                    onClick = {
                        repository.removeTrack(playlist.id, track)
                        Toast.makeText(this@FavoritesActivity, R.string.favorites_removed_track, Toast.LENGTH_SHORT).show()
                        if (currentPlaylist?.id == playlist.id) {
                            showTracks(playlist)
                        }
                    },
                ),
                cancelAction = ModalAction(getString(R.string.favorites_dialog_cancel)),
            )
        )
    }

    private fun confirmDeletePlaylist(playlist: FavoritePlaylist) {
        if (playlist.isDefault) return
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = getString(R.string.favorites_title),
                title = getString(R.string.favorites_delete_playlist_confirm_title),
                message = getString(R.string.favorites_delete_playlist_confirm_message),
                confirmAction = ModalAction(
                    getString(R.string.modal_default_delete),
                    isDanger = true,
                    onClick = {
                        if (repository.deletePlaylist(playlist.id)) {
                            Toast.makeText(this@FavoritesActivity, R.string.favorites_deleted_playlist, Toast.LENGTH_SHORT).show()
                            showPlaylistGrid()
                        }
                    },
                ),
                cancelAction = ModalAction(getString(R.string.favorites_dialog_cancel)),
            )
        )
    }

    private fun showCreatePlaylistDialog() {
        val existing = repository.getPlaylists().map { it.name }.toSet()
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
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                    )
                ),
                primaryAction = ModalAction(getString(R.string.favorites_create_playlist), isPrimary = true),
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
            if (repository.createPlaylist(name.trim()) == null) {
                modalCoordinator.updateFieldError(dialog, "playlist_name", getString(R.string.favorites_playlist_name_duplicate))
                return@bindFormPrimaryAction false
            }
            showPlaylistGrid()
            true
        }
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

    private fun ImageView.loadPlaylistArtworkOrDefault(playlist: FavoritePlaylist) {
        val requestKey = "favorites-cover:${playlist.id}:${playlist.updatedAt}"
        tag = requestKey
        setImageResource(R.drawable.default_cover)

        val tracks = repository.getTracks(playlist.id)
        val coverTrack = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() } ?: tracks.firstOrNull()
        val artworkCacheKey = coverTrack?.let(::favoriteArtworkCacheKey)
        artworkCacheKey?.let { cacheKey ->
            PlaybackArtworkCache.get(cacheKey)?.let { bitmap ->
                setImageBitmap(bitmap)
                return
            }
        }

        lifecycleScope.launch {
            val diskBitmap = withContext(Dispatchers.IO) {
                artworkCacheKey?.let { PlaybackArtworkCache.loadFromDisk(applicationContext, it) }
            }
            if (tag != requestKey) return@launch
            if (diskBitmap != null) {
                setImageBitmap(diskBitmap)
                artworkCacheKey?.let { PlaybackArtworkCache.put(it, diskBitmap) }
                return@launch
            }

            val resolvedBitmap = withContext(Dispatchers.IO) {
                loadPlaylistArtworkBitmap(playlist, coverTrack)
            }
            if (tag != requestKey) return@launch
            if (resolvedBitmap != null) {
                setImageBitmap(resolvedBitmap)
                artworkCacheKey?.let { cacheKey ->
                    PlaybackArtworkCache.put(cacheKey, resolvedBitmap)
                    PlaybackArtworkCache.saveAsync(applicationContext, cacheKey, resolvedBitmap)
                }
            } else {
                setImageResource(R.drawable.default_cover)
            }
        }
    }

    private suspend fun loadPlaylistArtworkBitmap(
        playlist: FavoritePlaylist,
        coverTrack: FavoriteTrack?,
    ): Bitmap? {
        playlist.coverArtworkUri
            ?.takeIf { it.isNotBlank() }
            ?.let { artworkUri ->
                loadBitmapFromArtworkUri(artworkUri, coverTrack?.sourceConfig)?.let { return it }
            }
        coverTrack ?: return null
        return resolveFavoriteTrackArtwork(coverTrack)
    }

    private suspend fun resolveFavoriteTrackArtwork(track: FavoriteTrack): Bitmap? {
        track.artworkUri
            ?.takeIf { it.isNotBlank() }
            ?.let { artworkUri ->
                loadBitmapFromArtworkUri(artworkUri, track.sourceConfig)?.let { return it }
            }
        val config = track.sourceConfig ?: return null
        val streamUri = track.streamUri.takeIf { it.startsWith("smb://", ignoreCase = true) } ?: return null
        loadEmbeddedArtwork(streamUri, config)?.let { return it }
        return loadSiblingArtwork(streamUri, config)
    }

    private fun loadBitmapFromArtworkUri(artworkUri: String, sourceConfig: SmbConfig?): Bitmap? {
        if (artworkUri.startsWith("smb://", ignoreCase = true)) {
            val config = sourceConfig ?: return null
            return loadSmbBitmap(artworkUri, config)
        }
        return runCatching {
            contentResolver.openInputStream(Uri.parse(artworkUri))?.use { stream ->
                PlaybackArtworkCache.decodeSampled(stream)
            }
        }.getOrNull()
    }

    private fun loadSiblingArtwork(mediaSmbUrl: String, config: SmbConfig): Bitmap? = runCatching {
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

    private fun loadSmbBitmap(smbUrl: String, config: SmbConfig): Bitmap? = runCatching {
        val smbFile = SmbFile(smbUrl, SmbContextFactory.build(config))
        if (!smbFile.exists() || smbFile.isDirectory) return@runCatching null
        SmbFileInputStream(smbFile).use(PlaybackArtworkCache::decodeSampled)
    }.getOrNull()

    private suspend fun loadEmbeddedArtwork(mediaSmbUrl: String, config: SmbConfig): Bitmap? = runCatching {
        val artwork = SmbAudioMetadataProbe.probe(config, mediaSmbUrl)?.artworkData ?: return@runCatching null
        PlaybackArtworkCache.decodeSampled(artwork)
    }.getOrNull()

    private fun favoriteArtworkCacheKey(track: FavoriteTrack): String {
        return track.streamUri.ifBlank { track.mediaId }
    }

    private fun renderCenteredActionIcon(button: Button, iconResId: Int) {
        val icon = ContextCompat.getDrawable(this, iconResId)?.let { source ->
            DrawableCompat.wrap(source).mutate().apply {
                DrawableCompat.setTint(this, ContextCompat.getColor(this@FavoritesActivity, R.color.ui_text_on_accent))
            }
        } ?: return
        button.overlay.clear()
        val iconWidth = icon.intrinsicWidth.coerceAtLeast(1)
        val iconHeight = icon.intrinsicHeight.coerceAtLeast(1)
        val left = (button.width - iconWidth) / 2
        val top = (button.height - iconHeight) / 2
        icon.setBounds(left, top, left + iconWidth, top + iconHeight)
        button.overlay.add(icon)
    }

    private fun dimenPx(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun matchParent(): Int = ViewGroup.LayoutParams.MATCH_PARENT

    private fun wrapContent(): Int = ViewGroup.LayoutParams.WRAP_CONTENT

    companion object {
        /**
         * 测试用途：根据播放列表列表和已包含集合构建选择项。
         * 不依赖 Activity 实例，可在纯 JUnit 中调用。
         */
        @VisibleForTesting
        internal fun buildFavoritesPlaylistChoicesForTest(
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
