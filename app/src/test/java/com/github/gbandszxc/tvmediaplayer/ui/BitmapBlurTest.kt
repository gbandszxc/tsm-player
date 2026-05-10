package com.github.gbandszxc.tvmediaplayer.ui

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BitmapBlurTest {

    @Test
    fun `blur keeps source dimensions`() {
        val source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)

        val result = BitmapBlur.blur(source, radius = 2)

        assertEquals(3, result.width)
        assertEquals(2, result.height)
    }

    @Test
    fun `blur does not mutate source bitmap`() {
        val source = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        source.setPixel(1, 0, Color.GREEN)
        source.setPixel(2, 0, Color.BLUE)

        BitmapBlur.blur(source, radius = 1)

        assertEquals(Color.RED, source.getPixel(0, 0))
        assertEquals(Color.GREEN, source.getPixel(1, 0))
        assertEquals(Color.BLUE, source.getPixel(2, 0))
    }

    @Test
    fun `zero radius returns independent copy`() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.CYAN)

        val result = BitmapBlur.blur(source, radius = 0)

        assertNotSame(source, result)
        assertEquals(Color.CYAN, result.getPixel(0, 0))
    }
}
