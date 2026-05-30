package com.github.gbandszxc.tvmediaplayer.favorites

data class FavoritePlaylist(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val coverArtworkUri: String?,
    val trackCount: Int
)
