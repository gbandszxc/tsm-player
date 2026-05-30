# Favorites Playlists Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build SQLite-backed favorites and custom playlists, with playback-page favorite toggling, a browser-page favorites entry, and a TV-friendly playlist grid/detail page.

**Architecture:** Add a focused `favorites` data package backed by `SQLiteOpenHelper`, then connect it to existing Media3 queue creation and button presentation patterns. UI remains XML + imperative Kotlin, matching the current Leanback-era Android TV style and existing playback/browser activities.

**Tech Stack:** Kotlin, Android SDK SQLiteOpenHelper, Media3, AppCompat AlertDialog, Robolectric/JUnit, existing XML token system from `DESIGN.md`.

---

## File Structure

- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritePlaylist.kt`: playlist model.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoriteTrack.kt`: saved track model and conversion helpers.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritesDbHelper.kt`: SQLite schema, creation, upgrade, default playlist initialization.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritesRepository.kt`: all playlist and track read/write operations.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoriteTrackMediaItems.kt`: convert favorite tracks into Media3 `MediaItem`s.
- Create `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivity.kt`: playlist grid, playlist detail list, play/remove/create interactions.
- Create `app/src/main/res/layout/activity_favorites.xml`: favorites page root.
- Create `app/src/main/res/drawable/ic_favorite_outline.xml`: white outline heart.
- Create `app/src/main/res/drawable/ic_favorite_filled.xml`: white filled heart.
- Create `app/src/main/res/drawable/ic_add_playlist.xml`: plus icon for first grid tile.
- Create `app/src/main/res/drawable/ic_delete.xml`: delete/remove icon.
- Create `app/src/main/res/drawable/bg_playlist_tile.xml`: focused/unfocused playlist tile background.
- Modify `app/src/main/AndroidManifest.xml`: register `FavoritesActivity`.
- Modify `app/src/main/res/layout/activity_playback.xml`: insert favorite button after play mode.
- Modify `app/src/main/res/layout/fragment_tv_browser.xml`: add browser favorite entry button and collapse icon-only playback buttons.
- Modify `app/src/main/res/values/dimens.xml`: add playlist tile dimensions and optional favorite expanded button width.
- Modify `app/src/main/res/values/strings.xml`: add user-facing favorite strings.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackButtonPresentation.kt`: add favorite and browser playback button specs.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`: favorite state, short-press toggle, long-press modal.
- Modify `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`: favorites entry button and icon-only presentation for playback controls.
- Modify `DESIGN.md`: update playback page, browser playback controls, favorites page.
- Modify `docs/MANUAL.md`: record favorites/playlists and SQLite persistence.
- Test `app/src/test/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritesRepositoryTest.kt`: repository behavior.
- Test `app/src/test/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoriteTrackMediaItemsTest.kt`: MediaItem conversion.
- Test `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackButtonPresentationTest.kt`: new icon/expand specs.
- Test `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivityLayoutTest.kt`: grid/detail container exists and is outside no scroll traps.

## Task 1: SQLite Favorites Data Layer

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritePlaylist.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoriteTrack.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritesDbHelper.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritesRepository.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritesRepositoryTest.kt`

- [ ] **Step 1: Write repository tests first**

Create `FavoritesRepositoryTest.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.favorites

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FavoritesRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: FavoritesRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
        repository = FavoritesRepository(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
    }

    @Test
    fun `default favorites playlist is created first`() {
        val playlists = repository.getPlaylists()

        assertEquals(FavoritesRepository.DEFAULT_PLAYLIST_ID, playlists.first().id)
        assertEquals("收藏夹", playlists.first().name)
        assertTrue(playlists.first().isDefault)
    }

    @Test
    fun `create playlist rejects blank and duplicate names`() {
        assertNull(repository.createPlaylist("   "))
        val first = repository.createPlaylist("夜间歌单")
        val duplicate = repository.createPlaylist(" 夜间歌单 ")

        assertNotNull(first)
        assertNull(duplicate)
        assertEquals(listOf("收藏夹", "夜间歌单"), repository.getPlaylists().map { it.name })
    }

    @Test
    fun `add track deduplicates per playlist and reports containment`() {
        val track = sampleTrack(mediaId = "Music/A.flac")

        assertTrue(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertFalse(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertTrue(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))
        assertEquals(1, repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).size)
    }

    @Test
    fun `same track can exist in different playlists`() {
        val custom = requireNotNull(repository.createPlaylist("精选"))
        val track = sampleTrack(mediaId = "Music/A.flac")

        assertTrue(repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track))
        assertTrue(repository.addTrack(custom.id, track))

        assertEquals(1, repository.getTracks(FavoritesRepository.DEFAULT_PLAYLIST_ID).size)
        assertEquals(1, repository.getTracks(custom.id).size)
    }

    @Test
    fun `remove track only removes playlist record`() {
        val track = sampleTrack(mediaId = "Music/A.flac")
        repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track)

        assertTrue(repository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))
        assertFalse(repository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))
        assertFalse(repository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, "Music/A.flac"))
    }

    @Test
    fun `playlist cover uses last added track artwork`() {
        val first = sampleTrack(mediaId = "Music/A.flac", artworkUri = "smb://nas/cover-a.jpg", addedAt = 10L)
        val second = sampleTrack(mediaId = "Music/B.flac", artworkUri = "smb://nas/cover-b.jpg", addedAt = 20L)

        repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, first)
        repository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, second)

        assertEquals("smb://nas/cover-b.jpg", repository.getPlaylists().first().coverArtworkUri)
    }

    private fun sampleTrack(
        mediaId: String,
        artworkUri: String? = null,
        addedAt: Long = 1_000L
    ): FavoriteTrack =
        FavoriteTrack(
            id = "track-${mediaId.hashCode()}",
            playlistId = FavoritesRepository.DEFAULT_PLAYLIST_ID,
            mediaId = mediaId,
            streamUri = "smb://nas/$mediaId",
            title = mediaId.substringAfterLast('/').substringBeforeLast('.'),
            artist = "artist",
            album = "album",
            artworkUri = artworkUri,
        sourceConnectionId = null,
            sourceConfig = SmbConfig(
                host = "nas",
                share = "Music",
                path = "",
                username = "user",
                password = "pass",
                guest = false,
                smb1Enabled = false
            ),
            addedAt = addedAt
        )
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.favorites.FavoritesRepositoryTest"
```

