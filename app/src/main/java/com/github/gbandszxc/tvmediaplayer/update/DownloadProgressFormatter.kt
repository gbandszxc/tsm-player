package com.github.gbandszxc.tvmediaplayer.update

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.R

data class DownloadProgressState(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
)

object DownloadProgressFormatter {
    fun formatSpeed(state: DownloadProgressState): String {
        if (state.speedBytesPerSecond <= 0L) return "-- MB/s"
        return "${formatMb(state.speedBytesPerSecond)} MB/s"
    }

    fun formatBytes(state: DownloadProgressState): String {
        val downloaded = formatMb(state.downloadedBytes)
        return if (state.totalBytes > 0L) {
            "$downloaded / ${formatMb(state.totalBytes)} MB"
        } else {
            "Downloaded $downloaded MB"
        }
    }

    fun formatBytes(context: Context, state: DownloadProgressState): String {
        val downloaded = formatMb(state.downloadedBytes)
        return if (state.totalBytes > 0L) {
            "$downloaded / ${formatMb(state.totalBytes)} MB"
        } else {
            context.getString(R.string.download_progress_downloaded_only, downloaded)
        }
    }

    fun progressPermille(state: DownloadProgressState): Int {
        if (state.totalBytes <= 0L) return 0
        return ((state.downloadedBytes.coerceAtLeast(0L) * 1000L) / state.totalBytes)
            .toInt()
            .coerceIn(0, 1000)
    }

    private fun formatMb(bytes: Long): String =
        "%.1f".format(bytes.coerceAtLeast(0L) / (1024.0 * 1024.0))
}
