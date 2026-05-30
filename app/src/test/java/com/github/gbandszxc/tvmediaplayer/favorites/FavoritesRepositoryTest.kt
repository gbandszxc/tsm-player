package com.github.gbandszxc.tvmediaplayer.favorites

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FavoritesRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: FavoritesRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
        repository = FavoritesRepository(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
    }

    @Test
    fun `default favorites playlist is created first`() {
        val custom = repository.createPlaylist("夜间播放")

        val playlists = repository.getPlaylists()

        assertNotNull(custom)
        assertEquals(FavoritesRepository.DEFAULT_PLAYLIST_ID, playlists.first().id)
        assertEquals("收藏夹", playlists.first().name)
        assertTrue(playlists.first().isDefault)
        assertEquals(listOf("收藏夹", "夜间播放"), playlists.map { it.name })
    }

    @Test
    fun `create playlist rejects blank and duplicate names`() {
        val created = repository.createPlaylist("  我的歌单  ")

        assertNull(repository.createPlaylist("   "))
        assertNull(repository.createPlaylist("我的歌单"))
        assertNotNull(created)
        assertEquals(listOf("收藏夹", "我的歌单"), repository.getPlaylists().map { it.name })
    }

    @Test
    fun `add track deduplicates per playlist and reports containment`() {
        val track = sampleTrack(mediaId = "Music/A.flac", title = "A")

        assertTrue(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertFalse(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track.copy(id = "track-a-duplicate")))

        assertTrue(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))
        assertFalse(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/B.flac"))
        assertEquals(1, repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).size)
    }

    @Test
    fun `same track can exist in different playlists`() {
        val custom = repository.createPlaylist("通勤") ?: error("playlist should be created")
        val track = sampleTrack(mediaId = "Music/A.flac", title = "A")

        assertTrue(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertTrue(repository.addTrack(custom.id, track.copy(id = "track-a-custom")))

        assertTrue(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))
        assertTrue(repository.containsTrack(custom.id, "Music/A.flac"))
        assertEquals(1, repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).size)
        assertEquals(1, repository.getTracks(custom.id).size)
    }

    @Test
    fun `remove track only removes playlist record`() {
        val custom = repository.createPlaylist("通勤") ?: error("playlist should be created")
        val track = sampleTrack(mediaId = "Music/A.flac", title = "A")
        repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track)
        repository.addTrack(custom.id, track.copy(id = "track-a-custom"))

        assertTrue(repository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))

        assertFalse(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))
        assertTrue(repository.containsTrack(custom.id, "Music/A.flac"))
        assertFalse(repository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))
    }

    @Test
    fun `playlist cover uses last added track artwork`() {
        repository.addTrack(
            FavoritesRepository.DEFAULT_PLAYLIST_ID,
            sampleTrack(
                id = "track-a",
                mediaId = "Music/A.flac",
                title = "A",
                artworkUri = "content://art/a",
                addedAt = 1_000L
            )
        )
        repository.addTrack(
            FavoritesRepository.DEFAULT_PLAYLIST_ID,
            sampleTrack(
                id = "track-b",
                mediaId = "Music/B.flac",
                title = "B",
                artworkUri = "content://art/b",
                addedAt = 2_000L
            )
        )

        val playlist = repository.getPlaylists().first()

        assertEquals("content://art/b", playlist.coverArtworkUri)
        assertEquals(2, playlist.trackCount)
    }

    @Test
    fun `tracks restore nullable source smb config fields`() {
        val expectedConfig = sampleConfig(
            host = "nas.local",
            share = "Music",
            path = "Albums",
            username = "alice",
            password = "secret",
            guest = false,
            smb1Enabled = true
        )
        repository.addTrack(
            FavoritesRepository.DEFAULT_PLAYLIST_ID,
            sampleTrack(
                mediaId = "Albums/A.flac",
                title = "A",
                artist = "Artist",
                album = "Album",
                sourceConnectionId = "conn-1",
                sourceConfig = expectedConfig
            )
        )

        val restored = repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).single()

        assertEquals(expectedConfig, restored.sourceConfig)
        assertEquals("conn-1", restored.sourceConnectionId)
        assertEquals("Artist", restored.artist)
        assertEquals("Album", restored.album)
    }

    private fun sampleTrack(
        id: String = "track-a",
        playlistId: String = FavoritesRepository.DEFAULT_PLAYLIST_ID,
        mediaId: String = "Music/A.flac",
        streamUri: String = "smb://nas/Music/A.flac",
        title: String = "Track A",
        artist: String? = null,
        album: String? = null,
        artworkUri: String? = null,
        sourceConnectionId: String? = null,
        sourceConfig: SmbConfig? = sampleConfig(),
        addedAt: Long = System.currentTimeMillis()
    ): FavoriteTrack =
        FavoriteTrack(
            id = id,
            playlistId = playlistId,
            mediaId = mediaId,
            streamUri = streamUri,
            title = title,
            artist = artist,
            album = album,
            artworkUri = artworkUri,
            sourceConnectionId = sourceConnectionId,
            sourceConfig = sourceConfig,
            addedAt = addedAt
        )

    private fun sampleConfig(
        host: String = "nas",
        share: String = "Media",
        path: String = "Music",
        username: String = "",
        password: String = "",
        guest: Boolean = true,
        smb1Enabled: Boolean = false
    ): SmbConfig =
        SmbConfig(
            host = host,
            share = share,
            path = path,
            username = username,
            password = password,
            guest = guest,
            smb1Enabled = smb1Enabled
        )
}