Expected: compilation fails because the `favorites` classes do not exist yet.

- [ ] **Step 3: Implement models and database helper**

Create `FavoritePlaylist.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.favorites

data class FavoritePlaylist(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val coverArtworkUri: String? = null,
    val trackCount: Int = 0
)
```

Create `FavoriteTrack.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.favorites

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

data class FavoriteTrack(
    val id: String,
    val playlistId: String,
    val mediaId: String,
    val streamUri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val sourceConnectionId: String?,
    val sourceConfig: SmbConfig?,
    val addedAt: Long
)
```

Create `FavoritesDbHelper.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.favorites

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FavoritesDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

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
        db.execSQL("CREATE INDEX idx_playlist_tracks_playlist_added ON playlist_tracks(playlist_id, added_at DESC)")
        ensureDefaultPlaylist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            onCreate(db)
        }
    }

    fun ensureDefaultPlaylist(db: SQLiteDatabase = writableDatabase) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", FavoritesRepository.DEFAULT_PLAYLIST_ID)
            put("name", "收藏夹")
            put("is_default", 1)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insertWithOnConflict("playlists", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    companion object {
        const val DB_NAME = "favorites.db"
        const val DB_VERSION = 1
    }
}
```

- [ ] **Step 4: Implement repository**

Create `FavoritesRepository.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.favorites

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import java.util.UUID

class FavoritesRepository(context: Context) {
    private val helper = FavoritesDbHelper(context.applicationContext)

    fun getPlaylists(): List<FavoritePlaylist> {
        helper.ensureDefaultPlaylist()
        val db = helper.readableDatabase
        return db.rawQuery(
            """
            SELECT p.id, p.name, p.is_default, p.created_at, p.updated_at,
                   latest.artwork_uri AS cover_artwork_uri,
                   COUNT(t.id) AS track_count
            FROM playlists p
            LEFT JOIN playlist_tracks t ON t.playlist_id = p.id
            LEFT JOIN playlist_tracks latest ON latest.id = (
                SELECT id FROM playlist_tracks
                WHERE playlist_id = p.id
                ORDER BY added_at DESC
                LIMIT 1
            )
            GROUP BY p.id, p.name, p.is_default, p.created_at, p.updated_at, latest.artwork_uri
            ORDER BY p.is_default DESC, p.created_at ASC
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toPlaylist())
            }
        }
    }

    fun createPlaylist(rawName: String): FavoritePlaylist? {
        val name = rawName.trim()
        if (name.isBlank()) return null
        if (getPlaylists().any { it.name == name }) return null

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("is_default", 0)
            put("created_at", now)
            put("updated_at", now)
        }
        val inserted = helper.writableDatabase.insert("playlists", null, values) != -1L
        return if (inserted) FavoritePlaylist(id, name, isDefault = false, createdAt = now, updatedAt = now) else null
    }

    fun getTracks(playlistId: String): List<FavoriteTrack> {
        return helper.readableDatabase.query(
            "playlist_tracks",
            null,
            "playlist_id = ?",
            arrayOf(playlistId),
            null,
            null,
            "added_at DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toTrack())
            }
        }
    }

    fun containsTrack(playlistId: String, mediaId: String): Boolean {
        return helper.readableDatabase.query(
            "playlist_tracks",
            arrayOf("id"),
            "playlist_id = ? AND media_id = ?",
            arrayOf(playlistId, mediaId),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }
    }

    fun addTrack(playlistId: String, track: FavoriteTrack): Boolean {
        val now = if (track.addedAt > 0L) track.addedAt else System.currentTimeMillis()
        val db = helper.writableDatabase
        val inserted = db.insertWithOnConflict(
            "playlist_tracks",
            null,
            track.copy(playlistId = playlistId, addedAt = now).toContentValues(),
            SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
        if (inserted) touchPlaylist(db, playlistId, now)
        return inserted
    }

    fun removeTrack(playlistId: String, mediaId: String): Boolean {
        val removed = helper.writableDatabase.delete(
            "playlist_tracks",
            "playlist_id = ? AND media_id = ?",
            arrayOf(playlistId, mediaId)
        ) > 0
        if (removed) touchPlaylist(helper.writableDatabase, playlistId, System.currentTimeMillis())
        return removed
    }

    private fun touchPlaylist(db: SQLiteDatabase, playlistId: String, updatedAt: Long) {
        val values = ContentValues().apply { put("updated_at", updatedAt) }
        db.update("playlists", values, "id = ?", arrayOf(playlistId))
    }

    private fun FavoriteTrack.toContentValues(): ContentValues =
        ContentValues().apply {
            put("id", id.ifBlank { UUID.randomUUID().toString() })
            put("playlist_id", playlistId)
            put("media_id", mediaId)
            put("stream_uri", streamUri)
            put("title", title.ifBlank { mediaId.substringAfterLast('/') })
            put("artist", artist)
            put("album", album)
            put("artwork_uri", artworkUri)
            put("source_connection_id", sourceConnectionId)
            put("source_host", sourceConfig?.host)
            put("source_share", sourceConfig?.share)
            put("source_path", sourceConfig?.path)
            put("source_username", sourceConfig?.username)
            put("source_password", sourceConfig?.password)
            put("source_guest", sourceConfig?.guest?.let { if (it) 1 else 0 })
            put("source_smb1", sourceConfig?.smb1Enabled?.let { if (it) 1 else 0 })
            put("added_at", addedAt)
        }

    private fun Cursor.toPlaylist(): FavoritePlaylist =
        FavoritePlaylist(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            isDefault = getInt(getColumnIndexOrThrow("is_default")) == 1,
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            coverArtworkUri = getStringOrNull("cover_artwork_uri"),
            trackCount = getInt(getColumnIndexOrThrow("track_count"))
        )

    private fun Cursor.toTrack(): FavoriteTrack {
        val sourceHost = getStringOrNull("source_host")
        return FavoriteTrack(
            id = getString(getColumnIndexOrThrow("id")),
            playlistId = getString(getColumnIndexOrThrow("playlist_id")),
            mediaId = getString(getColumnIndexOrThrow("media_id")),
            streamUri = getString(getColumnIndexOrThrow("stream_uri")),
            title = getString(getColumnIndexOrThrow("title")),
            artist = getStringOrNull("artist"),
            album = getStringOrNull("album"),
            artworkUri = getStringOrNull("artwork_uri"),
            sourceConnectionId = getStringOrNull("source_connection_id"),
            sourceConfig = sourceHost?.takeIf { it.isNotBlank() }?.let {
                SmbConfig(
                    host = it,
                    share = getStringOrNull("source_share").orEmpty(),
                    path = getStringOrNull("source_path").orEmpty(),
                    username = getStringOrNull("source_username").orEmpty(),
                    password = getStringOrNull("source_password").orEmpty(),
                    guest = getIntOrDefault("source_guest", 1) == 1,
                    smb1Enabled = getIntOrDefault("source_smb1", 0) == 1
                )
            },
            addedAt = getLong(getColumnIndexOrThrow("added_at"))
        )
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.getIntOrDefault(column: String, defaultValue: Int): Int {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) defaultValue else getInt(index)
    }

    companion object {
        const val DEFAULT_PLAYLIST_ID = "default_favorites"
    }
}
```

