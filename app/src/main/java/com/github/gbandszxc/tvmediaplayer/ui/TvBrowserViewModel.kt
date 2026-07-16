package com.github.gbandszxc.tvmediaplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.data.repo.BrowserConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.BrowseMode
import com.github.gbandszxc.tvmediaplayer.data.repo.BrowserModeStore
import com.github.gbandszxc.tvmediaplayer.data.repo.BrowserViewMode
import com.github.gbandszxc.tvmediaplayer.data.repo.JcifsSmbRepository
import com.github.gbandszxc.tvmediaplayer.data.repo.LocalFileRepository
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbFailureMapper
import com.github.gbandszxc.tvmediaplayer.domain.model.BrowseFocusAnchor
import com.github.gbandszxc.tvmediaplayer.domain.model.SavedSmbConnection
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbFailure
import com.github.gbandszxc.tvmediaplayer.domain.repo.SmbRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLocationResolver
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrowserSortOption(
    val labelResId: Int,
) {
    NAME_ASC(R.string.browser_sort_name_asc),
    NAME_DESC(R.string.browser_sort_name_desc),
    SIZE_ASC(R.string.browser_sort_size_asc),
    SIZE_DESC(R.string.browser_sort_size_desc),
    MODIFIED_ASC(R.string.browser_sort_modified_asc),
    MODIFIED_DESC(R.string.browser_sort_modified_desc),
}

data class TvBrowserState(
    val mode: BrowseMode = BrowseMode.NAS,
    val config: SmbConfig = SmbConfig.Empty,
    val savedConnections: List<SavedSmbConnection> = emptyList(),
    val activeConnectionId: String? = null,
    val currentPath: String = "",
    val entries: List<SmbEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val restoredFocusIndex: Int? = null,
    val inlineMessage: String? = null,
    val fastLocate: BrowseFastLocateState? = null,
    val isFastLocateMode: Boolean = false,
    val sortOption: BrowserSortOption = BrowserSortOption.NAME_ASC,
    val viewMode: BrowserViewMode = BrowserViewMode.LIST,
    val localPermissionRequired: Boolean = false,
)

