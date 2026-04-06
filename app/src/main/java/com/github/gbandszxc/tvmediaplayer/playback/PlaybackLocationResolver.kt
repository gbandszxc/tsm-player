package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

object PlaybackLocationResolver {

    data class Target(
        val mediaId: String?,
        val directoryPath: String,
        val sourceConnectionId: String?,
        val sourceConfig: SmbConfig
    ) {
        fun matchesConnection(activeConfig: SmbConfig, activeConnectionId: String?): Boolean {
            val targetId = sourceConnectionId?.trim()?.takeIf { it.isNotBlank() }
            if (targetId != null && targetId == activeConnectionId) return true
            return matchesConnection(activeConfig, sourceConfig)
        }
    }

    fun resolve(snapshot: LastPlaybackStore.Snapshot): Target? {
        val mediaId = deriveMediaId(snapshot)
        val directory = snapshot.currentDirectoryPath?.let(::normalizePath)
            ?: mediaId?.let(::parentDirectory)
            ?: return null
        return Target(
            mediaId = mediaId,
            directoryPath = directory,
            sourceConnectionId = snapshot.sourceConnectionId?.trim()?.takeIf { it.isNotBlank() },
            sourceConfig = snapshot.sourceConfig ?: SmbConfig.Empty
        )
    }

    fun fromSnapshot(snapshot: LastPlaybackStore.Snapshot): Target? = resolve(snapshot)

    fun matchesConnection(activeConfig: SmbConfig, targetConfig: SmbConfig): Boolean {
        if (targetConfig == SmbConfig.Empty) return false
        return activeConfig == targetConfig
    }

    fun normalizePath(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed
            .split('/', '\\')
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    fun parentDirectory(path: String?): String? {
        val normalized = normalizePath(path) ?: return null
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash) else ""
    }

    private fun deriveMediaId(snapshot: LastPlaybackStore.Snapshot): String? {
        return snapshot.currentMediaId?.let(::normalizePath)
            ?: snapshot.queueMediaIds.getOrNull(snapshot.currentIndex)?.let(::normalizePath)
            ?: snapshot.queueMediaIds.lastOrNull()?.let(::normalizePath)
    }
}