- [ ] **Step 5: Run repository tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.favorites.FavoritesRepositoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit data layer**

Run:

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites app/src/test/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoritesRepositoryTest.kt
git commit -m "feat: 增加收藏歌单数据层"
```

## Task 2: Favorite MediaItem Conversion

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoriteTrackMediaItems.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoriteTrackMediaItemsTest.kt`

- [ ] **Step 1: Write conversion tests**

Create `FavoriteTrackMediaItemsTest.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.favorites

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteTrackMediaItemsTest {

    @Test
    fun `media item preserves id uri metadata and artwork`() {
        val item = FavoriteTrackMediaItems.fromTracks(listOf(sampleTrack())).single()

        assertEquals("Music/A.flac", item.mediaId)
        assertEquals("smb://nas/Music/A.flac", item.localConfiguration?.uri.toString())
        assertEquals("A", item.mediaMetadata.title.toString())
        assertEquals("artist", item.mediaMetadata.artist.toString())
        assertEquals("album", item.mediaMetadata.albumTitle.toString())
        assertEquals("smb://nas/cover.jpg", item.mediaMetadata.artworkUri.toString())
    }

    private fun sampleTrack(): FavoriteTrack =
        FavoriteTrack(
            id = "id-1",
            playlistId = FavoritesRepository.DEFAULT_PLAYLIST_ID,
            mediaId = "Music/A.flac",
            streamUri = "smb://nas/Music/A.flac",
            title = "A",
            artist = "artist",
            album = "album",
            artworkUri = "smb://nas/cover.jpg",
            sourceConnectionId = "conn-1",
            sourceConfig = SmbConfig("nas", "Music", "", "", "", true, false),
            addedAt = 1L
        )
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackMediaItemsTest"
```

Expected: compilation fails because `FavoriteTrackMediaItems` is missing.

- [ ] **Step 3: Implement conversion helper**

Create `FavoriteTrackMediaItems.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.favorites

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

object FavoriteTrackMediaItems {
    fun fromTracks(tracks: List<FavoriteTrack>): List<MediaItem> {
        return tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.mediaId)
                .setUri(track.streamUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build()
                )
                .build()
        }
    }
}
```

- [ ] **Step 4: Run conversion tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackMediaItemsTest"
```

Expected: PASS.

- [ ] **Step 5: Commit conversion helper**

Run:

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoriteTrackMediaItems.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/favorites/FavoriteTrackMediaItemsTest.kt
git commit -m "feat: 支持收藏歌曲构建播放队列"
```

## Task 3: Shared Button Presentation and Icons

**Files:**
- Create: `app/src/main/res/drawable/ic_favorite_outline.xml`
- Create: `app/src/main/res/drawable/ic_favorite_filled.xml`
- Create: `app/src/main/res/drawable/ic_add_playlist.xml`
- Create: `app/src/main/res/drawable/ic_delete.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackButtonPresentation.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackButtonPresentationTest.kt`

- [ ] **Step 1: Extend presentation tests**

Append to `PlaybackButtonPresentationTest.kt`:

```kotlin
    @Test
    fun favoriteButtonReflectsDefaultPlaylistStateAndExpandsOnFocus() {
        val notSaved = PlaybackButtonPresentation.favorite(inDefaultFavorites = false, focused = false)
        val saved = PlaybackButtonPresentation.favorite(inDefaultFavorites = true, focused = true)

        assertEquals("", notSaved.text)
        assertEquals("收藏", saved.text)
        assertEquals("收藏", notSaved.contentDescription)
        assertTrue(saved.expandsOnFocus)
        assertEquals(R.drawable.ic_favorite_outline, notSaved.iconResId)
        assertEquals(R.drawable.ic_favorite_filled, saved.iconResId)
    }

    @Test
    fun browserPlaybackButtonsUseCollapsedIconsAndFocusedText() {
        val favorites = PlaybackButtonPresentation.browserFavorites(focused = true)
        val order = PlaybackButtonPresentation.browserPlayOrder(focused = true)
        val shuffle = PlaybackButtonPresentation.browserPlayShuffle(focused = true)

        assertEquals("收藏", favorites.text)
        assertEquals("顺序播放", order.text)
        assertEquals("随机播放", shuffle.text)
        assertEquals(R.drawable.ic_favorite_filled, favorites.iconResId)
        assertEquals(R.drawable.ic_play_order, order.iconResId)
        assertEquals(R.drawable.ic_shuffle, shuffle.iconResId)
    }
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.PlaybackButtonPresentationTest"
```

Expected: compilation fails because new presentation functions and icons do not exist.

