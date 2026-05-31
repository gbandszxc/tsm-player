package com.github.gbandszxc.tvmediaplayer.favorites

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

object FavoriteTrackQueueFilter {
    data class Result(
        val tracks: List<FavoriteTrack>,
        val startIndex: Int,
        val sourceConfig: SmbConfig?,
    )

    fun sameSourceQueue(tracks: List<FavoriteTrack>, selectedIndex: Int): Result {
        if (tracks.isEmpty()) return Result(emptyList(), 0, null)

        val safeSelectedIndex = selectedIndex.coerceIn(0, tracks.lastIndex)
        val selectedTrack = tracks[safeSelectedIndex]
        val selectedSource = selectedTrack.sourceConfig
        val filteredTracks = tracks.filter { it.sourceConfig == selectedSource }
        val filteredStartIndex = filteredTracks.indexOfFirst { it.id == selectedTrack.id }
            .takeIf { it >= 0 }
            ?: 0

        return Result(
            tracks = filteredTracks,
            startIndex = filteredStartIndex,
            sourceConfig = selectedSource,
        )
    }
}
