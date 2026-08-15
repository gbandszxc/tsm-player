package com.github.gbandszxc.tvmediaplayer.data.repo

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.domain.repo.SmbRepository
import com.github.gbandszxc.tvmediaplayer.playback.SmbContextFactory
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JcifsSmbRepository : SmbRepository {

    override suspend fun list(config: SmbConfig, path: String): List<SmbEntry> = withContext(Dispatchers.IO) {
        require(config.host.isNotBlank()) {
            "SMB host address cannot be empty"
        }

        val currentPath = path.trim('/').ifBlank { config.normalizedPath() }
        val target = resolveTarget(config, currentPath)
        val directory = SmbFile(target.url, SmbContextFactory.build(config))

        directory.listFiles()
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .mapNotNull { file -> mapToEntry(file, target.pathPrefix) }
    }

    private fun resolveTarget(config: SmbConfig, currentPath: String): TargetLocation {
        val host = config.host.trim()
        val configuredShare = config.share.trim()

        if (configuredShare.isNotBlank()) {
            val base = "smb://$host/$configuredShare".trimEnd('/')
            val url = if (currentPath.isBlank()) "$base/" else "$base/$currentPath/"
            return TargetLocation(url = url, pathPrefix = currentPath)
        }

        if (currentPath.isBlank()) {
            return TargetLocation(url = "smb://$host/", pathPrefix = "")
        }

        val dynamicShare = currentPath.substringBefore('/')
        val subPath = currentPath.substringAfter('/', "")
        val base = "smb://$host/$dynamicShare".trimEnd('/')
        val url = if (subPath.isBlank()) "$base/" else "$base/$subPath/"
        return TargetLocation(url = url, pathPrefix = currentPath)
    }

    private fun mapToEntry(file: SmbFile, currentPath: String): SmbEntry? {
        val name = file.name.trimEnd('/').trim()
        if (name.isBlank()) return null

        val fullPath = combinePath(currentPath, name)
        val lastModifiedAt = runCatching { file.lastModified() }.getOrNull()

        return if (file.isDirectory) {
            SmbEntry(
                name = name,
                fullPath = fullPath,
                isDirectory = true,
                sizeBytes = null,
                lastModifiedAt = lastModifiedAt,
            )
        } else {
            if (!isAudioFile(name)) return null
            SmbEntry(
                name = name,
                fullPath = fullPath,
                isDirectory = false,
                streamUri = file.canonicalPath,
                sizeBytes = runCatching { file.length() }.getOrNull(),
                lastModifiedAt = lastModifiedAt,
            )
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") ||
            lower.endsWith(".flac") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".ogg")
    }

    private fun combinePath(base: String, child: String): String =
        if (base.isBlank()) child else "$base/$child"

    private data class TargetLocation(val url: String, val pathPrefix: String)
}

