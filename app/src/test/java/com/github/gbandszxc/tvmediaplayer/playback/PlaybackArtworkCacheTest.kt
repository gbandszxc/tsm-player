package com.github.gbandszxc.tvmediaplayer.playback

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackArtworkCacheTest {

    @Test
    fun `large artwork is sampled below the cache edge before decoding`() {
        assertEquals(4, PlaybackArtworkCache.calculateInSampleSize(5368, 5368, 1024))
        assertEquals(1, PlaybackArtworkCache.calculateInSampleSize(1024, 768, 1024))
    }

    @Test
    fun `memory cache scales oversized bitmap to safe edge`() {
        val source = Bitmap.createBitmap(2048, 1536, Bitmap.Config.ARGB_8888)

        PlaybackArtworkCache.put("oversized", source)

        val cached = requireNotNull(PlaybackArtworkCache.get("oversized"))
        assertEquals(1024, cached.width)
        assertEquals(768, cached.height)
        assertTrue(cached.byteCount <= 1024 * 1024 * 4)
    }
}
