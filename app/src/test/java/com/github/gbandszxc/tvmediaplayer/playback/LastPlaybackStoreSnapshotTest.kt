package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastPlaybackStoreSnapshotTest {

    @Test
    fun `from stored values prefers explicit media and directory`() {
        val snapshot = LastPlaybackStore.Snapshot.fromStoredValues(
            queueUris = listOf("smb://nas/Music/Track 01.flac"),
            queueMediaIds = listOf("Music/Track 01.flac"),
            currentIndex = 0,
            positionMs = 1_000L,
            title = "Track 01",
            currentMediaId = "Music/Track 01.flac",
            currentDirectoryPath = "Music",
            sourceConnectionId = "conn-1",
            sourceConfig = null
        )

        assertEquals("Music/Track 01.flac", snapshot.currentMediaId)
        assertEquals("Music", snapshot.currentDirectoryPath)
        assertEquals("conn-1", snapshot.sourceConnectionId)
    }

    @Test
    fun `from stored values derives missing fields from queue`() {
        val snapshot = LastPlaybackStore.Snapshot.fromStoredValues(
            queueUris = listOf("smb://nas/Music/Track 01.flac", "smb://nas/Music/Album/Track 02.flac"),
            queueMediaIds = listOf("Music/Track 01.flac", "Music/Album/Track 02.flac"),
            currentIndex = 1,
            positionMs = 0L,
            title = "",
            currentMediaId = null,
            currentDirectoryPath = null,
            sourceConnectionId = null,
            sourceConfig = null
        )

        assertEquals("Music/Album/Track 02.flac", snapshot.currentMediaId)
        assertEquals("Music/Album", snapshot.currentDirectoryPath)
    }

    @Test
    fun `from stored values returns null for media and directory when queue empty`() {
        val snapshot = LastPlaybackStore.Snapshot.fromStoredValues(
            queueUris = emptyList(),
            queueMediaIds = emptyList(),
            currentIndex = 0,
            positionMs = 0L,
            title = "",
            currentMediaId = null,
            currentDirectoryPath = null,
            sourceConnectionId = null,
            sourceConfig = null
        )

        assertNull(snapshot.currentMediaId)
        assertNull(snapshot.currentDirectoryPath)
    }

    @Test
    fun `from stored values restores source config`() {
        val snapshot = LastPlaybackStore.Snapshot.fromStoredValues(
            queueUris = listOf("smb://nas/Music/Track 01.flac"),
            queueMediaIds = listOf("Music/Track 01.flac"),
            currentIndex = 0,
            positionMs = 0L,
            title = "",
            currentMediaId = null,
            currentDirectoryPath = null,
            sourceConnectionId = "conn-2",
            sourceConfig = sampleConfig(
                host = "nas",
                share = "Media",
                path = "Music",
                username = "user",
                password = "pass",
                guest = false,
                smb1Enabled = true
            )
        )

        assertEquals(
            SmbConfig(
                host = "nas",
                share = "Media",
                path = "Music",
                username = "user",
                password = "pass",
                guest = false,
                smb1Enabled = true
            ),
            snapshot.sourceConfig
        )
    }

    private fun sampleConfig(
        host: String = "192.168.1.2",
        share: String = "Media",
        path: String = "",
        username: String = "",
        password: String = "",
        guest: Boolean = true,
        smb1Enabled: Boolean = false
    ): SmbConfig =
        SmbConfig(
            host = host,
            share = share,
            path = path,
            username = username,
            password = password,
            guest = guest,
            smb1Enabled = smb1Enabled
        )
}
