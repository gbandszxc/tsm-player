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
            throw IllegalArgumentException("SMB 主机地址和共享名不能为空")
        }

        val nowPath = path.trim('/').ifBlank { config.normalizedPath() }
        val prefix = if (nowPath.isBlank()) "" else "$nowPath/"
        val demoTrack1 = "01 - 序章.flac"
        val demoTrack2 = "02 - 主题.mp3"
        return listOf(
            SmbEntry(name = "..", fullPath = nowPath, isDirectory = true),
            SmbEntry(name = "ACG", fullPath = "${prefix}ACG", isDirectory = true, lastModifiedAt = 1_717_000_000_000),
            SmbEntry(name = "古典音乐", fullPath = "${prefix}古典音乐", isDirectory = true, lastModifiedAt = 1_716_000_000_000),
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
