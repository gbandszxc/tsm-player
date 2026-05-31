package com.github.gbandszxc.tvmediaplayer.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
}
