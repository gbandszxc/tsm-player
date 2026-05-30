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
}
