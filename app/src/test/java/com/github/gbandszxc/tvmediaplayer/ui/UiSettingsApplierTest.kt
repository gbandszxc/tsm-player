package com.github.gbandszxc.tvmediaplayer.ui

import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class UiSettingsApplierTest {

    @Test
    fun `global scale layout expands virtual content size for scaled views`() {
        val layout = UiSettingsApplier.computeGlobalScaleLayoutForTest(
            parentWidth = 1200,
            parentHeight = 800,
            scalePercent = 120,
        )

        assertEquals(1000, layout.width)
        assertEquals(667, layout.height)
        assertEquals(1.2f, layout.scale, 0.001f)
    }

    @Test
    fun `global scale layout restores match parent at default scale`() {
        val layout = UiSettingsApplier.computeGlobalScaleLayoutForTest(
            parentWidth = 1200,
            parentHeight = 800,
            scalePercent = 100,
        )

        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, layout.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, layout.height)
        assertEquals(1f, layout.scale, 0.001f)
    }
}
