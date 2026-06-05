package com.github.gbandszxc.tvmediaplayer.history

import android.net.Uri
import androidx.media3.common.MediaItem
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

object PlayHistoryTrackFactory {

    fun fromMediaItem(
        mediaItem: MediaItem,
        sourceConfig: SmbConfig?,
        playedAt: Long = System.currentTimeMillis(),
    ): PlayHistoryTrack {
        val mediaId = mediaItem.mediaId
        val streamUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val metadata = mediaItem.mediaMetadata
        val title = metadata.title?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: displayFileName(mediaId.ifBlank { streamUri })

        return PlayHistoryTrack(
            id = "",
            mediaId = mediaId,
            streamUri = streamUri,
            title = title,
            artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() },
            album = metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            artworkUri = metadata.artworkUri?.toString()?.takeIf { it.isNotBlank() },
            sourceConnectionId = null,
            sourceConfig = sourceConfig?.takeIf { it.host.isNotBlank() },
            playedAt = playedAt,
        )
    }

    fun displayFileName(pathOrUri: String): String {
        val trimmed = pathOrUri.trim().trimEnd('/')
        val rawName = trimmed.substringAfterLast('/').ifBlank { trimmed }
        return Uri.decode(rawName).orEmpty().ifBlank { pathOrUri }
    }
}
