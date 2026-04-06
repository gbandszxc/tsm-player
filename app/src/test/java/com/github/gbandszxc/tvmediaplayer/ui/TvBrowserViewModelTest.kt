package com.github.gbandszxc.tvmediaplayer.ui

import com.github.gbandszxc.tvmediaplayer.data.repo.BrowserConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStoreState
import com.github.gbandszxc.tvmediaplayer.domain.model.SavedSmbConnection
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.domain.repo.SmbRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLocationResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvBrowserViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init restores persisted browse path instead of smb root path`() = runTest(dispatcher) {
        val config = sampleConfig(path = "Music")
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = config,
                activeConnectionId = "conn-1",
                savedConnections = listOf(SavedSmbConnection("conn-1", "NAS", config)),
                activeBrowsePath = "Music/Albums"
            )
        )
        val repository = FakeSmbRepository()

        val viewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()

        assertEquals("Music/Albums", viewModel.state.value.currentPath)
        assertEquals(listOf("Music/Albums"), repository.requestedPaths)
    }

    @Test
    fun `enter directory persists new browse path`() = runTest(dispatcher) {
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = sampleConfig(path = "Music"),
                activeConnectionId = "conn-1",
                savedConnections = emptyList(),
                activeBrowsePath = "Music"
            )
        )
        val repository = FakeSmbRepository()
        val viewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()

        viewModel.enterDirectory(SmbEntry(name = "Albums", fullPath = "Music/Albums", isDirectory = true))
        advanceUntilIdle()

        assertEquals("Music/Albums", viewModel.state.value.currentPath)
        assertTrue(store.savedBrowsePaths.contains("Music/Albums"))
        assertEquals(listOf("Music", "Music/Albums"), repository.requestedPaths)
    }

    @Test
    fun `enter parent directory returns parent path and persists it`() = runTest(dispatcher) {
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = sampleConfig(path = "Music"),
                activeConnectionId = "conn-1",
                savedConnections = emptyList(),
                activeBrowsePath = "Music/Albums/Disc1"
            )
        )
        val repository = FakeSmbRepository()
        val viewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()

        viewModel.enterDirectory(SmbEntry(name = "..", fullPath = "Music/Albums/Disc1", isDirectory = true))
        advanceUntilIdle()

        assertEquals("Music/Albums", viewModel.state.value.currentPath)
        assertTrue(store.savedBrowsePaths.contains("Music/Albums"))
        assertEquals(listOf("Music/Albums/Disc1", "Music/Albums"), repository.requestedPaths)
    }

    @Test
    fun `locate to playback directory reuses current connection when target matches active connection`() = runTest(dispatcher) {
        val activeConfig = sampleConfig(path = "Music")
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = activeConfig,
                activeConnectionId = "conn-1",
                savedConnections = listOf(SavedSmbConnection("conn-1", "NAS", activeConfig)),
                activeBrowsePath = "Music"
            )
        )
        val repository = FakeSmbRepository()
        val viewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()

        viewModel.locateToPlaybackDirectory(
            PlaybackLocationResolver.Target(
                mediaId = "Music/Albums/Track 01.flac",
                directoryPath = "Music/Albums",
                sourceConnectionId = "conn-1",
                sourceConfig = activeConfig
            )
        )
        advanceUntilIdle()

        assertEquals("Music/Albums", viewModel.state.value.currentPath)
        assertEquals(listOf("Music", "Music/Albums"), repository.requestedPaths)
        assertEquals(emptyList<String>(), store.activatedConnectionIds)
        assertEquals(emptyList<SmbConfig>(), store.activatedConfigs)
        assertTrue(store.savedBrowsePaths.contains("Music/Albums"))
    }

    @Test
    fun `locate to playback directory switches saved connection when target belongs to another smb`() = runTest(dispatcher) {
        val currentConfig = sampleConfig(path = "Music")
        val otherConfig = sampleConfig(host = "192.168.1.9", share = "Archive", path = "HiRes")
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = currentConfig,
                activeConnectionId = "conn-1",
                savedConnections = listOf(
                    SavedSmbConnection("conn-1", "NAS", currentConfig),
                    SavedSmbConnection("conn-2", "Archive", otherConfig)
                ),
                activeBrowsePath = "Music"
            )
        )
        val repository = FakeSmbRepository()
        val viewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()

        viewModel.locateToPlaybackDirectory(
            PlaybackLocationResolver.Target(
                mediaId = "HiRes/Artist/Track 01.flac",
                directoryPath = "HiRes/Artist",
                sourceConnectionId = "conn-2",
                sourceConfig = otherConfig
            )
        )
        advanceUntilIdle()

        assertEquals(otherConfig, viewModel.state.value.config)
        assertEquals("conn-2", viewModel.state.value.activeConnectionId)
        assertEquals("HiRes/Artist", viewModel.state.value.currentPath)
        assertEquals(listOf("conn-2"), store.activatedConnectionIds)
        assertTrue(store.savedBrowsePaths.contains("HiRes/Artist"))
        assertEquals(listOf("Music", "HiRes/Artist"), repository.requestedPaths)
    }

    @Test
    fun `locate to playback directory falls back to source config when saved connection no longer exists`() = runTest(dispatcher) {
        val currentConfig = sampleConfig(path = "Music")
        val snapshotConfig = sampleConfig(host = "192.168.1.9", share = "Archive", path = "HiRes")
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = currentConfig,
                activeConnectionId = "conn-1",
                savedConnections = listOf(SavedSmbConnection("conn-1", "NAS", currentConfig)),
                activeBrowsePath = "Music"
            )
        )
        val repository = FakeSmbRepository()
        val viewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()

        viewModel.locateToPlaybackDirectory(
            PlaybackLocationResolver.Target(
                mediaId = "HiRes/Artist/Track 01.flac",
                directoryPath = "HiRes/Artist",
                sourceConnectionId = "conn-missing",
                sourceConfig = snapshotConfig
            )
        )
        advanceUntilIdle()

        assertEquals(snapshotConfig, viewModel.state.value.config)
        assertEquals(null, viewModel.state.value.activeConnectionId)
        assertEquals("HiRes/Artist", viewModel.state.value.currentPath)
        assertEquals(listOf(snapshotConfig), store.activatedConfigs)
        assertTrue(store.savedBrowsePaths.contains("HiRes/Artist"))
        assertEquals(listOf("Music", "HiRes/Artist"), repository.requestedPaths)
    }

    private fun sampleConfig(
        host: String = "192.168.1.2",
        share: String = "Media",
        path: String
    ): SmbConfig =
        SmbConfig(
            host = host,
            share = share,
            path = path,
            username = "",
            password = "",
            guest = true
        )

    private class FakeSmbRepository : SmbRepository {
        val requestedPaths = mutableListOf<String>()

        override suspend fun list(config: SmbConfig, path: String): List<SmbEntry> {
            requestedPaths += path
            return emptyList()
        }
    }

    private class FakeBrowserConfigStore(
        private val state: SmbConfigStoreState
    ) : BrowserConfigStore {
        val savedBrowsePaths = mutableListOf<String>()
        val activatedConnectionIds = mutableListOf<String>()
        val activatedConfigs = mutableListOf<SmbConfig>()

        override suspend fun loadState(): SmbConfigStoreState = state

        override suspend fun saveConnection(connection: SavedSmbConnection, activate: Boolean) = Unit

        override suspend fun setActiveConnection(id: String) {
            activatedConnectionIds += id
        }

        override suspend fun setActiveConfig(config: SmbConfig, browsePath: String) {
            activatedConfigs += config
            savedBrowsePaths += browsePath
        }

        override suspend fun saveActiveBrowsePath(path: String) {
            savedBrowsePaths += path
        }

        override fun newConnectionId(): String = "generated-id"
    }
}