- [ ] **Step 3: Add vector drawables**

Create `ic_favorite_outline.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="@color/ui_text_on_accent"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M12,21C8,17.5 4,14.2 4,9.6C4,6.9 6.1,5 8.6,5C10,5 11.2,5.7 12,6.8C12.8,5.7 14,5 15.4,5C17.9,5 20,6.9 20,9.6C20,14.2 16,17.5 12,21Z" />
</vector>
```

Create `ic_favorite_filled.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/ui_text_on_accent"
        android:pathData="M12,21C8,17.5 4,14.2 4,9.6C4,6.9 6.1,5 8.6,5C10,5 11.2,5.7 12,6.8C12.8,5.7 14,5 15.4,5C17.9,5 20,6.9 20,9.6C20,14.2 16,17.5 12,21Z" />
</vector>
```

Create `ic_add_playlist.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp"
    android:height="32dp"
    android:viewportWidth="32"
    android:viewportHeight="32">
    <path android:strokeColor="@color/ui_text_on_accent" android:strokeWidth="3" android:strokeLineCap="round" android:pathData="M16,8L16,24" />
    <path android:strokeColor="@color/ui_text_on_accent" android:strokeWidth="3" android:strokeLineCap="round" android:pathData="M8,16L24,16" />
</vector>
```

Create `ic_delete.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="@android:color/transparent" android:strokeColor="@color/ui_text_on_accent" android:strokeWidth="2" android:strokeLineCap="round" android:pathData="M5,7L19,7" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="@color/ui_text_on_accent" android:strokeWidth="2" android:strokeLineJoin="round" android:pathData="M9,7L9,5L15,5L15,7M8,10L9,19L15,19L16,10" />
</vector>
```

- [ ] **Step 4: Add presentation functions**

Modify `PlaybackButtonPresentation.kt` by adding:

```kotlin
    fun favorite(inDefaultFavorites: Boolean, focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = "收藏",
            iconResId = if (inDefaultFavorites) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline,
            focused = focused,
        )
    }

    fun browserFavorites(focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = "收藏",
            iconResId = R.drawable.ic_favorite_filled,
            focused = focused,
        )
    }

    fun browserPlayOrder(focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = "顺序播放",
            iconResId = R.drawable.ic_play_order,
            focused = focused,
        )
    }

    fun browserPlayShuffle(focused: Boolean): PlaybackButtonSpec {
        return expandable(
            label = "随机播放",
            iconResId = R.drawable.ic_shuffle,
            focused = focused,
        )
    }
```

- [ ] **Step 5: Run presentation tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.PlaybackButtonPresentationTest"
```

Expected: PASS.

- [ ] **Step 6: Commit icons and presentation**

Run:

```powershell
git add app/src/main/res/drawable/ic_favorite_outline.xml app/src/main/res/drawable/ic_favorite_filled.xml app/src/main/res/drawable/ic_add_playlist.xml app/src/main/res/drawable/ic_delete.xml app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackButtonPresentation.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackButtonPresentationTest.kt
git commit -m "style: 增加收藏按钮图标表现"
```

## Task 4: Playback Page Favorite Button

**Files:**
- Modify: `app/src/main/res/layout/activity_playback.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`

- [ ] **Step 1: Add layout resources**

Add to `dimens.xml`:

```xml
    <dimen name="ui_playback_favorite_button_expanded_min_width">96dp</dimen>
```

Add to `strings.xml`:

```xml
    <string name="favorites_empty_current_track">暂无可收藏歌曲</string>
    <string name="favorites_added_default">已加入收藏夹</string>
    <string name="favorites_removed_default">已取消收藏</string>
    <string name="favorites_already_in_playlist">已在该播放列表中</string>
    <string name="favorites_select_playlist">选择播放列表</string>
    <string name="favorites_new_playlist">新建播放列表</string>
    <string name="favorites_playlist_name_hint">播放列表名称</string>
    <string name="favorites_playlist_name_empty">请输入播放列表名称</string>
    <string name="favorites_playlist_name_duplicate">播放列表已存在</string>
```

Insert this `Button` in `activity_playback.xml` immediately after `btn_play_mode` and before `btn_sleep_timer`:

```xml
                <Button
                    android:id="@+id/btn_favorite"
                    style="@style/TsmButtonDanger"
                    android:layout_width="@dimen/ui_playback_mode_button_collapsed_width"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="@dimen/ui_space_lg"
                    android:drawablePadding="@dimen/ui_space_sm"
                    android:gravity="center"
                    android:minWidth="@dimen/ui_playback_mode_button_collapsed_width"
                    android:nextFocusUp="@id/pb_playback"
                    android:text=""
                    android:textAllCaps="false" />
```

- [ ] **Step 2: Wire PlaybackActivity fields and listeners**

In `PlaybackActivity.kt`, add imports:

```kotlin
import android.widget.EditText
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrack
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesRepository
import java.util.UUID
```

Add fields:

```kotlin
    private lateinit var btnFavorite: Button
    private val favoritesRepository by lazy { FavoritesRepository(applicationContext) }
    private var currentTrackInDefaultFavorites: Boolean = false
```

In `bindViews`, after `btnPlayMode`:

```kotlin
        btnFavorite = findViewById(R.id.btn_favorite)
```

In `bindActions`, after play mode listener:

```kotlin
        btnFavorite.setOnClickListener { toggleDefaultFavorite() }
        btnFavorite.setOnLongClickListener {
            showFavoritePlaylistDialog()
            true
        }
        btnFavorite.setOnFocusChangeListener { _, _ -> updateFavoriteButtonPresentation() }
