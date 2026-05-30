package com.github.gbandszxc.tvmediaplayer.favorites

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteTrackMediaItemsTest {

    @Test
    fun `media item preserves id uri metadata and artwork`() {
        val item = FavoriteTrackMediaItems.fromTracks(listOf(sampleTrack())).single()

        assertEquals("Music/A.flac", item.mediaId)
        assertEquals("smb://nas/Music/A.flac", item.localConfiguration?.uri.toString())
        assertEquals("A", item.mediaMetadata.title.toString())
        assertEquals("artist", item.mediaMetadata.artist.toString())
        assertEquals("album", item.mediaMetadata.albumTitle.toString())
        assertEquals("smb://nas/cover.jpg", item.mediaMetadata.artworkUri.toString())
    }

    private fun sampleTrack(): FavoriteTrack =
        FavoriteTrack(
            id = "id-1",
            playlistId = FavoritesRepository.DEFAULT_PLAYLIST_ID,
            mediaId = "Music/A.flac",
            streamUri = "smb://nas/Music/A.flac",
            title = "A",
            artist = "artist",
            album = "album",
            artworkUri = "smb://nas/cover.jpg",
            sourceConnectionId = "conn-1",
            sourceConfig = SmbConfig("nas", "Music", "", "", "", true, false),
            addedAt = 1L
        )
}
