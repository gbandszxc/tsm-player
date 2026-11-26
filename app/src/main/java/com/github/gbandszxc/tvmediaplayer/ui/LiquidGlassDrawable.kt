package com.github.gbandszxc.tvmediaplayer.ui

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import com.github.gbandszxc.tvmediaplayer.R

/**
 * 液态玻璃光学表面 Drawable（API 21+ 全设备可用，零逐帧模糊开销）：
 * 1. 半透明烟熏填充（让氛围光影隐约透出）
 * 2. 顶部局部 28% 内光泽（下半部保持清透，彻底消除全高雾蒙蒙的脏灰感）
 * 3. 可选一次性斜向掠光（聚焦瞬间扫过，静止时 0 开销）
 * 4. 3 段对角镜面折射描边（仅 Paint.Style.STROKE，不铺底层，边缘锐利立体）
 *
 * 实现 [ConstantState] 以便 [UiMotion.applyPressFeedback] 安全拷贝作为圆角 ripple mask。
 */
internal class LiquidGlassDrawable(
    private val config: Config,
) : Drawable() {

    internal data class Config(
        val fillColor: Int,
        val glossTopColor: Int,
        val glossBottomColor: Int,
        val rimLightColor: Int,
        val rimMidColor: Int,
        val rimSoftColor: Int,
        val cornerRadiusPx: Float,
        val rimWidthPx: Float,
        val sheenColor: Int = DEFAULT_SHEEN_COLOR,
    )

    private class GlassConstantState(val config: Config) : ConstantState() {
        override fun newDrawable(): Drawable = LiquidGlassDrawable(config)
        override fun newDrawable(res: Resources?): Drawable = LiquidGlassDrawable(config)
        override fun getChangingConfigurations(): Int = 0
    }

    private val state = GlassConstantState(config)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = config.fillColor
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = config.rimWidthPx
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillRect = RectF()
    private val glossRect = RectF()
    private val rimRect = RectF()
    private val clipPath = Path()
    private var sheenProgress = -1f
    private var sheenAnimator: ValueAnimator? = null
    private var alphaValue = 255

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        fillRect.set(bounds)
        clipPath.reset()
        clipPath.addRoundRect(fillRect, config.cornerRadiusPx, config.cornerRadiusPx, Path.Direction.CW)

        val glossHeight = (h * GLOSS_HEIGHT_RATIO).coerceAtLeast(1f)
        glossRect.set(fillRect.left, fillRect.top, fillRect.right, fillRect.top + glossHeight)
        glossPaint.shader = LinearGradient(
            0f,
            glossRect.top,
            0f,
            glossRect.bottom,
            config.glossTopColor,
            config.glossBottomColor,
            Shader.TileMode.CLAMP,
        )

        val halfStroke = config.rimWidthPx / 2f
        rimRect.set(
            fillRect.left + halfStroke,
            fillRect.top + halfStroke,
            fillRect.right - halfStroke,
            fillRect.bottom - halfStroke,
        )
        rimPaint.shader = LinearGradient(
            rimRect.left,
            rimRect.top,
            rimRect.right,
            rimRect.bottom,
            intArrayOf(config.rimLightColor, config.rimMidColor, config.rimSoftColor),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return

        val r = config.cornerRadiusPx
        fillPaint.alpha = scaledAlpha(Color.alpha(config.fillColor))
        canvas.drawRoundRect(fillRect, r, r, fillPaint)

        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        glossPaint.alpha = alphaValue
        canvas.drawRect(glossRect, glossPaint)

        if (sheenProgress in 0f..1f) {
            drawSheen(canvas)
        }
        canvas.restoreToCount(saveCount)

        val rimRadius = (r - config.rimWidthPx / 2f).coerceAtLeast(0f)
        rimPaint.alpha = alphaValue
        canvas.drawRoundRect(rimRect, rimRadius, rimRadius, rimPaint)
    }

    private fun drawSheen(canvas: Canvas) {
        val w = fillRect.width()
        val h = fillRect.height()
        if (w <= 0f || h <= 0f) return
        val bandWidth = (w * 0.28f).coerceIn(48f, 220f)
        val xStart = fillRect.left - bandWidth + (w + bandWidth * 2f) * sheenProgress
        sheenPaint.shader = LinearGradient(
            xStart,
            fillRect.top,
            xStart + bandWidth,
            fillRect.bottom,
            intArrayOf(Color.TRANSPARENT, config.sheenColor, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        sheenPaint.alpha = alphaValue
        canvas.drawRect(fillRect, sheenPaint)
    }

    /** 触发一次性的玻璃表面斜向掠光（约 320ms）。 */
    fun triggerSheen() {
        sheenAnimator?.cancel()
        sheenAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SHEEN_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                sheenProgress = it.animatedValue as Float
                invalidateSelf()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    sheenProgress = -1f
                    invalidateSelf()
                }
            })
            start()
        }
    }

    private fun scaledAlpha(baseAlpha: Int): Int = (baseAlpha * alphaValue) / 255

    override fun setAlpha(alpha: Int) {
        if (alphaValue == alpha) return
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        glossPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getOutline(outline: Outline) {
        if (bounds.isEmpty) return
        outline.setRoundRect(bounds, config.cornerRadiusPx)
        outline.alpha = 1f
    }

    override fun getConstantState(): ConstantState = state

    companion object {
        private const val GLOSS_HEIGHT_RATIO = 0.28f
        private const val SHEEN_DURATION_MS = 320L
        private const val DEFAULT_SHEEN_COLOR = 0x2EFFFFFF
    }
}

