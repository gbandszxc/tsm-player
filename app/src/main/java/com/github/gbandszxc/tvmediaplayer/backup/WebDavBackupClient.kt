package com.github.gbandszxc.tvmediaplayer.backup

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

data class WebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remoteDirectory: String,
) {
    fun isReady(): Boolean =
        serverUrl.trim().isNotEmpty() && remoteDirectory.trim().isNotEmpty()
}

class WebDavBackupClient(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        (url.openConnection() as HttpURLConnection)
    },
) {
    fun upload(config: WebDavConfig, fileName: String, source: InputStream) {
        val connection = open(config, fileName, "PUT").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            connection.outputStream.use { output ->
                source.use { input -> input.copyTo(output) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw WebDavException("WebDAV 上传失败：HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    fun download(config: WebDavConfig, fileName: String, destination: OutputStream) {
        val connection = open(config, fileName, "GET")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw WebDavException("WebDAV 下载失败：HTTP $code")
            connection.inputStream.use { input ->
                destination.use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(config: WebDavConfig, fileName: String, method: String): HttpURLConnection {
        return connectionFactory(URL(resolveFileUrl(config, fileName))).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            if (config.username.isNotBlank() || config.password.isNotBlank()) {
                setRequestProperty("Authorization", "Basic ${basicAuth(config.username, config.password)}")
            }
        }
    }

    private fun resolveFileUrl(config: WebDavConfig, fileName: String): String {
        val base = config.serverUrl.trim().trimEnd('/')
        val dir = config.remoteDirectory.trim().trim('/').split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { encodePathSegment(it) }
        val encodedFile = encodePathSegment(fileName)
        return if (dir.isBlank()) "$base/$encodedFile" else "$base/$dir/$encodedFile"
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun basicAuth(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }
}

class WebDavException(message: String) : Exception(message)
