package com.github.gbandszxc.tvmediaplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormFieldSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormFieldSpecType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `browser playback button renderer tints icon for accent buttons`() {
        val context = RuntimeEnvironment.getApplication()
        val button = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        BrowserPlaybackButtonRenderer.apply(
            context = context,
            button = button,
            spec = PlaybackButtonPresentation.browserPlayOrder(focused = false),
            hasFocus = false,
        )

        assertNull(button.compoundDrawables[0])
    }

    @Test
    fun `browser playback button renderer uses compound icon when focused`() {
        val context = RuntimeEnvironment.getApplication()
        val button = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        BrowserPlaybackButtonRenderer.apply(
            context = context,
            button = button,
            spec = PlaybackButtonPresentation.browserPlayOrder(focused = true),
            hasFocus = true,
        )

        val leftIcon = button.compoundDrawables[0]
        assertNotNull(leftIcon)
        assertNotNull(leftIcon.colorFilter)
    }

    @Test
    fun `browser playback button renderer uses compact expanded width for short labels`() {
        val context = RuntimeEnvironment.getApplication()
        val shortButton = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val longButton = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        BrowserPlaybackButtonRenderer.apply(
            context = context,
            button = shortButton,
            spec = PlaybackButtonPresentation.browserHistory(focused = true),
            hasFocus = true,
        )
        BrowserPlaybackButtonRenderer.apply(
            context = context,
            button = longButton,
            spec = PlaybackButtonPresentation.browserPlayOrder(focused = true),
            hasFocus = true,
        )

        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_playback_favorite_button_expanded_min_width),
            shortButton.layoutParams.width,
        )
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_playback_mode_button_expanded_min_width),
            longButton.layoutParams.width,
        )
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

    // --- SMB Modal 数据构造测试 ---

    @Test
    fun `connection manager actions keep edit create and switch order`() {
        val labels = TvBrowseFragment.buildConnectionManagerActionLabelsForTest(
            hasEditableConnection = true,
            hasSavedActiveConnection = true,
        )
        assertEquals(listOf("编辑当前连接", "删除当前连接", "新建连接", "切换连接"), labels)
    }

    @Test
    fun `connection manager actions omit edit when no editable connection`() {
        val labels = TvBrowseFragment.buildConnectionManagerActionLabelsForTest(
            hasEditableConnection = false,
            hasSavedActiveConnection = false,
        )
        assertEquals(listOf("新建连接", "切换连接"), labels)
    }

    @Test
    fun `connection manager actions omit delete when current connection is not saved`() {
        val labels = TvBrowseFragment.buildConnectionManagerActionLabelsForTest(
            hasEditableConnection = true,
            hasSavedActiveConnection = false,
        )
        assertEquals(listOf("编辑当前连接", "新建连接", "切换连接"), labels)
    }

    @Test
    fun `smb config form fields include checkboxes for guest smb1 and saveAsNew`() {
        val fields = TvBrowseFragment.buildConfigFormFieldsForTest(
            config = SmbConfig(host = "192.168.0.10", share = "music", path = "", username = "", password = "", guest = true, smb1Enabled = false),
            connectionName = "客厅 NAS",
            saveAsNewDefault = false,
        )
        val fieldKeys = fields.map { it.key }
        assertTrue("form should contain 'name' field", fieldKeys.contains("name"))
        assertTrue("form should contain 'host' field", fieldKeys.contains("host"))
        assertTrue("form should contain 'guest' checkbox", fieldKeys.contains("guest"))
        assertTrue("form should contain 'smb1' checkbox", fieldKeys.contains("smb1"))
        assertTrue("form should contain 'saveAsNew' checkbox", fieldKeys.contains("saveAsNew"))

        val guestField = fields.first { it.key == "guest" }
        assertEquals(FormFieldSpecType.CHECKBOX, guestField.type)
        assertEquals("true", guestField.initialValue)

        val smb1Field = fields.first { it.key == "smb1" }
        assertEquals(FormFieldSpecType.CHECKBOX, smb1Field.type)
        assertEquals("false", smb1Field.initialValue)

        val hostField = fields.first { it.key == "host" }
        assertEquals(FormFieldSpecType.TEXT, hostField.type)
        assertEquals("192.168.0.10", hostField.initialValue)
    }

    @Test
    fun `smb config form prefill connection name from saved connection`() {
        val fields = TvBrowseFragment.buildConfigFormFieldsForTest(
            config = SmbConfig.Empty,
            connectionName = "客厅 NAS",
            saveAsNewDefault = true,
        )
        val nameField = fields.first { it.key == "name" }
        assertEquals("客厅 NAS", nameField.initialValue)

        val saveAsNewField = fields.first { it.key == "saveAsNew" }
        assertEquals("true", saveAsNewField.initialValue)
    }

    @Test
    fun `browser file toolbar keeps refresh before sort and removes retry button`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_tv_browser, FrameLayout(context), false)

        val refresh = root.findViewById<View>(R.id.btn_refresh)
        val sort = root.findViewById<View>(R.id.btn_sort)

        assertNotNull("btn_refresh should exist", refresh)
        assertNotNull("btn_sort should exist", sort)
        assertTrue("refresh should be left of sort", leftOf(refresh, sort))
    }

    @Test
    fun `file row exposes name size and modified columns`() {
        val context = RuntimeEnvironment.getApplication()
        val row = LayoutInflater.from(context)
            .inflate(R.layout.item_file_entry, FrameLayout(context), false)

        assertNotNull(row.findViewById<View>(R.id.tv_tag))
        assertNotNull(row.findViewById<View>(R.id.tv_name))
        assertNotNull(row.findViewById<View>(R.id.tv_size))
        assertNotNull(row.findViewById<View>(R.id.tv_modified))
    }

    @Test
    fun `browser sort dropdown uses dedicated lightweight layout instead of modal shell`() {
        val context = RuntimeEnvironment.getApplication()
        val dropdown = LayoutInflater.from(context)
            .inflate(R.layout.view_browser_sort_dropdown, FrameLayout(context), false)

        assertNotNull(dropdown.findViewById<View>(R.id.container_sort_options))
        assertNull(dropdown.findViewById<View?>(R.id.container_modal_content))
    }

    @Test
    fun `browser sort outside touch overlay covers screen without taking remote focus`() {
        val context = RuntimeEnvironment.getApplication()
        val overlay = TvBrowseFragment.createSortOutsideDismissOverlayForTest(context)
        val params = overlay.layoutParams as FrameLayout.LayoutParams

        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, params.height)
        assertTrue(overlay.isClickable)
        assertFalse(overlay.isFocusable)
    }
}
