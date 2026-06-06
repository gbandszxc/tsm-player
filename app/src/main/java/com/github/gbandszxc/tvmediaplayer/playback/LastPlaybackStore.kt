package com.github.gbandszxc.tvmediaplayer.playback

import android.content.Context
import android.content.SharedPreferences
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.json.JSONArray

object LastPlaybackStore {

    private const val PREF_NAME = "last_playback"
    private const val DB_PREFIX = "playback.last."
    private const val KEY_QUEUE_URIS = "queue_uris"
    private const val KEY_QUEUE_IDS = "queue_media_ids"
    private const val KEY_INDEX = "current_index"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_TITLE = "current_title"
    private const val KEY_ARTIST = "current_artist"
    private const val KEY_ALBUM_TITLE = "current_album_title"
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
        val artist: String = "",
        val albumTitle: String = "",
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
                artist: String = "",
                albumTitle: String = "",
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
                    artist = artist,
                    albumTitle = albumTitle,
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
        val db = db(context)
        db.putString(dbKey(KEY_QUEUE_URIS), JSONArray(snapshot.queueUris).toString())
        db.putString(dbKey(KEY_QUEUE_IDS), JSONArray(snapshot.queueMediaIds).toString())
        db.putInt(dbKey(KEY_INDEX), snapshot.currentIndex)
        db.putLong(dbKey(KEY_POSITION_MS), snapshot.positionMs)
        db.putString(dbKey(KEY_TITLE), snapshot.title)
        db.putString(dbKey(KEY_ARTIST), snapshot.artist)
        db.putString(dbKey(KEY_ALBUM_TITLE), snapshot.albumTitle)
        putNullable(db, dbKey(KEY_CURRENT_MEDIA_ID), snapshot.currentMediaId)
        putNullable(db, dbKey(KEY_CURRENT_DIRECTORY_PATH), snapshot.currentDirectoryPath)
        putNullable(db, dbKey(KEY_SOURCE_CONNECTION_ID), snapshot.sourceConnectionId)

