package com.github.gbandszxc.tvmediaplayer.ui

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserSortFormattingTest {

    @Test
    fun `format size returns readable unit and unknown placeholder`() {
        assertEquals("--", TvBrowseFragment.formatSizeForTest(null, isDirectory = false))
        assertEquals("--", TvBrowseFragment.formatSizeForTest(null, isDirectory = true))
        assertEquals("1.5 KB", TvBrowseFragment.formatSizeForTest(1536, isDirectory = false))
    }

    @Test
    fun `format modified time returns full timestamp`() {
        assertEquals(
            "2026-05-31 08:09:10",
            TvBrowseFragment.formatModifiedTimeForTest(
                1_780_186_150_000L,
                java.util.TimeZone.getTimeZone("Asia/Shanghai")
            )
        )
    }

    @Test
    fun `metadata placeholder is centered while real metadata is right aligned`() {
        assertEquals(Gravity.CENTER, TvBrowseFragment.metadataColumnGravityForTest("--"))
        assertEquals(Gravity.END, TvBrowseFragment.metadataColumnGravityForTest("42.8 MB"))
        assertEquals(Gravity.END, TvBrowseFragment.metadataColumnGravityForTest("2026-05-31 08:09:10"))
    }

    @Test
    fun `sort dropdown scroll delta keeps panel bottom visible with margin`() {
        assertEquals(
            36,
            TvBrowseFragment.sortDropdownScrollDeltaForTest(
                dropdownBottom = 620,
                visibleBottom = 600,
                safetyMargin = 16,
            )
        )
        assertEquals(
            0,
            TvBrowseFragment.sortDropdownScrollDeltaForTest(
                dropdownBottom = 580,
                visibleBottom = 600,
                safetyMargin = 16,
            )
        )
    }

    @Test
    fun `sort dropdown initial focus follows current sort option`() {
        assertEquals(0, TvBrowseFragment.sortDropdownInitialFocusIndexForTest(BrowserSortOption.NAME_ASC))
        assertEquals(3, TvBrowseFragment.sortDropdownInitialFocusIndexForTest(BrowserSortOption.SIZE_DESC))
        assertEquals(5, TvBrowseFragment.sortDropdownInitialFocusIndexForTest(BrowserSortOption.MODIFIED_DESC))
    }
}
