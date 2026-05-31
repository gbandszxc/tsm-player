package com.github.gbandszxc.tvmediaplayer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class SmbDataSourceOpenFailurePolicyTest {

    @Test
    fun `SMB missing file message is converted to file not found`() {
        val failure = IOException("Failed to open SMB stream", IOException("The system cannot find the file specified."))

        assertTrue(SmbDataSourceOpenFailurePolicy.isFileNotFound(failure))
    }

    @Test
    fun `SMB missing path message is converted to file not found`() {
        val failure = IOException("Failed to open SMB stream", IOException("STATUS_OBJECT_PATH_NOT_FOUND"))

        assertTrue(SmbDataSourceOpenFailurePolicy.isFileNotFound(failure))
    }

    @Test
    fun `SMB authentication and network failures are not file not found`() {
        assertFalse(SmbDataSourceOpenFailurePolicy.isFileNotFound(IOException("Access is denied.")))
        assertFalse(SmbDataSourceOpenFailurePolicy.isFileNotFound(SocketTimeoutException("Read timed out")))
        assertFalse(SmbDataSourceOpenFailurePolicy.isFileNotFound(IOException("Failed to connect to server")))
        assertFalse(SmbDataSourceOpenFailurePolicy.isFileNotFound(IOException("The network name cannot be found.")))
        assertFalse(SmbDataSourceOpenFailurePolicy.isFileNotFound(IOException("Network path not found.")))
    }
}
