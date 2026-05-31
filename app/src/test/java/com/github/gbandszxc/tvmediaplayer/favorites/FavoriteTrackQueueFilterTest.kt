package com.github.gbandszxc.tvmediaplayer.favorites

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteTrackQueueFilterTest {

    @Test
    fun `same source queue keeps only tracks matching selected source and remaps start index`() {
        val sourceA = SmbConfig("nas-a", "Music", "lossless", "alice", "secret-a", false, false)
        val sourceB = SmbConfig("nas-b", "Music", "lossless", "bob", "secret-b", false, true)
        val tracks = listOf(
            sampleTrack("a-1", sourceA),
            sampleTrack("b-1", sourceB),
            sampleTrack("a-2", sourceA),
            sampleTrack("none", null),
            sampleTrack("a-3", sourceA),
        )

        val result = FavoriteTrackQueueFilter.sameSourceQueue(tracks, selectedIndex = 2)

        assertEquals(listOf("a-1", "a-2", "a-3"), result.tracks.map { it.mediaId })
        assertEquals(1, result.startIndex)
        assertEquals(sourceA, result.sourceConfig)
    }

    @Test
    fun `same source queue keeps only null-source tracks when selected track has no source config`() {
        val source = SmbConfig("nas", "Music", "", "user", "password", false, false)
        val tracks = listOf(
            sampleTrack("with-source", source),
            sampleTrack("local-1", null),
            sampleTrack("local-2", null),
        )

        val result = FavoriteTrackQueueFilter.sameSourceQueue(tracks, selectedIndex = 1)

        assertEquals(listOf("local-1", "local-2"), result.tracks.map { it.mediaId })
        assertEquals(0, result.startIndex)
        assertNull(result.sourceConfig)
    }

    private fun sampleTrack(mediaId: String, sourceConfig: SmbConfig?): FavoriteTrack =
        FavoriteTrack(
            id = "id-$mediaId",
            playlistId = FavoritesRepository.DEFAULT_PLAYLIST_ID,
            mediaId = mediaId,
            streamUri = "smb://example/$mediaId",
            title = mediaId,
            artist = null,
            album = null,
            artworkUri = null,
            sourceConnectionId = null,
            sourceConfig = sourceConfig,
            addedAt = 1L,
        )
}
