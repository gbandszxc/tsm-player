package com.github.gbandszxc.tvmediaplayer.ui

import android.graphics.Rect
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.FragmentActivity
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class TvBrowseFragmentLayoutTest {

    @Test
    @Config(sdk = [28], qualifiers = "w960dp-h540dp-land")
    fun `up from file toolbar reveals the playback header in a subdirectory`() {
        val root = attachBrowser()
        val scroll = root.findViewById<ScrollView>(R.id.root_scroll)
        val toolbar = root.findViewById<View>(R.id.btn_refresh)
        val playback = root.findViewById<View>(R.id.btn_play_all)
        val scrollContent = scroll.getChildAt(0)
        root.findViewById<View>(R.id.panel_connection).visibility = View.GONE
        scrollContent.minimumHeight = 1200
        layoutBrowser(root, 960, 540)
        assertTrue(toolbar.requestFocus())
        scroll.scrollTo(0, 180)
        assertTrue(scroll.scrollY > 0)

        assertTrue(toolbar.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))

        assertTrue(playback.hasFocus())
        assertEquals(0, scroll.scrollY)
    }

    @Test
    @Config(sdk = [28], qualifiers = "w640dp-h360dp-land")
    fun `sort dropdown stays within a short viewport and holds side arrow focus`() {
        val root = attachBrowser()
        layoutBrowser(root, 640, 320)
        val sort = root.findViewById<View>(R.id.btn_sort)
        assertTrue(sort.requestFocus())
        sort.performClick()
        layoutBrowser(root, 640, 320)
        val dropdown = root.findViewById<View>(R.id.container_sort_dropdown)
        val option = root.findFocus()
        val margin = root.resources.getDimensionPixelSize(R.dimen.ui_space_3xl)

        assertTrue(dropdown.width > 0 && dropdown.height > 0)
        assertTrue(dropdown.left >= margin && dropdown.right <= root.width - margin)
        assertTrue(dropdown.top >= margin && dropdown.bottom <= root.height - margin)
        assertTrue(isDescendantOf(option.parent, dropdown))
        for (key in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertTrue(option.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
            assertSame(option, root.findFocus())
        }
    }

    @Test
    @Config(sdk = [28], qualifiers = "w640dp-h360dp-land")
    fun `settings entry scrolls clear of the rail bottom padding`() {
        val root = attachBrowser()
        layoutBrowser(root, 640, 320)
        val rail = root.findViewById<ScrollView>(R.id.panel_nav_rail)
        val settings = root.findViewById<View>(R.id.btn_settings)
        assertTrue(rail.canScrollVertically(1))

        assertTrue(settings.requestFocus())

        val bounds = Rect(0, 0, settings.width, settings.height)
        rail.offsetDescendantRectToMyCoords(settings, bounds)
        assertTrue(bounds.bottom <= rail.scrollY + rail.height - rail.paddingBottom)
    }

    private fun attachBrowser(): View {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val fragment = TvBrowseFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        return fragment.requireView()
    }

    private fun layoutBrowser(root: View, width: Int, height: Int) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
    }

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
    fun `collapsible nav rail sits outside scroll container with reserved start channel`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_tv_browser, FrameLayout(context), false)
        val scrollView = root.findViewById<ScrollView>(R.id.root_scroll)
        val scrollParams = scrollView.layoutParams as FrameLayout.LayoutParams
        val navRail = root.findViewById<View>(R.id.panel_nav_rail)
        val backTop = root.findViewById<View>(R.id.btn_back_top)
        val browseMode = root.findViewById<View>(R.id.btn_browse_mode)
        val favorites = root.findViewById<View>(R.id.btn_favorites)
        val history = root.findViewById<View>(R.id.btn_history)
        val navConnection = root.findViewById<View>(R.id.btn_nav_connection)
        val settings = root.findViewById<View>(R.id.btn_settings)

        assertEquals(0, scrollParams.topMargin)
        assertTrue(
            scrollParams.marginStart >= context.resources.getDimensionPixelSize(R.dimen.ui_nav_rail_collapsed_width),
        )
        assertFalse(isDescendantOf(navRail.parent, scrollView))
        assertTrue(isDescendantOf(backTop.parent, navRail))
        assertTrue(isDescendantOf(browseMode.parent, navRail))
        assertTrue(isDescendantOf(favorites.parent, navRail))
        assertTrue(isDescendantOf(history.parent, navRail))
        assertTrue(isDescendantOf(navConnection.parent, navRail))
        assertTrue(isDescendantOf(settings.parent, navRail))
        assertEquals(backTop.paddingStart, backTop.paddingEnd)
        assertEquals(favorites.paddingStart, favorites.paddingEnd)
        assertTrue(
            context.resources.getDimensionPixelSize(R.dimen.ui_nav_rail_expanded_width) <=
                (160 * context.resources.displayMetrics.density).toInt(),
        )
        assertTrue(context.getString(R.string.browser_nav_back).length <= 4)
    }

    @Test
    fun `nav rail expanded width adapts symmetrically to short and long labels`() {
        val shortWidth = TvBrowseFragment.computeNavRailExpandedWidthForTest(
            panelPaddingHorizontalPx = 16,
            itemPaddingStartPx = 12,
            itemPaddingEndPx = 12,
            iconWidthPx = 24,
            labelMarginStartPx = 12,
            maxLabelWidthPx = 32,
            opticalRightBalancePx = 2,
            collapsedWidthPx = 64,
            fallbackExpandedWidthPx = 148,
            maxExpandedWidthPx = 240,
        )
        assertEquals(110, shortWidth)
        assertTrue(shortWidth < 148)

        val nasWidth = TvBrowseFragment.computeNavRailExpandedWidthForTest(
            panelPaddingHorizontalPx = 16,
            itemPaddingStartPx = 12,
            itemPaddingEndPx = 12,
            iconWidthPx = 24,
            labelMarginStartPx = 12,
            maxLabelWidthPx = 64,
            opticalRightBalancePx = 2,
            collapsedWidthPx = 64,
            fallbackExpandedWidthPx = 148,
            maxExpandedWidthPx = 240,
        )
        assertEquals(142, nasWidth)

        val englishWidth = TvBrowseFragment.computeNavRailExpandedWidthForTest(
            panelPaddingHorizontalPx = 16,
            itemPaddingStartPx = 12,
            itemPaddingEndPx = 12,
            iconWidthPx = 24,
            labelMarginStartPx = 12,
            maxLabelWidthPx = 86,
            opticalRightBalancePx = 2,
            collapsedWidthPx = 64,
            fallbackExpandedWidthPx = 148,
            maxExpandedWidthPx = 240,
        )
        assertEquals(164, englishWidth)

        val fallbackWidth = TvBrowseFragment.computeNavRailExpandedWidthForTest(
            panelPaddingHorizontalPx = 16,
            itemPaddingStartPx = 12,
            itemPaddingEndPx = 12,
            iconWidthPx = 24,
            labelMarginStartPx = 12,
            maxLabelWidthPx = 0,
            opticalRightBalancePx = 2,
            collapsedWidthPx = 64,
            fallbackExpandedWidthPx = 148,
            maxExpandedWidthPx = 240,
        )
        assertEquals(148, fallbackWidth)
    }

    @Test
    fun `playback control row keeps play order shuffle and now playing buttons`() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_tv_browser, FrameLayout(context), false)
        val playAll = root.findViewById<View>(R.id.btn_play_all)
        val playShuffle = root.findViewById<View>(R.id.btn_play_shuffle)
        val nowPlaying = root.findViewById<View>(R.id.btn_now_playing)

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
            spec = PlaybackButtonPresentation.browserPlayOrder(context, focused = false),
            hasFocus = false,
        )

        assertNotNull(button.compoundDrawables[0])
        assertEquals(0, button.compoundDrawablePadding)
        val basePadding = context.resources.getDimensionPixelSize(R.dimen.ui_space_3xl)
        val correction = context.resources.getDimensionPixelSize(R.dimen.ui_space_sm) / 2
        assertEquals(basePadding + correction, button.paddingStart)
        assertEquals(basePadding - correction, button.paddingEnd)
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
            spec = PlaybackButtonPresentation.browserPlayOrder(context, focused = true),
            hasFocus = true,
        )

        val leftIcon = button.compoundDrawables[0]
        assertNotNull(leftIcon)
        assertNotNull(leftIcon.colorFilter)
    }

    @Test
    fun `browser playback button renderer lets focused labels size to one line`() {
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
            spec = PlaybackButtonPresentation.browserHistory(context, focused = true),
            hasFocus = true,
        )

        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            button.layoutParams.width,
        )
        assertEquals(
            1,
            button.maxLines,
        )
        assertTrue(button.isSingleLine)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_space_sm),
            button.compoundDrawablePadding,
        )
        assertTrue(
            "Focused browser controls should keep a compact minimum while allowing longer English labels to expand",
            button.minWidth >=
            context.resources.getDimensionPixelSize(R.dimen.ui_playback_favorite_button_expanded_min_width),
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
        assertEquals(listOf("Edit Current Connection", "Delete Current Connection", "New Connection", "Switch Connection"), labels)
    }

    @Test
    fun `connection manager actions omit edit when no editable connection`() {
        val labels = TvBrowseFragment.buildConnectionManagerActionLabelsForTest(
            hasEditableConnection = false,
            hasSavedActiveConnection = false,
        )
        assertEquals(listOf("New Connection", "Switch Connection"), labels)
    }

    @Test
    fun `connection manager actions omit delete when current connection is not saved`() {
        val labels = TvBrowseFragment.buildConnectionManagerActionLabelsForTest(
            hasEditableConnection = true,
            hasSavedActiveConnection = false,
        )
        assertEquals(listOf("Edit Current Connection", "New Connection", "Switch Connection"), labels)
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
        val viewMode = root.findViewById<View>(R.id.btn_view_mode)

        assertNotNull("btn_refresh should exist", refresh)
        assertNotNull("btn_sort should exist", sort)
        assertNotNull("btn_view_mode should exist", viewMode)
        assertTrue("refresh should be left of sort", leftOf(refresh, sort))
        assertTrue("sort should be left of view mode", leftOf(sort, viewMode))
    }

    @Test
    fun `file row exposes name size and modified columns`() {
        val context = RuntimeEnvironment.getApplication()
        val row = LayoutInflater.from(context)
            .inflate(R.layout.item_file_entry, FrameLayout(context), false)

        assertNotNull(row.findViewById<View>(R.id.iv_tag))
        assertNotNull(row.findViewById<View>(R.id.tv_name))
        assertNotNull(row.findViewById<View>(R.id.tv_size))
        assertNotNull(row.findViewById<View>(R.id.tv_modified))
    }

    @Test
    fun `grid file item exposes artwork and name without list metadata columns`() {
        val context = RuntimeEnvironment.getApplication()
        val item = LayoutInflater.from(context)
            .inflate(R.layout.item_file_entry_grid, FrameLayout(context), false)

        assertNotNull(item.findViewById<View>(R.id.iv_artwork))
        assertNotNull(item.findViewById<View>(R.id.tv_name))
        assertNull(item.findViewById<View?>(R.id.tv_size))
        assertNull(item.findViewById<View?>(R.id.tv_modified))
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