```

- [ ] **Step 3: Add favorite state helpers**

Add to `PlaybackActivity.kt`:

```kotlin
    private fun currentFavoriteTrack(): FavoriteTrack? {
        val item = player.currentMediaItem ?: return null
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        if (item.mediaId.isBlank() || uri.isBlank()) return null
        val display = PlaybackTrackInfoStore.shared.displayFor(
            key = uri,
            fallbackTitle = item.mediaMetadata.title?.toString().orEmpty(),
            fallbackArtist = item.mediaMetadata.artist?.toString().orEmpty(),
            fallbackAlbumTitle = item.mediaMetadata.albumTitle?.toString().orEmpty()
        )
        return FavoriteTrack(
            id = UUID.randomUUID().toString(),
            playlistId = FavoritesRepository.DEFAULT_PLAYLIST_ID,
            mediaId = item.mediaId,
            streamUri = uri,
            title = display.title.orEmpty().ifBlank { item.mediaId.substringAfterLast('/') },
            artist = display.artist.orEmpty().ifBlank { item.mediaMetadata.artist?.toString().orEmpty() }.ifBlank { null },
            album = display.albumTitle.orEmpty().ifBlank { item.mediaMetadata.albumTitle?.toString().orEmpty() }.ifBlank { null },
            artworkUri = item.mediaMetadata.artworkUri?.toString(),
            sourceConnectionId = null,
            sourceConfig = PlaybackConfigStore.current(),
            addedAt = System.currentTimeMillis()
        )
    }

    private fun refreshFavoriteState() {
        val mediaId = player.currentMediaItem?.mediaId.orEmpty()
        currentTrackInDefaultFavorites = mediaId.isNotBlank() &&
            favoritesRepository.containsTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, mediaId)
        updateFavoriteButtonPresentation()
    }

    private fun updateFavoriteButtonPresentation() {
        val spec = PlaybackButtonPresentation.favorite(
            inDefaultFavorites = currentTrackInDefaultFavorites,
            focused = btnFavorite.hasFocus()
        )
        renderPlaybackButton(
            button = btnFavorite,
            spec = spec,
            backgroundResId = R.drawable.bg_button_red,
            iconColorResId = R.color.ui_text_on_accent,
            collapsedWidthResId = R.dimen.ui_playback_mode_button_collapsed_width,
            expandedMinWidthResId = R.dimen.ui_playback_favorite_button_expanded_min_width,
        )
    }

    private fun toggleDefaultFavorite() {
        val track = currentFavoriteTrack()
        if (track == null) {
            showPlaybackToast(getString(R.string.favorites_empty_current_track))
            return
        }
        if (currentTrackInDefaultFavorites) {
            favoritesRepository.removeTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track.mediaId)
            currentTrackInDefaultFavorites = false
            showPlaybackToast(getString(R.string.favorites_removed_default))
        } else {
            favoritesRepository.addTrack(FavoritesRepository.DEFAULT_PLAYLIST_ID, track)
            currentTrackInDefaultFavorites = true
            showPlaybackToast(getString(R.string.favorites_added_default))
        }
        updateFavoriteButtonPresentation()
    }