/** 液态玻璃表面装配工具：统一面板、弹窗、控制坞、下拉框与圆角裁剪。 */
internal object LiquidGlassSurface {

    fun applyPanel(view: View) {
        apply(
            view = view,
            fillColorRes = R.color.ui_glass_panel,
            rimLightColorRes = R.color.ui_glass_rim_light,
            rimMidColorRes = R.color.ui_glass_rim_mid,
            rimSoftColorRes = R.color.ui_glass_rim_soft,
            cornerRadiusRes = R.dimen.ui_radius_panel,
        )
    }

    fun applyModalPanel(view: View) {
        apply(
            view = view,
            fillColorRes = R.color.ui_glass_panel_strong,
            rimLightColorRes = R.color.ui_glass_rim_light,
            rimMidColorRes = R.color.ui_glass_rim_mid,
            rimSoftColorRes = R.color.ui_glass_rim_soft,
            cornerRadiusRes = R.dimen.ui_radius_modal,
        )
    }

    fun applyDock(view: View) {
        apply(
            view = view,
            fillColorRes = R.color.ui_glass_dock,
            rimLightColorRes = R.color.ui_glass_rim_light,
            rimMidColorRes = R.color.ui_glass_rim_mid,
            rimSoftColorRes = R.color.ui_glass_rim_soft,
            cornerRadiusRes = R.dimen.ui_radius_dock,
        )
    }

    fun applyDropdown(view: View) {
        apply(
            view = view,
            fillColorRes = R.color.ui_glass_panel_strong,
            rimLightColorRes = R.color.ui_accent_blue_stroke,
            rimMidColorRes = R.color.ui_glass_rim_mid,
            rimSoftColorRes = R.color.ui_glass_blue_rim,
            cornerRadiusRes = R.dimen.ui_radius_card,
        )
    }

    fun applyRoundedClip(view: View, @DimenRes cornerRadiusRes: Int) {
        val radius = view.resources.getDimension(cornerRadiusRes)
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(target: View, outline: Outline) {
                outline.setRoundRect(0, 0, target.width, target.height, radius)
            }
        }
        view.clipToOutline = true
    }

    private fun apply(
        view: View,
        @ColorRes fillColorRes: Int,
        @ColorRes rimLightColorRes: Int,
        @ColorRes rimMidColorRes: Int,
        @ColorRes rimSoftColorRes: Int,
        @DimenRes cornerRadiusRes: Int,
    ) {
        val context = view.context
        val res = view.resources
        view.background = LiquidGlassDrawable(
            LiquidGlassDrawable.Config(
                fillColor = ContextCompat.getColor(context, fillColorRes),
                glossTopColor = ContextCompat.getColor(context, R.color.ui_glass_gloss_top),
                glossBottomColor = ContextCompat.getColor(context, R.color.ui_glass_gloss_bottom),
                rimLightColor = ContextCompat.getColor(context, rimLightColorRes),
                rimMidColor = ContextCompat.getColor(context, rimMidColorRes),
                rimSoftColor = ContextCompat.getColor(context, rimSoftColorRes),
                cornerRadiusPx = res.getDimension(cornerRadiusRes),
                rimWidthPx = res.getDimension(R.dimen.ui_stroke_glass_rim),
            )
        )
    }
}
