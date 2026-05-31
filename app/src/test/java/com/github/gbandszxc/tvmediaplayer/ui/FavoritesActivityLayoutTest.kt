package com.github.gbandszxc.tvmediaplayer.ui

import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritePlaylist
import com.github.gbandszxc.tvmediaplayer.favorites.FavoriteTrack
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesDbHelper
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesRepository
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
        assertNotEquals(
            HorizontalScrollView::class.java,
            root.findViewById<android.view.View>(R.id.grid_playlists).parent::class.java
        )
    }

    @Test
    fun `empty track list moves focus to back button`() {
        val activity = Robolectric.buildActivity(FavoritesActivity::class.java)
            .create()
            .get()
        val backButton = activity.findViewById<android.widget.Button>(R.id.btn_favorites_back)

        FavoritesEmptyTrackFocus.requestFallbackFocus(emptyList(), backButton)

        assertTrue(backButton.isFocused)
    }

    @Test
    fun `favorite track row exposes dedicated play and delete icon buttons for d pad`() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
        val repository = FavoritesRepository(context)
        repository.addTrack(
            FavoritesRepository.DEFAULT_PLAYLIST_ID,
            FavoriteTrack(
                id = "track-a",
                playlistId = FavoritesRepository.DEFAULT_PLAYLIST_ID,
                mediaId = "Music/A.flac",
                streamUri = "smb://nas/Media/Music/A.flac",
                title = "Track A",
                artist = "Artist",
                album = "Album",
                artworkUri = null,
                sourceConnectionId = null,
                sourceConfig = null,
                addedAt = 1_000L,
            )
        )

        val activity = Robolectric.buildActivity(FavoritesActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        val grid = activity.findViewById<android.widget.GridLayout>(R.id.grid_playlists)
        grid.getChildAt(1).performClick()

        val row = activity.findViewById<LinearLayout>(R.id.container_tracks).getChildAt(0) as LinearLayout
        val playButton = row.getChildAt(1) as Button
        val deleteButton = row.getChildAt(2) as Button

        assertEquals(3, row.childCount)
        assertEquals("", playButton.text.toString())
        assertEquals("", deleteButton.text.toString())
        assertTrue(playButton.isFocusable)
        assertTrue(deleteButton.isFocusable)
    }

    @Test
    fun `playlist detail only shows delete playlist button for custom playlists`() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FavoritesDbHelper.DB_NAME)
        val repository = FavoritesRepository(context)
        repository.createPlaylist("夜跑")

        val activity = Robolectric.buildActivity(FavoritesActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        val grid = activity.findViewById<android.widget.GridLayout>(R.id.grid_playlists)
        val deletePlaylistButton = activity.findViewById<Button>(R.id.btn_favorites_delete_playlist)

        grid.getChildAt(1).performClick()
        assertEquals(android.view.View.GONE, deletePlaylistButton.visibility)

        grid.getChildAt(2).performClick()
        assertEquals(android.view.View.VISIBLE, deletePlaylistButton.visibility)
    }

    @Test
    fun `buildFavoritesPlaylistChoices returns create new item after playlists`() {
        val choices = FavoritesActivity.buildFavoritesPlaylistChoicesForTest(
            playlists = listOf(
                FavoritePlaylist(
                    id = "default",
                    name = "收藏夹",
                    isDefault = true,
                    createdAt = 0L,
                    trackCount = 12,
                    coverArtworkUri = null,
                    updatedAt = 1L,
                ),
                FavoritePlaylist(
                    id = "night",
                    name = "夜间播放",
                    isDefault = false,
                    createdAt = 0L,
                    trackCount = 5,
                    coverArtworkUri = null,
                    updatedAt = 2L,
                ),
            ),
            containedPlaylists = emptySet(),
        )

        // "新建播放列表" 应该是最后一项
        assertEquals("+ 新建播放列表", choices.last().label)
        assertTrue(choices.last().createNew)
        assertFalse(choices.last().disabled)

        // 两个播放列表都可用
        assertEquals(2, choices.size - 1) // 减去最后的新建项
        assertFalse(choices[0].disabled)
        assertFalse(choices[1].disabled)
    }
}
