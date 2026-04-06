package com.github.gbandszxc.tvmediaplayer.playback

import android.content.Context
import android.content.SharedPreferences
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.json.JSONArray

object LastPlaybackStore {

    private const val PREF_NAME = "last_playback"
    private const val KEY_QUEUE_URIS = "queue_uris"
    private const val KEY_QUEUE_IDS = "queue_media_ids"
    private const val KEY_INDEX = "current_index"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_TITLE = "current_title"
    private const val KEY_CURRENT_MEDIA_ID = "current_media_id"
    private const val KEY_CURRENT_DIRECTORY_PATH = "current_directory_path"
    private const val KEY_SOURCE_CONNECTION_ID = "source_connection_id"
    private const val KEY_SOURCE_HOST = "source_host"
    private const val KEY_SOURCE_SHARE = "source_share"
    private const val KEY_SOURCE_PATH = "source_path"
    private const val KEY_SOURCE_USERNAME = "source_username"
    private const val KEY_SOURCE_PASSWORD = "source_password"
    private const val KEY_SOURCE_GUEST = "source_guest"
    private const val KEY_SOURCE_SMB1 = "source_smb1"

    data class Snapshot(
        val queueUris: List<String>,
        val queueMediaIds: List<String>,
        val currentIndex: Int,
        val positionMs: Long,
        val title: String,
        val currentMediaId: String? = null,
        val currentDirectoryPath: String? = null,
        val sourceConnectionId: String? = null,
        val sourceConfig: SmbConfig? = null
    ) {
        companion object {
            internal fun fromStoredValues(
                queueUris: List<String>,
                queueMediaIds: List<String>,
                currentIndex: Int,
                positionMs: Long,
                title: String,
                currentMediaId: String?,
                currentDirectoryPath: String?,
                sourceConnectionId: String?,
                sourceConfig: SmbConfig?
            ): Snapshot {
                val resolvedMediaId = deriveMediaId(currentMediaId, queueMediaIds, currentIndex)
                val resolvedDirectory = currentDirectoryPath?.let(::normalizePath)
                    ?: parentDirectory(resolvedMediaId)
                val normalizedConnectionId = sourceConnectionId?.trim()?.takeIf { it.isNotBlank() }
                return Snapshot(
                    queueUris = queueUris,
                    queueMediaIds = queueMediaIds,
                    currentIndex = currentIndex,
                    positionMs = positionMs,
                    title = title,
                    currentMediaId = resolvedMediaId,
                    currentDirectoryPath = resolvedDirectory,
                    sourceConnectionId = normalizedConnectionId,
                    sourceConfig = sourceConfig
                )
            }

            private fun deriveMediaId(
                currentMediaId: String?,
                queueMediaIds: List<String>,
                currentIndex: Int
            ): String? {
                return normalizePath(currentMediaId)
                    ?: queueMediaIds
                        .getOrNull(currentIndex)
                        ?.let(::normalizePath)
                    ?: queueMediaIds
                        .lastOrNull()
                        ?.let(::normalizePath)
            }

            private fun normalizePath(raw: String?): String? {
                if (raw == null) return null
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return ""
                return trimmed
                    .split('/', '\\')
                    .filter { it.isNotBlank() }
                    .joinToString("/")
            }

            private fun parentDirectory(mediaId: String?): String? {
                val normalized = normalizePath(mediaId) ?: return null
                val lastSlash = normalized.lastIndexOf('/')
                return if (lastSlash >= 0) normalized.substring(0, lastSlash) else ""
            }
        }
    }

    fun save(context: Context, snapshot: Snapshot) {
        val editor = prefs(context).edit()
            .putString(KEY_QUEUE_URIS, JSONArray(snapshot.queueUris).toString())
            .putString(KEY_QUEUE_IDS, JSONArray(snapshot.queueMediaIds).toString())
            .putInt(KEY_INDEX, snapshot.currentIndex)
            .putLong(KEY_POSITION_MS, snapshot.positionMs)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_CURRENT_MEDIA_ID, snapshot.currentMediaId)
            .putString(KEY_CURRENT_DIRECTORY_PATH, snapshot.currentDirectoryPath)
            .putString(KEY_SOURCE_CONNECTION_ID, snapshot.sourceConnectionId)

        if (snapshot.sourceConfig == null) {
            editor
                .remove(KEY_SOURCE_HOST)
                .remove(KEY_SOURCE_SHARE)
                .remove(KEY_SOURCE_PATH)
                .remove(KEY_SOURCE_USERNAME)
                .remove(KEY_SOURCE_PASSWORD)
                .remove(KEY_SOURCE_GUEST)
                .remove(KEY_SOURCE_SMB1)
        } else {
            editor
                .putString(KEY_SOURCE_HOST, snapshot.sourceConfig.host)
                .putString(KEY_SOURCE_SHARE, snapshot.sourceConfig.share)
                .putString(KEY_SOURCE_PATH, snapshot.sourceConfig.path)
                .putString(KEY_SOURCE_USERNAME, snapshot.sourceConfig.username)
                .putString(KEY_SOURCE_PASSWORD, snapshot.sourceConfig.password)
                .putBoolean(KEY_SOURCE_GUEST, snapshot.sourceConfig.guest)
                .putBoolean(KEY_SOURCE_SMB1, snapshot.sourceConfig.smb1Enabled)
        }
        editor.commit()
    }

    fun load(context: Context): Snapshot? {
        val prefs = prefs(context)
        val urisJson = prefs.getString(KEY_QUEUE_URIS, null) ?: return null
        val idsJson = prefs.getString(KEY_QUEUE_IDS, null) ?: return null
        return runCatching {
            val uris = JSONArray(urisJson).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            val ids = JSONArray(idsJson).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            if (uris.isEmpty()) return null
            Snapshot.fromStoredValues(
                queueUris = uris,
                queueMediaIds = ids,
                currentIndex = prefs.getInt(KEY_INDEX, 0),
                positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
                title = prefs.getString(KEY_TITLE, "").orEmpty(),
                currentMediaId = prefs.getString(KEY_CURRENT_MEDIA_ID, null),
                currentDirectoryPath = prefs.getString(KEY_CURRENT_DIRECTORY_PATH, null),
                sourceConnectionId = prefs.getString(KEY_SOURCE_CONNECTION_ID, null),
                sourceConfig = readSourceConfig(prefs)
            )
        }.getOrNull()
    }

    fun hasSnapshot(context: Context): Boolean {
        val json = prefs(context).getString(KEY_QUEUE_URIS, null) ?: return false
        return runCatching { JSONArray(json).length() > 0 }.getOrDefault(false)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun readSourceConfig(prefs: SharedPreferences): SmbConfig? {
        val host = prefs.getString(KEY_SOURCE_HOST, null)
        if (host.isNullOrBlank()) return null
        return SmbConfig(
            host = host,
            share = prefs.getString(KEY_SOURCE_SHARE, "").orEmpty(),
            path = prefs.getString(KEY_SOURCE_PATH, "").orEmpty(),
            username = prefs.getString(KEY_SOURCE_USERNAME, "").orEmpty(),
            password = prefs.getString(KEY_SOURCE_PASSWORD, "").orEmpty(),
            guest = prefs.getBoolean(KEY_SOURCE_GUEST, true),
            smb1Enabled = prefs.getBoolean(KEY_SOURCE_SMB1, false)
        )
    }
}
