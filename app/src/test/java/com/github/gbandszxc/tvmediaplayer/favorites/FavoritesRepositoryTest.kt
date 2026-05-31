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

        assertTrue(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertFalse(
            repository.containsTrack(
                FavoritesRepository.DEFAULT_PLAYLIST_ID,
                track.copy(mediaId = "Music/B.flac")
            )
        )
        assertEquals(1, repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).size)
    }

    @Test
    fun `same track can exist in different playlists`() {
        val custom = repository.createPlaylist("通勤") ?: error("playlist should be created")
        val track = sampleTrack(mediaId = "Music/A.flac", title = "A")

        assertTrue(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertTrue(repository.addTrack(custom.id, track))

        assertTrue(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertTrue(repository.containsTrack(custom.id, track))
        assertEquals(1, repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).size)
        assertEquals(1, repository.getTracks(custom.id).size)
    }

    @Test
    fun `same media id from different sources can coexist and is removed independently`() {
        val nasA = sampleTrack(
            id = "nas-a",
            mediaId = "Music/A.flac",
            streamUri = "smb://nas-a/Media/Music/A.flac",
            sourceConfig = sampleConfig(host = "nas-a")
        )
        val nasB = sampleTrack(
            id = "nas-b",
            mediaId = "Music/A.flac",
            streamUri = "smb://nas-b/Media/Music/A.flac",
            sourceConfig = sampleConfig(host = "nas-b")
        )

        assertTrue(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, nasA))
        assertTrue(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, nasB))

        assertTrue(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, nasA))
        assertTrue(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, nasB))
        assertEquals(2, repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).size)

        assertTrue(repository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, nasA))

        assertFalse(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, nasA))
        assertTrue(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, nasB))
        assertEquals("nas-b", repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).single().sourceConfig?.host)
    }

    @Test
    fun `remove track only removes playlist record`() {
        val custom = repository.createPlaylist("通勤") ?: error("playlist should be created")
        val track = sampleTrack(mediaId = "Music/A.flac", title = "A")
        repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track)
        repository.addTrack(custom.id, track)

        assertTrue(repository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))

        assertFalse(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertTrue(repository.containsTrack(custom.id, track))
        assertFalse(repository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
    }

    @Test
    fun `add track returns false for missing playlist and does not insert orphan record`() {
        val track = sampleTrack(mediaId = "Music/Orphan.flac", title = "Orphan")

        assertFalse(repository.addTrack("missing-playlist", track))

        assertFalse(repository.containsTrack("missing-playlist", track))
        assertTrue(repository.getTracks("missing-playlist").isEmpty())
    }

    @Test
    fun `add track updates playlist timestamp only when inserted`() {
        val custom = repository.createPlaylist("晨间") ?: error("playlist should be created")
        val beforeInsert = repository.getPlaylists().first { it.id == custom.id }.updatedAt
        Thread.sleep(5L)

        assertTrue(repository.addTrack(custom.id, sampleTrack(mediaId = "Music/A.flac", title = "A")))
        val afterInsert = repository.getPlaylists().first { it.id == custom.id }.updatedAt
        Thread.sleep(5L)

        assertFalse(repository.addTrack(custom.id, sampleTrack(mediaId = "Music/A.flac", title = "A")))
        val afterDuplicate = repository.getPlaylists().first { it.id == custom.id }.updatedAt

        assertTrue(afterInsert > beforeInsert)
        assertEquals(afterInsert, afterDuplicate)
    }

    @Test
    fun `remove track updates playlist timestamp only when deleted`() {
        val custom = repository.createPlaylist("午后") ?: error("playlist should be created")
        val track = sampleTrack(mediaId = "Music/A.flac", title = "A")
        repository.addTrack(custom.id, track)
        val beforeDelete = repository.getPlaylists().first { it.id == custom.id }.updatedAt
        Thread.sleep(5L)

        assertTrue(repository.removeTrack(custom.id, track))
        val afterDelete = repository.getPlaylists().first { it.id == custom.id }.updatedAt
        Thread.sleep(5L)

        assertFalse(repository.removeTrack(custom.id, track))
        val afterMissingDelete = repository.getPlaylists().first { it.id == custom.id }.updatedAt

        assertTrue(afterDelete > beforeDelete)
        assertEquals(afterDelete, afterMissingDelete)
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
    fun `playlist cover falls back to most recent track that actually has artwork`() {
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
                artworkUri = null,
                addedAt = 2_000L
            )
        )

        val playlist = repository.getPlaylists().first()

        assertEquals("content://art/a", playlist.coverArtworkUri)
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

    @Test
    fun `legacy database upgrade keeps tracks and allows same path from another source`() {
        createLegacyV1Database()
        repository = FavoritesRepository(context)

        val restored = repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).single()
        assertEquals("nas-a", restored.sourceConfig?.host)

        assertTrue(
            repository.addTrack(
                FavoritesRepository.DEFAULT_PLAYLIST_ID,
                sampleTrack(
                    id = "nas-b",
                    mediaId = "Music/A.flac",
                    streamUri = "smb://nas-b/Media/Music/A.flac",
                    sourceConfig = sampleConfig(host = "nas-b")
                )
            )
        )

        assertEquals(2, repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).size)
    }

    private fun createLegacyV1Database() {
        context.openOrCreateDatabase(FavoritesDbHelper.DB_NAME, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE playlists (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    is_default INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE playlist_tracks (
                    id TEXT PRIMARY KEY,
                    playlist_id TEXT NOT NULL,
                    media_id TEXT NOT NULL,
                    stream_uri TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT,
                    album TEXT,
                    artwork_uri TEXT,
                    source_connection_id TEXT,
                    source_host TEXT,
                    source_share TEXT,
                    source_path TEXT,
                    source_username TEXT,
                    source_password TEXT,
                    source_guest INTEGER,
                    source_smb1 INTEGER,
                    added_at INTEGER NOT NULL,
                    UNIQUE(playlist_id, media_id),
                    FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX idx_playlist_tracks_playlist_added
                ON playlist_tracks(playlist_id, added_at DESC)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO playlists (id, name, is_default, created_at, updated_at)
                VALUES ('default_favorites', '收藏夹', 1, 1000, 1000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO playlist_tracks (
                    id, playlist_id, media_id, stream_uri, title,
                    source_host, source_share, source_path, source_username,
                    source_guest, source_smb1, added_at
                )
                VALUES (
                    'legacy-a', 'default_favorites', 'Music/A.flac',
                    'smb://nas-a/Media/Music/A.flac', 'A',
                    'nas-a', 'Media', 'Music', '',
                    1, 0, 1000
                )
                """.trimIndent()
            )
            db.version = 1
        }
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
