package com.github.gbandszxc.tvmediaplayer.ui

import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackButtonPresentationTest {

    @Test
    fun transportButtonsStayIconOnlyEvenWhenFocused() {
        val previous = PlaybackButtonPresentation.previous()
        val play = PlaybackButtonPresentation.playPause(isPlaying = false)
        val pause = PlaybackButtonPresentation.playPause(isPlaying = true)
        val next = PlaybackButtonPresentation.next()

        assertEquals("", previous.text)
        assertEquals("", play.text)
        assertEquals("", pause.text)
        assertEquals("", next.text)

        assertFalse(previous.expandsOnFocus)
        assertFalse(play.expandsOnFocus)
        assertFalse(pause.expandsOnFocus)
        assertFalse(next.expandsOnFocus)

        assertEquals("上一首", previous.contentDescription)
        assertEquals("播放", play.contentDescription)
        assertEquals("暂停", pause.contentDescription)
        assertEquals("下一首", next.contentDescription)

        assertEquals(R.drawable.ic_skip_previous, previous.iconResId)
        assertEquals(R.drawable.ic_play, play.iconResId)
        assertEquals(R.drawable.ic_pause, pause.iconResId)
        assertEquals(R.drawable.ic_skip_next, next.iconResId)
    }

    @Test
    fun fullscreenLyricsAndBackOnlyShowTextWhenFocused() {
        val lyricsCollapsed = PlaybackButtonPresentation.lyricsFullscreen(focused = false)
        val lyricsFocused = PlaybackButtonPresentation.lyricsFullscreen(focused = true)
        val backCollapsed = PlaybackButtonPresentation.backToBrowser(focused = false)
        val backFocused = PlaybackButtonPresentation.backToBrowser(focused = true)

        assertEquals("", lyricsCollapsed.text)
        assertEquals("歌词全屏", lyricsFocused.text)
        assertEquals("", backCollapsed.text)
        assertEquals("返回文件页", backFocused.text)

        assertTrue(lyricsFocused.expandsOnFocus)
        assertTrue(backFocused.expandsOnFocus)
        assertEquals("歌词全屏", lyricsCollapsed.contentDescription)
        assertEquals("返回文件页", backCollapsed.contentDescription)

        assertEquals(R.drawable.ic_lyrics_fullscreen, lyricsFocused.iconResId)
        assertEquals(R.drawable.ic_back_to_folder, backFocused.iconResId)
    }

    @Test
    fun iconOnlyTransportButtonsStillDrawCenteredIconWhenFocused() {
        val pause = PlaybackButtonPresentation.playPause(isPlaying = true)
        val lyrics = PlaybackButtonPresentation.lyricsFullscreen(focused = true)

        assertTrue(PlaybackButtonPresentation.shouldDrawCenteredIcon(pause, hasFocus = true))
        assertFalse(PlaybackButtonPresentation.shouldDrawCenteredIcon(lyrics, hasFocus = true))
    }

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
}