class TvBrowserViewModel(
    private val repository: SmbRepository,
    private val configStore: BrowserConfigStore,
    private val localRepository: LocalFileRepository? = null,
    private val modeStore: BrowserModeStore? = null,
    private val appContext: Context? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(TvBrowserState())
    val state: StateFlow<TvBrowserState> = _state.asStateFlow()
    private var lastPersistedAnchor: AnchorFingerprint? = null
    private var lockedConnectionKey: String? = null
    private var loadGeneration: Long = 0L
    private var pendingLocateMediaId: String? = null
    private var nasBrowsePath: String = ""

    init {
        viewModelScope.launch {
            val loaded = configStore.loadState()
            val mode = modeStore?.loadMode() ?: BrowseMode.NAS
            val viewMode = modeStore?.loadViewMode() ?: BrowserViewMode.LIST
            val localPath = modeStore?.loadLocalPath().orEmpty()
            nasBrowsePath = loaded.activeBrowsePath
            _state.update {
                it.copy(
                    mode = mode,
                    viewMode = viewMode,
                    config = loaded.activeConfig,
                    currentPath = if (mode == BrowseMode.LOCAL) localPath else loaded.activeBrowsePath,
                    savedConnections = loaded.savedConnections,
                    activeConnectionId = loaded.activeConnectionId
                )
            }
            if (mode == BrowseMode.LOCAL || loaded.activeConfig.host.isNotBlank()) {
                loadCurrentPath()
            }
        }
    }

    fun toggleMode() {
        val next = if (_state.value.mode == BrowseMode.NAS) BrowseMode.LOCAL else BrowseMode.NAS
        val nextPath = if (next == BrowseMode.LOCAL) {
            modeStore?.loadLocalPath().orEmpty()
        } else {
            nasBrowsePath
        }
        _state.update {
            it.copy(
                mode = next,
                currentPath = nextPath,
                entries = emptyList(),
                error = null,
                localPermissionRequired = false,
                restoredFocusIndex = null,
                inlineMessage = null,
                fastLocate = null,
                isFastLocateMode = false,
            )
        }
        lastPersistedAnchor = null
        modeStore?.saveMode(next)
        loadCurrentPath()
    }

    fun toggleViewMode() {
        val next = if (_state.value.viewMode == BrowserViewMode.LIST) {
            BrowserViewMode.GRID
        } else {
            BrowserViewMode.LIST
        }
        _state.update { it.copy(viewMode = next) }
        modeStore?.saveViewMode(next)
    }

    fun onLocalStoragePermissionChanged() {
        if (_state.value.mode == BrowseMode.LOCAL) loadCurrentPath()
    }

    fun saveConfig(config: SmbConfig, name: String, saveAsNew: Boolean = false) {
        val id = if (saveAsNew) configStore.newConnectionId() else (_state.value.activeConnectionId ?: configStore.newConnectionId())
        val actualName = name.ifBlank { defaultConnectionName(config) }
        val saved = SavedSmbConnection(id = id, name = actualName, config = config)
        val rootPath = config.normalizedPath()
        nasBrowsePath = rootPath

        _state.update {
            val mutable = it.savedConnections.toMutableList()
            val index = mutable.indexOfFirst { c -> c.id == id }
            if (index >= 0) mutable[index] = saved else mutable.add(saved)
            it.copy(
                config = config,
                currentPath = rootPath,
                activeConnectionId = id,
                savedConnections = mutable,
                error = null
            )
        }

        viewModelScope.launch {
            configStore.saveConnection(saved, activate = true)
        }
        lockedConnectionKey = null
        loadCurrentPath()
    }

    fun switchConnection(connectionId: String) {
        val target = _state.value.savedConnections.firstOrNull { it.id == connectionId } ?: return
        val rootPath = target.config.normalizedPath()
        nasBrowsePath = rootPath
        _state.update {
            it.copy(
                config = target.config,
                currentPath = rootPath,
                activeConnectionId = target.id,
                error = null
            )
        }
        viewModelScope.launch {
            configStore.setActiveConnection(connectionId)
        }
        loadCurrentPath()
    }

    fun deleteActiveConnection() {
        val activeConnectionId = _state.value.activeConnectionId ?: return
        viewModelScope.launch {
            val updated = configStore.deleteConnection(activeConnectionId)
            _state.update {
                it.copy(
                    config = updated.activeConfig,
                    savedConnections = updated.savedConnections,
                    activeConnectionId = updated.activeConnectionId,
                    currentPath = updated.activeBrowsePath,
                    entries = emptyList(),
                    loading = false,
                    error = null,
                    toast = string(R.string.smb_deleted_connection),
                    restoredFocusIndex = null,
                    inlineMessage = null,
                    fastLocate = null,
                    isFastLocateMode = false
                )
            }
            lastPersistedAnchor = null
            nasBrowsePath = updated.activeBrowsePath
            if (updated.activeConfig.host.isNotBlank()) {
                loadCurrentPath()
            }
        }
    }

    fun loadCurrentPath() {
        loadCurrentPath(manualRetry = false)
    }

    fun refreshCurrentPath() {
        loadCurrentPath(manualRetry = true)
    }

    private fun loadCurrentPath(manualRetry: Boolean) {
        val snapshot = _state.value
        if (snapshot.mode == BrowseMode.LOCAL) {
            loadLocalPath(snapshot)
            return
        }
        if (snapshot.config.host.isBlank()) {
            _state.update { it.copy(error = string(R.string.smb_host_required)) }
            return
        }
        val connectionKey = failureLockKey(snapshot.activeConnectionId, snapshot.config)
        if (!manualRetry && lockedConnectionKey == connectionKey) {
            _state.update { it.copy(loading = false) }
            return
        }
        val generation = ++loadGeneration
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, toast = null) }
            runCatching {
                listCurrentPathWithRetry(snapshot.config, snapshot.currentPath)
            }.onSuccess { list ->
                if (generation != loadGeneration) return@launch
                val currentSortOption = _state.value.sortOption
                val sorted = sortEntries(list, currentSortOption)
                val anchorConnectionId = resolveAnchorConnectionId(snapshot.activeConnectionId, snapshot.config)
                val anchor = configStore.loadBrowseAnchor(anchorConnectionId, snapshot.currentPath)
                val locatedIndex = pendingLocateMediaId?.let { mediaId ->
                    sorted.indexOfFirst { it.fullPath == mediaId }.takeIf { it >= 0 }
                }
                val locatedEntry = locatedIndex?.let(sorted::getOrNull)
                val restore = locatedIndex
                    ?.let { AnchorRestoreResult(index = it, message = null) }
                    ?: resolveAnchorRestore(anchor, sorted)
                lastPersistedAnchor = locatedEntry?.let {
                    AnchorFingerprint(
                        connectionId = anchorConnectionId,
                        directoryPath = snapshot.currentPath,
                        itemKey = it.fullPath,
                        index = locatedIndex
                    )
                } ?: anchor
                    ?.takeIf { restore.index != null }
                    ?.let {
                        AnchorFingerprint(
                            connectionId = anchorConnectionId,
                            directoryPath = snapshot.currentPath,
                            itemKey = it.itemKey,
                            index = restore.index ?: return@let null
                        )
                    }
                pendingLocateMediaId = null
                _state.update {
                    it.copy(
                        entries = sorted,
                        loading = false,
                        restoredFocusIndex = restore.index,
                        inlineMessage = restore.message,
                        fastLocate = null,
                        isFastLocateMode = false
                    )
                }
                locatedEntry?.let { entry ->
                    configStore.saveBrowseAnchor(
                        connectionId = anchorConnectionId,
                        directoryPath = snapshot.currentPath,
                        anchor = BrowseFocusAnchor(
                            itemKey = entry.fullPath,
                            index = locatedIndex,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                if (lockedConnectionKey == connectionKey) {
                    lockedConnectionKey = null
                }
            }.onFailure { ex ->
                if (generation != loadGeneration) return@launch
                val message = smbFailureMessage(SmbFailureMapper.map(ex))
                lockedConnectionKey = connectionKey
                _state.update {
                    it.copy(
                        loading = false,
                        error = message,
                        toast = string(R.string.browser_smb_connection_failed, message)
                    )
                }
            }
        }
    }

    private fun loadLocalPath(snapshot: TvBrowserState) {
        if (!hasLocalStorageAccess()) {
            _state.update {
                it.copy(
                    loading = false,
                    entries = emptyList(),
                    error = string(R.string.browser_local_permission_required),
                    localPermissionRequired = true,
                )
            }
            return
        }
        val repository = localRepository ?: return
        val generation = ++loadGeneration
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, toast = null, localPermissionRequired = false) }
            runCatching { repository.list(snapshot.currentPath) }
                .onSuccess { list ->
                    if (generation != loadGeneration) return@onSuccess
                    val sorted = sortEntries(list, _state.value.sortOption)
                    val anchor = configStore.loadBrowseAnchor(LOCAL_ANCHOR_NAMESPACE, snapshot.currentPath)
                    val restore = resolveAnchorRestore(anchor, sorted)
                    _state.update {
                        it.copy(
                            entries = sorted,
                            loading = false,
                            restoredFocusIndex = restore.index,
                            inlineMessage = restore.message,
                            fastLocate = null,
                            isFastLocateMode = false,
                        )
                    }
                }
                .onFailure { ex ->
                    if (generation != loadGeneration) return@onFailure
                    _state.update {
                        it.copy(loading = false, error = ex.message ?: string(R.string.browser_local_directory_unavailable))
                    }
                }
        }
    }

    private suspend fun listCurrentPathWithRetry(config: SmbConfig, path: String): List<SmbEntry> {
        var lastError: Throwable? = null
        repeat(SMB_CONNECT_ATTEMPTS) { attempt ->
            runCatching {
                return repository.list(config, path)
            }.onFailure { ex ->
                lastError = ex
                val failure = SmbFailureMapper.map(ex)
                if (!failure.shouldRetryConnection() || attempt == SMB_CONNECT_ATTEMPTS - 1) {
                    throw ex
                }
                delay(SMB_CONNECT_BACKOFF_MS[attempt])
            }
        }
        throw lastError ?: IllegalStateException(string(R.string.smb_error_unknown))
    }

    fun enterDirectory(entry: SmbEntry) {
        if (!entry.isDirectory) {
            _state.update { it.copy(toast = entry.name) }
            return
        }
        val nextPath = if (entry.name == "..") {
            _state.value.currentPath.substringBeforeLast('/', "")
        } else {
            entry.fullPath
        }
        updateCurrentPath(nextPath)
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    fun consumeInlineMessage() {
        _state.update { it.copy(inlineMessage = null) }
    }

    fun navigateUp(): Boolean {
        if (_state.value.currentPath.isBlank()) return false
        updateCurrentPath(_state.value.currentPath.substringBeforeLast('/', ""))
        return true
    }

    fun locateToPlaybackDirectory(target: PlaybackLocationResolver.Target) {
        val targetPath = normalizePath(target.directoryPath)
        if (targetPath.isBlank()) {
            _state.update { it.copy(toast = string(R.string.playback_locate_failed)) }
            return
        }
        pendingLocateMediaId = target.mediaId?.let(PlaybackLocationResolver::normalizePath)

        val stateSnapshot = _state.value
        if (stateSnapshot.mode == BrowseMode.LOCAL && target.sourceConfig.host.isBlank()) {
            updateCurrentPath(targetPath)
            return
        }
        if (isCurrentConnectionTarget(stateSnapshot, target)) {
            applyLocatedPath(stateSnapshot.config, stateSnapshot.activeConnectionId, targetPath) {
                configStore.saveActiveBrowsePath(targetPath)
            }
            return
        }

        val matchedSavedConnection = findSavedConnection(stateSnapshot.savedConnections, target)
        if (matchedSavedConnection != null) {
            applyLocatedPath(matchedSavedConnection.config, matchedSavedConnection.id, targetPath) {
                configStore.setActiveConnection(matchedSavedConnection.id)
                configStore.saveActiveBrowsePath(targetPath)
            }
            return
        }

        if (target.sourceConfig.host.isBlank()) {
            _state.update { it.copy(toast = string(R.string.playback_locate_failed)) }
            return
        }

        applyLocatedPath(target.sourceConfig, null, targetPath) {
            configStore.setActiveConfig(target.sourceConfig, targetPath)
        }
    }

    fun enterFastLocate(visibleWindowSize: Int): Boolean {
        val snapshot = _state.value
        if (!BrowseFastLocateCalculator.canEnter(snapshot.entries.size, visibleWindowSize)) return false

        val initialIndex = snapshot.restoredFocusIndex
            ?.coerceIn(0, (snapshot.entries.size - 1).coerceAtLeast(0))
            ?: 0
        _state.update {
            it.copy(
                isFastLocateMode = true,
                fastLocate = BrowseFastLocateState(
                    totalCount = snapshot.entries.size,
                    currentIndex = initialIndex,
                    visibleWindowSize = visibleWindowSize.coerceAtLeast(1)
                ),
                inlineMessage = null
            )
        }
        return true
    }

    fun jumpFastLocateByPage(direction: Int) {
        val current = _state.value.fastLocate ?: return
        _state.update { it.copy(fastLocate = BrowseFastLocateCalculator.jumpPage(current, direction)) }
    }

    fun jumpFastLocateBySegment(direction: Int) {
        val current = _state.value.fastLocate ?: return
        _state.update { it.copy(fastLocate = BrowseFastLocateCalculator.jumpSegment(current, direction)) }
    }

    fun acceptFastLocate() {
        val targetIndex = _state.value.fastLocate?.currentIndex ?: return
        val targetEntry = _state.value.entries.getOrNull(targetIndex)
        _state.update {
            it.copy(
                restoredFocusIndex = targetIndex,
                isFastLocateMode = false,
                fastLocate = null,
                inlineMessage = null
            )
        }
        targetEntry?.let { persistBrowseAnchor(it, preferredIndex = targetIndex) }
    }

    fun cancelFastLocate() {
        _state.update {
            if (!it.isFastLocateMode) return@update it
            it.copy(isFastLocateMode = false, fastLocate = null)
        }
    }

    fun onItemFocused(index: Int, entry: SmbEntry) {
        persistBrowseAnchor(entry, preferredIndex = index)
    }

    fun selectSortOption(option: BrowserSortOption) {
        val snapshot = _state.value
        val focusedEntry = snapshot.restoredFocusIndex?.let(snapshot.entries::getOrNull)
        val sorted = sortEntries(snapshot.entries, option)
        _state.update {
            it.copy(
                sortOption = option,
                entries = sorted,
                restoredFocusIndex = focusedEntry?.let { entry ->
                    sorted.indexOfFirst { it.fullPath == entry.fullPath }
                }?.takeIf { idx -> idx >= 0 },
            )
        }
    }

    private fun sortEntries(entries: List<SmbEntry>, option: BrowserSortOption): List<SmbEntry> {
        val parent = entries.filter { it.name == ".." }
        val directories = entries.filter { it.isDirectory && it.name != ".." }
        val files = entries.filterNot { it.isDirectory }
        return parent + sortGroup(directories, option) + sortGroup(files, option)
    }

    private fun sortGroup(entries: List<SmbEntry>, option: BrowserSortOption): List<SmbEntry> {
        val comparator = when (option) {
            BrowserSortOption.NAME_ASC -> compareBy<SmbEntry> { it.name.lowercase() }
            BrowserSortOption.NAME_DESC -> compareByDescending<SmbEntry> { it.name.lowercase() }
            BrowserSortOption.SIZE_ASC -> compareBy<SmbEntry>(
                { it.sizeBytes == null },
                { it.sizeBytes ?: Long.MAX_VALUE },
                { it.name.lowercase() }
            )
            BrowserSortOption.SIZE_DESC -> compareBy<SmbEntry>(
                { it.sizeBytes == null },
                { -(it.sizeBytes ?: Long.MIN_VALUE) },
                { it.name.lowercase() }
            )
            BrowserSortOption.MODIFIED_ASC -> compareBy<SmbEntry>(
                { it.lastModifiedAt == null },
                { it.lastModifiedAt ?: Long.MAX_VALUE },
                { it.name.lowercase() }
            )
            BrowserSortOption.MODIFIED_DESC -> compareBy<SmbEntry>(
                { it.lastModifiedAt == null },
                { -(it.lastModifiedAt ?: Long.MIN_VALUE) },
                { it.name.lowercase() }
            )
        }
        return entries.sortedWith(comparator)
    }

    private fun defaultConnectionName(config: SmbConfig): String {
        val share = config.share.ifBlank { string(R.string.browser_all_shares_label) }
        return "${config.host} / $share"
    }

    private fun smbFailureMessage(failure: SmbFailure): String =
        string(
            when (failure) {
                SmbFailure.AUTH_FAILED -> R.string.smb_error_auth_failed
                SmbFailure.HOST_UNREACHABLE -> R.string.smb_error_host_unreachable
                SmbFailure.SHARE_NOT_FOUND -> R.string.smb_error_share_not_found
                SmbFailure.INVALID_PATH -> R.string.smb_error_invalid_path
                SmbFailure.TIMEOUT -> R.string.smb_error_timeout
                SmbFailure.UNKNOWN -> R.string.smb_error_unknown
            }
        )

    private fun isCurrentConnectionTarget(state: TvBrowserState, target: PlaybackLocationResolver.Target): Boolean {
        if (target.sourceConnectionId != null && target.sourceConnectionId == state.activeConnectionId) {
            return true
        }
        return PlaybackLocationResolver.matchesConnection(state.config, target.sourceConfig)
    }

    private fun findSavedConnection(
        savedConnections: List<SavedSmbConnection>,
        target: PlaybackLocationResolver.Target
    ): SavedSmbConnection? {
        target.sourceConnectionId?.let { targetId ->
            savedConnections.firstOrNull { it.id == targetId }?.let { return it }
        }
        return savedConnections.firstOrNull {
            PlaybackLocationResolver.matchesConnection(it.config, target.sourceConfig)
        }
    }

    private fun applyLocatedPath(
        config: SmbConfig,
        activeConnectionId: String?,
        browsePath: String,
        persist: suspend () -> Unit
    ) {
        _state.update {
            it.copy(
                config = config,
                activeConnectionId = activeConnectionId,
                currentPath = browsePath,
                error = null,
                restoredFocusIndex = null,
                inlineMessage = null,
                fastLocate = null,
                isFastLocateMode = false
            )
        }
        lastPersistedAnchor = null
        viewModelScope.launch { persist() }
        loadCurrentPath()
    }

    private fun updateCurrentPath(path: String) {
        val normalizedPath = normalizePath(path)
        _state.update {
            it.copy(
                currentPath = normalizedPath,
                error = null,
                restoredFocusIndex = null,
                inlineMessage = null,
                fastLocate = null,
                isFastLocateMode = false
            )
        }
        lastPersistedAnchor = null
        if (_state.value.mode == BrowseMode.NAS) nasBrowsePath = normalizedPath
        viewModelScope.launch {
            if (_state.value.mode == BrowseMode.LOCAL) {
                modeStore?.saveLocalPath(normalizedPath)
            } else {
                configStore.saveActiveBrowsePath(normalizedPath)
            }
        }
        loadCurrentPath()
    }

    private fun persistBrowseAnchor(entry: SmbEntry, preferredIndex: Int?) {
        val snapshot = _state.value
        if (snapshot.currentPath.isBlank() || snapshot.entries.isEmpty()) return

        val matchedIndex = snapshot.entries.indexOfFirst { it.fullPath == entry.fullPath }
        val realIndex = when {
            matchedIndex >= 0 -> matchedIndex
            preferredIndex != null &&
                preferredIndex in snapshot.entries.indices &&
                snapshot.entries[preferredIndex].fullPath == entry.fullPath -> preferredIndex
            else -> return
        }

        if (snapshot.restoredFocusIndex != realIndex) {
            _state.update { it.copy(restoredFocusIndex = realIndex) }
        }
        val anchorConnectionId = if (snapshot.mode == BrowseMode.LOCAL) {
            LOCAL_ANCHOR_NAMESPACE
        } else {
            resolveAnchorConnectionId(snapshot.activeConnectionId, snapshot.config)
        }
        val fingerprint = AnchorFingerprint(
            connectionId = anchorConnectionId,
            directoryPath = snapshot.currentPath,
            itemKey = entry.fullPath,
            index = realIndex
        )
        if (fingerprint == lastPersistedAnchor) return

        lastPersistedAnchor = fingerprint
        viewModelScope.launch {
            configStore.saveBrowseAnchor(
                connectionId = anchorConnectionId,
                directoryPath = snapshot.currentPath,
                anchor = BrowseFocusAnchor(
                    itemKey = entry.fullPath,
                    index = realIndex,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun resolveAnchorRestore(anchor: BrowseFocusAnchor?, entries: List<SmbEntry>): AnchorRestoreResult {
        if (anchor == null || entries.isEmpty()) return AnchorRestoreResult(index = null, message = null)

        val matchedIndex = entries.indexOfFirst { it.fullPath == anchor.itemKey }
        return if (matchedIndex >= 0) {
            AnchorRestoreResult(index = matchedIndex, message = null)
        } else {
            AnchorRestoreResult(
                index = anchor.index.coerceIn(0, entries.lastIndex),
                message = string(R.string.browser_directory_changed)
            )
        }
    }

    private fun normalizePath(path: String): String = path.trim().replace("\\", "/").trim('/')

    private fun resolveAnchorConnectionId(activeConnectionId: String?, config: SmbConfig): String {
        return activeConnectionId ?: buildTemporaryAnchorNamespace(config)
    }

    private fun failureLockKey(activeConnectionId: String?, config: SmbConfig): String =
        resolveAnchorConnectionId(activeConnectionId, config)

    private fun SmbFailure.shouldRetryConnection(): Boolean =
        this == SmbFailure.TIMEOUT || this == SmbFailure.HOST_UNREACHABLE

    private fun buildTemporaryAnchorNamespace(config: SmbConfig): String {
        val source = listOf(
            config.host.trim().lowercase(Locale.ROOT),
            config.share.trim().lowercase(Locale.ROOT),
            normalizePath(config.path).lowercase(Locale.ROOT),
            config.username.trim().lowercase(Locale.ROOT),
            config.guest.toString(),
            config.smb1Enabled.toString()
        ).joinToString(separator = "|")
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "temp-$hex"
    }

    private data class AnchorRestoreResult(
        val index: Int?,
        val message: String?
    )

    private data class AnchorFingerprint(
        val connectionId: String,
        val directoryPath: String,
        val itemKey: String,
        val index: Int
    )

    companion object {
        private const val LOCAL_ANCHOR_NAMESPACE = "local"
        private const val SMB_CONNECT_ATTEMPTS = 3
        private val SMB_CONNECT_BACKOFF_MS = longArrayOf(500L, 1_500L)

        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                return TvBrowserViewModel(
                    repository = JcifsSmbRepository(),
                    configStore = SmbConfigStore(appContext),
                    localRepository = LocalFileRepository(),
                    modeStore = BrowserModeStore(appContext),
                    appContext = appContext,
                ) as T
            }
        }
    }

    private fun hasLocalStorageAccess(): Boolean {
        val context = appContext ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun string(resId: Int, vararg args: Any): String {
        val value = appContext?.getString(resId, *args) ?: fallbackString(resId, *args)
        return value
    }

    private fun fallbackString(resId: Int, vararg args: Any): String {
        val template = when (resId) {
            R.string.smb_deleted_connection -> "Current connection deleted"
            R.string.smb_host_required -> "SMB host address cannot be empty"
            R.string.browser_smb_connection_failed -> "SMB connection failed: %1\$s"
            R.string.smb_error_auth_failed -> "SMB authentication failed. Check username and password"
            R.string.smb_error_host_unreachable -> "Server unreachable. Check the network or host address"
            R.string.smb_error_share_not_found -> "Share not found. Check the NAS share settings"
            R.string.smb_error_invalid_path -> "Invalid path. Check the subfolder setting"
            R.string.smb_error_timeout -> "Connection timed out. Try again later"
            R.string.smb_error_unknown -> "SMB browsing failed. Check the configuration and try again"
            R.string.playback_locate_failed -> "Could not locate the current playback folder"
            R.string.browser_all_shares_label -> "all shares"
            R.string.browser_directory_changed -> "Folder contents changed. Returned to the top"
            else -> ""
        }
        return if (args.isEmpty()) template else String.format(Locale.US, template, *args)
    }
}
