package com.github.gbandszxc.tvmediaplayer.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.media3.common.MediaItem
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackArtworkCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 液态玻璃氛围底：把当前播放封面缩样 + 3-pass 近似高斯模糊后铺满页面底层，
 * 叠加封面主辅双色柔光，让半透明烟熏玻璃面板有光影可透。
 * 无封面（或解码失败）时回落到冷银灰双光斑渐变 [R.drawable.bg_ambient_fallback]。
 */
object AmbientBackdrop {

    private const val MAX_AMBIENT_EDGE = 160
    private const val BLUR_RADIUS = 10
    private const val CROSSFADE_DURATION_MS = 360
    private const val FALLBACK_KEY = "\u0000fallback"
    private const val PRIMARY_GLOW_ALPHA = 0x42
    private const val SECONDARY_GLOW_ALPHA = 0x28
    private const val DEFAULT_GLOW_RADIUS_PX = 720
    private const val GLOW_RADIUS_FACTOR = 0.85f
    private const val BREATHE_DURATION_MS = 14_000L
    private const val SCRIM_ANIM_DURATION_MS = 360L
    private const val TARGET_MAX_BG_LUMINANCE = 0.034
    private const val MAX_ADAPTIVE_SCRIM_ALPHA = 0.97f
    private const val DEFAULT_BASE_SCRIM_ALPHA = 0.40f
    private const val DEFAULT_SCRIM_COLOR_RGB = 0x0B0D12
    private const val SCRIM_LUMINANCE = 0.00405

    private data class PreparedAmbient(
        val bitmap: Bitmap,
        val luminance: Double,
    )

