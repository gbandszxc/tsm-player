package com.github.gbandszxc.tvmediaplayer.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SqliteBackupManagerTest {

    private lateinit var context: Context
    private lateinit var manager: SqliteBackupManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
        manager = SqliteBackupManager(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
        manager.localBackupFile().delete()
    }

    @Test
    fun `export copies the current favorites sqlite database to the local backup file`() {
        createDatabaseWithPlaylist("night", "夜间")

        val result = manager.exportToLocalBackup()

        assertEquals(BackupOperationStatus.SUCCESS, result.status)
        assertEquals(manager.localBackupFile(), result.file)
        assertTrue(result.file.length() > 0L)
        assertEquals(listOf("收藏夹", "夜间"), readPlaylistNames(result.file.path))
    }

    @Test
    fun `import replaces the current favorites sqlite database from the local backup file`() {
        createDatabaseWithPlaylist("source", "备份歌单")
        assertEquals(BackupOperationStatus.SUCCESS, manager.exportToLocalBackup().status)
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
        createDatabaseWithPlaylist("current", "当前歌单")

        val result = manager.importFromLocalBackup()

        assertEquals(BackupOperationStatus.SUCCESS, result.status)
        assertEquals(listOf("收藏夹", "备份歌单"), readPlaylistNames(context.getDatabasePath(FavoritesDbHelper.DB_NAME).path))
    }

    @Test
    fun `import reports missing backup without changing the current database`() {
        createDatabaseWithPlaylist("current", "当前歌单")

        val result = manager.importFromLocalBackup()

        assertEquals(BackupOperationStatus.MISSING_SOURCE, result.status)
        assertEquals(listOf("收藏夹", "当前歌单"), readPlaylistNames(context.getDatabasePath(FavoritesDbHelper.DB_NAME).path))
    }

    private fun createDatabaseWithPlaylist(id: String, name: String) {
        FavoritesDbHelper(context).use { helper ->
            helper.writableDatabase.use { db ->
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO playlists (id, name, is_default, created_at, updated_at)
                    VALUES (?, ?, 0, ?, ?)
                    """.trimIndent(),
                    arrayOf(id, name, now, now)
                )
            }
        }
    }

    private fun readPlaylistNames(path: String): List<String> {
        return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM playlists ORDER BY is_default DESC, name ASC", emptyArray()).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
        }
    }
}
