package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLocationResolverTest {

    @Test
    fun `resolve returns provided target information when snapshot contains directory data`() {
        val config = sampleConfig(path = "Music/Albums")
        val snapshot = LastPlaybackStore.Snapshot(
            queueUris = listOf("smb://nas/Music/Albums/Track 01.flac"),
            queueMediaIds = listOf("Music/Albums/Track 01.flac"),
            currentIndex = 0,
            positionMs = 0L,
            title = "Track 01",
            currentMediaId = "Music/Albums/Track 01.flac",
            currentDirectoryPath = "Music/Albums",
            sourceConnectionId = "conn-1",
            sourceConfig = config
        )

        val target = PlaybackLocationResolver.resolve(snapshot)

        assertNotNull(target)
        assertEquals("Music/Albums/Track 01.flac", target?.mediaId)
        assertEquals("Music/Albums", target?.directoryPath)
        assertEquals(config, target?.sourceConfig)
        assertTrue(target!!.matchesConnection(config, "conn-1"))
    }

    @Test
    fun `resolve falls back to queue and parent directory when explicit directory is missing`() {
        val snapshot = LastPlaybackStore.Snapshot(
            queueUris = listOf("smb://nas/Music/Track 01.flac"),
            queueMediaIds = listOf("Music/Track 01.flac"),
            currentIndex = 0,
            positionMs = 0L,
            title = "",
            currentMediaId = null,
            currentDirectoryPath = null,
            sourceConnectionId = null,
            sourceConfig = null
        )

        val target = PlaybackLocationResolver.resolve(snapshot)

        assertNotNull(target)
        assertEquals("Music/Track 01.flac", target?.mediaId)
        assertEquals("Music", target?.directoryPath)
        assertEquals(SmbConfig.Empty, target?.sourceConfig)
        assertFalse(target!!.matchesConnection(sampleConfig(), "conn-other"))
    }

    @Test
    fun `path helpers normalize separators and expose parent directories`() {
        assertEquals(
            "Music/Albums",
            PlaybackLocationResolver.normalizePath("\\\\Music\\\\Albums\\\\")
        )
        assertEquals("Music/Albums", PlaybackLocationResolver.parentDirectory("Music/Albums/Track.flac"))
        assertEquals("", PlaybackLocationResolver.parentDirectory("Track.flac"))
    }

    private fun sampleConfig(
        host: String = "192.168.1.2",
        share: String = "Media",
        path: String = ""
    ): SmbConfig =
        SmbConfig(
            host = host,
            share = share,
            path = path,
            username = "",
            password = "",
            guest = true
        )
}
