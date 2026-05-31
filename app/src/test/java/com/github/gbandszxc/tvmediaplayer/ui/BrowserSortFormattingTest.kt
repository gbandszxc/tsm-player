package com.github.gbandszxc.tvmediaplayer.ui

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
}
