package com.github.gbandszxc.tvmediaplayer.playback

import java.io.FileNotFoundException
import java.io.IOException

object SmbDataSourceOpenFailurePolicy {
    fun wrapOpenFailure(uri: String, failure: Throwable): IOException {
        val message = "Failed to open SMB stream: $uri"
        return if (isFileNotFound(failure)) {
            FileNotFoundException(message).apply { initCause(failure) }
        } else {
            IOException(message, failure)
        }
    }

    fun isFileNotFound(failure: Throwable): Boolean =
        failure.causeChain().any { throwable ->
            throwable is FileNotFoundException || throwable.message.isFileNotFoundMessage()
        }

    private fun String?.isFileNotFoundMessage(): Boolean {
        val message = this?.lowercase().orEmpty()
        return listOf(
            "status_object_name_not_found",
            "status_object_path_not_found",
            "object name not found",
            "object path not found",
            "path not found",
            "file not found",
            "cannot find the file",
            "cannot find the path",
            "no such file",
        ).any { it in message }
    }

    private fun Throwable.causeChain(): Sequence<Throwable> = sequence {
        var current: Throwable? = this@causeChain
        while (current != null) {
            yield(current)
            current = current.cause
        }
    }
}
