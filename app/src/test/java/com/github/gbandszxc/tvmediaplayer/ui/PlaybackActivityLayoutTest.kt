package com.github.gbandszxc.tvmediaplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaybackActivityLayoutTest {

    @Test
    fun `favorite button sits between play mode and sleep timer`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.activity_playback, FrameLayout(context), false)
        val playMode = root.findViewById<View>(R.id.btn_play_mode)
        val favorite = root.findViewById<View>(R.id.btn_favorite)
        val sleepTimer = root.findViewById<View>(R.id.btn_sleep_timer)
        val buttonBar = playMode.parent as LinearLayout

        assertNotNull(favorite)
        assertEquals(buttonBar, favorite.parent)
        assertEquals(buttonBar.indexOfChild(playMode) + 1, buttonBar.indexOfChild(favorite))
        assertEquals(buttonBar.indexOfChild(favorite) + 1, buttonBar.indexOfChild(sleepTimer))
    }

    @Test
    fun `favorite playlist choices expose disabled rows to list selection`() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = FavoritePlaylistChoiceAdapter(
            context = context,
            choices = listOf(
                FavoritePlaylistChoice(
                    playlistId = "already-added",
                    label = "已收藏",
                    disabled = true,
                ),
                FavoritePlaylistChoice(
                    playlistId = "available",
                    label = "可收藏",
                    disabled = false,
                ),
            ),
        )

        assertFalse(adapter.areAllItemsEnabled())
        assertFalse(adapter.isEnabled(0))
        assertTrue(adapter.isEnabled(1))
    }

    @Test
    fun `buildFavoritePlaylistChoices puts create new item after playlists`() {
        val choices = PlaybackActivity.buildFavoritePlaylistChoicesForTest(
            playlists = listOf(
                com.github.gbandszxc.tvmediaplayer.favorites.FavoritePlaylist(
                    id = "default",
                    name = "收藏夹",
                    isDefault = true,
                    createdAt = 0L,
                    trackCount = 12,
                    coverArtworkUri = null,
                    updatedAt = 1L,
                ),
                com.github.gbandszxc.tvmediaplayer.favorites.FavoritePlaylist(
                    id = "night",
                    name = "夜间播放",
                    isDefault = false,
                    createdAt = 0L,
                    trackCount = 5,
                    coverArtworkUri = null,
                    updatedAt = 2L,
                ),
            ),
            containedPlaylists = setOf("default"),
        )

        // "新建播放列表" 应该是最后一项
        assertEquals("+ 新建播放列表", choices.last().label)
        assertTrue(choices.last().createNew)
        assertFalse(choices.last().disabled)

        // 已包含的播放列表应该是 disabled
        assertTrue(choices.first().disabled)
        // 未包含的播放列表应该可用
        assertFalse(choices[1].disabled)
    }
}
