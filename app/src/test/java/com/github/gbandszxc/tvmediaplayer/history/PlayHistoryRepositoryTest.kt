package com.github.gbandszxc.tvmediaplayer.history

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: PlayHistoryRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
        repository = PlayHistoryRepository(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
    }

    @Test
    fun `upsert deduplicates by source aware track key and refreshes played time`() {
        repository.record(sampleTrack(mediaId = "Music/A.flac", title = "Old", playedAt = 1_000L))

        repository.record(sampleTrack(mediaId = "Music/A.flac", title = "New", playedAt = 2_000L))

        val tracks = repository.query(page = 0, query = "")
        assertEquals(1, tracks.totalCount)
        assertEquals("New", tracks.items.single().title)
        assertEquals(2_000L, tracks.items.single().playedAt)
    }

    @Test
    fun `query returns newest first and paginates fifty per page`() {
        repeat(55) { index ->
            repository.record(sampleTrack(mediaId = "Music/$index.flac", title = "Track $index", playedAt = index.toLong()))
        }

        val firstPage = repository.query(page = 0, query = "")
        val secondPage = repository.query(page = 1, query = "")

        assertEquals(55, firstPage.totalCount)
        assertEquals(50, firstPage.items.size)
        assertEquals("Track 54", firstPage.items.first().title)
        assertEquals(5, secondPage.items.size)
        assertEquals("Track 4", secondPage.items.first().title)
    }

    @Test
    fun `record keeps only newest one thousand tracks`() {
        repeat(1_005) { index ->
            repository.record(sampleTrack(mediaId = "Music/$index.flac", title = "Track $index", playedAt = index.toLong()))
        }

        val page = repository.query(page = 19, query = "")

        assertEquals(1_000, page.totalCount)
        assertEquals(50, page.items.size)
        assertTrue(page.items.none { it.title == "Track 0" })
        assertEquals("Track 5", page.items.last().title)
    }

    @Test
    fun `search matches filename title artist and album`() {
        repository.record(sampleTrack(mediaId = "Music/hello-file.flac", title = "Song", artist = "Singer", album = "Album", playedAt = 1L))
        repository.record(sampleTrack(mediaId = "Music/other.flac", title = "夏の歌", artist = "Band", album = "Blue", playedAt = 2L))
        repository.record(sampleTrack(mediaId = "Music/third.flac", title = "Third", artist = "Yellow Artist", album = "Gold", playedAt = 3L))
        repository.record(sampleTrack(mediaId = "Music/fourth.flac", title = "Fourth", artist = "Someone", album = "夜间专辑", playedAt = 4L))

        assertEquals(listOf("Song"), repository.query(0, "hello-file").items.map { it.title })
        assertEquals(listOf("夏の歌"), repository.query(0, "夏").items.map { it.title })
        assertEquals(listOf("Third"), repository.query(0, "yellow").items.map { it.title })
        assertEquals(listOf("Fourth"), repository.query(0, "夜间").items.map { it.title })
    }

    private fun sampleTrack(
        mediaId: String,
        title: String,
        artist: String? = null,
        album: String? = null,
        playedAt: Long,
        sourceConfig: SmbConfig? = SmbConfig("nas", "Media", "Music", "", "", true, false),
    ): PlayHistoryTrack =
        PlayHistoryTrack(
            id = "",
            mediaId = mediaId,
            streamUri = "smb://nas/Media/$mediaId",
            title = title,
            artist = artist,
            album = album,
            artworkUri = null,
            sourceConnectionId = null,
            sourceConfig = sourceConfig,
            playedAt = playedAt,
        )
}