        if (snapshot.sourceConfig == null) {
            db.remove(dbKey(KEY_SOURCE_HOST))
            db.remove(dbKey(KEY_SOURCE_SHARE))
            db.remove(dbKey(KEY_SOURCE_PATH))
            db.remove(dbKey(KEY_SOURCE_USERNAME))
            db.remove(dbKey(KEY_SOURCE_PASSWORD))
            db.remove(dbKey(KEY_SOURCE_GUEST))
            db.remove(dbKey(KEY_SOURCE_SMB1))
        } else {
            db.putString(dbKey(KEY_SOURCE_HOST), snapshot.sourceConfig.host)
            db.putString(dbKey(KEY_SOURCE_SHARE), snapshot.sourceConfig.share)
            db.putString(dbKey(KEY_SOURCE_PATH), snapshot.sourceConfig.path)
            db.putString(dbKey(KEY_SOURCE_USERNAME), snapshot.sourceConfig.username)
            db.putString(dbKey(KEY_SOURCE_PASSWORD), snapshot.sourceConfig.password)
            db.putBoolean(dbKey(KEY_SOURCE_GUEST), snapshot.sourceConfig.guest)
            db.putBoolean(dbKey(KEY_SOURCE_SMB1), snapshot.sourceConfig.smb1Enabled)
        }
    }

    fun load(context: Context): Snapshot? {
        migrateLegacyIfNeeded(context)
        val db = db(context)
        val urisJson = db.getString(dbKey(KEY_QUEUE_URIS)) ?: return null
        val idsJson = db.getString(dbKey(KEY_QUEUE_IDS)) ?: return null
        return runCatching {
            val uris = JSONArray(urisJson).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            val ids = JSONArray(idsJson).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            if (uris.isEmpty()) return null
            Snapshot.fromStoredValues(
                queueUris = uris,
                queueMediaIds = ids,
                currentIndex = db.getInt(dbKey(KEY_INDEX)) ?: 0,
                positionMs = db.getLong(dbKey(KEY_POSITION_MS)) ?: 0L,
                title = db.getString(dbKey(KEY_TITLE)).orEmpty(),
                artist = db.getString(dbKey(KEY_ARTIST)).orEmpty(),
                albumTitle = db.getString(dbKey(KEY_ALBUM_TITLE)).orEmpty(),
                currentMediaId = db.getString(dbKey(KEY_CURRENT_MEDIA_ID)),
                currentDirectoryPath = db.getString(dbKey(KEY_CURRENT_DIRECTORY_PATH)),
                sourceConnectionId = db.getString(dbKey(KEY_SOURCE_CONNECTION_ID)),
                sourceConfig = readSourceConfig(db)
            )
        }.getOrNull()
    }

    fun hasSnapshot(context: Context): Boolean {
        migrateLegacyIfNeeded(context)
        val json = db(context).getString(dbKey(KEY_QUEUE_URIS)) ?: return false
        return runCatching { JSONArray(json).length() > 0 }.getOrDefault(false)
    }

    fun clear(context: Context) {
        db(context).removePrefix(DB_PREFIX)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun db(context: Context) = AppSettingsDbStore(context)

    private fun dbKey(key: String): String = "$DB_PREFIX$key"

    private fun readSourceConfig(db: AppSettingsDbStore): SmbConfig? {
        val host = db.getString(dbKey(KEY_SOURCE_HOST))
        if (host.isNullOrBlank()) return null
        return SmbConfig(
            host = host,
            share = db.getString(dbKey(KEY_SOURCE_SHARE)).orEmpty(),
            path = db.getString(dbKey(KEY_SOURCE_PATH)).orEmpty(),
            username = db.getString(dbKey(KEY_SOURCE_USERNAME)).orEmpty(),
            password = db.getString(dbKey(KEY_SOURCE_PASSWORD)).orEmpty(),
            guest = db.getBoolean(dbKey(KEY_SOURCE_GUEST)) ?: true,
            smb1Enabled = db.getBoolean(dbKey(KEY_SOURCE_SMB1)) ?: false
        )
    }

    private fun migrateLegacyIfNeeded(context: Context) {
        val db = db(context)
        if (db.getString(dbKey(KEY_QUEUE_URIS)) != null) return
        val prefs = prefs(context)
        val uris = prefs.getString(KEY_QUEUE_URIS, null) ?: return
        db.putString(dbKey(KEY_QUEUE_URIS), uris)
        db.putString(dbKey(KEY_QUEUE_IDS), prefs.getString(KEY_QUEUE_IDS, "[]").orEmpty())
        db.putInt(dbKey(KEY_INDEX), prefs.getInt(KEY_INDEX, 0))
        db.putLong(dbKey(KEY_POSITION_MS), prefs.getLong(KEY_POSITION_MS, 0L))
        db.putString(dbKey(KEY_TITLE), prefs.getString(KEY_TITLE, "").orEmpty())
        db.putString(dbKey(KEY_ARTIST), "")
        db.putString(dbKey(KEY_ALBUM_TITLE), "")
        putNullable(db, dbKey(KEY_CURRENT_MEDIA_ID), prefs.getString(KEY_CURRENT_MEDIA_ID, null))
        putNullable(db, dbKey(KEY_CURRENT_DIRECTORY_PATH), prefs.getString(KEY_CURRENT_DIRECTORY_PATH, null))
        putNullable(db, dbKey(KEY_SOURCE_CONNECTION_ID), prefs.getString(KEY_SOURCE_CONNECTION_ID, null))
        migrateNullable(db, prefs, KEY_SOURCE_HOST)
        migrateNullable(db, prefs, KEY_SOURCE_SHARE)
        migrateNullable(db, prefs, KEY_SOURCE_PATH)
        migrateNullable(db, prefs, KEY_SOURCE_USERNAME)
        migrateNullable(db, prefs, KEY_SOURCE_PASSWORD)
        if (prefs.contains(KEY_SOURCE_GUEST)) db.putBoolean(dbKey(KEY_SOURCE_GUEST), prefs.getBoolean(KEY_SOURCE_GUEST, true))
        if (prefs.contains(KEY_SOURCE_SMB1)) db.putBoolean(dbKey(KEY_SOURCE_SMB1), prefs.getBoolean(KEY_SOURCE_SMB1, false))
    }

    private fun migrateNullable(db: AppSettingsDbStore, prefs: SharedPreferences, key: String) {
        putNullable(db, dbKey(key), prefs.getString(key, null))
    }

    private fun putNullable(db: AppSettingsDbStore, key: String, value: String?) {
        if (value == null) db.remove(key) else db.putString(key, value)
    }
}
