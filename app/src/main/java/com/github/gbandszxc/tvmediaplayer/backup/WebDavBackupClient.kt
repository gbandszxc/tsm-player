package com.github.gbandszxc.tvmediaplayer.backup

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL

data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String,
) {
    fun isReady(): Boolean =
        url.trim().isNotEmpty()
}

data class WebDavConnectionTestResult(
    val success: Boolean,
    val message: String,
)

class WebDavBackupClient(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        (url.openConnection() as HttpURLConnection)
    },
) {
    fun upload(config: WebDavConfig, fileName: String, source: InputStream) {
        ensureConfiguredCollection(config)
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

    fun testConnection(config: WebDavConfig): WebDavConnectionTestResult {
        if (!config.isReady()) {
            return WebDavConnectionTestResult(false, "连接失败：请先填写 WebDAV URL")
        }
        return runCatching {
            val ensureResult = ensureConfiguredCollection(config)
            val message = if (ensureResult.created) {
                "连接成功：已创建 WebDAV 目录（${ensureResult.detail}）"
            } else {
                "连接成功：${ensureResult.detail}"
            }
            WebDavConnectionTestResult(true, message)
        }.getOrElse { ex ->
            val message = if (ex is WebDavException) {
                ex.message.orEmpty()
            } else {
                "连接失败：${ex.readableMessage()}"
            }
            WebDavConnectionTestResult(false, message)
        }
    }

    private fun open(config: WebDavConfig, fileName: String, method: String): HttpURLConnection {
        return connectionFactory(URL(resolveFileUrl(config, fileName))).apply {
            setRequestMethodCompat(method)
            connectTimeout = 15_000
            readTimeout = 30_000
            if (config.username.isNotBlank() || config.password.isNotBlank()) {
                setRequestProperty("Authorization", "Basic ${basicAuth(config.username, config.password)}")
            }
        }
    }

    private fun openCollection(config: WebDavConfig, url: URL, method: String): HttpURLConnection {
        return connectionFactory(url).apply {
            setRequestMethodCompat(method)
            connectTimeout = 15_000
            readTimeout = 30_000
            if (config.username.isNotBlank() || config.password.isNotBlank()) {
                setRequestProperty("Authorization", "Basic ${basicAuth(config.username, config.password)}")
            }
        }
    }

    private fun ensureConfiguredCollection(config: WebDavConfig): EnsureCollectionResult {
        val collectionUrl = URL(normalizeCollectionUrl(config.url))
        return ensureCollection(config, collectionUrl, mutableSetOf())
    }

    private fun ensureCollection(
        config: WebDavConfig,
        collectionUrl: URL,
        visitedUrls: MutableSet<String>,
    ): EnsureCollectionResult {
        val normalizedUrl = normalizeCollectionUrl(collectionUrl.toString())
        if (!visitedUrls.add(normalizedUrl)) {
            throw WebDavException("无法创建 WebDAV 目录：URL 层级解析出现循环，请检查 URL")
        }

        val status = requestCollection(config, URL(normalizedUrl), "HEAD")
        if (status.isSuccess) {
            return EnsureCollectionResult(created = false, detail = status.detail)
        }
        if (status.code !in setOf(404, 409)) {
            throw WebDavException(connectionFailureMessage(status.code, status.message))
        }

        val parentUrl = parentCollectionUrl(URL(normalizedUrl))
            ?: throw WebDavException("连接失败：${status.detail}，请检查 WebDAV 根地址是否正确")
        ensureCollection(config, parentUrl, visitedUrls)

        val createStatus = requestCollection(config, URL(normalizedUrl), "MKCOL")
        return when {
            createStatus.isSuccess -> EnsureCollectionResult(created = true, detail = createStatus.detail)
            createStatus.code == 405 -> EnsureCollectionResult(created = false, detail = createStatus.detail)
            createStatus.code == 409 -> throw WebDavException(
                "创建 WebDAV 目录失败：${createStatus.detail}，上级目录不存在或服务器不允许创建"
            )
            else -> throw WebDavException(createCollectionFailureMessage(createStatus.code, createStatus.message))
        }
    }

    private fun requestCollection(config: WebDavConfig, url: URL, method: String): HttpStatus {
        val connection = openCollection(config, url, method)
        return try {
            HttpStatus(connection.responseCode, connection.responseMessage)
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveFileUrl(config: WebDavConfig, fileName: String): String {
        val base = normalizeCollectionUrl(config.url)
        val encodedFile = encodePathSegment(fileName)
        return "$base/$encodedFile"
    }

    private fun normalizeCollectionUrl(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.endsWith("/") && URL(trimmed).path != "/") {
            trimmed.trimEnd('/')
        } else {
            trimmed
        }
    }

    private fun parentCollectionUrl(url: URL): URL? {
        val uri = url.toURI()
        val path = uri.path.trimEnd('/')
        val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentPath.isBlank()) return null
        return java.net.URI(
            uri.scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            parentPath,
            null,
            null
        ).toURL()
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun basicAuth(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private fun connectionFailureMessage(code: Int, message: String?): String {
        val detail = httpDetail(code, message)
        return when (code) {
            401, 403 -> "认证失败：$detail，请检查用户名或密码"
            404 -> "连接失败：$detail，请检查 URL 是否为有效 WebDAV 目录"
            405 -> "连接失败：$detail，服务器不允许目录探测请求，请确认该地址支持 WebDAV"
            409 -> "连接失败：$detail，URL 上级目录不存在或服务器不允许访问"
            in 500..599 -> "连接失败：$detail，服务器返回错误"
            else -> "连接失败：$detail"
        }
    }

    private fun createCollectionFailureMessage(code: Int, message: String?): String {
        val detail = httpDetail(code, message)
        return when (code) {
            401, 403 -> "创建 WebDAV 目录失败：$detail，请检查用户名、密码或目录创建权限"
            in 500..599 -> "创建 WebDAV 目录失败：$detail，服务器返回错误"
            else -> "创建 WebDAV 目录失败：$detail"
        }
    }

    private fun httpDetail(code: Int, message: String?): String =
        "HTTP $code${message?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"

    private fun HttpURLConnection.setRequestMethodCompat(method: String) {
        try {
            requestMethod = method
        } catch (ex: ProtocolException) {
            if (method != "MKCOL") throw ex
            setRequestMethodByReflection(method, ex)
        }
    }

    private fun HttpURLConnection.setRequestMethodByReflection(method: String, cause: ProtocolException) {
        var currentClass: Class<*>? = javaClass
        while (currentClass != null) {
            val candidate = currentClass
            runCatching {
                val field = candidate.getDeclaredField("method")
                field.isAccessible = true
                field.set(this, method)
                return
            }
            currentClass = candidate.superclass
        }
        throw cause
    }

    private fun Throwable.readableMessage(): String =
        when (this) {
            is java.net.UnknownHostException -> "无法解析主机，请检查 URL 域名"
            is java.net.SocketTimeoutException -> "连接超时，请检查网络或服务器状态"
            is javax.net.ssl.SSLException -> "TLS/SSL 握手失败，请检查 HTTPS 证书或服务器配置"
            is IOException -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
            else -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
        }
}

private data class EnsureCollectionResult(
    val created: Boolean,
    val detail: String,
)

private data class HttpStatus(
    val code: Int,
    val message: String?,
) {
    val isSuccess: Boolean
        get() = code in 200..299 || code == 207

    val detail: String
        get() = "HTTP $code${message?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"
}

class WebDavException(message: String) : Exception(message)
