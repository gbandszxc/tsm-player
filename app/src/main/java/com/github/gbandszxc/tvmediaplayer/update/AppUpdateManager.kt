package com.github.gbandszxc.tvmediaplayer.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.github.gbandszxc.tvmediaplayer.BuildConfig
import com.github.gbandszxc.tvmediaplayer.ui.modal.ActionModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalAction
import com.github.gbandszxc.tvmediaplayer.ui.modal.ProgressModalHandle
import com.github.gbandszxc.tvmediaplayer.ui.modal.ProgressModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {
    private const val GITHUB_BASE_URL = "https://github.com"
    private const val LATEST_RELEASE_URL =
        "$GITHUB_BASE_URL/gbandszxc/tsm-player/releases/latest"
    private const val EXPANDED_ASSETS_PATH = "/gbandszxc/tsm-player/releases/expanded_assets/"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private var automaticCheckStarted = false

    data class UpdateInfo(
        val versionName: String,
        val assetName: String,
        val downloadUrl: String,
        val sizeBytes: Long
    )

    fun currentAbi(): String {
        val supported = Build.SUPPORTED_ABIS.toList()
        return supported.firstOrNull { it == "arm64-v8a" }
            ?: supported.firstOrNull { it == "armeabi-v7a" }
            ?: supported.firstOrNull().orEmpty()
    }

    fun maybeCheckOnAppStart(activity: Activity) {
        if (automaticCheckStarted) return
        automaticCheckStarted = true
        checkAndPrompt(activity, silentWhenNoUpdate = true)
    }

    fun checkAndPrompt(activity: Activity, silentWhenNoUpdate: Boolean) {
        MainScope().let { scope ->
            scope.launchSafely(activity) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { fetchLatestUpdate() }
                }
                if (activity.isFinishing || activity.isDestroyed) return@launchSafely
                result.fold(
                    onSuccess = { update ->
                        if (update == null) {
                            if (!silentWhenNoUpdate) {
                                Toast.makeText(activity, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            showUpdateDialog(activity, update)
                        }
                    },
                    onFailure = { error ->
                        if (!silentWhenNoUpdate) {
                            Toast.makeText(
                                activity,
                                "检查更新失败：${error.message ?: "网络不可用"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }

    internal fun findMatchingAsset(
        currentVersionName: String,
        currentAbi: String,
        releaseVersionName: String,
        expandedAssetsHtml: String
    ): UpdateInfo? {
        val releaseVersion = normalizeVersion(releaseVersionName)
        if (releaseVersion.isBlank()) return null
        if (compareVersions(releaseVersion, currentVersionName) <= 0) return null

        val assetRegex = Regex("""href="([^"]*releases/download/[^"]+\.apk)"""")
        return assetRegex.findAll(expandedAssetsHtml)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .map { path -> path.substringAfterLast('/') to path }
            .firstNotNullOfOrNull { (name, path) ->
                if (!name.endsWith(".apk", ignoreCase = true)) return@firstNotNullOfOrNull null
                if (!name.contains("-release-", ignoreCase = true)) return@firstNotNullOfOrNull null
                if (!name.contains("-$currentAbi-", ignoreCase = true)) return@firstNotNullOfOrNull null
                if (!name.contains("-$releaseVersion.apk", ignoreCase = true)) return@firstNotNullOfOrNull null
                val downloadUrl = if (path.startsWith("http")) path else "$GITHUB_BASE_URL$path"
                UpdateInfo(
                    versionName = releaseVersion,
                    assetName = name,
                    downloadUrl = downloadUrl,
                    sizeBytes = 0L
                )
            }
    }

    internal fun compareVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val max = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until max) {
            val l = leftParts.getOrElse(i) { 0 }
            val r = rightParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun fetchLatestUpdate(): UpdateInfo? {
        val latestPage = readPage(LATEST_RELEASE_URL)
        val releaseVersion = latestPage.finalUrl.substringAfterLast('/').takeIf {
            it.isNotBlank() && !it.equals("latest", ignoreCase = true)
        } ?: parseLatestReleaseTag(latestPage.content)
        val expandedAssetsHtml = readTextFromUrl("$GITHUB_BASE_URL$EXPANDED_ASSETS_PATH$releaseVersion")
        return findMatchingAsset(
            currentVersionName = BuildConfig.VERSION_NAME,
            currentAbi = currentAbi(),
            releaseVersionName = releaseVersion,
            expandedAssetsHtml = expandedAssetsHtml
        )
    }

    private fun showUpdateDialog(activity: Activity, update: UpdateInfo) {
        TsmModalCoordinator(activity).showActionModal(
            ActionModalSpec(
                sectionLabel = "更新",
                title = "发现新版本 ${update.versionName}",
                message = "检测到适用于 ${currentAbi()} 的安装包：${update.assetName}",
                actions = listOf(
                    ModalAction("稍后"),
                    ModalAction("下载并安装", isPrimary = true) { downloadAndInstall(activity, update) },
                ),
            )
        )
    }

    private fun downloadAndInstall(activity: Activity, update: UpdateInfo) {
        val coordinator = TsmModalCoordinator(activity)
        val progressHandle = coordinator.showProgressModal(
            ProgressModalSpec(
                sectionLabel = "更新",
                title = "正在下载更新",
                fileName = update.assetName,
                percent = 0,
                indeterminate = update.sizeBytes <= 0L,
                message = "请稍候，下载完成后将进入安装流程。",
            )
        )

        MainScope().let { scope ->
            scope.launchSafely(activity) {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        downloadApk(activity, update) { percent ->
                            activity.runOnUiThread {
                                progressHandle.onProgress(percent)
                            }
                        }
                    }
                }
                progressHandle.onDismiss()
                if (activity.isFinishing || activity.isDestroyed) return@launchSafely
                result.fold(
                    onSuccess = { installApk(activity, it) },
                    onFailure = {
                        Toast.makeText(
                            activity,
                            "下载更新失败：${it.message ?: "网络不可用"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    private suspend fun downloadApk(
        context: Context,
        update: UpdateInfo,
        onProgress: (Int) -> Unit
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, update.assetName)
        val connection = openConnection(update.downloadUrl)
        try {
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastProgress = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (update.sizeBytes > 0L) {
                            val progress = ((downloaded * 100) / update.sizeBytes).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress.coerceIn(0, 100))
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return apk
    }

    private fun installApk(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    private fun readTextFromUrl(url: String): String {
        return readPage(url).content
    }

    private fun readPage(url: String): PageResponse {
        val connection = openConnection(url)
        try {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            return PageResponse(finalUrl = connection.url.toString(), content = content)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("User-Agent", "TSM-Player/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun parseLatestReleaseTag(html: String): String {
        return Regex("""/gbandszxc/tsm-player/releases/tag/([^"?#<]+)""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("&quot;", "")
            .orEmpty()
    }

    private fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V")

    private fun versionParts(version: String): List<Int> =
        normalizeVersion(version)
            .split('.', '-', '_')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    private data class PageResponse(
        val finalUrl: String,
        val content: String
    )

    private fun CoroutineScope.launchSafely(
        activity: Activity,
        block: suspend CoroutineScope.() -> Unit
    ) {
        launch {
            if (!activity.isFinishing && !activity.isDestroyed) {
                block()
            }
        }
    }
}
