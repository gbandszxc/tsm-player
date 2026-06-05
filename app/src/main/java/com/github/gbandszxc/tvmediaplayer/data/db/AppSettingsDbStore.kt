package com.github.gbandszxc.tvmediaplayer.data.db

import android.content.ContentValues
import android.content.Context
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesDbHelper

class AppSettingsDbStore(context: Context) {
    private val dbHelper = FavoritesDbHelper(context.applicationContext)

    fun getString(key: String): String? =
        dbHelper.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_VALUE),
            "$COLUMN_KEY = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    fun putString(key: String, value: String) {
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_VALUE, value)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun remove(key: String) {
        dbHelper.writableDatabase.delete(TABLE, "$COLUMN_KEY = ?", arrayOf(key))
    }

    fun removePrefix(prefix: String) {
        dbHelper.writableDatabase.delete(TABLE, "$COLUMN_KEY LIKE ?", arrayOf("$prefix%"))
    }

    fun getInt(key: String): Int? = getString(key)?.toIntOrNull()
    fun putInt(key: String, value: Int) = putString(key, value.toString())
    fun getLong(key: String): Long? = getString(key)?.toLongOrNull()
    fun putLong(key: String, value: Long) = putString(key, value.toString())
    fun getFloat(key: String): Float? = getString(key)?.toFloatOrNull()
    fun putFloat(key: String, value: Float) = putString(key, value.toString())
    fun getBoolean(key: String): Boolean? = getString(key)?.toBooleanStrictOrNull()
    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    companion object {
        const val TABLE = "app_settings"
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
