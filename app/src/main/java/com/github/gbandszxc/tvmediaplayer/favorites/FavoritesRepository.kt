package com.github.gbandszxc.tvmediaplayer.favorites

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import java.util.UUID

class FavoritesRepository(context: Context) {

    private val dbHelper = FavoritesDbHelper(context.applicationContext)

    fun getPlaylists(): List<FavoritePlaylist> {
        dbHelper.ensureDefaultPlaylist()
        val sql = """
            SELECT
                p.id,
                p.name,
                p.is_default,
                p.created_at,
                p.updated_at,
                (
                    SELECT t.artwork_uri
                    FROM playlist_tracks t
                    WHERE t.playlist_id = p.id
                    ORDER BY t.added_at DESC
                    LIMIT 1
                ) AS cover_artwork_uri,
                COUNT(t.id) AS track_count
            FROM playlists p
            LEFT JOIN playlist_tracks t ON t.playlist_id = p.id
            GROUP BY p.id, p.name, p.is_default, p.created_at, p.updated_at
            ORDER BY p.is_default DESC, p.created_at ASC
        """.trimIndent()
        return dbHelper.readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toFavoritePlaylist())
                }
            }
        }
    }

    fun createPlaylist(rawName: String): FavoritePlaylist? {
        val name = rawName.trim()
        if (name.isBlank()) return null

        dbHelper.ensureDefaultPlaylist()
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("name", name)
            put("is_default", 0)
            put("created_at", now)
            put("updated_at", now)
        }
        val insertedId = dbHelper.writableDatabase.insertWithOnConflict(
            "playlists",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (insertedId == -1L) return null

        return getPlaylists().firstOrNull { it.name == name }
    }

    fun getTracks(playlistId: String): List<FavoriteTrack> =
        dbHelper.readableDatabase.query(
            "playlist_tracks",
            null,
            "playlist_id = ?",
            arrayOf(playlistId),
            null,
            null,
            "added_at DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toFavoriteTrack())
                }
            }
        }

    fun containsTrack(playlistId: String, mediaId: String): Boolean =
        dbHelper.readableDatabase.query(
            "playlist_tracks",
            arrayOf("id"),
            "playlist_id = ? AND media_id = ?",
            arrayOf(playlistId, mediaId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            cursor.moveToFirst()
        }

    fun addTrack(playlistId: String, track: FavoriteTrack): Boolean {
        dbHelper.ensureDefaultPlaylist()
        val db = dbHelper.writableDatabase
        if (!playlistExists(db, playlistId)) return false

        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("playlist_id", playlistId)
            put("media_id", track.mediaId)
            put("stream_uri", track.streamUri)
            put("title", track.title)
            putNullable("artist", track.artist)
            putNullable("album", track.album)
            putNullable("artwork_uri", track.artworkUri)
            putNullable("source_connection_id", track.sourceConnectionId)
            putSourceConfig(track.sourceConfig)
            put("added_at", track.addedAt)
        }
        val insertedId = db.insertWithOnConflict(
            "playlist_tracks",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (insertedId == -1L) return false

        updatePlaylistTimestamp(db, playlistId)
        return true
    }

    private fun playlistExists(db: SQLiteDatabase, playlistId: String): Boolean =
        db.query(
            "playlists",
            arrayOf("id"),
            "id = ?",
            arrayOf(playlistId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            cursor.moveToFirst()
        }

    fun removeTrack(playlistId: String, mediaId: String): Boolean {
        val deleted = dbHelper.writableDatabase.delete(
            "playlist_tracks",
            "playlist_id = ? AND media_id = ?",
            arrayOf(playlistId, mediaId)
        )
        return deleted > 0
    }

    private fun updatePlaylistTimestamp(db: SQLiteDatabase, playlistId: String) {
        val values = ContentValues().apply {
            put("updated_at", System.currentTimeMillis())
        }
        db.update("playlists", values, "id = ?", arrayOf(playlistId))
    }

    private fun Cursor.toFavoritePlaylist(): FavoritePlaylist =
        FavoritePlaylist(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            isDefault = getInt(getColumnIndexOrThrow("is_default")) == 1,
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            coverArtworkUri = getNullableString("cover_artwork_uri"),
            trackCount = getInt(getColumnIndexOrThrow("track_count"))
        )

    private fun Cursor.toFavoriteTrack(): FavoriteTrack =
        FavoriteTrack(
            id = getString(getColumnIndexOrThrow("id")),
            playlistId = getString(getColumnIndexOrThrow("playlist_id")),
            mediaId = getString(getColumnIndexOrThrow("media_id")),
            streamUri = getString(getColumnIndexOrThrow("stream_uri")),
            title = getString(getColumnIndexOrThrow("title")),
            artist = getNullableString("artist"),
            album = getNullableString("album"),
            artworkUri = getNullableString("artwork_uri"),
            sourceConnectionId = getNullableString("source_connection_id"),
            sourceConfig = getSourceConfig(),
            addedAt = getLong(getColumnIndexOrThrow("added_at"))
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
            smb1Enabled = getNullableInt("source_smb1") == 1
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
        if (value == null) {
            putNull(key)
        } else {
            put(key, value)
        }
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
        const val DEFAULT_PLAYLIST_ID = "default_favorites"
    }
}
