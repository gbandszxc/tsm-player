package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

object PlaybackMetadataFallbacks {
    fun titleFromMediaId(mediaId: String): String =
        mediaId.substringAfterLast('/').substringBeforeLast('.')

    fun artistFromConfig(config: SmbConfig?): String? =
        config?.share?.ifBlank { config.host }?.takeIf { it.isNotBlank() }

    fun albumFromMediaId(config: SmbConfig?, mediaId: String): String? {
        val parent = mediaId.substringBeforeLast('/', "")
        val fallback = config?.share?.ifBlank { "SMB" } ?: "SMB"
        return if (parent.isBlank()) fallback else parent.substringAfterLast('/')
    }
}
