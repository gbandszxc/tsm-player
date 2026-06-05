package com.github.gbandszxc.tvmediaplayer.favorites

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackIdentity

class FavoritesDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val appContext = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createFavoritesTables(db)
        createAppSettingsTable(db)
        createSmbTables(db)
        createPlayHistoryTable(db)
        insertDefaultPlaylist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            migrateTracksToSourceAwareIdentity(db)
        }
        if (oldVersion < 3) {
            createAppSettingsTable(db)
            createSmbTables(db)
        }
        if (oldVersion < 4) {
            createPlayHistoryTable(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            insertDefaultPlaylist(db)
            migrateLegacyFavoritesDatabaseIfNeeded(db)
        }
    }

    fun ensureDefaultPlaylist() {
        writableDatabase.use { db ->
            insertDefaultPlaylist(db)
        }
    }

    private fun insertDefaultPlaylist(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", FavoritesRepository.DEFAULT_PLAYLIST_ID)
            put("name", DEFAULT_PLAYLIST_NAME)
            put("is_default", 1)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insertWithOnConflict("playlists", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun createFavoritesTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                is_default INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_tracks (
                id TEXT PRIMARY KEY,
                playlist_id TEXT NOT NULL,
                media_id TEXT NOT NULL,
                track_key TEXT NOT NULL,
                stream_uri TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                artwork_uri TEXT,
                source_connection_id TEXT,
                source_host TEXT,
                source_share TEXT,
                source_path TEXT,
                source_username TEXT,
                source_password TEXT,
                source_guest INTEGER,
                source_smb1 INTEGER,
                added_at INTEGER NOT NULL,
                UNIQUE(playlist_id, track_key),
                FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_playlist_tracks_playlist_added
            ON playlist_tracks(playlist_id, added_at DESC)
            """.trimIndent()
        )
    }

    private fun createAppSettingsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${AppSettingsDbStore.TABLE} (
                ${AppSettingsDbStore.COLUMN_KEY} TEXT PRIMARY KEY,
                ${AppSettingsDbStore.COLUMN_VALUE} TEXT NOT NULL,
                ${AppSettingsDbStore.COLUMN_UPDATED_AT} INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createSmbTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS smb_connections (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                host TEXT NOT NULL,
                share TEXT NOT NULL,
                path TEXT NOT NULL,
                username TEXT NOT NULL,
                password TEXT NOT NULL,
                guest INTEGER NOT NULL,
                smb1 INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS browse_anchors (
                namespace TEXT NOT NULL,
                directory_path TEXT NOT NULL,
                item_key TEXT NOT NULL,
                anchor_index INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(namespace, directory_path)
            )
            """.trimIndent()
        )
    }

    private fun createPlayHistoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS play_history (
                id TEXT PRIMARY KEY,
                track_key TEXT NOT NULL UNIQUE,
                media_id TEXT NOT NULL,
                stream_uri TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                artwork_uri TEXT,
                source_connection_id TEXT,
                source_host TEXT,
                source_share TEXT,
                source_path TEXT,
                source_username TEXT,
                source_password TEXT,
                source_guest INTEGER,
                source_smb1 INTEGER,
                played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_play_history_played_at
            ON play_history(played_at DESC)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_play_history_search
            ON play_history(title, artist, album, media_id)
            """.trimIndent()
        )
    }

    private fun migrateTracksToSourceAwareIdentity(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE playlist_tracks RENAME TO playlist_tracks_legacy")
        db.execSQL(
            """
            CREATE TABLE playlist_tracks (
                id TEXT PRIMARY KEY,
                playlist_id TEXT NOT NULL,
                media_id TEXT NOT NULL,
                track_key TEXT NOT NULL,
                stream_uri TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                artwork_uri TEXT,
                source_connection_id TEXT,
                source_host TEXT,
                source_share TEXT,
                source_path TEXT,
                source_username TEXT,
                source_password TEXT,
                source_guest INTEGER,
                source_smb1 INTEGER,
                added_at INTEGER NOT NULL,
                UNIQUE(playlist_id, track_key),
                FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO playlist_tracks (
                id, playlist_id, media_id, track_key, stream_uri, title, artist, album, artwork_uri,
                source_connection_id, source_host, source_share, source_path, source_username,
                source_password, source_guest, source_smb1, added_at
            )
            SELECT
                id, playlist_id, media_id,
                CASE
                    WHEN source_host IS NULL OR source_host = '' THEN
                        'local' || char(31) || media_id
                    ELSE
                        'smb' || char(31) || source_host || char(31) ||
                        coalesce(source_share, '') || char(31) ||
                        coalesce(source_path, '') || char(31) ||
                        coalesce(source_username, '') || char(31) ||
                        CASE WHEN coalesce(source_guest, 0) = 1 THEN '1' ELSE '0' END || char(31) ||
                        CASE WHEN coalesce(source_smb1, 0) = 1 THEN '1' ELSE '0' END || char(31) ||
                        media_id
                END,
                stream_uri, title, artist, album, artwork_uri,
                source_connection_id, source_host, source_share, source_path, source_username,
                source_password, source_guest, source_smb1, added_at
            FROM playlist_tracks_legacy
            """.trimIndent()
        )
        db.execSQL("DROP TABLE playlist_tracks_legacy")
        db.execSQL(
            """
            CREATE INDEX idx_playlist_tracks_playlist_added
            ON playlist_tracks(playlist_id, added_at DESC)
            """.trimIndent()
        )
    }

    private fun migrateLegacyFavoritesDatabaseIfNeeded(db: SQLiteDatabase) {
        val legacyFile = appContext.getDatabasePath(LEGACY_FAVORITES_DB_NAME)
        if (!legacyFile.exists()) return
        if (hasUserFavoriteData(db)) return

        runCatching {
            SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
                copyLegacyPlaylists(db, legacy)
                copyLegacyTracks(db, legacy)
            }
        }
    }

    private fun hasUserFavoriteData(db: SQLiteDatabase): Boolean {
        db.rawQuery(
            "SELECT COUNT(*) FROM playlists WHERE id != ?",
            arrayOf(FavoritesRepository.DEFAULT_PLAYLIST_ID)
        ).use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) return true
        }
        db.rawQuery("SELECT COUNT(*) FROM playlist_tracks", emptyArray()).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) > 0
        }
    }

    private fun copyLegacyPlaylists(target: SQLiteDatabase, legacy: SQLiteDatabase) {
        legacy.query("playlists", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues().apply {
                    put("id", cursor.getStringValue("id"))
                    put("name", cursor.getStringValue("name"))
                    put("is_default", cursor.getIntValue("is_default") ?: 0)
                    put("created_at", cursor.getLongValue("created_at") ?: System.currentTimeMillis())
                    put("updated_at", cursor.getLongValue("updated_at") ?: System.currentTimeMillis())
                }
                target.insertWithOnConflict("playlists", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }

    private fun copyLegacyTracks(target: SQLiteDatabase, legacy: SQLiteDatabase) {
        if (!legacy.hasTable("playlist_tracks")) return
        val hasTrackKey = legacy.hasColumn("playlist_tracks", "track_key")
        legacy.query("playlist_tracks", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val mediaId = cursor.getStringValue("media_id")
                val sourceConfig = cursor.toLegacySourceConfig()
                val trackKey = if (hasTrackKey) {
                    cursor.getStringValue("track_key")
                } else {
                    FavoriteTrackIdentity.keyOf(mediaId, sourceConfig)
                }
                val values = ContentValues().apply {
                    put("id", cursor.getStringValue("id"))
                    put("playlist_id", cursor.getStringValue("playlist_id"))
                    put("media_id", mediaId)
                    put("track_key", trackKey)
                    put("stream_uri", cursor.getStringValue("stream_uri"))
                    put("title", cursor.getStringValue("title"))
                    putNullable("artist", cursor.getNullableString("artist"))
                    putNullable("album", cursor.getNullableString("album"))
                    putNullable("artwork_uri", cursor.getNullableString("artwork_uri"))
                    putNullable("source_connection_id", cursor.getNullableString("source_connection_id"))
                    putNullable("source_host", cursor.getNullableString("source_host"))
                    putNullable("source_share", cursor.getNullableString("source_share"))
                    putNullable("source_path", cursor.getNullableString("source_path"))
                    putNullable("source_username", cursor.getNullableString("source_username"))
                    putNullable("source_password", cursor.getNullableString("source_password"))
                    putNullable("source_guest", cursor.getIntValue("source_guest"))
                    putNullable("source_smb1", cursor.getIntValue("source_smb1"))
                    put("added_at", cursor.getLongValue("added_at") ?: System.currentTimeMillis())
                }
                target.insertWithOnConflict("playlist_tracks", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }

    private fun SQLiteDatabase.hasTable(table: String): Boolean =
        rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }

    private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        rawQuery("PRAGMA table_info($table)", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) return true
            }
            false
        }

    private fun Cursor.getNullableString(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.getStringValue(column: String): String = getNullableString(column).orEmpty()

    private fun Cursor.getIntValue(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getInt(index)
    }

    private fun Cursor.getLongValue(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private fun Cursor.toLegacySourceConfig(): com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig? {
        val host = getNullableString("source_host") ?: return null
        return com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig(
            host = host,
            share = getNullableString("source_share").orEmpty(),
            path = getNullableString("source_path").orEmpty(),
            username = getNullableString("source_username").orEmpty(),
            password = getNullableString("source_password").orEmpty(),
            guest = getIntValue("source_guest") != 0,
            smb1Enabled = getIntValue("source_smb1") == 1
        )
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: Int?) {
        if (value == null) putNull(key) else put(key, value)
    }

    companion object {
        const val DB_NAME = "tsm-player.db"
        const val LEGACY_FAVORITES_DB_NAME = "favorites.db"
        const val DB_VERSION = 4
        const val DEFAULT_PLAYLIST_NAME = "收藏夹"
    }
}
