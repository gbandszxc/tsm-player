package com.github.gbandszxc.tvmediaplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class LastPlaybackResumeRequest(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean
)

object LastPlaybackResumeBuilder {
    fun fromSnapshot(snapshot: LastPlaybackStore.Snapshot): LastPlaybackResumeRequest? {
        if (snapshot.queueUris.isEmpty()) return null
        val startIndex = snapshot.currentIndex.coerceIn(0, snapshot.queueUris.lastIndex)
        val mediaItems = snapshot.queueUris.mapIndexed { index, uri ->
            val mediaId = snapshot.queueMediaIds.getOrElse(index) { "" }
            val title = if (index == startIndex) {
                snapshot.title.ifBlank { PlaybackMetadataFallbacks.titleFromMediaId(mediaId) }
            } else {
                PlaybackMetadataFallbacks.titleFromMediaId(mediaId)
            }
            val artist = if (index == startIndex) {
                snapshot.artist.ifBlank { PlaybackMetadataFallbacks.artistFromConfig(snapshot.sourceConfig) }
            } else {
                PlaybackMetadataFallbacks.artistFromConfig(snapshot.sourceConfig)
            }
            val albumTitle = if (index == startIndex) {
                snapshot.albumTitle.ifBlank { PlaybackMetadataFallbacks.albumFromMediaId(snapshot.sourceConfig, mediaId) }
            } else {
                PlaybackMetadataFallbacks.albumFromMediaId(snapshot.sourceConfig, mediaId)
            }
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title.ifBlank { null })
                        .setArtist(artist?.ifBlank { null })
                        .setAlbumTitle(albumTitle?.ifBlank { null })
                        .build()
                )
                .build()
        }
        return LastPlaybackResumeRequest(
            mediaItems = mediaItems,
            startIndex = startIndex,
            positionMs = snapshot.positionMs,
            playWhenReady = true
        )
    }
}
