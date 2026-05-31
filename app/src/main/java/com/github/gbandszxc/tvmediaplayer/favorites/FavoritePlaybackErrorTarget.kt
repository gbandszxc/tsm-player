package com.github.gbandszxc.tvmediaplayer.favorites

object FavoritePlaybackErrorTarget {
    fun resolve(
        currentTracks: List<FavoriteTrack>,
        currentMediaItemIndex: Int,
        currentMediaId: String?,
        activeMediaIds: Set<String>,
    ): FavoriteTrack? {
        val mediaId = currentMediaId?.takeIf { it.isNotBlank() } ?: return null
        if (mediaId !in activeMediaIds) return null

        val track = currentTracks.getOrNull(currentMediaItemIndex) ?: return null
        return track.takeIf { it.mediaId == mediaId }
    }
}
