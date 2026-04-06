package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.gbandszxc.tvmediaplayer.data.repo.BrowserConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.JcifsSmbRepository
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbFailureMapper
import com.github.gbandszxc.tvmediaplayer.domain.model.SavedSmbConnection
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.domain.repo.SmbRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLocationResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvBrowserState(
    val config: SmbConfig = SmbConfig.Empty,
    val savedConnections: List<SavedSmbConnection> = emptyList(),
    val activeConnectionId: String? = null,
    val currentPath: String = "",
    val entries: List<SmbEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val toast: String? = null
)

class TvBrowserViewModel(
    private val repository: SmbRepository,
    private val configStore: BrowserConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow(TvBrowserState())
    val state: StateFlow<TvBrowserState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = configStore.loadState()
            _state.update {
                it.copy(
                    config = loaded.activeConfig,
                    currentPath = loaded.activeBrowsePath,
                    savedConnections = loaded.savedConnections,
                    activeConnectionId = loaded.activeConnectionId
                )
            }
            if (loaded.activeConfig.host.isNotBlank()) {
                loadCurrentPath()
            }
        }
    }

    fun saveConfig(config: SmbConfig, name: String, saveAsNew: Boolean = false) {
        val id = if (saveAsNew) configStore.newConnectionId() else (_state.value.activeConnectionId ?: configStore.newConnectionId())
        val actualName = name.ifBlank { defaultConnectionName(config) }
        val saved = SavedSmbConnection(id = id, name = actualName, config = config)
        val rootPath = config.normalizedPath()

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
        loadCurrentPath()
    }

    fun switchConnection(connectionId: String) {
        val target = _state.value.savedConnections.firstOrNull { it.id == connectionId } ?: return
        val rootPath = target.config.normalizedPath()
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

    fun loadCurrentPath() {
        val snapshot = _state.value
        if (snapshot.config.host.isBlank()) {
            _state.update { it.copy(error = "SMB 主机地址不能为空") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, toast = null) }
            runCatching {
                repository.list(snapshot.config, snapshot.currentPath)
            }.onSuccess { list ->
                _state.update { it.copy(entries = list, loading = false) }
            }.onFailure { ex ->
                val message = SmbFailureMapper.toUserMessage(SmbFailureMapper.map(ex))
                _state.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun enterDirectory(entry: SmbEntry) {
        if (!entry.isDirectory) {
            _state.update { it.copy(toast = "待实现：播放 ${entry.name}") }
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

    fun navigateUp(): Boolean {
        if (_state.value.currentPath.isBlank()) return false
        updateCurrentPath(_state.value.currentPath.substringBeforeLast('/', ""))
        return true
    }

    fun locateToPlaybackDirectory(target: PlaybackLocationResolver.Target) {
        val targetPath = normalizePath(target.directoryPath)
        if (targetPath.isBlank()) {
            _state.update { it.copy(toast = "无法定位当前播放目录") }
            return
        }

        val stateSnapshot = _state.value
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
            _state.update { it.copy(toast = "无法定位当前播放目录") }
            return
        }

        applyLocatedPath(target.sourceConfig, null, targetPath) {
            configStore.setActiveConfig(target.sourceConfig, targetPath)
        }
    }

    private fun defaultConnectionName(config: SmbConfig): String {
        val share = config.share.ifBlank { "全部共享" }
        return "${config.host} / $share"
    }

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
                error = null
            )
        }
        viewModelScope.launch { persist() }
        loadCurrentPath()
    }

    private fun updateCurrentPath(path: String) {
        val normalizedPath = normalizePath(path)
        _state.update {
            it.copy(
                currentPath = normalizedPath,
                error = null
            )
        }
        viewModelScope.launch {
            configStore.saveActiveBrowsePath(normalizedPath)
        }
        loadCurrentPath()
    }

    private fun normalizePath(path: String): String = path.trim().replace("\\", "/").trim('/')

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                return TvBrowserViewModel(
                    repository = JcifsSmbRepository(),
                    configStore = SmbConfigStore(appContext)
                ) as T
            }
        }
    }
}
