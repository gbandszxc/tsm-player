package com.github.gbandszxc.tvmediaplayer.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritePlaybackErrorTargetTest {

    @Test
    fun `resolve returns indexed track when current media id matches active favorite session`() {
        val tracks = listOf(sampleTrack("a"), sampleTrack("b"), sampleTrack("c"))

        val result = FavoritePlaybackErrorTarget.resolve(
            currentTracks = tracks,
            currentMediaItemIndex = 1,
            currentMediaId = "b",
            activeMediaIds = setOf("a", "b", "c"),
        )

        assertEquals("b", result?.mediaId)
    }

    @Test
    fun `resolve ignores indexed favorite track when player media id differs`() {
        val tracks = listOf(sampleTrack("a"), sampleTrack("b"))

        val result = FavoritePlaybackErrorTarget.resolve(
            currentTracks = tracks,
            currentMediaItemIndex = 1,
            currentMediaId = "external-track",
            activeMediaIds = setOf("a", "b"),
        )

        assertNull(result)
    }

    @Test
    fun `resolve ignores media id outside active favorite playback session`() {
        val tracks = listOf(sampleTrack("a"), sampleTrack("b"))

        val result = FavoritePlaybackErrorTarget.resolve(
            currentTracks = tracks,
            currentMediaItemIndex = 1,
            currentMediaId = "b",
            activeMediaIds = setOf("external-track"),
        )

        assertNull(result)
    }

    private fun sampleTrack(mediaId: String): FavoriteTrack =
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
            sourceConfig = null,
            addedAt = 1L,
        )
}
