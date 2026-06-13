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

data class WebDavClientMessages(
    val uploadFailedHttp: (Int) -> String = { "WebDAV upload failed: HTTP $it" },
    val downloadFailedHttp: (Int) -> String = { "WebDAV download failed: HTTP $it" },
    val missingUrl: String = "Connection failed: enter a WebDAV URL first",
    val createdDirectory: (String) -> String = { "Connection succeeded: WebDAV folder created ($it)" },
    val connectionSuccess: (String) -> String = { "Connection succeeded: $it" },
    val connectionFailed: (String) -> String = { "Connection failed: $it" },
    val createLoop: String = "Could not create WebDAV folder: URL hierarchy loop detected. Check the URL",
    val invalidRoot: (String) -> String = { "Connection failed: $it. Check whether the WebDAV root URL is correct" },
    val createParentFailed: (String) -> String = { "Could not create WebDAV folder: $it. Parent folder is missing or creation is not allowed" },
    val authFailed: (String) -> String = { "Authentication failed: $it. Check username or password" },
    val invalidDirectory: (String) -> String = { "Connection failed: $it. Check whether the URL is a valid WebDAV folder" },
    val probeNotAllowed: (String) -> String = { "Connection failed: $it. The server rejected folder probing. Make sure this URL supports WebDAV" },
    val parentInaccessible: (String) -> String = { "Connection failed: $it. The parent URL is missing or inaccessible" },
    val serverError: (String) -> String = { "Connection failed: $it. The server returned an error" },
    val createAuthFailed: (String) -> String = { "Could not create WebDAV folder: $it. Check credentials or folder creation permissions" },
    val createServerError: (String) -> String = { "Could not create WebDAV folder: $it. The server returned an error" },
    val createFailed: (String) -> String = { "Could not create WebDAV folder: $it" },
    val unknownHost: String = "Could not resolve host. Check the URL domain",
    val timeout: String = "Connection timed out. Check the network or server status",
    val sslError: String = "TLS/SSL handshake failed. Check the HTTPS certificate or server setup",
)

class WebDavBackupClient(
    private val messages: WebDavClientMessages = WebDavClientMessages(),
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
            if (code !in 200..299) throw WebDavException(messages.uploadFailedHttp(code))
        } finally {
            connection.disconnect()
        }
    }

    fun download(config: WebDavConfig, fileName: String, destination: OutputStream) {
        val connection = open(config, fileName, "GET")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw WebDavException(messages.downloadFailedHttp(code))
            connection.inputStream.use { input ->
                destination.use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    fun testConnection(config: WebDavConfig): WebDavConnectionTestResult {
        if (!config.isReady()) {
            return WebDavConnectionTestResult(false, messages.missingUrl)
        }
        return runCatching {
            val ensureResult = ensureConfiguredCollection(config)
            val message = if (ensureResult.created) {
                messages.createdDirectory(ensureResult.detail)
            } else {
                messages.connectionSuccess(ensureResult.detail)
            }
            WebDavConnectionTestResult(true, message)
        }.getOrElse { ex ->
            val message = if (ex is WebDavException) {
                ex.message.orEmpty()
            } else {
                messages.connectionFailed(ex.readableMessage())
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
            throw WebDavException(messages.createLoop)
        }

        val status = requestCollection(config, URL(normalizedUrl), "HEAD")
        if (status.isSuccess) {
            return EnsureCollectionResult(created = false, detail = status.detail)
        }
        if (status.code !in setOf(404, 409)) {
            throw WebDavException(connectionFailureMessage(status.code, status.message))
        }

        val parentUrl = parentCollectionUrl(URL(normalizedUrl))
            ?: throw WebDavException(messages.invalidRoot(status.detail))
        ensureCollection(config, parentUrl, visitedUrls)

        val createStatus = requestCollection(config, URL(normalizedUrl), "MKCOL")
        return when {
            createStatus.isSuccess -> EnsureCollectionResult(created = true, detail = createStatus.detail)
            createStatus.code == 405 -> EnsureCollectionResult(created = false, detail = createStatus.detail)
            createStatus.code == 409 -> throw WebDavException(
                messages.createParentFailed(createStatus.detail)
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
            401, 403 -> messages.authFailed(detail)
            404 -> messages.invalidDirectory(detail)
            405 -> messages.probeNotAllowed(detail)
            409 -> messages.parentInaccessible(detail)
            in 500..599 -> messages.serverError(detail)
            else -> messages.connectionFailed(detail)
        }
    }

    private fun createCollectionFailureMessage(code: Int, message: String?): String {
        val detail = httpDetail(code, message)
        return when (code) {
            401, 403 -> messages.createAuthFailed(detail)
            in 500..599 -> messages.createServerError(detail)
            else -> messages.createFailed(detail)
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
            is java.net.UnknownHostException -> messages.unknownHost
            is java.net.SocketTimeoutException -> messages.timeout
            is javax.net.ssl.SSLException -> messages.sslError
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
