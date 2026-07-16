package com.github.gbandszxc.tvmediaplayer.ui

import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaybackButtonPresentationTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun transportButtonsStayIconOnlyEvenWhenFocused() {
        val previous = PlaybackButtonPresentation.previous(context)
        val play = PlaybackButtonPresentation.playPause(context, isPlaying = false)
        val pause = PlaybackButtonPresentation.playPause(context, isPlaying = true)
        val next = PlaybackButtonPresentation.next(context)

        assertEquals("", previous.text)
        assertEquals("", play.text)
        assertEquals("", pause.text)
        assertEquals("", next.text)

        assertFalse(previous.expandsOnFocus)
        assertFalse(play.expandsOnFocus)
        assertFalse(pause.expandsOnFocus)
        assertFalse(next.expandsOnFocus)

        assertEquals(context.getString(R.string.common_previous), previous.contentDescription)
        assertEquals(context.getString(R.string.common_play), play.contentDescription)
        assertEquals(context.getString(R.string.common_pause), pause.contentDescription)
        assertEquals(context.getString(R.string.common_next), next.contentDescription)

        assertEquals(R.drawable.ic_skip_previous, previous.iconResId)
        assertEquals(R.drawable.ic_play, play.iconResId)
        assertEquals(R.drawable.ic_pause, pause.iconResId)
        assertEquals(R.drawable.ic_skip_next, next.iconResId)
    }

    @Test
    fun fullscreenLyricsAndBackOnlyShowTextWhenFocused() {
        val lyricsCollapsed = PlaybackButtonPresentation.lyricsFullscreen(context, focused = false)
        val lyricsFocused = PlaybackButtonPresentation.lyricsFullscreen(context, focused = true)
        val backCollapsed = PlaybackButtonPresentation.backToBrowser(context, focused = false)
        val backFocused = PlaybackButtonPresentation.backToBrowser(context, focused = true)

        assertEquals("", lyricsCollapsed.text)
        assertEquals(context.getString(R.string.playback_lyrics_fullscreen), lyricsFocused.text)
        assertEquals("", backCollapsed.text)
        assertEquals(context.getString(R.string.playback_back_to_browser), backFocused.text)

        assertTrue(lyricsFocused.expandsOnFocus)
        assertTrue(backFocused.expandsOnFocus)
        assertEquals(context.getString(R.string.playback_lyrics_fullscreen), lyricsCollapsed.contentDescription)
        assertEquals(context.getString(R.string.playback_back_to_browser), backCollapsed.contentDescription)

        assertEquals(R.drawable.ic_lyrics_fullscreen, lyricsFocused.iconResId)
        assertEquals(R.drawable.ic_back_to_folder, backFocused.iconResId)
    }

    @Test
    fun favoriteButtonReflectsDefaultPlaylistStateAndExpandsOnFocus() {
        val notSaved = PlaybackButtonPresentation.favorite(context, inDefaultFavorites = false, focused = false)
        val saved = PlaybackButtonPresentation.favorite(context, inDefaultFavorites = true, focused = true)

        assertEquals("", notSaved.text)
        assertEquals(context.getString(R.string.playback_favorite), saved.text)
        assertEquals(context.getString(R.string.playback_favorite), notSaved.contentDescription)
        assertTrue(saved.expandsOnFocus)
        assertEquals(R.drawable.ic_favorite_outline, notSaved.iconResId)
        assertEquals(R.drawable.ic_favorite_filled, saved.iconResId)
    }

    @Test
    fun browserPlaybackButtonsUseCollapsedIconsAndFocusedText() {
        val collapsedFavorites = PlaybackButtonPresentation.browserFavorites(context, focused = false)
        val collapsedHistory = PlaybackButtonPresentation.browserHistory(context, focused = false)
        val collapsedOrder = PlaybackButtonPresentation.browserPlayOrder(context, focused = false)
        val collapsedShuffle = PlaybackButtonPresentation.browserPlayShuffle(context, focused = false)
        val favorites = PlaybackButtonPresentation.browserFavorites(context, focused = true)
        val history = PlaybackButtonPresentation.browserHistory(context, focused = true)
        val order = PlaybackButtonPresentation.browserPlayOrder(context, focused = true)
        val shuffle = PlaybackButtonPresentation.browserPlayShuffle(context, focused = true)

        assertEquals("", collapsedFavorites.text)
        assertEquals("", collapsedHistory.text)
        assertEquals("", collapsedOrder.text)
        assertEquals("", collapsedShuffle.text)
        assertEquals(context.getString(R.string.playback_favorite), collapsedFavorites.contentDescription)
        assertEquals(context.getString(R.string.history_title), collapsedHistory.contentDescription)
        assertEquals(context.getString(R.string.playback_mode_order), collapsedOrder.contentDescription)
        assertEquals(context.getString(R.string.playback_mode_shuffle), collapsedShuffle.contentDescription)
        assertEquals(context.getString(R.string.playback_favorite), favorites.text)
        assertEquals(context.getString(R.string.history_title), history.text)
        assertEquals(context.getString(R.string.playback_mode_order), order.text)
        assertEquals(context.getString(R.string.playback_mode_shuffle), shuffle.text)
        assertEquals(R.drawable.ic_favorite_filled, favorites.iconResId)
        assertEquals(R.drawable.ic_history, history.iconResId)
        assertEquals(R.drawable.ic_play_order, order.iconResId)
        assertEquals(R.drawable.ic_shuffle, shuffle.iconResId)
    }

    @Test
    fun browseModeUsesSwitchIconAndShowsCurrentSourceWhenFocused() {
        val networkCollapsed = PlaybackButtonPresentation.browserBrowseMode(context, local = false, focused = false)
        val localFocused = PlaybackButtonPresentation.browserBrowseMode(context, local = true, focused = true)

        assertEquals("", networkCollapsed.text)
        assertEquals(context.getString(R.string.browser_mode_local), networkCollapsed.contentDescription)
        assertEquals(context.getString(R.string.browser_mode_nas), localFocused.text)
        assertEquals(R.drawable.ic_switch_source, networkCollapsed.iconResId)
        assertEquals(R.dimen.ui_playback_favorite_button_expanded_min_width, localFocused.browserExpandedWidthResId)
        assertTrue(localFocused.expandsOnFocus)
    }

    @Test
    fun browserViewModeUsesCurrentModeIconAndOnlyExpandsTextOnFocus() {
        val listCollapsed = PlaybackButtonPresentation.browserViewMode(context, grid = false, focused = false)
        val gridFocused = PlaybackButtonPresentation.browserViewMode(context, grid = true, focused = true)

        assertEquals("", listCollapsed.text)
        assertEquals(context.getString(R.string.browser_view_list), listCollapsed.contentDescription)
        assertEquals(R.drawable.ic_view_list, listCollapsed.iconResId)
        assertEquals(context.getString(R.string.browser_view_grid), gridFocused.text)
        assertEquals(context.getString(R.string.browser_view_grid), gridFocused.contentDescription)
        assertEquals(R.drawable.ic_view_grid, gridFocused.iconResId)
        assertTrue(gridFocused.expandsOnFocus)
    }
}
