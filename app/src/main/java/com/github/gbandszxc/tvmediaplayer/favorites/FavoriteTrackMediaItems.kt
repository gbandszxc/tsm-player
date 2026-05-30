package com.github.gbandszxc.tvmediaplayer.favorites

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

object FavoriteTrackMediaItems {
    fun fromTracks(tracks: List<FavoriteTrack>): List<MediaItem> {
        return tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.mediaId)
                .setUri(track.streamUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build()
                )
                .build()
        }
    }
}
