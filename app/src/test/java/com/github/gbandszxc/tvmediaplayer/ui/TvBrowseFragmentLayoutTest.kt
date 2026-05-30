package com.github.gbandszxc.tvmediaplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.ScrollView
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TvBrowseFragmentLayoutTest {

    @Test
    fun `fast locate panel is attached outside scroll container so it stays visible during long list jumps`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_tv_browser, FrameLayout(context), false)
        val scrollView = root.findViewById<ScrollView>(R.id.root_scroll)
        val fastLocatePanel = root.findViewById<View>(R.id.panel_fast_locate)

        assertFalse(isDescendantOf(fastLocatePanel.parent, scrollView))
        assertSame(root, fastLocatePanel.parent)
    }

    @Test
    fun `playback control row keeps favorites before playback action buttons`() {
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

    private fun isDescendantOf(parent: ViewParent?, ancestor: View): Boolean {
        var current = parent
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun leftOf(left: View, right: View): Boolean {
        val parent = left.parent as ViewParent
        return parent === right.parent && (parent as ViewGroup).indexOfChild(left) < parent.indexOfChild(right)
    }
}
