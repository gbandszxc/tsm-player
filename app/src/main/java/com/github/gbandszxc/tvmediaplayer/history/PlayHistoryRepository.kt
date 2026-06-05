package com.github.gbandszxc.tvmediaplayer.history

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackIdentity
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesDbHelper
import java.util.UUID

class PlayHistoryRepository(context: Context) {

    private val dbHelper = FavoritesDbHelper(context.applicationContext)

    fun record(track: PlayHistoryTrack) {
        val db = dbHelper.writableDatabase
        val trackKey = FavoriteTrackIdentity.keyOf(track.mediaId, track.sourceConfig)
        val values = ContentValues().apply {
            put("id", existingId(db, trackKey) ?: UUID.randomUUID().toString())
            put("track_key", trackKey)
            put("media_id", track.mediaId)
            put("stream_uri", track.streamUri)
            put("title", track.title.ifBlank { PlayHistoryTrackFactory.displayFileName(track.mediaId.ifBlank { track.streamUri }) })
            putNullable("artist", track.artist)
            putNullable("album", track.album)
            putNullable("artwork_uri", track.artworkUri)
            putNullable("source_connection_id", track.sourceConnectionId)
            putSourceConfig(track.sourceConfig)
            put("played_at", track.playedAt)
        }
        db.insertWithOnConflict("play_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        trimOldRows(db)
    }

    fun query(page: Int, query: String): PlayHistoryPage {
        val safePage = page.coerceAtLeast(0)
        val where = buildSearchWhere(query)
        val args = buildSearchArgs(query)
        val total = count(where, args)
        val offset = safePage * PAGE_SIZE
        val items = dbHelper.readableDatabase.query(
            "play_history",
            null,
            where,
            args,
            null,
            null,
            "played_at DESC",
            "$offset,$PAGE_SIZE",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toPlayHistoryTrack())
                }
            }
        }
        return PlayHistoryPage(items, safePage, PAGE_SIZE, total)
    }

    private fun count(where: String?, args: Array<String>?): Int =
        dbHelper.readableDatabase.query(
            "play_history",
            arrayOf("COUNT(*)"),
            where,
            args,
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun buildSearchWhere(query: String): String? {
        if (query.isBlank()) return null
        return """
            media_id LIKE ? COLLATE NOCASE
            OR stream_uri LIKE ? COLLATE NOCASE
            OR title LIKE ? COLLATE NOCASE
            OR artist LIKE ? COLLATE NOCASE
            OR album LIKE ? COLLATE NOCASE
        """.trimIndent()
    }

    private fun buildSearchArgs(query: String): Array<String>? {
        if (query.isBlank()) return null
        val like = "%${query.trim()}%"
        return arrayOf(like, like, like, like, like)
    }

    private fun existingId(db: SQLiteDatabase, trackKey: String): String? =
        db.query(
            "play_history",
            arrayOf("id"),
            "track_key = ?",
            arrayOf(trackKey),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun trimOldRows(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM play_history
            WHERE id IN (
                SELECT id FROM play_history
                ORDER BY played_at DESC
                LIMIT -1 OFFSET $MAX_HISTORY_COUNT
            )
            """.trimIndent()
        )
    }

    private fun Cursor.toPlayHistoryTrack(): PlayHistoryTrack =
        PlayHistoryTrack(
            id = getString(getColumnIndexOrThrow("id")),
            mediaId = getString(getColumnIndexOrThrow("media_id")),
            streamUri = getString(getColumnIndexOrThrow("stream_uri")),
            title = getString(getColumnIndexOrThrow("title")),
            artist = getNullableString("artist"),
            album = getNullableString("album"),
            artworkUri = getNullableString("artwork_uri"),
            sourceConnectionId = getNullableString("source_connection_id"),
            sourceConfig = getSourceConfig(),
            playedAt = getLong(getColumnIndexOrThrow("played_at")),
        )

    private fun Cursor.getSourceConfig(): SmbConfig? {
        val host = getNullableString("source_host") ?: return null
        return SmbConfig(
            host = host,
            share = getNullableString("source_share").orEmpty(),
            path = getNullableString("source_path").orEmpty(),
            username = getNullableString("source_username").orEmpty(),
            password = getNullableString("source_password").orEmpty(),
            guest = getNullableInt("source_guest") != 0,
            smb1Enabled = getNullableInt("source_smb1") == 1,
        )
    }

    private fun Cursor.getNullableString(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getNullableInt(columnName: String): Int? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getInt(index)
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putSourceConfig(sourceConfig: SmbConfig?) {
        if (sourceConfig == null) {
            putNull("source_host")
            putNull("source_share")
            putNull("source_path")
            putNull("source_username")
            putNull("source_password")
            putNull("source_guest")
            putNull("source_smb1")
            return
        }
        put("source_host", sourceConfig.host)
        put("source_share", sourceConfig.share)
        put("source_path", sourceConfig.path)
        put("source_username", sourceConfig.username)
        put("source_password", sourceConfig.password)
        put("source_guest", if (sourceConfig.guest) 1 else 0)
        put("source_smb1", if (sourceConfig.smb1Enabled) 1 else 0)
    }

    companion object {
        const val PAGE_SIZE = 50
        const val MAX_HISTORY_COUNT = 1_000
    }
}
