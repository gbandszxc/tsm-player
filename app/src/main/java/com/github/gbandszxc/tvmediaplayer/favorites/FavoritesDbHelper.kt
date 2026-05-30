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
                UNIQUE(playlist_id, media_id),
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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

    companion object {
        const val DB_NAME = "favorites.db"
        const val DB_VERSION = 1
        const val DEFAULT_PLAYLIST_NAME = "收藏夹"
    }
}
