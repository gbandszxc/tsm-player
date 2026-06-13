package com.github.gbandszxc.tvmediaplayer.data.repo

import android.net.Uri
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.domain.repo.SmbRepository
import kotlinx.coroutines.delay

class FakeSmbRepository : SmbRepository {
    override suspend fun list(config: SmbConfig, path: String): List<SmbEntry> {
        delay(180)
        if (config.host.isBlank() || config.share.isBlank()) {
            throw IllegalArgumentException("SMB host address and share name cannot be empty")
        }

        val nowPath = path.trim('/').ifBlank { config.normalizedPath() }
        val prefix = if (nowPath.isBlank()) "" else "$nowPath/"
        val demoTrack1 = "01 - Prologue.flac"
        val demoTrack2 = "02 - Theme.mp3"
        return listOf(
            SmbEntry(name = "..", fullPath = nowPath, isDirectory = true),
            SmbEntry(name = "ACG", fullPath = "${prefix}ACG", isDirectory = true, lastModifiedAt = 1_717_000_000_000),
            SmbEntry(name = "Classical Music", fullPath = "${prefix}Classical Music", isDirectory = true, lastModifiedAt = 1_716_000_000_000),
            SmbEntry(
                name = demoTrack1,
                fullPath = "${prefix}${demoTrack1}",
                isDirectory = false,
                streamUri = "${config.rootUrl()}/${Uri.encode(demoTrack1)}",
                sizeBytes = 26_634_854,
                lastModifiedAt = 1_716_000_000_000,
            ),
            SmbEntry(
                name = demoTrack2,
                fullPath = "${prefix}${demoTrack2}",
                isDirectory = false,
                streamUri = "${config.rootUrl()}/${Uri.encode(demoTrack2)}",
                sizeBytes = 8_234_000,
                lastModifiedAt = 1_715_000_000_000,
            )
        )
    }
}
