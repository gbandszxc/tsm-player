package com.github.gbandszxc.tvmediaplayer.history

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

data class PlayHistoryTrack(
    val id: String,
    val mediaId: String,
    val streamUri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val sourceConnectionId: String?,
    val sourceConfig: SmbConfig?,
    val playedAt: Long,
)

data class PlayHistoryPage(
    val items: List<PlayHistoryTrack>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
)
