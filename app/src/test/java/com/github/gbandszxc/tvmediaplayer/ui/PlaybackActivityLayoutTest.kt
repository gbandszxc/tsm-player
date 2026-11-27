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
        assertEquals("+ New Playlist", choices.last().label)
        assertTrue(choices.last().createNew)
        assertFalse(choices.last().disabled)

        // 已包含的播放列表应该是 disabled
        assertTrue(choices.first().disabled)
        // 未包含的播放列表应该可用
        assertFalse(choices[1].disabled)
    }

    @Test
    fun `fullscreen artwork overlay hosts floating stage and non-focusable slim bottom progress`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.activity_playback, FrameLayout(context), false)
        val host = root.findViewById<View>(R.id.layout_artwork_fullscreen_host)
        val stage = root.findViewById<View>(R.id.layout_artwork_fullscreen_stage)
        val rim = root.findViewById<View>(R.id.view_artwork_fullscreen_rim)
        val progress = root.findViewById<View>(R.id.pb_artwork_fullscreen_progress)
        val progressParams = progress.layoutParams as FrameLayout.LayoutParams

        assertNotNull(host)
        assertNotNull(stage)
        assertNotNull(rim)
        assertNotNull(progress)
        assertFalse(progress.isFocusable)
        assertTrue(host.paddingBottom > progressParams.bottomMargin + progressParams.height)
    }

    @Test
    fun `fullscreen lyrics layout exposes dual-column theater controls`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.activity_lyrics_fullscreen, FrameLayout(context), false)
        val stage = root.findViewById<View>(R.id.layout_fullscreen_artwork_stage)
        val playPause = root.findViewById<View>(R.id.btn_fullscreen_play_pause)
        val close = root.findViewById<View>(R.id.btn_close_fullscreen_lyrics)
        val progress = root.findViewById<View>(R.id.pb_fullscreen_progress)
        val lyrics = root.findViewById<View>(R.id.tv_fullscreen_lyrics)

        assertNotNull(stage)
        assertNotNull(playPause)
        assertNotNull(close)
        assertNotNull(progress)
        assertNotNull(lyrics)
        assertTrue(playPause.isFocusable)
        assertTrue(close.isFocusable)
        assertEquals(close.id, playPause.nextFocusRightId)
        assertEquals(playPause.id, close.nextFocusLeftId)
    }

    @Test
    fun `settings layout exposes dual floating panels and fixed detail header`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.activity_settings, FrameLayout(context), false)
        val sidebar = root.findViewById<View>(R.id.panel_settings_sidebar)
        val detailPanel = root.findViewById<View>(R.id.panel_settings_detail)
        val detailTitle = root.findViewById<View>(R.id.tv_settings_detail_title)
        val scrollDetail = root.findViewById<View>(R.id.scroll_settings_detail)

        assertNotNull(sidebar)
        assertNotNull(detailPanel)
        assertNotNull(detailTitle)
        assertNotNull(scrollDetail)
    }
}