```

- [ ] **Step 4: Add long-press modal**

Add to `PlaybackActivity.kt`:

```kotlin
    private data class PlaylistChoice(
        val label: String,
        val playlistId: String?,
        val disabled: Boolean
    )

    private fun showFavoritePlaylistDialog() {
        val track = currentFavoriteTrack()
        if (track == null) {
            showPlaybackToast(getString(R.string.favorites_empty_current_track))
            return
        }
        val choices = favoritesRepository.getPlaylists().map { playlist ->
            val exists = favoritesRepository.containsTrack(playlist.id, track.mediaId)
            PlaylistChoice(
                label = if (exists) "${playlist.name}（已添加）" else playlist.name,
                playlistId = playlist.id,
                disabled = exists
            )
        } + PlaylistChoice(getString(R.string.favorites_new_playlist), null, disabled = false)

        val adapter = object : ArrayAdapter<PlaylistChoice>(
            this,
            android.R.layout.simple_list_item_1,
            choices
        ) {
            override fun isEnabled(position: Int): Boolean = !requireNotNull(getItem(position)).disabled
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val choice = requireNotNull(getItem(position))
                (view as TextView).text = choice.label
                view.alpha = if (choice.disabled) 0.45f else 1f
                return view
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.favorites_select_playlist)
            .setAdapter(adapter) { _, which ->
                val choice = choices[which]
                if (choice.playlistId == null) {
                    showCreatePlaylistAndAddDialog(track)
                    return@setAdapter
                }
                addTrackToPlaylist(choice.playlistId, track)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addTrackToPlaylist(playlistId: String, track: FavoriteTrack) {
        val playlist = favoritesRepository.getPlaylists().firstOrNull { it.id == playlistId } ?: return
        if (!favoritesRepository.addTrack(playlist.id, track)) {
            showPlaybackToast(getString(R.string.favorites_already_in_playlist))
            return
        }
        if (playlist.id == FavoritesRepository.DEFAULT_PLAYLIST_ID) {
            currentTrackInDefaultFavorites = true
            updateFavoriteButtonPresentation()
        }
        showPlaybackToast("已加入${playlist.name}")
    }

    private fun showCreatePlaylistAndAddDialog(track: FavoriteTrack) {
        val input = EditText(this).apply {
            hint = getString(R.string.favorites_playlist_name_hint)
            typeface = AppFonts.regular(this@PlaybackActivity)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.favorites_new_playlist)
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建并收藏") { _, _ ->
                val rawName = input.text.toString()
                if (rawName.isBlank()) {
                    showPlaybackToast(getString(R.string.favorites_playlist_name_empty))
                    return@setPositiveButton
                }
                val playlist = favoritesRepository.createPlaylist(rawName)
                if (playlist == null) {
                    showPlaybackToast(getString(R.string.favorites_playlist_name_duplicate))
                    return@setPositiveButton
                }
                favoritesRepository.addTrack(playlist.id, track)
                showPlaybackToast("已加入${playlist.name}")
            }
            .show()
    }
```

Add imports:

```kotlin
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
```

- [ ] **Step 5: Refresh favorite state on media changes**

In the existing player listener events where title/metadata/playback UI is refreshed, call:

```kotlin
        refreshFavoriteState()
```

Also call `refreshFavoriteState()` once after player setup and after `updatePlaybackButtons()`.

- [ ] **Step 6: Build-check playback resources**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.PlaybackButtonPresentationTest"
.\gradlew.bat assembleDebug
```

Expected: tests PASS and Debug APKs are produced.

- [ ] **Step 7: Commit playback page favorite**

Run:

```powershell
git add app/src/main/res/layout/activity_playback.xml app/src/main/res/values/dimens.xml app/src/main/res/values/strings.xml app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt
git commit -m "feat: 播放页增加收藏切换"
```

## Task 5: Browser Page Favorites Entry and Icon-Only Playback Buttons

**Files:**
- Modify: `app/src/main/res/layout/fragment_tv_browser.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt`

- [ ] **Step 1: Extend layout test**

Add to `TvBrowseFragmentLayoutTest.kt`:

```kotlin
    @Test
    fun `playback panel has favorites before order and shuffle before now playing`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_tv_browser, FrameLayout(context), false)
        val favorites = root.findViewById<View>(R.id.btn_favorites)
        val playAll = root.findViewById<View>(R.id.btn_play_all)
        val playShuffle = root.findViewById<View>(R.id.btn_play_shuffle)
        val nowPlaying = root.findViewById<View>(R.id.btn_now_playing)

        assertTrue(leftOf(favorites, playAll))
        assertTrue(leftOf(playAll, playShuffle))
        assertTrue(leftOf(playShuffle, nowPlaying))
    }

    private fun leftOf(left: View, right: View): Boolean {
        val parent = left.parent as ViewParent
        return parent === right.parent && (parent as ViewGroup).indexOfChild(left) < parent.indexOfChild(right)
    }
```

Add imports:

```kotlin
import android.view.ViewGroup
import org.junit.Assert.assertTrue
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest"
```

Expected: fails because `btn_favorites` does not exist.

- [ ] **Step 3: Update XML layout**

In `fragment_tv_browser.xml`, inside the playback control inner horizontal button row, insert before `btn_play_all`:

```xml
                            <Button
                                android:id="@+id/btn_favorites"
                                style="@style/TsmButtonDanger"
                                android:layout_width="@dimen/ui_playback_mode_button_collapsed_width"
                                android:layout_height="wrap_content"
                                android:drawablePadding="@dimen/ui_space_sm"
                                android:gravity="center"
                                android:minWidth="@dimen/ui_playback_mode_button_collapsed_width"
                                android:text=""
                                android:textAllCaps="false" />
```

Change `btn_play_all` and `btn_play_shuffle` to:

```xml
                                android:layout_width="@dimen/ui_playback_mode_button_collapsed_width"
                                android:minWidth="@dimen/ui_playback_mode_button_collapsed_width"
                                android:drawablePadding="@dimen/ui_space_sm"
                                android:text=""
```

Keep `btn_play_shuffle` margin start. Keep `btn_now_playing` at `layout_width="0dp"` and `layout_weight="1"`.

- [ ] **Step 4: Wire TvBrowseFragment**

Add field:

```kotlin
    private lateinit var btnFavorites: Button
```

In `bindViews` before `btnPlayAll`:

```kotlin
        btnFavorites = root.findViewById(R.id.btn_favorites)
```

In `bindActions`:

```kotlin
        btnFavorites.setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }
        listOf(btnFavorites, btnPlayAll, btnPlayShuffle).forEach { button ->
            button.setOnFocusChangeListener { _, _ -> updateBrowserPlaybackButtonPresentation() }
        }
        updateBrowserPlaybackButtonPresentation()
```

Add helper:

```kotlin
    private fun updateBrowserPlaybackButtonPresentation() {
        applyBrowserButtonSpec(btnFavorites, PlaybackButtonPresentation.browserFavorites(btnFavorites.hasFocus()))
        applyBrowserButtonSpec(btnPlayAll, PlaybackButtonPresentation.browserPlayOrder(btnPlayAll.hasFocus()))
        applyBrowserButtonSpec(btnPlayShuffle, PlaybackButtonPresentation.browserPlayShuffle(btnPlayShuffle.hasFocus()))
    }

    private fun applyBrowserButtonSpec(button: Button, spec: PlaybackButtonSpec) {
        button.text = spec.text
        button.contentDescription = spec.contentDescription
        button.setCompoundDrawablesWithIntrinsicBounds(spec.iconResId, 0, 0, 0)
        val width = if (button.hasFocus()) {
            resources.getDimensionPixelSize(R.dimen.ui_playback_mode_button_expanded_min_width)
        } else {
            resources.getDimensionPixelSize(R.dimen.ui_playback_mode_button_collapsed_width)
        }
        button.minWidth = width
        button.layoutParams = button.layoutParams.apply { this.width = width }
    }
```

- [ ] **Step 5: Run browser layout tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest"
```

Expected: PASS.

- [ ] **Step 6: Commit browser controls**

Run:

```powershell
git add app/src/main/res/layout/fragment_tv_browser.xml app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt
git commit -m "style: 列表页播放控制改为图标展开"
```

## Task 6: Favorites Activity Grid and Detail Playback

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivity.kt`
- Create: `app/src/main/res/layout/activity_favorites.xml`
- Create: `app/src/main/res/drawable/bg_playlist_tile.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivityLayoutTest.kt`

- [ ] **Step 1: Write layout smoke test**

Create `FavoritesActivityLayoutTest.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FavoritesActivityLayoutTest {

    @Test
    fun `favorites layout exposes playlist grid and track list containers`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.activity_favorites, FrameLayout(context), false)

        assertNotNull(root.findViewById(R.id.grid_playlists))
        assertNotNull(root.findViewById(R.id.container_tracks))
        assertNotNull(root.findViewById(R.id.btn_favorites_back))
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.FavoritesActivityLayoutTest"
```

Expected: fails because layout does not exist.

- [ ] **Step 3: Add tile background and layout**

Create `bg_playlist_tile.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape>
            <solid android:color="@color/ui_bg_item_focus" />
            <stroke android:width="@dimen/ui_stroke_focus" android:color="@color/ui_accent_blue_stroke" />
            <corners android:radius="@dimen/ui_radius_panel" />
        </shape>
    </item>
    <item>
        <shape>
            <solid android:color="@color/ui_bg_panel" />
            <stroke android:width="1dp" android:color="@color/ui_divider" />
            <corners android:radius="@dimen/ui_radius_panel" />
        </shape>
    </item>
</selector>
```

Add to `dimens.xml`:

```xml
    <dimen name="ui_playlist_tile_width">180dp</dimen>
    <dimen name="ui_playlist_tile_height">148dp</dimen>
    <dimen name="ui_favorites_delete_button_width">56dp</dimen>
```

Create `activity_favorites.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/ui_bg_tv_blue">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="@dimen/ui_space_4xl">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tv_favorites_title"
                style="@style/TsmTextPageTitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="收藏" />

            <Button
                android:id="@+id/btn_favorites_back"
                style="@style/TsmButtonDark"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:minWidth="120dp"
                android:text="返回"
                android:textAllCaps="false" />
        </LinearLayout>

        <HorizontalScrollView
            android:id="@+id/scroll_playlist_grid"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ui_space_4xl"
            android:scrollbars="none">

            <GridLayout
                android:id="@+id/grid_playlists"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:columnCount="4"
                android:orientation="horizontal" />
        </HorizontalScrollView>

        <ScrollView
            android:id="@+id/scroll_tracks"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_marginTop="@dimen/ui_space_4xl"
            android:layout_weight="1"
            android:scrollbars="none"
            android:visibility="gone">

            <LinearLayout
                android:id="@+id/container_tracks"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />
        </ScrollView>
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 4: Implement FavoritesActivity**

Create `FavoritesActivity.kt`:

```kotlin
package com.github.gbandszxc.tvmediaplayer.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritePlaylist
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrack
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrackMediaItems
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class FavoritesActivity : BaseActivity() {
    private val repository by lazy { FavoritesRepository(applicationContext) }
    private lateinit var title: TextView
    private lateinit var backButton: Button
    private lateinit var grid: GridLayout
    private lateinit var trackScroll: ScrollView
    private lateinit var trackContainer: LinearLayout
    private var selectedPlaylist: FavoritePlaylist? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)
        title = findViewById(R.id.tv_favorites_title)
        backButton = findViewById(R.id.btn_favorites_back)
        grid = findViewById(R.id.grid_playlists)
        trackScroll = findViewById(R.id.scroll_tracks)
        trackContainer = findViewById(R.id.container_tracks)
        backButton.setOnClickListener {
            if (selectedPlaylist != null) showPlaylistGrid() else finish()
        }
        showPlaylistGrid()
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onStop() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        mediaController = null
        super.onStop()
    }

    private fun showPlaylistGrid() {
        selectedPlaylist = null
        title.text = "收藏"
        trackScroll.visibility = View.GONE
        grid.visibility = View.VISIBLE
        grid.removeAllViews()
        grid.addView(createAddPlaylistTile())
        repository.getPlaylists().forEach { playlist ->
            grid.addView(createPlaylistTile(playlist))
        }
    }

    private fun createAddPlaylistTile(): View =
        createTile(titleText = "添加播放列表", artworkUri = null, iconResId = R.drawable.ic_add_playlist).apply {
            setOnClickListener { showCreatePlaylistDialog() }
        }

    private fun createPlaylistTile(playlist: FavoritePlaylist): View =
        createTile(titleText = "${playlist.name}\n${playlist.trackCount} 首", artworkUri = playlist.coverArtworkUri, iconResId = null).apply {
            setOnClickListener { showTracks(playlist) }
        }

    private fun createTile(titleText: String, artworkUri: String?, iconResId: Int?): LinearLayout {
        val context = this
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = getDrawable(R.drawable.bg_playlist_tile)
            val params = GridLayout.LayoutParams().apply {
                width = resources.getDimensionPixelSize(R.dimen.ui_playlist_tile_width)
                height = resources.getDimensionPixelSize(R.dimen.ui_playlist_tile_height)
                setMargins(0, 0, resources.getDimensionPixelSize(R.dimen.ui_space_lg), resources.getDimensionPixelSize(R.dimen.ui_space_lg))
            }
            layoutParams = params
            val image = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(72, 72)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(iconResId ?: R.drawable.default_cover)
                if (!artworkUri.isNullOrBlank()) {
                    setImageURI(android.net.Uri.parse(artworkUri))
                }
            }
            val text = TextView(context).apply {
                text = titleText
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.ui_text_primary))
                textSize = 15f
                typeface = AppFonts.medium(context)
            }
            addView(image)
            addView(text)
        }
    }

    private fun showTracks(playlist: FavoritePlaylist) {
        selectedPlaylist = playlist
        title.text = playlist.name
        grid.visibility = View.GONE
        trackScroll.visibility = View.VISIBLE
        trackContainer.removeAllViews()
        val tracks = repository.getTracks(playlist.id)
        if (tracks.isEmpty()) {
            Toast.makeText(this, "该播放列表暂无歌曲", Toast.LENGTH_SHORT).show()
        }
        tracks.forEachIndexed { index, track ->
            trackContainer.addView(createTrackRow(playlist, track, index))
        }
    }

    private fun createTrackRow(playlist: FavoritePlaylist, track: FavoriteTrack, index: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = getDrawable(R.drawable.bg_file_item)
            setPadding(16, 10, 16, 10)
            setOnClickListener { playPlaylistFrom(playlist, index) }

            val label = TextView(this@FavoritesActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${index + 1}. ${track.title}${track.artist?.let { " - $it" }.orEmpty()}"
                setTextColor(getColor(R.color.ui_text_primary))
                textSize = 18f
                typeface = AppFonts.medium(this@FavoritesActivity)
            }
            val delete = Button(this@FavoritesActivity).apply {
                setBackgroundResource(R.drawable.bg_button_red)
                setTextColor(getColor(R.color.ui_text_on_accent))
                minWidth = resources.getDimensionPixelSize(R.dimen.ui_favorites_delete_button_width)
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.ui_favorites_delete_button_width),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_delete, 0, 0, 0)
                text = ""
                contentDescription = "移除"
                setOnClickListener {
                    repository.removeTrack(playlist.id, track.mediaId)
                    showTracks(playlist)
                }
            }
            addView(label)
            addView(delete)
        }
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply {
            hint = "播放列表名称"
            typeface = AppFonts.regular(this@FavoritesActivity)
        }
        AlertDialog.Builder(this)
            .setTitle("新建播放列表")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val playlist = repository.createPlaylist(input.text.toString())
                if (playlist == null) {
                    Toast.makeText(this, "播放列表已存在或名称为空", Toast.LENGTH_SHORT).show()
                }
                showPlaylistGrid()
            }
            .show()
    }

    private fun playPlaylistFrom(playlist: FavoritePlaylist, startIndex: Int) {
        val tracks = repository.getTracks(playlist.id)
        if (tracks.isEmpty()) {
            Toast.makeText(this, "该播放列表暂无歌曲", Toast.LENGTH_SHORT).show()
            return
        }
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(this, "播放器初始化中，请稍后重试", Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }
        val invalid = tracks.getOrNull(startIndex)?.takeIf { it.streamUri.isBlank() }
        if (invalid != null) {
            confirmRemoveInvalidTrack(playlist, invalid)
            return
        }
        runCatching {
            val mediaItems = FavoriteTrackMediaItems.fromTracks(tracks)
            controller.repeatMode = Player.REPEAT_MODE_OFF
            controller.setShuffleModeEnabled(false)
            controller.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
            controller.prepare()
            controller.play()
        }.onFailure {
            confirmRemoveInvalidTrack(playlist, tracks[startIndex.coerceIn(0, tracks.lastIndex)])
        }
    }

    private fun confirmRemoveInvalidTrack(playlist: FavoritePlaylist, track: FavoriteTrack) {
        AlertDialog.Builder(this)
            .setMessage("歌曲已失效，是否移除？")
            .setPositiveButton("移除") { _, _ ->
                repository.removeTrack(playlist.id, track.mediaId)
                showTracks(playlist)
            }
            .setNegativeButton("保留", null)
            .show()
    }

    private fun ensureController() {
        if (mediaController != null || controllerFuture != null) return
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController = it }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(this, "播放器连接失败", Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor()
        )
    }
}
```

- [ ] **Step 5: Register activity**

Add to `AndroidManifest.xml` inside `<application>`:

```xml
        <activity
            android:name=".ui.FavoritesActivity"
            android:exported="false"
            android:screenOrientation="landscape" />
