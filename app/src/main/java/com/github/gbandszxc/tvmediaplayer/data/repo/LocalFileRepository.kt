package com.github.gbandszxc.tvmediaplayer.data.repo

import android.os.Environment
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFileRepository {
    suspend fun list(path: String): List<SmbEntry> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory().canonicalFile
        val directory = File(root, path.trim('/')).canonicalFile
        require(directory.path == root.path || directory.path.startsWith("${root.path}${File.separator}")) {
            "Local path is outside external storage"
        }
        require(directory.isDirectory && directory.canRead()) { "Local directory is unavailable" }

        directory.listFiles().orEmpty().mapNotNull { file ->
            val name = file.name.trim()
            if (name.isBlank() || file.isHidden) return@mapNotNull null
            when {
                file.isDirectory -> SmbEntry(
                    name = name,
                    fullPath = root.toPath().relativize(file.canonicalFile.toPath()).toString().replace('\\', '/'),
                    isDirectory = true,
                    lastModifiedAt = file.lastModified().takeIf { it > 0L },
                )
                isAudioFile(name) -> SmbEntry(
                    name = name,
                    fullPath = root.toPath().relativize(file.canonicalFile.toPath()).toString().replace('\\', '/'),
                    isDirectory = false,
                    streamUri = file.toURI().toString(),
                    sizeBytes = file.length(),
                    lastModifiedAt = file.lastModified().takeIf { it > 0L },
                )
                else -> null
            }
        }
    }

    private fun isAudioFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "aac", "m4a", "wav", "ogg")
    }
}
