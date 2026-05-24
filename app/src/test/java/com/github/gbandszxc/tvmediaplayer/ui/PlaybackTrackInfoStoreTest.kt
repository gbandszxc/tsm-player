package com.github.gbandszxc.tvmediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTrackInfoStoreTest {

    @Test
    fun `keeps tag info when same track is rendered again with fallback metadata`() {
        val store = PlaybackTrackInfoStore()
        val key = "smb://nas/Music/album/01.flac"

        store.remember(
            key,
            PlaybackTrackInfo(
                title = "Tag Title",
                artist = "Tag Artist",
                albumTitle = "Tag Album"
            )
        )

        val first = store.displayFor(
            key = key,
            fallbackTitle = "01",
            fallbackArtist = "Music",
            fallbackAlbumTitle = "album"
        )
        val second = store.displayFor(
            key = key,
            fallbackTitle = "01",
            fallbackArtist = "Music",
            fallbackAlbumTitle = "album"
        )

        assertEquals("Tag Title", first.title)
        assertEquals("Tag Artist", second.artist)
        assertEquals("Tag Album", second.albumTitle)
    }

    @Test
    fun `does not reuse previous tag info for another track`() {
        val store = PlaybackTrackInfoStore()
        store.remember(
            "smb://nas/Music/album/01.flac",
            PlaybackTrackInfo(
                title = "First Title",
                artist = "First Artist",
                albumTitle = "First Album"
            )
        )

        val display = store.displayFor(
            key = "smb://nas/Music/album/02.flac",
            fallbackTitle = "02",
            fallbackArtist = "Music",
            fallbackAlbumTitle = "album"
        )

        assertEquals("02", display.title)
        assertEquals("Music", display.artist)
        assertEquals("album", display.albumTitle)
    }
}