```

- [ ] **Step 6: Run layout test and build**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.FavoritesActivityLayoutTest"
.\gradlew.bat assembleDebug
```

Expected: tests PASS and Debug APKs are produced.

- [ ] **Step 7: Commit favorites activity**

Run:

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivity.kt app/src/main/res/layout/activity_favorites.xml app/src/main/res/drawable/bg_playlist_tile.xml app/src/main/res/values/dimens.xml app/src/main/AndroidManifest.xml app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivityLayoutTest.kt
git commit -m "feat: 增加收藏页播放列表管理"
```

## Task 7: Documentation and Final Verification

**Files:**
- Modify: `DESIGN.md`
- Modify: `docs/MANUAL.md`

- [ ] **Step 1: Update DESIGN.md**

Update these sections:

- `### 首页：SMB 浏览器`: playback control ASCII now shows `[收藏] [顺序播放] [随机播放] [回到当前播放]`.
- `### 播放页`: bottom bar now shows `[播放模式] [收藏] [睡眠]`; favorite states mention outline/filled heart.
- Add `### 收藏页`: playlist grid and track list ASCII from the spec.
- `### 遥控器与焦点规则`: mention playback long-press favorite modal and favorites page row removal.

- [ ] **Step 2: Update docs/MANUAL.md**

