package com.github.gbandszxc.tvmediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFastLocateCalculatorTest {

    private val calculator = BrowseFastLocateCalculator()

    @Test
    fun fastLocatePageJumpClampsAndAdvancesByWindow() {
        val state = BrowseFastLocateState(totalCount = 60, currentIndex = 55, visibleWindowSize = 10)
        val forward = calculator.jumpPage(state, forward = true)
        assertEquals(59, forward.currentIndex)
        assertEquals(100, forward.progressPercent)

        val backward = calculator.jumpPage(state.copy(currentIndex = 5), forward = false)
        assertEquals(0, backward.currentIndex)
        assertEquals(0, backward.progressPercent)
    }

    @Test
    fun fastLocateSegmentJumpMovesTenPercentOrOne() {
        val state = BrowseFastLocateState(totalCount = 80, currentIndex = 20, visibleWindowSize = 8)
        val forward = calculator.jumpSegment(state, forward = true)
        assertEquals(28, forward.currentIndex)

        val backward = calculator.jumpSegment(state, forward = false)
        assertEquals(12, backward.currentIndex)

        val tiny = BrowseFastLocateState(totalCount = 5, currentIndex = 1, visibleWindowSize = 3)
        val tinyForward = calculator.jumpSegment(tiny, forward = true)
        assertEquals(2, tinyForward.currentIndex)
    }

    @Test
    fun fastLocateDisabledForShortLists() {
        val shortList = BrowseFastLocateState(totalCount = 11, currentIndex = 2, visibleWindowSize = 6)
        assertFalse(calculator.canEnter(shortList))

        val longList = shortList.copy(totalCount = 25)
        assertTrue(calculator.canEnter(longList))

        val zeroWindow = shortList.copy(visibleWindowSize = 0)
        assertFalse(calculator.canEnter(zeroWindow))
    }
}
