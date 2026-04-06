package com.github.gbandszxc.tvmediaplayer.ui

import com.github.gbandszxc.tvmediaplayer.data.repo.BrowserConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStoreState
import com.github.gbandszxc.tvmediaplayer.domain.model.SavedSmbConnection
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.domain.repo.SmbRepository
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

    private fun sampleConfig(path: String): SmbConfig =
        SmbConfig(
            host = "192.168.1.2",
            share = "Media",
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

        override suspend fun loadState(): SmbConfigStoreState = state

        override suspend fun saveConnection(connection: SavedSmbConnection, activate: Boolean) = Unit

        override suspend fun setActiveConnection(id: String) = Unit

        override suspend fun saveActiveBrowsePath(path: String) {
            savedBrowsePaths += path
        }

        override fun newConnectionId(): String = "generated-id"
    }
}