    internal fun computeAverageLuminance(bitmap: Bitmap): Double {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0.0
        val step = maxOf(1, minOf(w, h) / 28)
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                sum += srgbColorLuminance(bitmap.getPixel(x, y))
                count++
                x += step
            }
            y += step
        }
        return if (count == 0) 0.0 else sum / count
    }

    internal fun computeAdaptiveScrimAlpha(ambientLuminance: Double, baseAlpha: Float): Float {
        val clampedBase = baseAlpha.coerceIn(0f, MAX_ADAPTIVE_SCRIM_ALPHA)
        val lum = ambientLuminance.coerceIn(0.0, 1.0)
        val effectiveAtBase = (1.0 - clampedBase) * lum + clampedBase * SCRIM_LUMINANCE
        if (effectiveAtBase <= TARGET_MAX_BG_LUMINANCE || lum <= SCRIM_LUMINANCE) {
            return clampedBase
        }
        val required = ((lum - TARGET_MAX_BG_LUMINANCE) / (lum - SCRIM_LUMINANCE)).toFloat()
        return required.coerceIn(clampedBase, MAX_ADAPTIVE_SCRIM_ALPHA)
    }

    private fun srgbColorLuminance(color: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private fun deriveRegionGlowColor(
        bitmap: Bitmap,
        startYRatio: Float,
        endYRatio: Float,
    ): Int {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return 0
        val yStart = (h * startYRatio).toInt().coerceIn(0, h - 1)
        val yEnd = (h * endYRatio).toInt().coerceIn(yStart + 1, h)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        val step = maxOf(1, minOf(w, h) / 28)
        var y = yStart
        while (y < yEnd) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                n++
                x += step
            }
            y += step
        }
        if (n == 0) return 0
        val hsv = FloatArray(3)
        Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.32f).coerceIn(0.22f, 0.68f)
        hsv[2] = (hsv[2] * 1.10f).coerceIn(0.36f, 0.62f)
        return Color.HSVToColor(hsv)
    }

    /** 小缓存：存模糊后的 160px ARGB_8888 位图（每张约 100KB）及平均亮度，避免 RGB_565 暗部色带。 */
    private val blurredCache = LruCache<String, PreparedAmbient>(3)

    /** 绑定页面氛围底与自适应压暗遮罩。 */
    fun bind(imageView: ImageView, scrimView: View): Controller =
        Controller(imageView, scrimView).also { it.reset() }

    class Controller internal constructor(
        private val view: ImageView,
        private val scrimView: View,
    ) {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var currentKey: String? = null
        private var job: Job? = null
        private var breathing = false
        private var breatheAnimator: AnimatorSet? = null
        private var scrimAnimator: ValueAnimator? = null
        private val scrimRgb: Int
        private val baseScrimAlpha: Float
        private var currentScrimAlpha: Float

        init {
            val bgColor = (scrimView.background as? ColorDrawable)?.color
            if (bgColor != null) {
                scrimRgb = bgColor and 0x00FFFFFF
                baseScrimAlpha = (Color.alpha(bgColor) / 255f).coerceIn(0f, MAX_ADAPTIVE_SCRIM_ALPHA)
            } else {
                scrimRgb = DEFAULT_SCRIM_COLOR_RGB
                baseScrimAlpha = DEFAULT_BASE_SCRIM_ALPHA
            }
            currentScrimAlpha = baseScrimAlpha
        }

        fun update(artworkUri: String?, resolved: Bitmap? = null) {
            val key = artworkUri?.takeIf(String::isNotBlank) ?: FALLBACK_KEY
            if (key == currentKey) return
            currentKey = key
            job?.cancel()

            if (key == FALLBACK_KEY) {
                apply(null)
                return
            }
            blurredCache.get(key)?.let {
                apply(it)
                return
            }
            job = scope.launch {
                val prepared = withContext(Dispatchers.IO) {
                    resolved?.let(::prepareAmbient) ?: decodeAmbient(view.context, artworkUri)
                }
                if (prepared != null) blurredCache.put(key, prepared)
                if (key == currentKey) apply(prepared)
            }
        }

        fun updateFromMediaItem(item: MediaItem?) {
            if (item == null) {
                update(null)
                return
            }
            val cacheKey = item.localConfiguration?.uri?.toString().orEmpty().ifBlank { item.mediaId }
            update(
                item.mediaMetadata.artworkUri?.toString(),
                PlaybackArtworkCache.get(cacheKey),
            )
        }

        /**
         * 播放页氛围慢呼吸：仅使用 GPU 属性动画（scale/translation），
         * 零逐帧位图或 CPU 模糊消耗；暂停时平滑停稳。
         */
        fun setBreathing(enabled: Boolean) {
            if (breathing == enabled) return
            breathing = enabled
            if (!enabled) {
                stopBreathing(smoothReset = true)
                return
            }
            if (view.isInTouchMode && view.width <= 0) return
            startBreathing()
        }

        private fun startBreathing() {
            breatheAnimator?.cancel()
            val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.06f, 1.13f).apply {
                duration = BREATHE_DURATION_MS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.06f, 1.13f).apply {
                duration = BREATHE_DURATION_MS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
            }
            val tx = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -14f, 16f).apply {
                duration = BREATHE_DURATION_MS + 2_000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
            }
            val ty = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -10f, 14f).apply {
                duration = BREATHE_DURATION_MS + 4_000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
            }
            breatheAnimator = AnimatorSet().apply {
                playTogether(scaleX, scaleY, tx, ty)
                start()
            }
        }

        private fun stopBreathing(smoothReset: Boolean) {
            breatheAnimator?.cancel()
            breatheAnimator = null
            if (smoothReset && view.width > 0) {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(400L)
                    .start()
            } else {
                view.scaleX = 1f
                view.scaleY = 1f
                view.translationX = 0f
                view.translationY = 0f
            }
        }

        fun dispose() {
            stopBreathing(smoothReset = false)
            scrimAnimator?.cancel()
            scrimAnimator = null
            job?.cancel()
            scope.cancel()
        }

        internal fun reset() {
            if (view.drawable == null) view.setImageDrawable(fallbackDrawable(view.context))
        }

        private fun apply(prepared: PreparedAmbient?) {
            val bitmap = prepared?.bitmap
            val targetScrimAlpha = if (prepared != null) {
                computeAdaptiveScrimAlpha(prepared.luminance, baseScrimAlpha)
            } else {
                baseScrimAlpha
            }
            animateScrimAlpha(targetScrimAlpha)
            val next: Drawable = if (bitmap != null) {
                val topGlow = deriveRegionGlowColor(bitmap, 0f, 0.55f)
                val bottomGlow = deriveRegionGlowColor(bitmap, 0.45f, 1f)
                LayerDrawable(
                    arrayOf(
                        BitmapDrawable(view.resources, bitmap),
                        radialGlowDrawable(topGlow, PRIMARY_GLOW_ALPHA, 0.80f, 0.14f, 0.85f),
                        radialGlowDrawable(bottomGlow, SECONDARY_GLOW_ALPHA, 0.18f, 0.86f, 0.72f),
                    )
                )
            } else {
                fallbackDrawable(view.context)
            }
            val old = view.drawable
            if (old == null) {
                view.setImageDrawable(next)
                return
            }
            val transition = TransitionDrawable(arrayOf(old, next)).apply {
                setCrossFadeEnabled(true)
                startTransition(CROSSFADE_DURATION_MS)
            }
            view.setImageDrawable(transition)
        }

        private fun radialGlowDrawable(
            color: Int,
            alpha: Int,
            centerX: Float,
            centerY: Float,
            radiusFactor: Float,
        ): GradientDrawable {
            return GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                setColors(
                    intArrayOf(
                        ColorUtils.setAlphaComponent(color, alpha),
                        Color.TRANSPARENT,
                    )
                )
                setGradientCenter(centerX, centerY)
                val baseRadius = view.width.takeIf { it > 0 } ?: DEFAULT_GLOW_RADIUS_PX
                gradientRadius = baseRadius * GLOW_RADIUS_FACTOR * radiusFactor
            }
        }

        private fun animateScrimAlpha(targetAlpha: Float) {
            if (kotlin.math.abs(currentScrimAlpha - targetAlpha) < 0.005f) {
                applyScrimAlphaImmediate(targetAlpha)
                return
            }
            scrimAnimator?.cancel()
            if (scrimView.width <= 0) {
                applyScrimAlphaImmediate(targetAlpha)
                return
            }
            scrimAnimator = ValueAnimator.ofFloat(currentScrimAlpha, targetAlpha).apply {
                duration = SCRIM_ANIM_DURATION_MS
                addUpdateListener { anim ->
                    applyScrimAlphaImmediate(anim.animatedValue as Float)
                }
                start()
            }
        }

        private fun applyScrimAlphaImmediate(alpha: Float) {
            currentScrimAlpha = alpha
            val a = (alpha * 255f).toInt().coerceIn(0, 255)
            scrimView.setBackgroundColor((a shl 24) or scrimRgb)
        }

        private fun fallbackDrawable(context: Context): Drawable =
            ContextCompat.getDrawable(context, R.drawable.bg_ambient_fallback)
                ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
    }

    private fun decodeAmbient(context: Context, artworkUri: String?): PreparedAmbient? = runCatching {
        val uri = artworkUri?.takeIf(String::isNotBlank) ?: return null
        val bytes = when {
            uri.startsWith("file:", ignoreCase = true) ->
                File(Uri.parse(uri).path ?: return null).takeIf(File::isFile)?.readBytes()
            else ->
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        } ?: return null
        prepareAmbient(PlaybackArtworkCache.decodeSampled(bytes))
    }.getOrNull()

    private fun prepareAmbient(source: Bitmap?): PreparedAmbient? {
        source ?: return null
        val largestEdge = maxOf(source.width, source.height)
        val scale = if (largestEdge > MAX_AMBIENT_EDGE) {
            MAX_AMBIENT_EDGE.toFloat() / largestEdge
        } else {
            1f
        }
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        val blurred = BitmapBlur.blur(small, BLUR_RADIUS)
        return PreparedAmbient(blurred, computeAverageLuminance(blurred))
    }
}
