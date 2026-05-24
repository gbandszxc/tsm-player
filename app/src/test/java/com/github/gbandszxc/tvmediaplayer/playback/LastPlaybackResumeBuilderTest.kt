package com.github.gbandszxc.tvmediaplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LastPlaybackResumeBuilderTest {

    @Test
    fun `resume request starts playback from stored position`() {
        val snapshot = LastPlaybackStore.Snapshot(
            queueUris = listOf("smb://nas/Music/Album/01.flac"),
            queueMediaIds = listOf("Music/Album/01.flac"),
            currentIndex = 0,
            positionMs = 12_345L,
            title = "Tag Title"
        )

        val request = LastPlaybackResumeBuilder.fromSnapshot(snapshot)

        requireNotNull(request)
        assertEquals(0, request.startIndex)
        assertEquals(12_345L, request.positionMs)
        assertTrue(request.playWhenReady)
    }

    @Test
    fun `resume request keeps stored title for current item`() {
        val snapshot = LastPlaybackStore.Snapshot(
            queueUris = listOf(
                "smb://nas/Music/Album/01.flac",
                "smb://nas/Music/Album/02.flac"
            ),
            queueMediaIds = listOf("Music/Album/01.flac", "Music/Album/02.flac"),
            currentIndex = 1,
            positionMs = 0L,
            title = "Stored Current Title"
        )

        val request = LastPlaybackResumeBuilder.fromSnapshot(snapshot)

        requireNotNull(request)
        assertEquals("01", request.mediaItems[0].mediaMetadata.title.toString())
        assertEquals("Stored Current Title", request.mediaItems[1].mediaMetadata.title.toString())
    }
}
