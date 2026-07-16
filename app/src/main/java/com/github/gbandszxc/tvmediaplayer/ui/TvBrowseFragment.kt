package com.github.gbandszxc.tvmediaplayer.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.data.repo.BrowseMode
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackStore
import com.github.gbandszxc.tvmediaplayer.playback.LocalMediaItemFactory
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackResumeBuilder
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLocationResolver
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackQueueBuilder
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.github.gbandszxc.tvmediaplayer.playback.SmbMediaItemFactory
import com.github.gbandszxc.tvmediaplayer.ui.modal.ActionModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ConfirmModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormFieldSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormFieldSpecType
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ListModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalAction
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalListRow
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinator
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalFormValidators
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class TvBrowseFragment : Fragment() {

    private val viewModel by viewModels<TvBrowserViewModel> {
        TvBrowserViewModel.factory(requireContext().applicationContext)
    }

    private val mediaItemFactory by lazy { SmbMediaItemFactory() }
    private val localMediaItemFactory by lazy { LocalMediaItemFactory() }
    private val modalCoordinator by lazy { TsmModalCoordinator(requireActivity()) }
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val fastLocateConfirmGuard = FastLocateConfirmGuard()
    private val browseListRenderGate = BrowseListRenderGate()

    private lateinit var panelConnection: View
    private lateinit var rootScroll: ScrollView
    private lateinit var btnBackTop: Button
    private lateinit var btnBrowseMode: Button
    private lateinit var btnSettings: Button
    private lateinit var tvConnection: TextView
    private lateinit var tvSavedCount: TextView
    private lateinit var btnManage: Button
    private lateinit var tvPath: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnSort: Button
    private lateinit var tvStatus: TextView
    private lateinit var panelFastLocate: View
    private lateinit var tvFastLocateHint: TextView
    private lateinit var tvFastLocateTarget: TextView
    private lateinit var fastLocateTrack: View
    private lateinit var fastLocateIndicator: View
    private lateinit var btnFavorites: Button
    private lateinit var btnHistory: Button
    private lateinit var btnPlayAll: Button
    private lateinit var btnPlayShuffle: Button
    private lateinit var btnNowPlaying: Button
    private lateinit var filesContainer: LinearLayout
    private var sortDropdownView: View? = null
    private var sortOutsideDismissView: View? = null
    private val sortDropdownSafetyMarginPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.ui_space_3xl)
    private val browsePlayerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED)
            ) {
                updateNowPlayingButton()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_tv_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        bindActions(view)
        bindBackHandler()
        collectState()
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onLocalStoragePermissionChanged()
    }

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    override fun onDestroyView() {
        dismissSortDropdown()
        view?.let { root ->
            ViewCompat.removeOnUnhandledKeyEventListener(root, globalMenuKeyListener)
        }
        fastLocateConfirmGuard.reset()
        browseListRenderGate.reset()
        super.onDestroyView()
    }

    private fun bindViews(root: View) {
        panelConnection = root.findViewById(R.id.panel_connection)
        rootScroll = root.findViewById(R.id.root_scroll)
        btnBackTop = root.findViewById(R.id.btn_back_top)
        btnBrowseMode = root.findViewById(R.id.btn_browse_mode)
        btnSettings = root.findViewById(R.id.btn_settings)
        tvConnection = root.findViewById(R.id.tv_connection)
        tvSavedCount = root.findViewById(R.id.tv_saved_count)
        btnManage = root.findViewById(R.id.btn_manage)
        tvPath = root.findViewById(R.id.tv_path)
        btnRefresh = root.findViewById(R.id.btn_refresh)
        btnSort = root.findViewById(R.id.btn_sort)
        tvStatus = root.findViewById(R.id.tv_status)
        panelFastLocate = root.findViewById(R.id.panel_fast_locate)
        tvFastLocateHint = root.findViewById(R.id.tv_fast_locate_hint)
        tvFastLocateTarget = root.findViewById(R.id.tv_fast_locate_target)
        fastLocateTrack = root.findViewById(R.id.view_fast_locate_track)
        fastLocateIndicator = root.findViewById(R.id.view_fast_locate_indicator)
        btnFavorites = root.findViewById(R.id.btn_favorites)
        btnHistory = root.findViewById(R.id.btn_history)
        btnPlayAll = root.findViewById(R.id.btn_play_all)
        btnPlayShuffle = root.findViewById(R.id.btn_play_shuffle)
        btnNowPlaying = root.findViewById(R.id.btn_now_playing)
        filesContainer = root.findViewById(R.id.container_files)
        panelFastLocate.bringToFront()
    }

    private fun bindActions(root: View) {
        btnBackTop.setOnClickListener { navigateUpDirectory() }
        btnBrowseMode.setOnClickListener {
            val isEnteringLocal = viewModel.state.value.mode == BrowseMode.NAS
            viewModel.toggleMode()
            if (isEnteringLocal) requestLocalStorageAccess()
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        btnManage.setOnClickListener { showConnectionManagerDialog() }
        btnRefresh.setOnClickListener {
            if (viewModel.state.value.localPermissionRequired) requestLocalStorageAccess() else viewModel.refreshCurrentPath()
        }
        btnSort.setOnClickListener { showSortDropdown() }
        btnFavorites.setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }
        btnHistory.setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        btnPlayAll.setOnClickListener { playDirectory(shuffle = false) }
        btnPlayShuffle.setOnClickListener { playDirectory(shuffle = true) }
        btnNowPlaying.setOnClickListener {
            if (mediaController?.currentMediaItem != null) {
                startActivity(Intent(requireContext(), PlaybackActivity::class.java))
            } else {
                resumeLastPlayback()
            }
        }
        bindTouchFeedback()
        btnFavorites.setOnFocusChangeListener { _, _ -> updateBrowserPlaybackButtonPresentation() }
        btnHistory.setOnFocusChangeListener { _, _ -> updateBrowserPlaybackButtonPresentation() }
        btnPlayAll.setOnFocusChangeListener { _, _ -> updateBrowserPlaybackButtonPresentation() }
        btnPlayShuffle.setOnFocusChangeListener { _, _ -> updateBrowserPlaybackButtonPresentation() }

        root.isFocusableInTouchMode = true
        root.requestFocus()
        root.setOnKeyListener { _, keyCode, event ->
            handleFastLocateKey(keyCode, event)
        }
        ViewCompat.addOnUnhandledKeyEventListener(root, globalMenuKeyListener)
        updateBrowserPlaybackButtonPresentation()
        updateNowPlayingButton()
    }

    private fun bindTouchFeedback() {
        // 深色按钮（返回/设置/刷新/排序）用提亮 overlay；彩色按钮（管理/收藏/历史/顺序/随机/当前播放）用加深 overlay。
        UiMotion.applyPressFeedback(btnBackTop, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnBrowseMode, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnSettings, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnRefresh, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnSort, R.color.ui_press_overlay_light)
        UiMotion.applyPressFeedback(btnManage, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnFavorites, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnHistory, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnPlayAll, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnPlayShuffle, R.color.ui_press_overlay_dark)
        UiMotion.applyPressFeedback(btnNowPlaying, R.color.ui_press_overlay_dark)
    }

    private fun requestLocalStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val packageUri = Uri.parse("package:${requireContext().packageName}")
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri))
            }.getOrElse {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_LOCAL_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCAL_STORAGE) viewModel.onLocalStoragePermissionChanged()
    }

    private fun updateBrowserPlaybackButtonPresentation() {
        applyBrowserButtonSpec(btnFavorites, PlaybackButtonPresentation.browserFavorites(requireContext(), btnFavorites.hasFocus()))
        applyBrowserButtonSpec(
            button = btnHistory,
            spec = PlaybackButtonPresentation.browserHistory(requireContext(), btnHistory.hasFocus()),
            iconColorResId = R.color.ui_text_warning_dark,
        )
        applyBrowserButtonSpec(btnPlayAll, PlaybackButtonPresentation.browserPlayOrder(requireContext(), btnPlayAll.hasFocus()))
        applyBrowserButtonSpec(btnPlayShuffle, PlaybackButtonPresentation.browserPlayShuffle(requireContext(), btnPlayShuffle.hasFocus()))
    }

    private fun applyBrowserButtonSpec(
        button: Button,
        spec: PlaybackButtonSpec,
        iconColorResId: Int = R.color.ui_text_on_accent,
    ) {
        BrowserPlaybackButtonRenderer.apply(
            context = requireContext(),
            button = button,
            spec = spec,
            hasFocus = button.hasFocus(),
            iconColorResId = iconColorResId,
        )
    }

    private fun bindBackHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (sortDropdownView != null) {
                        dismissSortDropdown()
                        return
                    }
                    if (viewModel.state.value.isFastLocateMode) {
                        viewModel.cancelFastLocate()
                        return
                    }
                    if (navigateUpDirectory()) return
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    private fun collectState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                render(state)
                state.toast?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.consumeToast()
                }
            }
        }
    }

    private fun render(state: TvBrowserState) {
        val showConnectionSection = state.mode == BrowseMode.NAS && state.currentPath.isBlank()
        panelConnection.visibility = if (showConnectionSection) View.VISIBLE else View.GONE
        btnBrowseMode.text = getString(
            if (state.mode == BrowseMode.NAS) R.string.browser_mode_nas else R.string.browser_mode_local
        )

        btnBackTop.isEnabled = state.currentPath.isNotBlank()
        btnBackTop.alpha = if (btnBackTop.isEnabled) 1f else 0.55f

        tvConnection.text = getString(R.string.browser_connection_current, configText(state.config))
        tvSavedCount.text = getString(R.string.browser_saved_count, state.savedConnections.size)

        val pathLabel = when {
            state.mode == BrowseMode.LOCAL && state.currentPath.isBlank() -> getString(R.string.browser_local_root)
            state.currentPath.isBlank() -> "/"
            else -> "/${state.currentPath}"
        }
        tvPath.text = getString(R.string.browser_current_path, pathLabel)
        btnRefresh.text = getString(
            if (state.localPermissionRequired) R.string.browser_local_authorize else R.string.browser_refresh_current_dir
        )

        when {
            state.error != null -> {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = state.error
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.ui_text_error))
            }
            state.loading -> {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.common_loading)
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.ui_accent_blue_stroke_soft))
            }
            !state.inlineMessage.isNullOrBlank() -> {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = state.inlineMessage
                tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.ui_accent_blue_stroke_soft))
            }
            else -> tvStatus.visibility = View.GONE
        }

        renderFastLocatePanel(state)

        btnSort.text = getString(state.sortOption.labelResId)

        val displayEntries = buildList {
            if (state.currentPath.isNotBlank()) {
                add(SmbEntry(name = "..", fullPath = state.currentPath, isDirectory = true))
            }
            addAll(state.entries)
        }
        if (browseListRenderGate.shouldRebuild("${state.currentPath}|${state.sortOption.name}", displayEntries)) {
            renderFileItems(state, displayEntries)
        }
        ensureBrowseFocus(state, displayEntries)
    }

    private fun renderFileItems(state: TvBrowserState, entries: List<SmbEntry>) {
        filesContainer.removeAllViews()
        val hasParentEntry = state.currentPath.isNotBlank()
        entries.forEachIndexed { displayIndex, entry ->
            val itemView = layoutInflater.inflate(R.layout.item_file_entry, filesContainer, false)
            val ivTag: ImageView = itemView.findViewById(R.id.iv_tag)
            val tvName: TextView = itemView.findViewById(R.id.tv_name)
            val tvSize: TextView = itemView.findViewById(R.id.tv_size)
            val tvModified: TextView = itemView.findViewById(R.id.tv_modified)

            ivTag.setImageResource(
                if (entry.isDirectory) R.drawable.ic_tag_folder else R.drawable.ic_tag_music
            )
            tvName.text = entry.name
            val isParentEntry = hasParentEntry && displayIndex == 0
            if (isParentEntry) {
                tvSize.visibility = View.GONE
                tvModified.visibility = View.GONE
            } else {
                tvSize.visibility = View.VISIBLE
                tvModified.visibility = View.VISIBLE
                tvSize.text = formatFileSize(entry.sizeBytes, entry.isDirectory)
                tvModified.text = formatModifiedTime(entry.lastModifiedAt)
                tvSize.gravity = metadataColumnGravity(tvSize.text)
                tvModified.gravity = metadataColumnGravity(tvModified.text)
            }

            itemView.setOnClickListener {
                if (viewModel.state.value.isFastLocateMode) return@setOnClickListener
                onFileClicked(entry)
            }
            itemView.setOnLongClickListener {
                val entered = viewModel.enterFastLocate(estimateVisibleWindowSize())
                if (!entered) {
                    Toast.makeText(requireContext(), R.string.browser_fast_locate_too_short, Toast.LENGTH_SHORT).show()
                } else {
                    fastLocateConfirmGuard.arm()
                }
                true
            }
            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus || viewModel.state.value.isFastLocateMode) return@setOnFocusChangeListener
                val stateIndex = mapDisplayIndexToStateIndex(displayIndex, hasParentEntry) ?: return@setOnFocusChangeListener
                viewModel.onItemFocused(stateIndex, entry)
            }
            itemView.setOnKeyListener { _, keyCode, event ->
                handleFastLocateKey(keyCode, event)
            }
            UiMotion.applyPressFeedback(itemView, R.color.ui_press_overlay_light)
            filesContainer.addView(itemView)
        }
    }

    private fun ensureBrowseFocus(state: TvBrowserState, entries: List<SmbEntry>) {
        filesContainer.post {
            val root = view ?: return@post
            if (entries.isEmpty()) {
                btnBackTop.requestFocus()
                return@post
            }

            val hasParentEntry = state.currentPath.isNotBlank()
            val targetDisplayIndex = when {
                state.isFastLocateMode -> state.fastLocate?.currentIndex?.let {
                    mapStateIndexToDisplayIndex(it, hasParentEntry)
                }
                state.restoredFocusIndex != null -> mapStateIndexToDisplayIndex(state.restoredFocusIndex, hasParentEntry)
                else -> null
            }?.coerceIn(0, filesContainer.childCount - 1)

            val focused = root.findFocus()
            if (targetDisplayIndex != null) {
                val currentDisplayIndex = focused?.let(::findFocusedDisplayIndex) ?: -1
                if (currentDisplayIndex != targetDisplayIndex || focused == null || focused === root) {
                    filesContainer.getChildAt(targetDisplayIndex)?.requestFocus()
                }
                return@post
            }

            if (focused != null && focused !== root) return@post
            filesContainer.getChildAt(0)?.requestFocus() ?: btnBackTop.requestFocus()
        }
    }

    private fun renderFastLocatePanel(state: TvBrowserState) {
        val locate = state.fastLocate
        val inMode = state.isFastLocateMode && locate != null
        panelFastLocate.visibility = if (inMode) View.VISIBLE else View.GONE
        if (!inMode || locate == null) {
            fastLocateConfirmGuard.reset()
            return
        }

        tvFastLocateHint.text = getString(R.string.browser_fast_locate_hint, locate.progressPercent)
        tvFastLocateTarget.text = getString(
            R.string.browser_fast_locate_target,
            locate.currentIndex + 1,
            locate.totalCount,
        )

        fastLocateTrack.post {
            val trackHeight = fastLocateTrack.height
            val indicatorHeight = fastLocateIndicator.height
            if (trackHeight <= 0 || indicatorHeight <= 0) return@post
            val usable = (trackHeight - indicatorHeight).coerceAtLeast(0)
            val top = (usable * (locate.progressPercent / 100f)).roundToInt()

            val layoutParams = fastLocateIndicator.layoutParams as? FrameLayout.LayoutParams ?: return@post
            if (layoutParams.topMargin != top) {
                layoutParams.topMargin = top
                fastLocateIndicator.layoutParams = layoutParams
            }
        }
    }
    private val globalMenuKeyListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
        if (event.keyCode != KeyEvent.KEYCODE_MENU || event.action != KeyEvent.ACTION_UP) {
            return@OnUnhandledKeyEventListenerCompat false
        }
        if (viewModel.state.value.mode == BrowseMode.NAS) showConnectionManagerDialog()
        true
    }

    private fun estimateVisibleWindowSize(): Int {
        val firstRow = filesContainer.getChildAt(0) ?: return 1
        val rowParams = firstRow.layoutParams as? ViewGroup.MarginLayoutParams
        val rowHeightUnit = firstRow.height + (rowParams?.topMargin ?: 0) + (rowParams?.bottomMargin ?: 0)
        if (rowHeightUnit <= 0) return 1

        val scrollRect = Rect()
        val containerRect = Rect()
        val hasScrollRect = rootScroll.getGlobalVisibleRect(scrollRect)
        val hasContainerRect = filesContainer.getGlobalVisibleRect(containerRect)
        val overlapHeight = if (hasScrollRect && hasContainerRect) {
            max(0, min(scrollRect.bottom, containerRect.bottom) - max(scrollRect.top, containerRect.top))
        } else {
            0
        }
        val effectiveHeight = if (overlapHeight > 0) overlapHeight else rootScroll.height
        return max(1, (effectiveHeight + rowHeightUnit - 1) / rowHeightUnit)
    }

    private fun handleFastLocateKey(keyCode: Int, event: KeyEvent): Boolean {
        val state = viewModel.state.value
        if (!state.isFastLocateMode) return false
        if (fastLocateConfirmGuard.shouldConsumeWhileFastLocate(keyCode, event.action)) return true
        if (keyCode == KeyEvent.KEYCODE_MENU) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.jumpFastLocateByPage(direction = -1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.jumpFastLocateByPage(direction = 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                viewModel.jumpFastLocateBySegment(direction = -1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                viewModel.jumpFastLocateBySegment(direction = 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                fastLocateConfirmGuard.reset()
                viewModel.acceptFastLocate()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                fastLocateConfirmGuard.reset()
                viewModel.cancelFastLocate()
                true
            }
            else -> true
        }
    }

    private fun findFocusedDisplayIndex(focused: View): Int {
        var current: View? = focused
        while (current != null && current.parent !== filesContainer) {
            current = current.parent as? View
        }
        return if (current == null) -1 else filesContainer.indexOfChild(current)
    }

    private fun mapDisplayIndexToStateIndex(displayIndex: Int, hasParentEntry: Boolean): Int? {
        if (displayIndex < 0) return null
        if (!hasParentEntry) return displayIndex
        if (displayIndex == 0) return null
        return displayIndex - 1
    }

    private fun mapStateIndexToDisplayIndex(stateIndex: Int, hasParentEntry: Boolean): Int {
        return if (hasParentEntry) stateIndex + 1 else stateIndex
    }

    private fun onFileClicked(entry: SmbEntry) {
        if (entry.isDirectory) {
            viewModel.enterDirectory(entry)
            return
        }
        val queue = PlaybackQueueBuilder.fromDirectory(viewModel.state.value.entries)
        val startIndex = PlaybackQueueBuilder.startIndex(queue, entry)
        playQueue(queue, startIndex, shuffle = false)
    }

    private fun navigateUpDirectory(): Boolean {
        return viewModel.navigateUp()
    }

    private fun playDirectory(shuffle: Boolean) {
        val queue = PlaybackQueueBuilder.fromDirectory(viewModel.state.value.entries)
        playQueue(queue, startIndex = 0, shuffle = shuffle)
    }

    private fun playQueue(queue: List<SmbEntry>, startIndex: Int, shuffle: Boolean) {
        if (queue.isEmpty()) {
            Toast.makeText(requireContext(), R.string.browser_no_playable_audio, Toast.LENGTH_SHORT).show()
            return
        }
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(requireContext(), R.string.browser_player_init_failed, Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val state = viewModel.state.value
        val config = if (state.mode == BrowseMode.LOCAL) SmbConfig.Empty else state.config
        PlaybackConfigStore.update(config)
        lifecycleScope.launch {
            runCatching {
                val targetEntry = queue[startIndex.coerceIn(0, queue.lastIndex)]
                val targetUri = targetEntry.streamUri.orEmpty()
                val queueUris = queue.mapNotNull { it.streamUri }
                val existingUris = currentQueueUris(controller)

                if (existingUris == queueUris && controller.currentMediaItem?.localConfiguration?.uri.toString() == targetUri) {
                    controller.repeatMode = Player.REPEAT_MODE_OFF
                    controller.setShuffleModeEnabled(shuffle)
                    if (!controller.isPlaying) {
                        controller.play()
                    }
                    return@runCatching
                }

                if (existingUris == queueUris) {
                    controller.repeatMode = Player.REPEAT_MODE_OFF
                    controller.setShuffleModeEnabled(shuffle)
                    controller.seekToDefaultPosition(startIndex.coerceIn(0, controller.mediaItemCount - 1))
                    controller.play()
                    return@runCatching
                }

                val mediaItems = withContext(Dispatchers.IO) {
                    if (state.mode == BrowseMode.LOCAL) localMediaItemFactory.create(queue) else mediaItemFactory.create(config, queue)
                }
                controller.repeatMode = Player.REPEAT_MODE_OFF
                controller.setShuffleModeEnabled(shuffle)
                controller.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
                controller.prepare()
                controller.play()
            }.onSuccess {
                startActivity(Intent(requireContext(), PlaybackActivity::class.java))
            }.onFailure { ex ->
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.browser_play_failed,
                        ex.message ?: getString(R.string.browser_play_failed_fallback),
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun ensureController() {
        if (mediaController != null || controllerFuture != null) return

        val token = SessionToken(
            requireContext(),
            ComponentName(requireContext(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        controller.addListener(browsePlayerListener)
                        updateNowPlayingButton()
                    }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(requireContext(), R.string.browser_player_connect_failed, Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun releaseController() {
        mediaController?.removeListener(browsePlayerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        updateNowPlayingButton()
    }

    private fun updateNowPlayingButton() {
        val controller = mediaController
        val currentMediaItem = controller?.currentMediaItem
        val hasActivePlaying = currentMediaItem != null

        if (hasActivePlaying) {
            val title = activeNowPlayingTitle(requireNotNull(controller), requireNotNull(currentMediaItem))
            showNowPlayingButton(
                if (title.isBlank()) getString(R.string.browser_now_playing)
                else getString(R.string.browser_now_playing_with_title, title)
            )
            return
        }

        val ctx = context ?: return
        if (UiSettingsStore.rememberLastPlayback(ctx) && LastPlaybackStore.hasSnapshot(ctx)) {
            val snapshot = LastPlaybackStore.load(ctx)
            if (snapshot != null) {
                showNowPlayingButton(
                    if (snapshot.title.isBlank()) getString(R.string.browser_resume_playback)
                    else getString(R.string.browser_resume_playback_with_title, snapshot.title)
                )
                return
            }
        }

        btnNowPlaying.visibility = View.GONE
        btnNowPlaying.isSelected = false
    }

    private fun showNowPlayingButton(text: String) {
        btnNowPlaying.text = text
        btnNowPlaying.isSelected = true
        btnNowPlaying.visibility = View.VISIBLE
    }

    private fun activeNowPlayingTitle(controller: MediaController, mediaItem: MediaItem): String {
        val key = mediaItem.localConfiguration?.uri?.toString().orEmpty().ifBlank {
            mediaItem.mediaId
        }
        return PlaybackTrackInfoStore.shared.displayFor(
            key = key,
            fallbackTitle = controller.mediaMetadata.title?.toString().orEmpty(),
            fallbackArtist = controller.mediaMetadata.artist?.toString().orEmpty(),
            fallbackAlbumTitle = controller.mediaMetadata.albumTitle?.toString().orEmpty()
        ).title.orEmpty()
    }

    private fun currentQueueUris(controller: MediaController): List<String> {
        return buildList {
            repeat(controller.mediaItemCount) { index ->
                controller.getMediaItemAt(index).localConfiguration?.uri?.toString()?.let(::add)
            }
        }
    }

    private fun resumeLastPlayback() {
        val context = requireContext()
        val snapshot = LastPlaybackStore.load(context) ?: return
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(context, R.string.browser_player_init_failed, Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val config = viewModel.state.value.config
        PlaybackConfigStore.update(config)

        lifecycleScope.launch {
            runCatching {
                val request = LastPlaybackResumeBuilder.fromSnapshot(snapshot)
                    ?: error(getString(R.string.browser_last_queue_empty))
                controller.repeatMode = Player.REPEAT_MODE_OFF
                controller.setShuffleModeEnabled(false)
                controller.setMediaItems(request.mediaItems, request.startIndex, request.positionMs)
                controller.prepare()
                if (request.playWhenReady) {
                    controller.play()
                }
            }.onSuccess {
                startActivity(Intent(requireContext(), PlaybackActivity::class.java))
            }.onFailure { ex ->
                Toast.makeText(
                    context,
                    getString(
                        R.string.browser_resume_failed,
                        ex.message ?: getString(R.string.browser_resume_failed_fallback),
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showConnectionManagerDialog() {
        val state = viewModel.state.value
        val hasEditable = state.config.host.isNotBlank()
        val hasSavedActiveConnection = state.activeConnectionId != null
        val configDesc = configText(state.config)
        modalCoordinator.showActionModal(
            ActionModalSpec(
                sectionLabel = "SMB",
                title = getString(R.string.smb_connection_manager),
                message = getString(R.string.browser_connection_current, configDesc),
                actions = listOfNotNull(
                    if (hasEditable) {
                        ModalAction(getString(R.string.browser_edit_current_connection), isPrimary = true) {
                            showConfigDialog(false)
                        }
                    } else null,
                    if (hasSavedActiveConnection) {
                        ModalAction(getString(R.string.smb_delete_current_connection), isDanger = true) {
                            showDeleteCurrentConnectionConfirm()
                        }
                    } else null,
                    ModalAction(getString(R.string.browser_new_connection)) { showConfigDialog(true) },
                    ModalAction(getString(R.string.browser_switch_connection)) { showSwitchDialog() },
                ),
            )
        )
    }

    private fun showSwitchDialog() {
        val saved = viewModel.state.value.savedConnections
        if (saved.isEmpty()) {
            showConfigDialog(true)
            return
        }

        modalCoordinator.showListModal(
            ListModalSpec(
                sectionLabel = "SMB",
                title = getString(R.string.smb_switch_connection),
                rows = saved.map { conn ->
                    ModalListRow(
                        key = conn.id,
                        label = "${conn.name}（${conn.config.host}）",
                        dismissOnClick = true,
                        onClick = { viewModel.switchConnection(conn.id) },
                    )
                },
            )
        )
    }

    private fun showConfigDialog(saveAsNewDefault: Boolean) {
        val current = viewModel.state.value.config
        val activeName = viewModel.state.value.savedConnections
            .firstOrNull { it.id == viewModel.state.value.activeConnectionId }
            ?.name.orEmpty()

        val fields = buildConfigFormFields(current, activeName, saveAsNewDefault)

        val dialog = modalCoordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "SMB",
                title = getString(R.string.smb_config_title),
                fields = fields,
                primaryAction = ModalAction(getString(R.string.browser_save_and_connect), isPrimary = true),
                secondaryAction = ModalAction(getString(R.string.common_cancel)),
            )
        )

        modalCoordinator.bindTextFieldsToClearCheckbox(dialog, "guest", "username", "password")
        modalCoordinator.bindFormPrimaryAction(
            dialog,
            "name", "host", "share", "path", "username", "password", "guest", "smb1", "saveAsNew",
        ) { values ->
            val hostError = TsmModalFormValidators.validateSmbHost(
                values.getValue("host"),
                emptyMessage = getString(R.string.validation_smb_host_required),
            )
            if (hostError != null) {
                modalCoordinator.updateFieldError(dialog, "host", hostError)
                return@bindFormPrimaryAction false
            }
            val config = SmbConfig(
                host = values.getValue("host").trim(),
                share = values.getValue("share").trim(),
                path = values.getValue("path").trim(),
                username = values.getValue("username").trim(),
                password = values.getValue("password"),
                guest = values.getValue("guest").toBooleanStrictOrNull() ?: false,
                smb1Enabled = values.getValue("smb1").toBooleanStrictOrNull() ?: false,
            )
            viewModel.saveConfig(
                config = config,
                name = values.getValue("name").trim(),
                saveAsNew = values.getValue("saveAsNew").toBooleanStrictOrNull() ?: false,
            )
            true
        }
    }

    private fun configText(config: SmbConfig): String {
        if (config.host.isBlank()) return getString(R.string.common_not_configured)
        return if (config.share.isBlank()) {
            getString(R.string.browser_smb_all_shares, config.host)
        } else {
            val path = config.normalizedPath()
            if (path.isBlank()) "smb://${config.host}/${config.share}"
            else "smb://${config.host}/${config.share}/$path"
        }
    }

    private fun buildConfigFormFields(
        config: SmbConfig,
        connectionName: String,
        saveAsNewDefault: Boolean,
    ): List<FormFieldSpec> = listOf(
        FormFieldSpec("name", getString(R.string.browser_form_connection_name), connectionName, getString(R.string.common_optional), InputType.TYPE_CLASS_TEXT),
        FormFieldSpec("host", getString(R.string.browser_form_host), config.host, "", InputType.TYPE_CLASS_TEXT),
        FormFieldSpec("share", getString(R.string.browser_form_share), config.share, getString(R.string.common_optional), InputType.TYPE_CLASS_TEXT),
        FormFieldSpec("path", getString(R.string.browser_form_path), config.path, getString(R.string.common_optional), InputType.TYPE_CLASS_TEXT),
        FormFieldSpec("username", getString(R.string.browser_form_username), config.username, getString(R.string.common_optional), InputType.TYPE_CLASS_TEXT),
        FormFieldSpec("password", getString(R.string.browser_form_password), config.password, getString(R.string.common_optional), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
        FormFieldSpec("guest", getString(R.string.browser_form_guest), config.guest.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
        FormFieldSpec("smb1", getString(R.string.browser_form_smb1), config.smb1Enabled.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
        FormFieldSpec("saveAsNew", getString(R.string.browser_form_save_as_new), saveAsNewDefault.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
    )

    @VisibleForTesting
    internal fun formatFileSize(sizeBytes: Long?, isDirectory: Boolean): String {
        if (isDirectory || sizeBytes == null) return "--"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = sizeBytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        val display = if (unitIndex == 0 || value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
        return "$display ${units[unitIndex]}"
    }

    @VisibleForTesting
    internal fun formatModifiedTime(timestamp: Long?): String {
        if (timestamp == null) return "--"
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(timestamp))
    }

    @VisibleForTesting
    internal fun metadataColumnGravity(text: CharSequence): Int {
        return if (text.toString() == "--") Gravity.CENTER else Gravity.END
    }

    companion object {
        private const val REQUEST_LOCAL_STORAGE = 4003
        /**
         * 构建连接管理弹窗的操作标签列表，用于测试验证顺序和条件。
         */
        @VisibleForTesting
        internal fun buildConnectionManagerActionLabelsForTest(
            hasEditableConnection: Boolean,
            hasSavedActiveConnection: Boolean,
        ): List<String> {
            return listOfNotNull(
                if (hasEditableConnection) "Edit Current Connection" else null,
                if (hasSavedActiveConnection) "Delete Current Connection" else null,
                "New Connection",
                "Switch Connection",
            )
        }

        /**
         * 构建 SMB 配置表单字段列表，用于测试验证字段构成。
         */
        @VisibleForTesting
        internal fun buildConfigFormFieldsForTest(
            config: SmbConfig,
            connectionName: String,
            saveAsNewDefault: Boolean,
        ): List<FormFieldSpec> = listOf(
            FormFieldSpec("name", "Connection Name", connectionName, "Optional", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("host", "SMB Server", config.host, "", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("share", "Share Name", config.share, "Optional", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("path", "Subfolder", config.path, "Optional", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("username", "Username", config.username, "Optional", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("password", "Password", config.password, "Optional", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            FormFieldSpec("guest", "Guest / Anonymous", config.guest.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
            FormFieldSpec("smb1", "Enable SMB1 Compatibility (off by default)", config.smb1Enabled.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
            FormFieldSpec("saveAsNew", "Save as New Connection", saveAsNewDefault.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
        )

        @VisibleForTesting
        fun formatSizeForTest(sizeBytes: Long?, isDirectory: Boolean): String =
            TvBrowseFragment().formatFileSize(sizeBytes, isDirectory)

        @VisibleForTesting
        fun formatModifiedTimeForTest(timestamp: Long?, timeZone: java.util.TimeZone): String {
            if (timestamp == null) return "--"
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            formatter.timeZone = timeZone
            return formatter.format(java.util.Date(timestamp))
        }

        @VisibleForTesting
        fun metadataColumnGravityForTest(text: String): Int =
            TvBrowseFragment().metadataColumnGravity(text)

        @VisibleForTesting
        fun createSortOutsideDismissOverlayForTest(context: Context): View =
            createSortOutsideDismissOverlay(context)

        @VisibleForTesting
        fun sortDropdownScrollDeltaForTest(
            dropdownBottom: Int,
            visibleBottom: Int,
            safetyMargin: Int,
        ): Int = calculateSortDropdownScrollDelta(dropdownBottom, visibleBottom, safetyMargin)

        @VisibleForTesting
        fun sortDropdownInitialFocusIndexForTest(option: BrowserSortOption): Int =
            sortDropdownInitialFocusIndex(option)

        private fun sortDropdownInitialFocusIndex(option: BrowserSortOption): Int =
            BrowserSortOption.entries.indexOf(option).coerceAtLeast(0)

        private fun calculateSortDropdownScrollDelta(
            dropdownBottom: Int,
            visibleBottom: Int,
            safetyMargin: Int,
        ): Int = max(0, dropdownBottom + safetyMargin - visibleBottom)

        private fun createSortOutsideDismissOverlay(context: Context): View {
            return View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                isClickable = true
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
    }

    private fun showSortDropdown() {
        if (sortDropdownView != null) return
        val rootView = view as? FrameLayout ?: return
        val outsideDismissView = createSortOutsideDismissOverlay(requireContext()).apply {
            setOnClickListener { dismissSortDropdown() }
        }
        val dropdown = layoutInflater.inflate(R.layout.view_browser_sort_dropdown, rootView, false)
        val optionsContainer = dropdown.findViewById<LinearLayout>(R.id.container_sort_options)
        renderSortOptions(optionsContainer)
        keepSortOptionFocusInsideDropdown(optionsContainer)
        scrollToRevealSortDropdown(dropdown)

        val location = IntArray(2)
        btnSort.getLocationOnScreen(location)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = location[0]
        params.topMargin = location[1] + btnSort.height

        rootView.addView(outsideDismissView)
        rootView.addView(dropdown, params)
        sortOutsideDismissView = outsideDismissView
        sortDropdownView = dropdown

        optionsContainer.getChildAt(sortDropdownInitialFocusIndex(viewModel.state.value.sortOption))?.requestFocus()
    }

    private fun scrollToRevealSortDropdown(dropdown: View) {
        val visibleRect = Rect()
        if (!rootScroll.getGlobalVisibleRect(visibleRect)) return

        dropdown.measure(
            View.MeasureSpec.makeMeasureSpec(rootScroll.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val location = IntArray(2)
        btnSort.getLocationOnScreen(location)
        val dropdownBottom = location[1] + btnSort.height + dropdown.measuredHeight
        val scrollDelta = calculateSortDropdownScrollDelta(
            dropdownBottom = dropdownBottom,
            visibleBottom = visibleRect.bottom,
            safetyMargin = sortDropdownSafetyMarginPx,
        )
        if (scrollDelta > 0) {
            rootScroll.scrollBy(0, scrollDelta)
        }
    }

    private fun keepSortOptionFocusInsideDropdown(container: LinearLayout) {
        val childCount = container.childCount
        if (childCount == 0) return
        for (index in 0 until childCount) {
            val child = container.getChildAt(index)
            child.nextFocusUpId = if (index == 0) child.id else container.getChildAt(index - 1).id
            child.nextFocusDownId = if (index == childCount - 1) child.id else container.getChildAt(index + 1).id
        }
    }

    private fun renderSortOptions(container: LinearLayout) {
        BrowserSortOption.entries.forEach { option ->
            val itemView = layoutInflater.inflate(R.layout.item_browser_sort_option, container, false)
            val tvOption = itemView as TextView
            itemView.id = View.generateViewId()
            tvOption.text = getString(option.labelResId)
            itemView.setOnClickListener {
                viewModel.selectSortOption(option)
                dismissSortDropdown()
            }
            UiMotion.applyPressFeedback(itemView, R.color.ui_press_overlay_light)
            container.addView(itemView)
        }
    }

    private fun dismissSortDropdown() {
        sortDropdownView?.let { dropdown ->
            (dropdown.parent as? ViewGroup)?.removeView(dropdown)
        }
        sortOutsideDismissView?.let { outsideDismissView ->
            (outsideDismissView.parent as? ViewGroup)?.removeView(outsideDismissView)
        }
        sortDropdownView = null
        sortOutsideDismissView = null
        if (::btnSort.isInitialized) {
            btnSort.requestFocus()
        }
    }

    fun handlePlaybackLocateTarget(target: PlaybackLocationResolver.Target) {
        viewModel.locateToPlaybackDirectory(target)
    }

    private fun showDeleteCurrentConnectionConfirm() {
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = "SMB",
                title = getString(R.string.smb_delete_connection_confirm_title),
                message = getString(R.string.smb_delete_connection_confirm_message),
                confirmAction = ModalAction(
                    getString(R.string.modal_default_delete),
                    isDanger = true,
                    onClick = { viewModel.deleteActiveConnection() },
                ),
                cancelAction = ModalAction(getString(R.string.modal_default_cancel)),
            )
        )
    }
}
