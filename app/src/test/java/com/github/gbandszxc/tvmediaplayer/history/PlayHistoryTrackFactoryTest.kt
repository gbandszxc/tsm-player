package com.github.gbandszxc.tvmediaplayer.history

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayHistoryTrackFactoryTest {

    @Test
    fun `creates history track from media item metadata and source config`() {
        val item = MediaItem.Builder()
            .setMediaId("Albums/01.flac")
            .setUri("smb://nas/Media/Albums/01.flac")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Tag Title")
                    .setArtist("Tag Artist")
                    .setAlbumTitle("Tag Album")
                    .setArtworkUri(Uri.parse("content://art/1"))
                    .build()
            )
            .build()
        val source = SmbConfig("nas", "Media", "Albums", "alice", "secret", false, true)

        val track = PlayHistoryTrackFactory.fromMediaItem(item, source, playedAt = 42L)

        assertEquals("Albums/01.flac", track.mediaId)
        assertEquals("smb://nas/Media/Albums/01.flac", track.streamUri)
        assertEquals("Tag Title", track.title)
        assertEquals("Tag Artist", track.artist)
        assertEquals("Tag Album", track.album)
        assertEquals("content://art/1", track.artworkUri)
        assertEquals(source, track.sourceConfig)
        assertEquals(42L, track.playedAt)
    }

    @Test
    fun `falls back to decoded filename when title metadata is blank`() {
        val item = MediaItem.Builder()
            .setMediaId("Albums/01%20Hello.flac")
            .setUri("smb://nas/Media/Albums/01%20Hello.flac")
            .build()

        val track = PlayHistoryTrackFactory.fromMediaItem(item, SmbConfig.Empty, playedAt = 1L)

        assertEquals("01 Hello.flac", track.title)
    }
}
