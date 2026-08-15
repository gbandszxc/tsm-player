package com.github.gbandszxc.tvmediaplayer.playback

import android.net.Uri
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import java.util.concurrent.ConcurrentHashMap
import jcifs.CIFSContext
import jcifs.smb.SmbFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class SmbMediaItemFactory {

    /** 目录 → 同目录封面 URL 的记忆化缓存，避免滚动宫格时反复探测 SMB */
    private val directoryArtworkCache = ConcurrentHashMap<String, String?>()

    fun create(config: SmbConfig, queue: List<SmbEntry>): List<MediaItem> {
        val context = SmbContextFactory.build(config)
        return queue.map { entry ->
            val directoryPath = entry.fullPath.substringBeforeLast('/', "")
            val artworkUri = directoryArtworkCache.getOrPut(directoryPath) {
                resolveArtworkUri(config, directoryPath, context)
            }
            MediaItem.Builder()
                .setMediaId(entry.fullPath)
                .setUri(requireNotNull(entry.streamUri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(parseTitle(entry.name))
                        .setArtist(PlaybackMetadataFallbacks.artistFromConfig(config))
                        .setAlbumTitle(parseAlbum(config, entry.fullPath))
                        .setArtworkUri(artworkUri?.let(Uri::parse))
                        .build()
                )
                .build()
        }
    }

    internal fun parseTitle(fileName: String): String = fileName.substringBeforeLast('.', fileName)

    internal fun parseAlbum(config: SmbConfig, fullPath: String): String {
        return PlaybackMetadataFallbacks.albumFromMediaId(config, fullPath) ?: "SMB"
    }

    private fun resolveArtworkUri(config: SmbConfig, directoryPath: String, context: CIFSContext): String? {
        val directoryBase = buildDirectoryBase(config, directoryPath)
        val candidates = listOf(
            "folder.jpg", "folder.png",
            "cover.jpg", "cover.png",
            "front.jpg", "front.png",
        )
        for (candidate in candidates) {
            val artworkUrl = "$directoryBase/$candidate"
            runCatching {
                val file = SmbFile(artworkUrl, context)
                if (file.exists() && !file.isDirectory) {
                    return file.canonicalPath
                }
            }
        }
        return null
    }

    private fun buildDirectoryBase(config: SmbConfig, path: String): String {
        val hostBase = "smb://${config.host.trim()}".trimEnd('/')
        val share = config.share.trim()
        return when {
            share.isNotBlank() && path.isBlank() -> "$hostBase/$share"
            share.isNotBlank() -> "$hostBase/$share/$path"
            path.isBlank() -> hostBase
            else -> "$hostBase/$path"
        }
    }
}
