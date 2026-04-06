package com.github.gbandszxc.tvmediaplayer.ui

import com.github.gbandszxc.tvmediaplayer.data.repo.BrowserConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStoreState
import com.github.gbandszxc.tvmediaplayer.domain.model.BrowseFocusAnchor
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
import org.junit.Assert.assertNull
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

    @Test
    fun `init restores focus index from browse anchor when item key matches`() = runTest(dispatcher) {
        val config = sampleConfig(path = "Music")
        val browsePath = "Music/Albums"
        val entries = listOf(
            SmbEntry(name = "Track 01", fullPath = "Music/Albums/Track 01.flac", isDirectory = false),
            SmbEntry(name = "Track 02", fullPath = "Music/Albums/Track 02.flac", isDirectory = false)
        )
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = config,
                activeConnectionId = "conn-1",
                savedConnections = listOf(SavedSmbConnection("conn-1", "NAS", config)),
                activeBrowsePath = browsePath
            ),
            anchors = mutableMapOf(
                ("conn-1" to browsePath) to BrowseFocusAnchor(
                    itemKey = "Music/Albums/Track 02.flac",
                    index = 1,
                    updatedAt = 10L
                )
            )
        )
        val repository = FakeSmbRepository(
            entriesByPath = mapOf(
                browsePath to entries
            )
        )

        val viewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.restoredFocusIndex)
        assertEquals(listOf("conn-1" to browsePath), store.loadedAnchors)
    }

    @Test
    fun `locate playback directory falls back to head when stored anchor conflicts with entries`() = runTest(dispatcher) {
        val config = sampleConfig(path = "Music")
        val browsePath = "Music/Albums"
        val entries = listOf(
            SmbEntry(name = "Track 01", fullPath = "Music/Albums/Track 01.flac", isDirectory = false),
            SmbEntry(name = "Track 02", fullPath = "Music/Albums/Track 02.flac", isDirectory = false)
        )
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = config,
                activeConnectionId = "conn-1",
                savedConnections = listOf(SavedSmbConnection("conn-1", "NAS", config)),
                activeBrowsePath = "Music"
            ),
            anchors = mutableMapOf(
                ("conn-1" to browsePath) to BrowseFocusAnchor(
                    itemKey = "Music/Albums/Track 99.flac",
                    index = 1,
                    updatedAt = 10L
                )
            )
        )
        val repository = FakeSmbRepository(
            entriesByPath = mapOf(
                "Music" to emptyList(),
                browsePath to entries
            )
        )
        val viewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()

        viewModel.locateToPlaybackDirectory(
            PlaybackLocationResolver.Target(
                mediaId = "Music/Albums/Track 02.flac",
                directoryPath = browsePath,
                sourceConnectionId = "conn-1",
                sourceConfig = config
            )
        )
        advanceUntilIdle()

        assertEquals(browsePath, viewModel.state.value.currentPath)
        assertEquals(0, viewModel.state.value.restoredFocusIndex)
        assertEquals("目录内容已变化，已回到开头", viewModel.state.value.inlineMessage)
    }

    @Test
    fun `clear browse cache removes stored anchor so focus is not restored`() = runTest(dispatcher) {
        val config = sampleConfig(path = "Music")
        val browsePath = "Music"
        val entries = listOf(
            SmbEntry(name = "Track 01", fullPath = "Music/Track 01.flac", isDirectory = false),
            SmbEntry(name = "Track 02", fullPath = "Music/Track 02.flac", isDirectory = false)
        )
        val store = FakeBrowserConfigStore(
            state = SmbConfigStoreState(
                activeConfig = config,
                activeConnectionId = "conn-1",
                savedConnections = listOf(SavedSmbConnection("conn-1", "NAS", config)),
                activeBrowsePath = browsePath
            )
        )
        val repository = FakeSmbRepository(
            entriesByPath = mapOf(
                browsePath to entries
            )
        )

        val firstViewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()
        firstViewModel.onItemFocused(index = 1, entry = entries[1])
        advanceUntilIdle()

        val secondViewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()
        assertEquals(1, secondViewModel.state.value.restoredFocusIndex)

        store.clearBrowseCache()

        val thirdViewModel = TvBrowserViewModel(repository, store)
        advanceUntilIdle()
        assertNull(thirdViewModel.state.value.restoredFocusIndex)
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

    private class FakeSmbRepository(
        private val entriesByPath: Map<String, List<SmbEntry>> = emptyMap()
    ) : SmbRepository {
        val requestedPaths = mutableListOf<String>()

        override suspend fun list(config: SmbConfig, path: String): List<SmbEntry> {
            requestedPaths += path
            return entriesByPath[path].orEmpty()
        }
    }

    private class FakeBrowserConfigStore(
        private val state: SmbConfigStoreState,
        private val anchors: MutableMap<Pair<String?, String>, BrowseFocusAnchor> = mutableMapOf()
    ) : BrowserConfigStore {
        val savedBrowsePaths = mutableListOf<String>()
        val activatedConnectionIds = mutableListOf<String>()
        val activatedConfigs = mutableListOf<SmbConfig>()
        val loadedAnchors = mutableListOf<Pair<String?, String>>()
        val savedAnchors = mutableListOf<Pair<Pair<String?, String>, BrowseFocusAnchor>>()

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

        override suspend fun loadBrowseAnchor(connectionId: String?, directoryPath: String): BrowseFocusAnchor? {
            val key = connectionId to directoryPath
            loadedAnchors += key
            return anchors[key]
        }

        override suspend fun saveBrowseAnchor(connectionId: String?, directoryPath: String, anchor: BrowseFocusAnchor) {
            val key = connectionId to directoryPath
            anchors[key] = anchor
            savedAnchors += key to anchor
        }

        override suspend fun clearBrowseCache() {
            anchors.clear()
        }

        override fun newConnectionId(): String = "generated-id"
    }
}
