package com.github.gbandszxc.tvmediaplayer.ui

import android.graphics.Bitmap
import android.graphics.Color

object BitmapBlur {

    private const val PASSES = 3

    /**
     * 3-pass 滑动窗口均值模糊（近似高斯模糊），时间复杂度 O(W·H)，
     * 消除单次盒模糊放大后的块状边缘与暗部色带。
     */
    fun blur(source: Bitmap, radius: Int): Bitmap {
        val safeRadius = radius.coerceAtLeast(0)
        val input = source.copy(Bitmap.Config.ARGB_8888, false)
        if (safeRadius == 0 || input.width == 0 || input.height == 0) return input

        val width = input.width
        val height = input.height
        val size = width * height
        var current = IntArray(size)
        var scratch = IntArray(size)
        input.getPixels(current, 0, width, 0, 0, width, height)

        repeat(PASSES) {
            boxBlurHorizontal(current, scratch, width, height, safeRadius)
            boxBlurVertical(scratch, current, width, height, safeRadius)
        }

        return Bitmap.createBitmap(current, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlurHorizontal(
        sourcePixels: IntArray,
        outputPixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val windowSize = radius * 2 + 1
        val lastX = width - 1
        for (y in 0 until height) {
            val rowOffset = y * width
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0

            val firstColor = sourcePixels[rowOffset]
            alpha += Color.alpha(firstColor) * (radius + 1)
            red += Color.red(firstColor) * (radius + 1)
            green += Color.green(firstColor) * (radius + 1)
            blue += Color.blue(firstColor) * (radius + 1)

            for (dx in 1..radius) {
                val sampleColor = sourcePixels[rowOffset + dx.coerceAtMost(lastX)]
                alpha += Color.alpha(sampleColor)
                red += Color.red(sampleColor)
                green += Color.green(sampleColor)
                blue += Color.blue(sampleColor)
            }

            for (x in 0 until width) {
                outputPixels[rowOffset + x] = Color.argb(
                    alpha / windowSize,
                    red / windowSize,
                    green / windowSize,
                    blue / windowSize,
                )
                val addColor = sourcePixels[rowOffset + (x + 1 + radius).coerceAtMost(lastX)]
                val subColor = sourcePixels[rowOffset + (x - radius).coerceAtLeast(0)]
                alpha += Color.alpha(addColor) - Color.alpha(subColor)
                red += Color.red(addColor) - Color.red(subColor)
                green += Color.green(addColor) - Color.green(subColor)
                blue += Color.blue(addColor) - Color.blue(subColor)
            }
        }
    }

    private fun boxBlurVertical(
        sourcePixels: IntArray,
        outputPixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val windowSize = radius * 2 + 1
        val lastY = height - 1
        for (x in 0 until width) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0

            val firstColor = sourcePixels[x]
            alpha += Color.alpha(firstColor) * (radius + 1)
            red += Color.red(firstColor) * (radius + 1)
            green += Color.green(firstColor) * (radius + 1)
            blue += Color.blue(firstColor) * (radius + 1)

            for (dy in 1..radius) {
                val sampleColor = sourcePixels[dy.coerceAtMost(lastY) * width + x]
                alpha += Color.alpha(sampleColor)
                red += Color.red(sampleColor)
                green += Color.green(sampleColor)
                blue += Color.blue(sampleColor)
            }

            for (y in 0 until height) {
                outputPixels[y * width + x] = Color.argb(
                    alpha / windowSize,
                    red / windowSize,
                    green / windowSize,
                    blue / windowSize,
                )
                val addColor = sourcePixels[(y + 1 + radius).coerceAtMost(lastY) * width + x]
                val subColor = sourcePixels[(y - radius).coerceAtLeast(0) * width + x]
                alpha += Color.alpha(addColor) - Color.alpha(subColor)
                red += Color.red(addColor) - Color.red(subColor)
                green += Color.green(addColor) - Color.green(subColor)
                blue += Color.blue(addColor) - Color.blue(subColor)
            }
        }
    }
}
