package com.github.gbandszxc.tvmediaplayer.update

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressFormatterTest {
    @Test
    fun `formats speed and known total size`() {
        val state = DownloadProgressState(
            downloadedBytes = 18_600_000L,
            totalBytes = 42_000_000L,
            speedBytesPerSecond = 5_200_000L,
        )

        assertEquals("5.0 MB/s", DownloadProgressFormatter.formatSpeed(state))
        assertEquals("17.7 / 40.1 MB", DownloadProgressFormatter.formatBytes(state))
        assertEquals(442, DownloadProgressFormatter.progressPermille(state))
    }

    @Test
    fun `formats unknown total size without fake progress`() {
        val state = DownloadProgressState(
            downloadedBytes = 1_572_864L,
            totalBytes = -1L,
            speedBytesPerSecond = 0L,
        )

        assertEquals("-- MB/s", DownloadProgressFormatter.formatSpeed(state))
        assertEquals("Downloaded 1.5 MB", DownloadProgressFormatter.formatBytes(state))
        assertEquals(0, DownloadProgressFormatter.progressPermille(state))
    }
}
