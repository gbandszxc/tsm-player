package com.github.gbandszxc.tvmediaplayer.ui

import android.graphics.Bitmap
import android.graphics.Color

object BitmapBlur {
    fun blur(source: Bitmap, radius: Int): Bitmap {
        val safeRadius = radius.coerceAtLeast(0)
        val input = source.copy(Bitmap.Config.ARGB_8888, false)
        if (safeRadius == 0 || input.width == 0 || input.height == 0) return input

        val width = input.width
        val height = input.height
        val sourcePixels = IntArray(width * height)
        val horizontalPixels = IntArray(width * height)
        val outputPixels = IntArray(width * height)
        input.getPixels(sourcePixels, 0, width, 0, 0, width, height)

        boxBlurHorizontal(sourcePixels, horizontalPixels, width, height, safeRadius)
        boxBlurVertical(horizontalPixels, outputPixels, width, height, safeRadius)

        return Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlurHorizontal(
        sourcePixels: IntArray,
        outputPixels: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (dx in -radius..radius) {
                    val sampleX = (x + dx).coerceIn(0, width - 1)
                    val color = sourcePixels[y * width + sampleX]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    count++
                }
                outputPixels[y * width + x] = Color.argb(
                    alpha / count,
                    red / count,
                    green / count,
                    blue / count
                )
            }
        }
    }

    private fun boxBlurVertical(
        sourcePixels: IntArray,
        outputPixels: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (dy in -radius..radius) {
                    val sampleY = (y + dy).coerceIn(0, height - 1)
                    val color = sourcePixels[sampleY * width + x]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    count++
                }
                outputPixels[y * width + x] = Color.argb(
                    alpha / count,
                    red / count,
                    green / count,
                    blue / count
                )
            }
        }
    }
}
