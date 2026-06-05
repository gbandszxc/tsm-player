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
            val responseCode = when (opened.size) {
                0 -> 207
                1 -> 201
                else -> error("Unexpected request")
            }
            FakeConnection(url, responseCode = responseCode).also(opened::add)
        }
        val config = WebDavConfig(
            url = "https://dav.example.com/remote.php/dav/files/me/tsm-player",
            username = "alice",
            password = "secret"
        )

        client.upload(config, "favorites.db", byteArrayOf(1, 2, 3).inputStream())

        assertEquals("https://dav.example.com/remote.php/dav/files/me/tsm-player", opened[0].url.toString())
        assertEquals("HEAD", opened[0].requestMethod)
        val request = opened[1]
        assertEquals("https://dav.example.com/remote.php/dav/files/me/tsm-player/favorites.db", request.url.toString())
        assertEquals("PUT", request.requestMethod)
        assertEquals("Basic YWxpY2U6c2VjcmV0", request.capturedRequestProperties["Authorization"])
        assertArrayEquals(byteArrayOf(1, 2, 3), request.output.toByteArray())
    }

    @Test
    fun `upload creates a missing configured collection before PUT`() {
        val opened = mutableListOf<FakeConnection>()
        val client = WebDavBackupClient { url ->
            val responseCode = when (url.toString()) {
                "https://dav.example.com/base/backups/music" -> if (opened.none { it.url.toString() == url.toString() }) 404 else 201
                "https://dav.example.com/base/backups" -> 207
                "https://dav.example.com/base/backups/music/tsm-player.db" -> 201
                else -> error("Unexpected URL $url")
            }
            FakeConnection(url, responseCode = responseCode).also(opened::add)
        }
        val config = WebDavConfig(
            url = "https://dav.example.com/base/backups/music",
            username = "alice",
            password = "secret"
        )

        client.upload(config, "tsm-player.db", byteArrayOf(4, 5, 6).inputStream())

        assertEquals(
            listOf(
                "HEAD https://dav.example.com/base/backups/music",
                "HEAD https://dav.example.com/base/backups",
                "MKCOL https://dav.example.com/base/backups/music",
                "PUT https://dav.example.com/base/backups/music/tsm-player.db",
            ),
            opened.map { "${it.requestMethod} ${it.url}" }
        )
    }

    @Test
    fun `download reads bytes from the configured backup url`() {
        val client = WebDavBackupClient { url ->
            FakeConnection(url, responseCode = 200, responseBody = byteArrayOf(7, 8, 9))
        }
        val config = WebDavConfig(
            url = "https://dav.example.com/base/backups/",
            username = "",
            password = ""
        )
        val destination = ByteArrayOutputStream()

        client.download(config, "favorites.db", destination)

        assertArrayEquals(byteArrayOf(7, 8, 9), destination.toByteArray())
    }

    @Test
    fun `configuration is ready only when server and directory are present`() {
        assertTrue(WebDavConfig("https://dav.example.com/backups", "", "").isReady())
        assertEquals(false, WebDavConfig("", "", "").isReady())
    }

    @Test
    fun `test connection sends HEAD to configured url and reports success`() {
        val opened = mutableListOf<FakeConnection>()
        val client = WebDavBackupClient { url ->
            FakeConnection(url, responseCode = 207).also(opened::add)
        }
        val config = WebDavConfig(
            url = "https://dav.example.com/base/backups",
            username = "alice",
            password = "secret"
        )

        val result = client.testConnection(config)

        val request = opened.single()
        assertEquals("https://dav.example.com/base/backups", request.url.toString())
        assertEquals("HEAD", request.requestMethod)
        assertEquals(true, result.success)
        assertEquals("连接成功：HTTP 207", result.message)
    }

    @Test
    fun `test connection creates a missing configured collection`() {
        val opened = mutableListOf<FakeConnection>()
        val client = WebDavBackupClient { url ->
            val responseCode = when (url.toString()) {
                "https://dav.example.com/base/backups/music" -> if (opened.none { it.url.toString() == url.toString() }) 404 else 201
                "https://dav.example.com/base/backups" -> 207
                else -> error("Unexpected URL $url")
            }
            FakeConnection(url, responseCode = responseCode, responseMessageText = if (responseCode == 201) "Created" else "").also(opened::add)
        }
        val config = WebDavConfig(
            url = "https://dav.example.com/base/backups/music",
            username = "alice",
            password = "secret"
        )

        val result = client.testConnection(config)

        assertEquals(
            listOf(
                "HEAD https://dav.example.com/base/backups/music",
                "HEAD https://dav.example.com/base/backups",
                "MKCOL https://dav.example.com/base/backups/music",
            ),
            opened.map { "${it.requestMethod} ${it.url}" }
        )
        assertEquals(true, result.success)
        assertEquals("连接成功：已创建 WebDAV 目录（HTTP 201 Created）", result.message)
    }

    @Test
    fun `test connection does not trust OPTIONS success when collection HEAD is missing`() {
        val opened = mutableListOf<FakeConnection>()
        val existingCollections = mutableSetOf("https://dav.example.com/base")
        val client = WebDavBackupClient { url ->
            val urlText = url.toString()
            FakeConnection(
                url = url,
                responseMessageText = "Created",
                responseCodeProvider = { method ->
                    when {
                        method == "OPTIONS" -> 200
                        existingCollections.contains(urlText) -> 200
                        method == "MKCOL" -> 201
                        else -> 404
                    }
                }
            ).also { connection ->
                opened += connection
                connection.onResponseCodeRead = {
                    if (connection.requestMethod == "MKCOL") {
                        existingCollections += urlText
                    }
                }
            }
        }
        val config = WebDavConfig(
            url = "https://dav.example.com/base/backups",
            username = "alice",
            password = "secret"
        )

        val result = client.testConnection(config)

        assertEquals(
            listOf(
                "HEAD https://dav.example.com/base/backups",
                "HEAD https://dav.example.com/base",
                "MKCOL https://dav.example.com/base/backups",
            ),
            opened.map { "${it.requestMethod} ${it.url}" }
        )
        assertEquals(true, result.success)
        assertEquals("连接成功：已创建 WebDAV 目录（HTTP 201 Created）", result.message)
    }

    @Test
    fun `test connection creates nested missing configured collections from parent to child`() {
        val opened = mutableListOf<FakeConnection>()
        val existingCollections = mutableSetOf("https://dav.example.com/base")
        val client = WebDavBackupClient { url ->
            val urlText = url.toString()
            FakeConnection(
                url = url,
                responseMessageText = "Created",
                responseCodeProvider = { method ->
                    when (urlText) {
                        "https://dav.example.com/base",
                        "https://dav.example.com/base/backups",
                        "https://dav.example.com/base/backups/2026",
                        "https://dav.example.com/base/backups/2026/june" -> {
                            when {
                                existingCollections.contains(urlText) -> 207
                                method == "MKCOL" -> 201
                                else -> 404
                            }
                        }
                        else -> error("Unexpected URL $url")
                    }
                }
            ).also { connection ->
                opened += connection
                connection.onResponseCodeRead = {
                    if (connection.requestMethod == "MKCOL") {
                        existingCollections += urlText
                    }
                }
            }
        }
        val config = WebDavConfig(
            url = "https://dav.example.com/base/backups/2026/june",
            username = "alice",
            password = "secret"
        )

        val result = client.testConnection(config)

        assertEquals(
            listOf(
                "HEAD https://dav.example.com/base/backups/2026/june",
                "HEAD https://dav.example.com/base/backups/2026",
                "HEAD https://dav.example.com/base/backups",
                "HEAD https://dav.example.com/base",
                "MKCOL https://dav.example.com/base/backups",
                "MKCOL https://dav.example.com/base/backups/2026",
                "MKCOL https://dav.example.com/base/backups/2026/june",
            ),
            opened.map { "${it.requestMethod} ${it.url}" }
        )
        assertEquals(true, result.success)
        assertEquals("连接成功：已创建 WebDAV 目录（HTTP 201 Created）", result.message)
    }

    @Test
    fun `test connection reports auth failure with status details`() {
        val client = WebDavBackupClient { url ->
            FakeConnection(url, responseCode = 401, responseMessageText = "Unauthorized")
        }

        val result = client.testConnection(WebDavConfig("https://dav.example.com/backups", "bad", "bad"))

        assertEquals(false, result.success)
        assertEquals("认证失败：HTTP 401 Unauthorized，请检查用户名或密码", result.message)
    }

    private class FakeConnection(
        url: URL,
        private val responseCode: Int = 200,
        private val responseBody: ByteArray = ByteArray(0),
        private val responseMessageText: String = "",
        private val responseCodeProvider: (String) -> Int = { responseCode },
    ) : HttpURLConnection(url) {
        val output = ByteArrayOutputStream()
        val capturedRequestProperties = mutableMapOf<String, String>()
        var onResponseCodeRead: () -> Unit = {}
        private var capturedMethod: String = "GET"

        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun setRequestMethod(method: String) {
            capturedMethod = method
        }
        override fun getRequestMethod(): String = capturedMethod
        override fun getResponseCode(): Int {
            val code = responseCodeProvider(capturedMethod)
            onResponseCodeRead()
            return code
        }
        override fun getResponseMessage(): String = responseMessageText
        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody)
        override fun getOutputStream(): OutputStream = output
        override fun setRequestProperty(key: String, value: String) {
            capturedRequestProperties[key] = value
        }
    }
}
