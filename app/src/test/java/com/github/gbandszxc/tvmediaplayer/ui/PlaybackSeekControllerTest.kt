package com.github.gbandszxc.tvmediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSeekControllerTest {

    @Test
    fun `single right key previews five seconds and commits immediately`() {
        val controller = PlaybackSeekController()

        val result = controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 0,
            currentPositionMs = 60_000L,
            durationMs = 240_000L,
            nowMs = 1_000L
        )

        assertEquals(65_000L, result.previewPositionMs)
        assertEquals(65_000L, result.commitPositionMs)
    }

    @Test
    fun `repeat count keeps existing seek acceleration steps`() {
        assertEquals(5_000L, PlaybackSeekController.stepMs(repeatCount = 0))
        assertEquals(5_000L, PlaybackSeekController.stepMs(repeatCount = 4))
        assertEquals(10_000L, PlaybackSeekController.stepMs(repeatCount = 5))
        assertEquals(30_000L, PlaybackSeekController.stepMs(repeatCount = 12))
        assertEquals(60_000L, PlaybackSeekController.stepMs(repeatCount = 25))
        assertEquals(90_000L, PlaybackSeekController.stepMs(repeatCount = 40))
    }

    @Test
    fun `dense repeat events update preview without committing every seek`() {
        val controller = PlaybackSeekController()

        val first = controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 0,
            currentPositionMs = 60_000L,
            durationMs = 3_600_000L,
            nowMs = 1_000L
        )
        val second = controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 1,
            currentPositionMs = 65_000L,
            durationMs = 3_600_000L,
            nowMs = 1_100L
        )

        assertEquals(65_000L, first.commitPositionMs)
        assertEquals(70_000L, second.previewPositionMs)
        assertNull(second.commitPositionMs)
    }

    @Test
    fun `repeat event after throttle window commits pending position`() {
        val controller = PlaybackSeekController()

        controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 0,
            currentPositionMs = 60_000L,
            durationMs = 3_600_000L,
            nowMs = 1_000L
        )
        val result = controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 1,
            currentPositionMs = 65_000L,
            durationMs = 3_600_000L,
            nowMs = 1_260L
        )

        assertEquals(70_000L, result.previewPositionMs)
        assertEquals(70_000L, result.commitPositionMs)
    }

    @Test
    fun `key up commits final pending position and resets state`() {
        val controller = PlaybackSeekController()

        assertFalse(controller.isPreviewActive)
        controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 0,
            currentPositionMs = 60_000L,
            durationMs = 3_600_000L,
            nowMs = 1_000L
        )
        assertTrue(controller.isPreviewActive)
        controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 1,
            currentPositionMs = 65_000L,
            durationMs = 3_600_000L,
            nowMs = 1_100L
        )

        assertEquals(70_000L, controller.commitPending()?.positionMs)
        assertFalse(controller.isPreviewActive)
        assertNull(controller.commitPending())
    }

    @Test
    fun `idle commit returns pending position only after idle delay`() {
        val controller = PlaybackSeekController()

        controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 0,
            currentPositionMs = 60_000L,
            durationMs = 3_600_000L,
            nowMs = 1_000L
        )
        controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 1,
            currentPositionMs = 65_000L,
            durationMs = 3_600_000L,
            nowMs = 1_100L
        )

        assertNull(controller.commitIfIdle(nowMs = 1_250L))
        assertEquals(70_000L, controller.commitIfIdle(nowMs = 1_280L)?.positionMs)
    }

    @Test
    fun `left and right seeking clamp to valid duration range`() {
        val controller = PlaybackSeekController()

        val left = controller.onDirectionKeyDown(
            direction = -1,
            repeatCount = 0,
            currentPositionMs = 2_000L,
            durationMs = 240_000L,
            nowMs = 1_000L
        )
        controller.reset()
        val right = controller.onDirectionKeyDown(
            direction = 1,
            repeatCount = 0,
            currentPositionMs = 239_000L,
            durationMs = 240_000L,
            nowMs = 2_000L
        )

        assertEquals(0L, left.previewPositionMs)
        assertEquals(240_000L, right.previewPositionMs)
    }
}
