package com.github.gbandszxc.tvmediaplayer.favorites

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteInvalidTrackPolicyTest {

    @Test
    fun `blank stream uri is immediately invalid`() {
        assertTrue(FavoriteInvalidTrackPolicy.isBlankStreamUriInvalid(" "))
    }

    @Test
    fun `ordinary playback error does not offer removal`() {
        val error = PlaybackException("transient network error", IOException("connection reset"), 1000)

        assertFalse(FavoriteInvalidTrackPolicy.shouldOfferRemoval(error))
    }

    @Test
    fun `unknown playback error does not offer removal`() {
        val error = PlaybackException("unknown", null, PlaybackException.ERROR_CODE_UNSPECIFIED)

        assertFalse(FavoriteInvalidTrackPolicy.shouldOfferRemoval(error))
    }

    @Test
    fun `file not found playback error offers removal`() {
        val error = PlaybackException(
            "file missing",
            FileNotFoundException("Music/missing.flac"),
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        )

        assertTrue(FavoriteInvalidTrackPolicy.shouldOfferRemoval(error))
    }
}
