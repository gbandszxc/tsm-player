package com.github.gbandszxc.tvmediaplayer.favorites

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FavoritesDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE playlists (
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
            CREATE INDEX idx_playlist_tracks_playlist_added
            ON playlist_tracks(playlist_id, added_at DESC)
            """.trimIndent()
        )
        insertDefaultPlaylist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            migrateTracksToSourceAwareIdentity(db)
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

    companion object {
        const val DB_NAME = "favorites.db"
        const val DB_VERSION = 2
        const val DEFAULT_PLAYLIST_NAME = "收藏夹"
    }
}
