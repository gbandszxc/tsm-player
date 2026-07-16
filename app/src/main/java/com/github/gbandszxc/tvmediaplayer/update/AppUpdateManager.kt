package com.github.gbandszxc.tvmediaplayer.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.github.gbandszxc.tvmediaplayer.BuildConfig
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.ui.modal.ActionModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ListModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalAction
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalListRow
import com.github.gbandszxc.tvmediaplayer.ui.modal.ProgressModalHandle
import com.github.gbandszxc.tvmediaplayer.ui.modal.ProgressModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
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
    private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
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
                                Toast.makeText(activity, R.string.update_latest_version, Toast.LENGTH_SHORT).show()
                            }
                        } else if (silentWhenNoUpdate &&
                            UpdatePromptSnoozeStore(activity).shouldSkipAutomaticPrompt(update.versionName)
                        ) {
                            return@launchSafely
                        } else {
                            showUpdateDialog(activity, update)
                        }
                    },
                    onFailure = { error ->
                        if (!silentWhenNoUpdate) {
                            Toast.makeText(
                                activity,
                                activity.getString(
                                    R.string.update_check_failed,
                                    error.message ?: activity.getString(R.string.update_network_unavailable),
                                ),
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

    private fun showUpdateDialog(activity: Activity, update: UpdateInfo, previewOnly: Boolean = false) {
        TsmModalCoordinator(activity).showActionModal(
            ActionModalSpec(
                sectionLabel = "",
                title = activity.getString(R.string.update_found_title, update.versionName),
                message = activity.getString(R.string.update_found_message, currentAbi(), update.assetName),
                actions = listOf(
                    ModalAction(activity.getString(R.string.update_later)) { showSnoozeOptions(activity, update, previewOnly) },
                    ModalAction(activity.getString(R.string.update_download_install), isPrimary = true) {
                        if (previewOnly) {
                            Toast.makeText(activity, R.string.update_preview_no_download, Toast.LENGTH_SHORT).show()
                        } else {
                            downloadAndInstall(activity, update)
                        }
                    },
                ),
                cancelable = true,
            )
        )
    }

    private fun showSnoozeOptions(activity: Activity, update: UpdateInfo, previewOnly: Boolean = false) {
        val store = UpdatePromptSnoozeStore(activity)
        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            TsmModalCoordinator(activity).showListModal(
                ListModalSpec(
                    sectionLabel = "",
                    title = activity.getString(R.string.update_snooze_title),
                    message = if (previewOnly) {
                        activity.getString(R.string.update_snooze_preview_message)
                    } else {
                        activity.getString(R.string.update_snooze_message)
                    },
                    rows = createSnoozeRows(
                        update,
                        store,
                        previewOnly,
                        onceLabel = activity.getString(R.string.update_snooze_this_session),
                        sevenDaysLabel = activity.getString(R.string.update_snooze_7_days),
                        nextVersionLabel = activity.getString(R.string.update_snooze_next_version),
                    ),
                    cancelable = true,
                )
            )
        }
    }

    internal fun createSnoozeRows(
        update: UpdateInfo,
        store: UpdatePromptSnoozeStore,
        previewOnly: Boolean,
        onceLabel: String = "This session",
        sevenDaysLabel: String = "7 days",
        nextVersionLabel: String = "Next version",
    ): List<ModalListRow> {
        return listOf(
            ModalListRow(
                key = "once",
                label = onceLabel,
                dismissOnClick = true,
            ) {
                if (!previewOnly) store.snoozeOnce(update.versionName)
            },
            ModalListRow(
                key = "seven_days",
                label = sevenDaysLabel,
                dismissOnClick = true,
            ) {
                if (!previewOnly) store.snoozeForSevenDays(update.versionName)
            },
            ModalListRow(
                key = "until_next_version",
                label = nextVersionLabel,
                dismissOnClick = true,
            ) {
                if (!previewOnly) store.snoozeUntilNextVersion(update.versionName)
            },
        )
    }

    private fun downloadAndInstall(activity: Activity, update: UpdateInfo) {
        val coordinator = TsmModalCoordinator(activity)
        val progressHandle = coordinator.showProgressModal(
            ProgressModalSpec(
                sectionLabel = activity.getString(R.string.update_section),
                title = activity.getString(R.string.update_downloading_title),
                fileName = update.assetName,
                initialState = DownloadProgressState(
                    downloadedBytes = 0L,
                    totalBytes = update.sizeBytes,
                    speedBytesPerSecond = 0L,
                ),
                message = activity.getString(R.string.update_download_message),
            )
        )

        MainScope().let { scope ->
            scope.launchSafely(activity) {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        downloadApk(activity, update) { state ->
                            activity.runOnUiThread {
                                progressHandle.onProgress(state)
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
                            activity.getString(
                                R.string.update_download_failed,
                                it.message ?: activity.getString(R.string.update_network_unavailable),
                            ),
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
        onProgress: (DownloadProgressState) -> Unit
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, update.assetName)
        val connection = openConnection(update.downloadUrl)
        try {
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: update.sizeBytes
            val startedAt = System.currentTimeMillis()
            var lastEmitAt = startedAt
            var lastEmitBytes = 0L
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitAt >= PROGRESS_UPDATE_INTERVAL_MS) {
                            val elapsedMs = (now - lastEmitAt).coerceAtLeast(1L)
                            val speed = ((downloaded - lastEmitBytes) * 1000L) / elapsedMs
                            onProgress(
                                DownloadProgressState(
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes,
                                    speedBytesPerSecond = speed,
                                )
                            )
                            lastEmitAt = now
                            lastEmitBytes = downloaded
                        }
                    }
                    val totalElapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                    onProgress(
                        DownloadProgressState(
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            speedBytesPerSecond = (downloaded * 1000L) / totalElapsedMs,
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
        return apk
    }

    fun previewDownloadProgress(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val totalBytes = 42L * 1024L * 1024L
        val progressHandle = TsmModalCoordinator(activity).showProgressModal(
            ProgressModalSpec(
                sectionLabel = activity.getString(R.string.update_section),
                title = activity.getString(R.string.update_downloading_title),
                fileName = "tsm-player-release-${currentAbi()}-preview.apk",
                initialState = DownloadProgressState(
                    downloadedBytes = 0L,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = 0L,
                ),
                message = activity.getString(R.string.update_preview_download_message),
            )
        )
        MainScope().launchSafely(activity) {
            var downloaded = 0L
            var tick = 0
            while (downloaded < totalBytes && !activity.isFinishing && !activity.isDestroyed) {
                delay(100L)
                tick += 1
                val speed = 4_200_000L + (tick % 8) * 220_000L
                downloaded = (downloaded + speed / 10L).coerceAtMost(totalBytes)
                progressHandle.onProgress(
                    DownloadProgressState(
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes,
                        speedBytesPerSecond = speed,
                    )
                )
            }
            delay(500L)
            progressHandle.onDismiss()
        }
    }

    fun previewStartupUpdatePrompt(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        showUpdateDialog(
            activity,
            UpdateInfo(
                versionName = "9.9.9-preview",
                assetName = "tsm-player-release-${currentAbi()}-9.9.9-preview.apk",
                downloadUrl = "https://example.invalid/tsm-player-preview.apk",
                sizeBytes = 0L,
            ),
            previewOnly = true,
        )
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
