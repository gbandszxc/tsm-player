package com.github.gbandszxc.tvmediaplayer.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesDbHelper
import java.io.File

enum class BackupOperationStatus {
    SUCCESS,
    MISSING_SOURCE,
    INVALID_SOURCE,
    FAILED,
}

data class BackupOperationResult(
    val status: BackupOperationStatus,
    val file: File,
    val message: String = "",
)

class SqliteBackupManager(context: Context) {
    private val appContext = context.applicationContext

    fun localBackupFile(): File =
        File(backupDir(), FavoritesDbHelper.DB_NAME)

    fun exportToLocalBackup(): BackupOperationResult =
        exportToFile(localBackupFile())

    fun importFromLocalBackup(): BackupOperationResult =
        importFromFile(localBackupFile())

    fun exportToFile(target: File): BackupOperationResult {
        val source = appContext.getDatabasePath(FavoritesDbHelper.DB_NAME)
        if (!source.exists()) {
            FavoritesDbHelper(appContext).use { it.ensureDefaultPlaylist() }
        }
        return runCatching {
            checkpointDatabase()
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            BackupOperationResult(BackupOperationStatus.SUCCESS, target)
        }.getOrElse { ex ->
            BackupOperationResult(BackupOperationStatus.FAILED, target, ex.message.orEmpty())
        }
    }

    fun importFromFile(source: File): BackupOperationResult {
        if (!source.exists()) {
            return BackupOperationResult(BackupOperationStatus.MISSING_SOURCE, source)
        }
        val verifiedCopy = File(appContext.cacheDir, "favorites-backup-verify.db")
        runCatching {
            source.copyTo(verifiedCopy, overwrite = true)
        }.getOrElse { ex ->
            return BackupOperationResult(BackupOperationStatus.FAILED, source, ex.message.orEmpty())
        }

        if (!isValidFavoritesDatabase(verifiedCopy)) {
            verifiedCopy.delete()
            return BackupOperationResult(BackupOperationStatus.INVALID_SOURCE, source)
        }

        val target = appContext.getDatabasePath(FavoritesDbHelper.DB_NAME)
        return runCatching {
            target.parentFile?.mkdirs()
            deleteWalFiles(target)
            verifiedCopy.copyTo(target, overwrite = true)
            FavoritesDbHelper(appContext).use { it.ensureDefaultPlaylist() }
            verifiedCopy.delete()
            BackupOperationResult(BackupOperationStatus.SUCCESS, source)
        }.getOrElse { ex ->
            verifiedCopy.delete()
            BackupOperationResult(BackupOperationStatus.FAILED, source, ex.message.orEmpty())
        }
    }

    private fun backupDir(): File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "backups").apply { mkdirs() }

    private fun checkpointDatabase() {
        FavoritesDbHelper(appContext).use { helper ->
            helper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray()).use { cursor ->
                if (cursor.moveToFirst()) {
                    // Force query execution before closing the database.
                }
            }
        }
    }

    private fun isValidFavoritesDatabase(file: File): Boolean {
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                REQUIRED_TABLES.all { table ->
                    db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
                        arrayOf(table)
                    ).use { cursor -> cursor.moveToFirst() }
                }
            }
        }.getOrDefault(false)
    }

    private fun deleteWalFiles(databaseFile: File) {
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
    }

    private companion object {
        val REQUIRED_TABLES = listOf(
            "playlists",
            "playlist_tracks",
            "app_settings",
            "smb_connections",
            "browse_anchors"
        )
    }
}
