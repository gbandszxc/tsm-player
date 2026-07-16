package com.github.gbandszxc.tvmediaplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import java.io.File

class LocalMediaItemFactory {
    fun create(queue: List<SmbEntry>): List<MediaItem> = queue.map { entry ->
        val file = File(requireNotNull(Uri.parse(entry.streamUri).path))
        val artwork = listOf("folder.jpg", "cover.jpg", "front.jpg")
            .map { File(file.parentFile, it) }
            .firstOrNull { it.isFile }
            ?.toURI()
            ?.toString()
        MediaItem.Builder()
            .setMediaId(entry.fullPath)
            .setUri(requireNotNull(entry.streamUri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(entry.name.substringBeforeLast('.', entry.name))
                    .setArtworkUri(artwork?.let(Uri::parse))
                    .build()
            )
            .build()
    }
}