Add a new section:

```markdown
## 17. 收藏与播放列表

应用提供 SQLite 持久化的收藏与自定义播放列表能力。播放页收藏按钮位于播放模式右侧，默认回显当前歌曲在默认“收藏夹”中的状态：空心爱心表示未收藏，实心爱心表示已收藏。短按 OK 可加入或取消默认收藏夹；长按 OK 可选择已有自定义播放列表或新建播放列表，已包含当前歌曲的列表会置灰不可选。

列表页播放控制区域提供红色收藏入口，点击进入收藏页。收藏页首页以宫格展示播放列表，左上角为添加播放列表，其后为默认收藏夹和用户自建列表；宫格封面使用该列表最后加入歌曲的封面，没有封面时使用默认封面。进入播放列表后，歌曲按纵向列表展示，可点击播放，也可通过右侧删除图标从列表移除。移除只删除收藏记录，不删除 SMB 原始文件。

收藏数据保存在应用私有 SQLite 数据库 `favorites.db` 中，当前 schema 版本为 1，包含 `playlists` 与 `playlist_tracks` 两张表。后续涉及收藏数据结构变化时，应通过数据库版本升级迁移，不直接清空用户收藏。
```

Use section number `17`, because `docs/MANUAL.md` currently ends at section `16. Launcher 图标资源`.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.favorites.*" --tests "com.github.gbandszxc.tvmediaplayer.ui.PlaybackButtonPresentationTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.FavoritesActivityLayoutTest"
```

Expected: PASS.

- [ ] **Step 4: Run full unit tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Build Debug package**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: both Debug APKs exist:

```text
app\build\outputs\apk\debug\tsm-player-debug-armeabi-v7a-1.0.7.apk
app\build\outputs\apk\debug\tsm-player-debug-arm64-v8a-1.0.7.apk
```

- [ ] **Step 6: Commit docs and verification updates**

Run:

```powershell
git add DESIGN.md docs/MANUAL.md
git commit -m "docs: 记录收藏播放列表能力"
```

## Self-Review Checklist

- Spec coverage: data persistence, playback-page button, browser-page entry, favorites grid, track list playback/removal, duplicate disabled rows, invalid track removal prompt, docs, and Debug packaging are each covered by tasks.
- Placeholder scan: no incomplete sections or ambiguous instructions remain; command expectations are explicit.
- Type consistency: `FavoritePlaylist`, `FavoriteTrack`, `FavoritesRepository`, and `FavoriteTrackMediaItems` names are consistent across tasks.
