package com.github.gbandszxc.tvmediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFastLocateCalculatorTest {

    @Test
    fun fastLocatePageJumpClampsAndAdvancesByWindow() {
        val state = BrowseFastLocateState(totalCount = 60, currentIndex = 55, visibleWindowSize = 10)
        val forward = BrowseFastLocateCalculator.jumpPage(state, direction = 1)
        assertEquals(59, forward.currentIndex)
        assertEquals(100, forward.progressPercent)

        val backward = BrowseFastLocateCalculator.jumpPage(state.copy(currentIndex = 5), direction = -1)
        assertEquals(0, backward.currentIndex)
        assertEquals(0, backward.progressPercent)
    }

    @Test
    fun fastLocateSegmentJumpMovesTenPercentOrOne() {
        val state = BrowseFastLocateState(totalCount = 80, currentIndex = 20, visibleWindowSize = 8)
        val forward = BrowseFastLocateCalculator.jumpSegment(state, direction = 1)
        assertEquals(28, forward.currentIndex)

        val backward = BrowseFastLocateCalculator.jumpSegment(state, direction = -1)
        assertEquals(12, backward.currentIndex)

        val tiny = BrowseFastLocateState(totalCount = 5, currentIndex = 1, visibleWindowSize = 3)
        val tinyForward = BrowseFastLocateCalculator.jumpSegment(tiny, direction = 1)
        assertEquals(2, tinyForward.currentIndex)
    }

    @Test
    fun fastLocateDisabledForShortLists() {
        assertFalse(BrowseFastLocateCalculator.canEnter(totalCount = 11, visibleWindowSize = 6))
        assertTrue(BrowseFastLocateCalculator.canEnter(totalCount = 25, visibleWindowSize = 6))
        assertFalse(BrowseFastLocateCalculator.canEnter(totalCount = 25, visibleWindowSize = 0))
    }
}
