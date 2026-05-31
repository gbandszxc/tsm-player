package com.github.gbandszxc.tvmediaplayer.favorites

import androidx.media3.common.PlaybackException
import java.io.FileNotFoundException

object FavoriteInvalidTrackPolicy {
    fun isBlankStreamUriInvalid(streamUri: String): Boolean = streamUri.isBlank()

    fun shouldOfferRemoval(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) return true
        return error.causeChain().any { it is FileNotFoundException }
    }

    private fun Throwable.causeChain(): Sequence<Throwable> = sequence {
        var current: Throwable? = this@causeChain
        while (current != null) {
            yield(current)
            current = current.cause
        }
    }
}
