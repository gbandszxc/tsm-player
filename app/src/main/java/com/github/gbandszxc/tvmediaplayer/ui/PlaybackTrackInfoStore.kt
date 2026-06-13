package com.github.gbandszxc.tvmediaplayer.ui

data class PlaybackTrackInfo(
    val title: String?,
    val artist: String?,
    val albumTitle: String?
)

class PlaybackTrackInfoStore {
    private val tagsByKey = mutableMapOf<String, PlaybackTrackInfo>()

    fun remember(key: String, info: PlaybackTrackInfo) {
        tagsByKey[key] = info
    }

    fun displayFor(
        key: String?,
        fallbackTitle: String,
        fallbackArtist: String,
        fallbackAlbumTitle: String
    ): PlaybackTrackInfo {
        val tag = key?.let(tagsByKey::get)
        return PlaybackTrackInfo(
            title = tag?.title.takeUnless { it.isNullOrBlank() } ?: fallbackTitle,
            artist = tag?.artist.takeUnless { it.isNullOrBlank() } ?: fallbackArtist,
            albumTitle = tag?.albumTitle.takeUnless { it.isNullOrBlank() } ?: fallbackAlbumTitle
        )
    }

    companion object {
        val shared: PlaybackTrackInfoStore = PlaybackTrackInfoStore()
    }
}

object PlaybackTrackInfoResolver {
    fun resolve(
        store: PlaybackTrackInfoStore,
        key: String?,
        mediaItemTitle: String?,
        mediaItemArtist: String?,
        mediaItemAlbumTitle: String?,
        playerTitle: String?,
        playerArtist: String?,
        playerAlbumTitle: String?,
        emptyTitleFallback: String = "Nothing playing",
    ): PlaybackTrackInfo {
        val fallbackTitle = firstNotBlank(mediaItemTitle, playerTitle) ?: emptyTitleFallback
        val fallbackArtist = firstNotBlank(mediaItemArtist, playerArtist) ?: "-"
        val fallbackAlbumTitle = firstNotBlank(mediaItemAlbumTitle, playerAlbumTitle) ?: "-"
        return store.displayFor(
            key = key,
            fallbackTitle = fallbackTitle,
            fallbackArtist = fallbackArtist,
            fallbackAlbumTitle = fallbackAlbumTitle
        )
    }

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }
}
