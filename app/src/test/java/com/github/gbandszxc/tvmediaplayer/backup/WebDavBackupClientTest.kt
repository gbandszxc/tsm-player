package com.github.gbandszxc.tvmediaplayer.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class WebDavBackupClientTest {

    @Test
    fun `upload sends a PUT request to the configured backup url with basic auth`() {
        val opened = mutableListOf<FakeConnection>()
        val client = WebDavBackupClient { url ->
            FakeConnection(url, responseCode = 201).also(opened::add)
        }
        val config = WebDavConfig(
            serverUrl = "https://dav.example.com/remote.php/dav/files/me",
            username = "alice",
            password = "secret",
            remoteDirectory = "tsm-player"
        )

        client.upload(config, "favorites.db", byteArrayOf(1, 2, 3).inputStream())

        val request = opened.single()
        assertEquals("https://dav.example.com/remote.php/dav/files/me/tsm-player/favorites.db", request.url.toString())
        assertEquals("PUT", request.requestMethod)
        assertEquals("Basic YWxpY2U6c2VjcmV0", request.capturedRequestProperties["Authorization"])
        assertArrayEquals(byteArrayOf(1, 2, 3), request.output.toByteArray())
    }

    @Test
    fun `download reads bytes from the configured backup url`() {
        val client = WebDavBackupClient { url ->
            FakeConnection(url, responseCode = 200, responseBody = byteArrayOf(7, 8, 9))
        }
        val config = WebDavConfig(
            serverUrl = "https://dav.example.com/base/",
            username = "",
            password = "",
            remoteDirectory = "/backups/"
        )
        val destination = ByteArrayOutputStream()

        client.download(config, "favorites.db", destination)

        assertArrayEquals(byteArrayOf(7, 8, 9), destination.toByteArray())
    }

    @Test
    fun `configuration is ready only when server and directory are present`() {
        assertTrue(WebDavConfig("https://dav.example.com", "", "", "backups").isReady())
        assertEquals(false, WebDavConfig("", "", "", "backups").isReady())
        assertEquals(false, WebDavConfig("https://dav.example.com", "", "", "").isReady())
    }

    private class FakeConnection(
        url: URL,
        private val responseCode: Int,
        private val responseBody: ByteArray = ByteArray(0),
    ) : HttpURLConnection(url) {
        val output = ByteArrayOutputStream()
        val capturedRequestProperties = mutableMapOf<String, String>()

        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun getResponseCode(): Int = responseCode
        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody)
        override fun getOutputStream(): OutputStream = output
        override fun setRequestProperty(key: String, value: String) {
            capturedRequestProperties[key] = value
        }
    }
}
