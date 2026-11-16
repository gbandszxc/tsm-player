package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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
 * 液态玻璃氛围底：把当前播放封面缩样 + 一次性 CPU 模糊后铺满页面底层，
 * 让半透明玻璃面板有内容可透。无封面（或解码失败）时回落到深色渐变 [R.drawable.bg_ambient_fallback]。
 *
 * 性能约束（对齐 MANUAL 的低配 TV 预算）：
 * - 模糊源最长边 [MAX_AMBIENT_EDGE]px（远小于封面缓存的 1024px 上限），模糊一次性完成后进小缓存，
 *   不做逐帧 / GPU 实时模糊。
 * - 换曲时用 [TransitionDrawable] 做交叉淡入，时长遵循项目 150-250ms 动效区间。
 *
 * 布局接入：页面根布局（FrameLayout）第 0/1 位放
 * `<ImageView android:id="@+id/iv_ambient">` 与 `<View android:id="@+id/view_ambient_scrim">`，
 * 内容层位于其上。scrim 用 @color/ui_ambient_scrim 保证文字对比度。
 */
object AmbientBackdrop {

    private const val MAX_AMBIENT_EDGE = 160
    private const val BLUR_RADIUS = 14
    private const val CROSSFADE_DURATION_MS = 250
    private const val FALLBACK_KEY = "\u0000fallback"
    private const val GLOW_ALPHA = 0x30
    private const val DEFAULT_GLOW_RADIUS_PX = 720
    private const val GLOW_RADIUS_FACTOR = 0.9f

    /**
     * 从模糊位图提取主色作为光晕颜色：采样求均值后转 HSV，
     * 提饱和并限制明度——光晕需要可感知的色相，但不允许过艳或发白。
     */
    private fun deriveGlowColor(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return 0
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        val step = maxOf(1, minOf(w, h) / 32)
        var y = 0
        while (y < h) {
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
        hsv[1] = (hsv[1] * 1.25f).coerceIn(0.25f, 0.6f)
        hsv[2] = hsv[2].coerceIn(0.35f, 0.55f)
        return Color.HSVToColor(hsv)
    }

    /** 小缓存：仅存模糊后的 160px 位图（RGB_565 每张约 50KB），按封面 URI 寻址。 */
    private val blurredCache = LruCache<String, Bitmap>(3)

    /**
     * 绑定页面氛围底。[imageView] 期望满铺在页面根布局最底层，
     * [scrimView] 叠在其上承担暗化遮罩（纯 XML 着色，无需代码参与）。
     */
    fun bind(imageView: ImageView, scrimView: View): Controller =
        Controller(imageView).also { it.reset() }

    class Controller internal constructor(private val view: ImageView) {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var currentKey: String? = null
        private var job: Job? = null

        /**
         * 更新氛围底。优先使用调用方已解析的 [resolved] 位图（如播放页当前封面），
         * 否则按 [artworkUri] 解码（本地 file/content URI）；SMB URI 不在此处做网络 IO，
         * 由调用方通过 PlaybackArtworkCache 命中后传入。
         */
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
                val bitmap = withContext(Dispatchers.IO) {
                    resolved?.let(::prepareAmbient) ?: decodeAmbient(view.context, artworkUri)
                }
                if (bitmap != null) blurredCache.put(key, bitmap)
                if (key == currentKey) apply(bitmap)
            }
        }

        /** 页面销毁时调用，取消在途解码。 */
        fun dispose() {
            job?.cancel()
            scope.cancel()
        }

        /** 初始铺兜底渐变，避免首帧露出窗口背景。 */
        internal fun reset() {
            if (view.drawable == null) view.setImageDrawable(fallbackDrawable(view.context))
        }

        private fun apply(bitmap: Bitmap?) {
            val next: Drawable = if (bitmap != null) {
                // 模糊封面 + 封面主色柔光：底色退后成"暗房"，颜色全部让给内容
                LayerDrawable(
                    arrayOf(
                        BitmapDrawable(view.resources, bitmap),
                        coverGlowDrawable(bitmap),
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

        /** 封面主色 radial 柔光（右上角），与兜底渐变的光晕位置一致。 */
        private fun coverGlowDrawable(bitmap: Bitmap): GradientDrawable {
            val glow = GradientDrawable()
            glow.gradientType = GradientDrawable.RADIAL_GRADIENT
            glow.setColors(
                intArrayOf(
                    ColorUtils.setAlphaComponent(deriveGlowColor(bitmap), GLOW_ALPHA),
                    Color.TRANSPARENT,
                )
            )
            glow.setGradientCenter(0.82f, 0.12f)
            val radiusPx = view.width.takeIf { it > 0 } ?: DEFAULT_GLOW_RADIUS_PX
            glow.gradientRadius = radiusPx * GLOW_RADIUS_FACTOR
            return glow
        }

        private fun fallbackDrawable(context: Context): Drawable =
            ContextCompat.getDrawable(context, R.drawable.bg_ambient_fallback)
                ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
    }

    private fun decodeAmbient(context: Context, artworkUri: String?): Bitmap? = runCatching {
        val uri = artworkUri?.takeIf(String::isNotBlank) ?: return null
        val bytes = when {
            uri.startsWith("file:", ignoreCase = true) ->
                File(Uri.parse(uri).path ?: return null).takeIf(File::isFile)?.readBytes()
            else ->
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        } ?: return null
        prepareAmbient(PlaybackArtworkCache.decodeSampled(bytes))
    }.getOrNull()

    /** 缩样到 [MAX_AMBIENT_EDGE] → 一次性盒模糊 → RGB_565，位图预算远低于 1024px 上限。 */
    private fun prepareAmbient(source: Bitmap?): Bitmap? {
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
        return if (blurred.config == Bitmap.Config.RGB_565) {
            blurred
        } else {
            blurred.copy(Bitmap.Config.RGB_565, false) ?: blurred
        }
    }
}
