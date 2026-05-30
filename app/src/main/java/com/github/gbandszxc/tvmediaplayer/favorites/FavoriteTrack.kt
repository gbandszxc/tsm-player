package com.github.gbandszxc.tvmediaplayer.favorites

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

data class FavoriteTrack(
    val id: String,
    val playlistId: String,
    val mediaId: String,
    val streamUri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val sourceConnectionId: String?,
    val sourceConfig: SmbConfig?,
    val addedAt: Long
)
