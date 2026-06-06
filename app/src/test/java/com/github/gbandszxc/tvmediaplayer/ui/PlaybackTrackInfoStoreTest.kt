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

    @Test
    fun `uses media item metadata when player metadata is temporarily empty`() {
        val store = PlaybackTrackInfoStore()

        val display = PlaybackTrackInfoResolver.resolve(
            store = store,
            key = "smb://nas/Music/album/01.flac",
            mediaItemTitle = "01 - 夜に駆ける",
            mediaItemArtist = "YOASOBI",
            mediaItemAlbumTitle = "THE BOOK",
            playerTitle = "",
            playerArtist = "",
            playerAlbumTitle = ""
        )

        assertEquals("01 - 夜に駆ける", display.title)
        assertEquals("YOASOBI", display.artist)
        assertEquals("THE BOOK", display.albumTitle)
    }

    @Test
    fun `keeps remembered tag info when returning from another screen with empty metadata`() {
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

        val display = PlaybackTrackInfoResolver.resolve(
            store = store,
            key = key,
            mediaItemTitle = "",
            mediaItemArtist = "",
            mediaItemAlbumTitle = "",
            playerTitle = "",
            playerArtist = "",
            playerAlbumTitle = ""
        )

        assertEquals("Tag Title", display.title)
        assertEquals("Tag Artist", display.artist)
        assertEquals("Tag Album", display.albumTitle)
    }
}
