package com.github.gbandszxc.tvmediaplayer.favorites

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

object FavoriteTrackIdentity {
    private const val SEPARATOR = "\u001F"

    fun keyOf(track: FavoriteTrack): String = keyOf(track.mediaId, track.sourceConfig)

    fun keyOf(mediaId: String, sourceConfig: SmbConfig?): String =
        if (sourceConfig == null || sourceConfig.host.isBlank()) {
            listOf("local", mediaId).joinToString(SEPARATOR)
        } else {
            listOf(
                "smb",
                sourceConfig.host,
                sourceConfig.share,
                sourceConfig.path,
                sourceConfig.username,
                if (sourceConfig.guest) "1" else "0",
                if (sourceConfig.smb1Enabled) "1" else "0",
                mediaId,
            ).joinToString(SEPARATOR)
        }
}
