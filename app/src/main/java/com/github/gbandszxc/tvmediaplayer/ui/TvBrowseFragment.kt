package com.github.gbandszxc.tvmediaplayer.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackStore
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackResumeBuilder
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLocationResolver
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackQueueBuilder
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.github.gbandszxc.tvmediaplayer.playback.SmbMediaItemFactory
import com.github.gbandszxc.tvmediaplayer.ui.modal.ActionModalSpec
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
    private val modalCoordinator by lazy { TsmModalCoordinator(requireActivity()) }
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val fastLocateConfirmGuard = FastLocateConfirmGuard()
    private val browseListRenderGate = BrowseListRenderGate()

    private lateinit var panelConnection: View
    private lateinit var rootScroll: ScrollView
    private lateinit var btnBackTop: Button
    private lateinit var btnSettings: Button
    private lateinit var tvConnection: TextView
    private lateinit var tvSavedCount: TextView
    private lateinit var btnManage: Button
    private lateinit var tvPath: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnRetry: Button
    private lateinit var tvStatus: TextView
    private lateinit var panelFastLocate: View
    private lateinit var tvFastLocateHint: TextView
    private lateinit var tvFastLocateTarget: TextView
    private lateinit var fastLocateTrack: View
    private lateinit var fastLocateIndicator: View
    private lateinit var btnFavorites: Button
    private lateinit var btnPlayAll: Button
    private lateinit var btnPlayShuffle: Button
    private lateinit var btnNowPlaying: Button
    private lateinit var filesContainer: LinearLayout
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

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    override fun onDestroyView() {
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
        btnSettings = root.findViewById(R.id.btn_settings)
        tvConnection = root.findViewById(R.id.tv_connection)
        tvSavedCount = root.findViewById(R.id.tv_saved_count)
        btnManage = root.findViewById(R.id.btn_manage)
        tvPath = root.findViewById(R.id.tv_path)
        btnRefresh = root.findViewById(R.id.btn_refresh)
        btnRetry = root.findViewById(R.id.btn_retry)
        tvStatus = root.findViewById(R.id.tv_status)
        panelFastLocate = root.findViewById(R.id.panel_fast_locate)
        tvFastLocateHint = root.findViewById(R.id.tv_fast_locate_hint)
        tvFastLocateTarget = root.findViewById(R.id.tv_fast_locate_target)
        fastLocateTrack = root.findViewById(R.id.view_fast_locate_track)
        fastLocateIndicator = root.findViewById(R.id.view_fast_locate_indicator)
        btnFavorites = root.findViewById(R.id.btn_favorites)
        btnPlayAll = root.findViewById(R.id.btn_play_all)
        btnPlayShuffle = root.findViewById(R.id.btn_play_shuffle)
        btnNowPlaying = root.findViewById(R.id.btn_now_playing)
        filesContainer = root.findViewById(R.id.container_files)
        panelFastLocate.bringToFront()
    }

    private fun bindActions(root: View) {
        btnBackTop.setOnClickListener { navigateUpDirectory() }
        btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        btnManage.setOnClickListener { showConnectionManagerDialog() }
        btnRefresh.setOnClickListener { viewModel.loadCurrentPath() }
        btnRetry.setOnClickListener { viewModel.loadCurrentPath() }
        btnFavorites.setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
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
        btnFavorites.setOnFocusChangeListener { _, _ -> updateBrowserPlaybackButtonPresentation() }
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

    private fun updateBrowserPlaybackButtonPresentation() {
        applyBrowserButtonSpec(btnFavorites, PlaybackButtonPresentation.browserFavorites(btnFavorites.hasFocus()))
        applyBrowserButtonSpec(btnPlayAll, PlaybackButtonPresentation.browserPlayOrder(btnPlayAll.hasFocus()))
        applyBrowserButtonSpec(btnPlayShuffle, PlaybackButtonPresentation.browserPlayShuffle(btnPlayShuffle.hasFocus()))
    }

    private fun applyBrowserButtonSpec(button: Button, spec: PlaybackButtonSpec) {
        BrowserPlaybackButtonRenderer.apply(
            context = requireContext(),
            button = button,
            spec = spec,
            hasFocus = button.hasFocus(),
        )
    }

    private fun bindBackHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
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
        val showConnectionSection = state.currentPath.isBlank()
        panelConnection.visibility = if (showConnectionSection) View.VISIBLE else View.GONE

        btnBackTop.isEnabled = state.currentPath.isNotBlank()
        btnBackTop.alpha = if (btnBackTop.isEnabled) 1f else 0.55f

        tvConnection.text = "当前连接：${configText(state.config)}"
        tvSavedCount.text = "已保存连接：${state.savedConnections.size} 个"

        val pathLabel = if (state.currentPath.isBlank()) "/" else "/${state.currentPath}"
        tvPath.text = "当前路径：$pathLabel"

        btnRetry.visibility = if (state.error != null) View.VISIBLE else View.GONE
        when {
            state.error != null -> {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = state.error
                tvStatus.setTextColor(Color.parseColor("#FCA5A5"))
            }
            state.loading -> {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = "加载中..."
                tvStatus.setTextColor(Color.parseColor("#BFDBFE"))
            }
            !state.inlineMessage.isNullOrBlank() -> {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = state.inlineMessage
                tvStatus.setTextColor(Color.parseColor("#BFDBFE"))
            }
            else -> tvStatus.visibility = View.GONE
        }

        renderFastLocatePanel(state)

        val displayEntries = buildList {
            if (state.currentPath.isNotBlank()) {
                add(SmbEntry(name = "..", fullPath = state.currentPath, isDirectory = true))
            }
            addAll(state.entries)
        }
        if (browseListRenderGate.shouldRebuild(state.currentPath, displayEntries)) {
            renderFileItems(state, displayEntries)
        }
        ensureBrowseFocus(state, displayEntries)
    }

    private fun renderFileItems(state: TvBrowserState, entries: List<SmbEntry>) {
        filesContainer.removeAllViews()
        val hasParentEntry = state.currentPath.isNotBlank()
        entries.forEachIndexed { displayIndex, entry ->
            val itemView = layoutInflater.inflate(R.layout.item_file_entry, filesContainer, false)
            val tvTag: TextView = itemView.findViewById(R.id.tv_tag)
            val tvName: TextView = itemView.findViewById(R.id.tv_name)

            tvTag.text = if (entry.isDirectory) "📁" else "🎵"
            tvTag.background = null
            tvName.text = entry.name

            itemView.setOnClickListener {
                if (viewModel.state.value.isFastLocateMode) return@setOnClickListener
                onFileClicked(entry)
            }
            itemView.setOnLongClickListener {
                val entered = viewModel.enterFastLocate(estimateVisibleWindowSize())
                if (!entered) {
                    Toast.makeText(requireContext(), "当前列表较短，无法进入快速定位", Toast.LENGTH_SHORT).show()
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

        tvFastLocateHint.text = "快速定位模式 ${locate.progressPercent}%"
        tvFastLocateTarget.text =
            "当前：${locate.currentIndex + 1}/${locate.totalCount}\n↑/↓整屏跳  ←/→10%跳  确认接受  返回取消"

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
        showConnectionManagerDialog()
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
            Toast.makeText(requireContext(), "当前目录没有可播放音频", Toast.LENGTH_SHORT).show()
            return
        }
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(requireContext(), "播放器初始化中，请稍后重试", Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val config = viewModel.state.value.config
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
                    mediaItemFactory.create(config, queue)
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
                    "播放失败：${ex.message ?: "请检查 SMB 连接与账号"}",
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
                        Toast.makeText(requireContext(), "播放器连接失败", Toast.LENGTH_SHORT).show()
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
            showNowPlayingButton(if (title.isBlank()) "回到当前播放" else "回到当前播放：$title")
            return
        }

        val ctx = context ?: return
        if (UiSettingsStore.rememberLastPlayback(ctx) && LastPlaybackStore.hasSnapshot(ctx)) {
            val snapshot = LastPlaybackStore.load(ctx)
            if (snapshot != null) {
                showNowPlayingButton(if (snapshot.title.isBlank()) "继续上次播放" else "继续上次播放：${snapshot.title}")
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
            Toast.makeText(context, "播放器初始化中，请稍后重试", Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val config = viewModel.state.value.config
        PlaybackConfigStore.update(config)

        lifecycleScope.launch {
            runCatching {
                val request = LastPlaybackResumeBuilder.fromSnapshot(snapshot)
                    ?: error("上次播放队列为空")
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
                    "恢复播放失败：${ex.message ?: "请检查 SMB 连接"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showConnectionManagerDialog() {
        val hasEditable = viewModel.state.value.config.host.isNotBlank()
        val configDesc = configText(viewModel.state.value.config)
        modalCoordinator.showActionModal(
            ActionModalSpec(
                sectionLabel = "SMB",
                title = getString(R.string.smb_connection_manager),
                message = "当前连接：$configDesc",
                actions = listOfNotNull(
                    if (hasEditable) {
                        ModalAction("编辑当前连接", isPrimary = true) { showConfigDialog(false) }
                    } else null,
                    ModalAction("新建连接") { showConfigDialog(true) },
                    ModalAction("切换连接") { showSwitchDialog() },
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
                primaryAction = ModalAction("保存并连接", isPrimary = true),
                secondaryAction = ModalAction("取消"),
            )
        )

        modalCoordinator.bindFormPrimaryAction(
            dialog,
            "name", "host", "share", "path", "username", "password", "guest", "smb1", "saveAsNew",
        ) { values ->
            val hostError = TsmModalFormValidators.validateSmbHost(values.getValue("host"))
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
        if (config.host.isBlank()) return "未配置"
        return if (config.share.isBlank()) {
            "smb://${config.host}（全部共享）"
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
    ): List<FormFieldSpec> = buildConfigFormFieldsForTest(config, connectionName, saveAsNewDefault)

    companion object {
        /**
         * 构建连接管理弹窗的操作标签列表，用于测试验证顺序和条件。
         */
        @VisibleForTesting
        internal fun buildConnectionManagerActionLabelsForTest(hasEditableConnection: Boolean): List<String> {
            return listOfNotNull(
                if (hasEditableConnection) "编辑当前连接" else null,
                "新建连接",
                "切换连接",
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
            FormFieldSpec("name", "连接名称", connectionName, "可留空", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("host", "SMB 服务器", config.host, "", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("share", "共享名", config.share, "可留空", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("path", "子路径", config.path, "可留空", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("username", "用户名", config.username, "可留空", InputType.TYPE_CLASS_TEXT),
            FormFieldSpec("password", "密码", config.password, "可留空", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            FormFieldSpec("guest", "访客 / 匿名", config.guest.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
            FormFieldSpec("smb1", "启用 SMB1 兼容（默认关闭）", config.smb1Enabled.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
            FormFieldSpec("saveAsNew", "另存为新连接", saveAsNewDefault.toString(), "", 0, type = FormFieldSpecType.CHECKBOX),
        )
    }

    fun handlePlaybackLocateTarget(target: PlaybackLocationResolver.Target) {
        viewModel.locateToPlaybackDirectory(target)
    }
}
