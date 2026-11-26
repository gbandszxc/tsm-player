package com.github.gbandszxc.tvmediaplayer.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
class ContrastReadabilityTest {

    private fun srgbLuminance(color: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private fun compositeOver(fgColor: Int, bgLuminance: Double): Double {
        val alpha = Color.alpha(fgColor) / 255.0
        val fgOpaque = Color.rgb(Color.red(fgColor), Color.green(fgColor), Color.blue(fgColor))
        val fgLum = srgbLuminance(fgOpaque)
        return alpha * fgLum + (1.0 - alpha) * bgLuminance
    }

    private fun contrast(fgColor: Int, bgLuminance: Double): Double {
        val l1 = compositeOver(fgColor, bgLuminance)
        val hi = max(l1, bgLuminance)
        val lo = min(l1, bgLuminance)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun ambientBgLuminance(coverColor: Int, scrimColor: Int): Double {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(coverColor)
        }
        val coverLum = AmbientBackdrop.computeAverageLuminance(bmp)
        bmp.recycle()
        val baseAlpha = Color.alpha(scrimColor) / 255f
        val adaptiveAlpha = AmbientBackdrop.computeAdaptiveScrimAlpha(coverLum, baseAlpha)
        val adaptiveScrim = Color.argb(
            (adaptiveAlpha * 255f).toInt().coerceIn(0, 255),
            Color.red(scrimColor),
            Color.green(scrimColor),
            Color.blue(scrimColor),
        )
        return compositeOver(adaptiveScrim, coverLum)
    }

    @Test
    fun `dark fallback cover keeps base scrim alpha unchanged`() {
        val context = RuntimeEnvironment.getApplication()
        val scrim = ContextCompat.getColor(context, R.color.ui_ambient_scrim)
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#141821"))
        }
        val lum = AmbientBackdrop.computeAverageLuminance(bmp)
        bmp.recycle()
        val baseAlpha = Color.alpha(scrim) / 255f
        assertEquals(baseAlpha, AmbientBackdrop.computeAdaptiveScrimAlpha(lum, baseAlpha), 0.001f)
    }

    @Test
    fun `text tokens meet WCAG AA over dark pastel and white covers with adaptive scrim`() {
        val context = RuntimeEnvironment.getApplication()
        val scrim = ContextCompat.getColor(context, R.color.ui_ambient_scrim)
        val glassPanel = ContextCompat.getColor(context, R.color.ui_glass_panel)
        val primary = ContextCompat.getColor(context, R.color.ui_text_primary)
        val secondary = ContextCompat.getColor(context, R.color.ui_text_secondary)
        val muted = ContextCompat.getColor(context, R.color.ui_text_muted)

        val covers = listOf(
            "fallback" to Color.parseColor("#141821"),
            "pastel" to Color.parseColor("#D8B8D0"),
            "white" to Color.WHITE,
        )

        for ((name, cover) in covers) {
            val ambientLum = ambientBgLuminance(cover, scrim)
            val panelLum = compositeOver(glassPanel, ambientLum)

            for ((surfaceName, surfaceLum) in listOf("ambient" to ambientLum, "panel" to panelLum)) {
                assertTrue(
                    "$name $surfaceName primary < 4.5",
                    contrast(primary, surfaceLum) >= 4.5,
                )
                assertTrue(
                    "$name $surfaceName secondary < 4.5",
                    contrast(secondary, surfaceLum) >= 4.5,
                )
                assertTrue(
                    "$name $surfaceName muted=${contrast(muted, surfaceLum)} < 4.5",
                    contrast(muted, surfaceLum) >= 4.5,
                )
            }
        }
    }

    @Test
    fun `tinted focus buttons meet WCAG AA across cover luminances`() {
        val context = RuntimeEnvironment.getApplication()
        val scrim = ContextCompat.getColor(context, R.color.ui_ambient_scrim)

        val pairs = listOf(
            "blue" to (R.color.ui_glass_blue_focus to R.color.ui_text_on_accent),
            "green" to (R.color.ui_glass_green_focus to R.color.ui_text_on_accent),
            "red" to (R.color.ui_glass_red_focus to R.color.ui_accent_red_text),
            "yellow" to (R.color.ui_glass_yellow_focus to R.color.ui_text_warning_glass),
            "amber" to (R.color.ui_glass_amber_focus to R.color.ui_text_warning_glass),
        )

        for (cover in listOf(Color.parseColor("#141821"), Color.WHITE)) {
            val ambientLum = ambientBgLuminance(cover, scrim)
            for ((label, ids) in pairs) {
                val bgLum = compositeOver(ContextCompat.getColor(context, ids.first), ambientLum)
                val fg = ContextCompat.getColor(context, ids.second)
                val ratio = contrast(fg, bgLum)
                assertTrue("button $label contrast=$ratio < 4.5", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `lyric hierarchy tokens remain readable over brightest cover`() {
        val context = RuntimeEnvironment.getApplication()
        val panelBg = compositeOver(
            ContextCompat.getColor(context, R.color.ui_glass_panel),
            ambientBgLuminance(Color.WHITE, ContextCompat.getColor(context, R.color.ui_ambient_scrim)),
        )
        val fullscreenBg = ambientBgLuminance(
            Color.WHITE,
            ContextCompat.getColor(context, R.color.ui_ambient_scrim_deep),
        )

        val active = ContextCompat.getColor(context, R.color.ui_lyric_active)
        val near = ContextCompat.getColor(context, R.color.ui_lyric_near)
        val mid = ContextCompat.getColor(context, R.color.ui_lyric_mid)
        val far = ContextCompat.getColor(context, R.color.ui_lyric_far)

        for (bg in listOf(panelBg, fullscreenBg)) {
            assertTrue(contrast(active, bg) >= 7.0)
            assertTrue(contrast(near, bg) >= 4.5)
            assertTrue(contrast(mid, bg) >= 4.5)
            assertTrue("lyric far=${contrast(far, bg)} < 3.0", contrast(far, bg) >= 3.0)
        }
    }
}
