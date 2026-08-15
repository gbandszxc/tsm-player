package com.github.gbandszxc.tvmediaplayer.data.repo

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.gbandszxc.tvmediaplayer.domain.model.BrowseFocusAnchor
import com.github.gbandszxc.tvmediaplayer.domain.model.SavedSmbConnection
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.data.db.AppSettingsDbStore
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesDbHelper
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.smbDataStore by preferencesDataStore(name = "smb_config")

data class SmbConfigStoreState(
    val activeConfig: SmbConfig,
    val activeConnectionId: String?,
    val savedConnections: List<SavedSmbConnection>,
    val activeBrowsePath: String = activeConfig.normalizedPath()
)

interface BrowserConfigStore {
    suspend fun loadState(): SmbConfigStoreState
    suspend fun saveConnection(connection: SavedSmbConnection, activate: Boolean = true)
    suspend fun setActiveConnection(id: String)
    suspend fun deleteConnection(id: String): SmbConfigStoreState
    suspend fun setActiveConfig(config: SmbConfig, browsePath: String)
    suspend fun saveActiveBrowsePath(path: String)
    suspend fun loadBrowseAnchor(connectionId: String?, directoryPath: String): BrowseFocusAnchor?
    suspend fun saveBrowseAnchor(connectionId: String?, directoryPath: String, anchor: BrowseFocusAnchor)
    suspend fun clearBrowseCache()
    fun newConnectionId(): String
}

class SmbConfigStore(private val appContext: Context) : BrowserConfigStore {
    private val dbHelper = FavoritesDbHelper.shared(appContext)
    private val settings = AppSettingsDbStore(appContext)

    /** 进程内迁移只跑一次；之后各方法不再重复探测 legacy 状态 */
    @Volatile
    private var migrationChecked = false

