package com.example.tvmediaplayer.playback

import com.example.tvmediaplayer.domain.model.SmbEntry

object PlaybackQueueBuilder {
    fun fromDirectory(entries: List<SmbEntry>): List<SmbEntry> =
        entries.filter { !it.isDirectory && !it.streamUri.isNullOrBlank() }

    fun startIndex(queue: List<SmbEntry>, target: SmbEntry): Int =
        queue.indexOfFirst { it.fullPath == target.fullPath }.coerceAtLeast(0)
}

