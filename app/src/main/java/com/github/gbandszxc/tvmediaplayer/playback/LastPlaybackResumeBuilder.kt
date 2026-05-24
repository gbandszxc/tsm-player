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
                snapshot.title.ifBlank { deriveTitle(mediaId) }
            } else {
                deriveTitle(mediaId)
            }
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title.ifBlank { null })
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

    private fun deriveTitle(mediaId: String): String =
        mediaId.substringAfterLast('/').substringBeforeLast('.')
}