    override suspend fun loadState(): SmbConfigStoreState = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        loadStateFromDb()
    }

    private fun loadStateFromDb(): SmbConfigStoreState {
        val saved = loadSavedConnectionsFromDb()
        val activeId = settings.getString(DB_KEY_ACTIVE_CONNECTION_ID)
        val activeBrowsePath = settings.getString(DB_KEY_ACTIVE_BROWSE_PATH)

        if (saved.isNotEmpty()) {
            val active = saved.firstOrNull { it.id == activeId } ?: saved.first()
            return SmbConfigStoreState(
                activeConfig = active.config,
                activeConnectionId = active.id,
                savedConnections = saved,
                activeBrowsePath = activeBrowsePath ?: active.config.normalizedPath()
            )
        }

        val activeConfig = readActiveConfigFromDb()
        return SmbConfigStoreState(
            activeConfig = activeConfig,
            activeConnectionId = null,
            savedConnections = emptyList(),
            activeBrowsePath = activeBrowsePath ?: activeConfig.normalizedPath()
        )
    }

    private suspend fun loadLegacyState(): SmbConfigStoreState {
        val preferences = appContext.smbDataStore.data
            .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
            .first()

        val saved = decodeSaved(preferences[Keys.SAVED_CONNECTIONS_JSON])
        val activeId = preferences[Keys.ACTIVE_CONNECTION_ID]
        val activeBrowsePath = preferences[Keys.ACTIVE_BROWSE_PATH]

        if (saved.isNotEmpty()) {
            val active = saved.firstOrNull { it.id == activeId } ?: saved.first()
            return SmbConfigStoreState(
                activeConfig = active.config,
                activeConnectionId = active.id,
                savedConnections = saved,
                activeBrowsePath = activeBrowsePath ?: active.config.normalizedPath()
            )
        }

        val legacyConfig = preferences.toLegacyConfig()
        return SmbConfigStoreState(
            activeConfig = legacyConfig,
            activeConnectionId = null,
            savedConnections = emptyList(),
            activeBrowsePath = activeBrowsePath ?: legacyConfig.normalizedPath()
        )
    }

    override suspend fun saveConnection(connection: SavedSmbConnection, activate: Boolean) = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        settings.inTransaction {
            upsertConnection(connection)
            if (activate) {
                settings.putString(DB_KEY_ACTIVE_CONNECTION_ID, connection.id)
                writeActiveConfigToDb(connection.config)
                settings.putString(DB_KEY_ACTIVE_BROWSE_PATH, connection.config.normalizedPath())
            }
        }
    }

    override suspend fun setActiveConnection(id: String) = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        val target = loadSavedConnectionsFromDb().firstOrNull { it.id == id } ?: return@withContext
        settings.inTransaction {
            settings.putString(DB_KEY_ACTIVE_CONNECTION_ID, id)
            writeActiveConfigToDb(target.config)
            settings.putString(DB_KEY_ACTIVE_BROWSE_PATH, target.config.normalizedPath())
        }
    }

    override suspend fun deleteConnection(id: String): SmbConfigStoreState = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        val current = loadSavedConnectionsFromDb()
        if (current.none { it.id == id }) return@withContext loadStateFromDb()

        settings.inTransaction {
            dbHelper.writableDatabase.delete(TABLE_SMB_CONNECTIONS, "id = ?", arrayOf(id))
            val remaining = current.filterNot { it.id == id }
            val currentActiveId = settings.getString(DB_KEY_ACTIVE_CONNECTION_ID)
            val activeBrowsePath = settings.getString(DB_KEY_ACTIVE_BROWSE_PATH).orEmpty()
            val nextActive = when {
                remaining.isEmpty() -> null
                currentActiveId == id -> remaining.first()
                else -> remaining.firstOrNull { it.id == currentActiveId } ?: remaining.first()
            }

            if (nextActive == null) {
                settings.remove(DB_KEY_ACTIVE_CONNECTION_ID)
                writeActiveConfigToDb(SmbConfig.Empty)
                settings.putString(DB_KEY_ACTIVE_BROWSE_PATH, "")
            } else {
                settings.putString(DB_KEY_ACTIVE_CONNECTION_ID, nextActive.id)
                writeActiveConfigToDb(nextActive.config)
                val nextBrowsePath = if (nextActive.id == currentActiveId && currentActiveId != id) {
                    activeBrowsePath
                } else {
                    nextActive.config.normalizedPath()
                }
                settings.putString(DB_KEY_ACTIVE_BROWSE_PATH, normalizeBrowsePath(nextBrowsePath))
            }

            dbHelper.writableDatabase.delete(
                TABLE_BROWSE_ANCHORS,
                "namespace = ?",
                arrayOf(id)
            )
        }
        loadStateFromDb()
    }

    override suspend fun setActiveConfig(config: SmbConfig, browsePath: String) = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        settings.inTransaction {
            settings.remove(DB_KEY_ACTIVE_CONNECTION_ID)
            writeActiveConfigToDb(config)
            settings.putString(DB_KEY_ACTIVE_BROWSE_PATH, normalizeBrowsePath(browsePath))
        }
    }

    override suspend fun saveActiveBrowsePath(path: String) = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        settings.putString(DB_KEY_ACTIVE_BROWSE_PATH, normalizeBrowsePath(path))
    }

    override suspend fun loadBrowseAnchor(connectionId: String?, directoryPath: String): BrowseFocusAnchor? = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        val namespace = connectionId.orEmpty()
        val path = normalizeBrowsePath(directoryPath)
        dbHelper.readableDatabase.query(
            TABLE_BROWSE_ANCHORS,
            null,
            "namespace = ? AND directory_path = ?",
            arrayOf(namespace, path),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else BrowseFocusAnchor(
                itemKey = cursor.getString(cursor.getColumnIndexOrThrow("item_key")),
                index = cursor.getInt(cursor.getColumnIndexOrThrow("anchor_index")),
                updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
            )
        }
    }

    override suspend fun saveBrowseAnchor(connectionId: String?, directoryPath: String, anchor: BrowseFocusAnchor) {
        withContext(Dispatchers.IO) {
            migrateLegacyIfNeeded()
            val values = ContentValues().apply {
                put("namespace", connectionId.orEmpty())
                put("directory_path", normalizeBrowsePath(directoryPath))
                put("item_key", anchor.itemKey)
                put("anchor_index", anchor.index)
                put("updated_at", anchor.updatedAt)
            }
            dbHelper.writableDatabase.insertWithOnConflict(
                TABLE_BROWSE_ANCHORS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    override suspend fun clearBrowseCache() {
        withContext(Dispatchers.IO) {
            migrateLegacyIfNeeded()
            dbHelper.writableDatabase.delete(TABLE_BROWSE_ANCHORS, null, null)
        }
    }

    override fun newConnectionId(): String = UUID.randomUUID().toString()

    private suspend fun migrateLegacyIfNeeded() {
        if (migrationChecked) return
        if (hasDbSmbState()) {
            migrationChecked = true
            return
        }
        val legacy = loadLegacyState()
        settings.inTransaction {
            legacy.savedConnections.forEach { upsertConnection(it) }
            if (legacy.activeConnectionId != null) {
                settings.putString(DB_KEY_ACTIVE_CONNECTION_ID, legacy.activeConnectionId)
            }
            writeActiveConfigToDb(legacy.activeConfig)
            settings.putString(DB_KEY_ACTIVE_BROWSE_PATH, legacy.activeBrowsePath)
        }

        val preferences = appContext.smbDataStore.data
            .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
            .first()
        settings.inTransaction {
            decodeBrowseAnchors(preferences[Keys.BROWSE_ANCHORS_JSON]).forEach { (key, anchor) ->
                val parts = key.split("|", limit = 2)
                upsertBrowseAnchor(parts.getOrNull(0), parts.getOrNull(1).orEmpty(), anchor)
            }
        }
        migrationChecked = true
    }

    private fun hasDbSmbState(): Boolean {
        if (settings.getString(DB_KEY_ACTIVE_BROWSE_PATH) != null) return true
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_SMB_CONNECTIONS", emptyArray()).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) > 0
        }
    }

    private fun upsertConnection(connection: SavedSmbConnection) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", connection.id)
            put("name", connection.name)
            putConfig(connection.config)
            put("created_at", now)
            put("updated_at", now)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            TABLE_SMB_CONNECTIONS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun ContentValues.putConfig(config: SmbConfig) {
        put("host", config.host)
        put("share", config.share)
        put("path", config.path)
        put("username", config.username)
        put("password", config.password)
        put("guest", if (config.guest) 1 else 0)
        put("smb1", if (config.smb1Enabled) 1 else 0)
    }

    private fun loadSavedConnectionsFromDb(): List<SavedSmbConnection> =
        dbHelper.readableDatabase.query(
            TABLE_SMB_CONNECTIONS,
            null,
            null,
            null,
            null,
            null,
            "created_at ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SavedSmbConnection(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            config = cursor.toSmbConfig()
                        )
                    )
                }
            }
        }

    private fun upsertBrowseAnchor(connectionId: String?, directoryPath: String, anchor: BrowseFocusAnchor) {
        val values = ContentValues().apply {
            put("namespace", connectionId.orEmpty())
            put("directory_path", normalizeBrowsePath(directoryPath))
            put("item_key", anchor.itemKey)
            put("anchor_index", anchor.index)
            put("updated_at", anchor.updatedAt)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            TABLE_BROWSE_ANCHORS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun Cursor.toSmbConfig(): SmbConfig =
        SmbConfig(
            host = getString(getColumnIndexOrThrow("host")),
            share = getString(getColumnIndexOrThrow("share")),
            path = getString(getColumnIndexOrThrow("path")),
            username = getString(getColumnIndexOrThrow("username")),
            password = getString(getColumnIndexOrThrow("password")),
            guest = getInt(getColumnIndexOrThrow("guest")) == 1,
            smb1Enabled = getInt(getColumnIndexOrThrow("smb1")) == 1
        )

    private fun writeActiveConfigToDb(config: SmbConfig) {
        settings.putString(DB_KEY_ACTIVE_HOST, config.host)
        settings.putString(DB_KEY_ACTIVE_SHARE, config.share)
        settings.putString(DB_KEY_ACTIVE_PATH, config.path)
        settings.putString(DB_KEY_ACTIVE_USERNAME, config.username)
        settings.putString(DB_KEY_ACTIVE_PASSWORD, config.password)
        settings.putBoolean(DB_KEY_ACTIVE_GUEST, config.guest)
        settings.putBoolean(DB_KEY_ACTIVE_SMB1, config.smb1Enabled)
    }

    private fun readActiveConfigFromDb(): SmbConfig =
        SmbConfig(
            host = settings.getString(DB_KEY_ACTIVE_HOST).orEmpty(),
            share = settings.getString(DB_KEY_ACTIVE_SHARE).orEmpty(),
            path = settings.getString(DB_KEY_ACTIVE_PATH).orEmpty(),
            username = settings.getString(DB_KEY_ACTIVE_USERNAME).orEmpty(),
            password = settings.getString(DB_KEY_ACTIVE_PASSWORD).orEmpty(),
            guest = settings.getBoolean(DB_KEY_ACTIVE_GUEST) ?: true,
            smb1Enabled = settings.getBoolean(DB_KEY_ACTIVE_SMB1) ?: false
        )

    private fun decodeSaved(json: String?): List<SavedSmbConnection> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val configObj = item.optJSONObject("config") ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        SavedSmbConnection(
                            id = id,
                            name = item.optString("name"),
                            config = SmbConfig(
                                host = configObj.optString("host"),
                                share = configObj.optString("share"),
                                path = configObj.optString("path"),
                                username = configObj.optString("username"),
                                password = configObj.optString("password"),
                                guest = configObj.optBoolean("guest", true),
                                smb1Enabled = configObj.optBoolean("smb1Enabled", false)
                            )
                        )
                    )
                }
            }
        }.onFailure { ex ->
            Log.w(TAG, "decode legacy saved connections failed", ex)
        }.getOrDefault(emptyList())
    }

    private fun decodeBrowseAnchors(json: String?): MutableMap<String, BrowseFocusAnchor> {
        if (json.isNullOrBlank()) return mutableMapOf()
        return runCatching {
            val root = JSONObject(json)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = root.optJSONObject(key) ?: continue
                    val itemKey = item.optString("itemKey")
                    if (itemKey.isBlank()) continue
                    put(
                        key,
                        BrowseFocusAnchor(
                            itemKey = itemKey,
                            index = item.optInt("index", 0),
                            updatedAt = item.optLong("updatedAt", 0L)
                        )
                    )
                }
            }.toMutableMap()
        }.onFailure { ex ->
            Log.w(TAG, "decode legacy browse anchors failed", ex)
        }.getOrDefault(mutableMapOf())
    }

    private fun Preferences.toLegacyConfig(): SmbConfig =
        SmbConfig(
            host = this[Keys.HOST].orEmpty(),
            share = this[Keys.SHARE].orEmpty(),
            path = this[Keys.PATH].orEmpty(),
            username = this[Keys.USERNAME].orEmpty(),
            password = this[Keys.PASSWORD].orEmpty(),
            guest = this[Keys.GUEST] ?: true,
            smb1Enabled = this[Keys.SMB1] ?: false
        )

    private fun normalizeBrowsePath(path: String): String = path.trim().replace("\\", "/").trim('/')

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val SHARE = stringPreferencesKey("share")
        val PATH = stringPreferencesKey("path")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val GUEST = booleanPreferencesKey("guest")
        val SMB1 = booleanPreferencesKey("smb1")
        val SAVED_CONNECTIONS_JSON = stringPreferencesKey("saved_connections_json")
        val ACTIVE_CONNECTION_ID = stringPreferencesKey("active_connection_id")
        val ACTIVE_BROWSE_PATH = stringPreferencesKey("active_browse_path")
        val BROWSE_ANCHORS_JSON = stringPreferencesKey("browse_anchors_json")
    }

    private companion object {
        const val TAG = "SmbConfigStore"
        const val TABLE_SMB_CONNECTIONS = "smb_connections"
        const val TABLE_BROWSE_ANCHORS = "browse_anchors"
        const val DB_KEY_ACTIVE_CONNECTION_ID = "smb.active_connection_id"
        const val DB_KEY_ACTIVE_BROWSE_PATH = "smb.active_browse_path"
        const val DB_KEY_ACTIVE_HOST = "smb.active.host"
        const val DB_KEY_ACTIVE_SHARE = "smb.active.share"
        const val DB_KEY_ACTIVE_PATH = "smb.active.path"
        const val DB_KEY_ACTIVE_USERNAME = "smb.active.username"
        const val DB_KEY_ACTIVE_PASSWORD = "smb.active.password"
        const val DB_KEY_ACTIVE_GUEST = "smb.active.guest"
        const val DB_KEY_ACTIVE_SMB1 = "smb.active.smb1"
    }
}
