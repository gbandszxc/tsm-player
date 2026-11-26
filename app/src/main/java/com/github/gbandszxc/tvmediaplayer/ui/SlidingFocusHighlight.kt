package com.github.gbandszxc.tvmediaplayer.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.github.gbandszxc.tvmediaplayer.R
import kotlin.math.roundToInt

/**
 * 共享滑动液态焦点框（基于 [ViewGroupOverlay]，API 21+ 全兼容，不向容器插入额外子 View）。
 *
 * 遥控器在列表、设置项、宫格卡、弹出列表间移动焦点时，焦点框以阻尼弹簧物理平滑滑行并
 * 自适应目标尺寸变化，同时伴随柔和外发光与一次性斜向镜面掠光。
 */
internal class SlidingFocusHighlight private constructor(
    private val container: ViewGroup,
    @ColorRes strokeColorRes: Int,
    @DimenRes cornerRadiusRes: Int,
) {
    private val highlightDrawable = SlidingGlassHighlightDrawable(
        strokeColor = ContextCompat.getColor(container.context, strokeColorRes),
        glowColor = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(container.context, strokeColorRes),
            GLOW_ALPHA,
        ),
        glossTopColor = ContextCompat.getColor(container.context, R.color.ui_glass_gloss_top),
        cornerRadiusPx = container.resources.getDimension(cornerRadiusRes),
        strokeWidthPx = container.resources.getDimension(R.dimen.ui_stroke_focus),
    )

    private val currentRect = RectF()
    private val startRect = RectF()
    private val targetRect = RectF()
    private val scratchRect = Rect()
    private var hasVisibleRect = false
    private var attachedToOverlay = false
    private var glideAnimator: ValueAnimator? = null
    private var alphaAnimator: ValueAnimator? = null
    private var sheenAnimator: ValueAnimator? = null
    private val springInterpolator = UiMotion.springGlideInterpolator()

    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        onGlobalFocusChanged(newFocus)
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            registerFocusListener()
        }

        override fun onViewDetachedFromWindow(v: View) {
            unregisterFocusListener()
            cancelAnimators()
        }
    }

    init {
        if (container.isAttachedToWindow) {
            registerFocusListener()
        }
        container.addOnAttachStateChangeListener(attachListener)
    }

    private fun registerFocusListener() {
        val observer = container.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalFocusChangeListener(focusListener)
            observer.addOnGlobalFocusChangeListener(focusListener)
        }
    }

    private fun unregisterFocusListener() {
        val observer = container.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalFocusChangeListener(focusListener)
        }
    }

    private fun onGlobalFocusChanged(newFocus: View?) {
        if (container.isInTouchMode) {
            hideImmediately()
            return
        }
        val targetChild = findDirectChildHosting(newFocus)
        if (targetChild == null) {
            fadeOut()
            return
        }
        if (targetChild.width <= 0 || targetChild.height <= 0) {
            targetChild.post {
                if (targetChild.hasFocus() || targetChild === container.findFocus()) {
                    moveHighlightTo(targetChild)
                }
            }
            return
        }
        moveHighlightTo(targetChild)
    }

    private fun findDirectChildHosting(focusView: View?): View? {
        var current: View? = focusView ?: return null
        while (current != null && current !== container) {
            val parent = current.parent as? View ?: return null
            if (parent === container) return current
            current = parent
        }
        return null
    }

    private fun moveHighlightTo(target: View) {
        if (!computeChildBounds(target, scratchRect)) return
        ensureOverlayAttached()

        targetRect.set(scratchRect)
        if (!hasVisibleRect) {
            hasVisibleRect = true
            currentRect.set(targetRect)
            applyBounds(currentRect)
            fadeIn()
            triggerSheen()
            return
        }

        if (currentRect == targetRect) {
            fadeIn()
            return
        }

        glideAnimator?.cancel()
        startRect.set(currentRect)
        fadeIn()
        triggerSheen()

        glideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLIDE_DURATION_MS
            interpolator = springInterpolator
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                currentRect.set(
                    startRect.left + (targetRect.left - startRect.left) * f,
                    startRect.top + (targetRect.top - startRect.top) * f,
                    startRect.right + (targetRect.right - startRect.right) * f,
                    startRect.bottom + (targetRect.bottom - startRect.bottom) * f,
                )
                applyBounds(currentRect)
            }
            start()
        }
    }

    private fun computeChildBounds(target: View, out: Rect): Boolean {
        if (target.width <= 0 || target.height <= 0) return false
        out.set(0, 0, target.width, target.height)
        return runCatching {
            container.offsetDescendantRectToMyCoords(target, out)
            true
        }.getOrDefault(false)
    }

    private fun applyBounds(rect: RectF) {
        highlightDrawable.setBounds(
            rect.left.roundToInt(),
            rect.top.roundToInt(),
            rect.right.roundToInt(),
            rect.bottom.roundToInt(),
        )
    }

    private fun ensureOverlayAttached() {
        if (!attachedToOverlay) {
            container.overlay.add(highlightDrawable)
            attachedToOverlay = true
        }
    }

    private fun fadeIn() {
        alphaAnimator?.cancel()
        val from = highlightDrawable.currentAlpha
        if (from == 255) return
        alphaAnimator = ValueAnimator.ofInt(from, 255).apply {
            duration = FADE_IN_DURATION_MS
            addUpdateListener { highlightDrawable.alpha = it.animatedValue as Int }
            start()
        }
    }

    private fun fadeOut() {
        if (!hasVisibleRect) return
        alphaAnimator?.cancel()
        val from = highlightDrawable.currentAlpha
        if (from == 0) {
            hasVisibleRect = false
            return
        }
        alphaAnimator = ValueAnimator.ofInt(from, 0).apply {
            duration = FADE_OUT_DURATION_MS
            addUpdateListener { highlightDrawable.alpha = it.animatedValue as Int }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (highlightDrawable.currentAlpha == 0) {
                        hasVisibleRect = false
                    }
                }
            })
            start()
        }
    }

    private fun hideImmediately() {
        cancelAnimators()
        hasVisibleRect = false
        highlightDrawable.alpha = 0
    }

    private fun triggerSheen() {
        sheenAnimator?.cancel()
        sheenAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SHEEN_DURATION_MS
            addUpdateListener { highlightDrawable.setSheenProgress(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    highlightDrawable.setSheenProgress(-1f)
                }
            })
            start()
        }
    }

    private fun cancelAnimators() {
        glideAnimator?.cancel()
        alphaAnimator?.cancel()
        sheenAnimator?.cancel()
    }

    private class SlidingGlassHighlightDrawable(
        private val strokeColor: Int,
        private val glowColor: Int,
        private val glossTopColor: Int,
        private val cornerRadiusPx: Float,
        private val strokeWidthPx: Float,
    ) : Drawable() {

        var currentAlpha: Int = 0
            private set

        private var sheenProgress: Float = -1f
        private val rect = RectF()
        private val glowRect = RectF()
        private val glossRect = RectF()

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = glowColor
            strokeWidth = strokeWidthPx * 2.4f
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            strokeWidth = strokeWidthPx
        }
        private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        fun setSheenProgress(progress: Float) {
            sheenProgress = progress
            invalidateSelf()
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            val half = strokeWidthPx / 2f
            rect.set(
                bounds.left + half,
                bounds.top + half,
                bounds.right - half,
                bounds.bottom - half,
            )
            glowRect.set(bounds)
            val glossHeight = (bounds.height() * 0.25f).coerceAtLeast(1f)
            glossRect.set(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.top + glossHeight,
            )
            glossPaint.shader = LinearGradient(
                0f,
                glossRect.top,
                0f,
                glossRect.bottom,
                ColorUtils.setAlphaComponent(glossTopColor, 0x16),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }

        override fun draw(canvas: Canvas) {
            if (currentAlpha == 0 || bounds.isEmpty) return

            glossPaint.alpha = currentAlpha
            canvas.drawRoundRect(glossRect, cornerRadiusPx, cornerRadiusPx, glossPaint)

            if (sheenProgress in 0f..1f) {
                val w = bounds.width().toFloat()
                val band = (w * 0.24f).coerceIn(48f, 200f)
                val xStart = bounds.left - band + (w + band * 2f) * sheenProgress
                sheenPaint.shader = LinearGradient(
                    xStart,
                    bounds.top.toFloat(),
                    xStart + band,
                    bounds.bottom.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0x22FFFFFF, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                sheenPaint.alpha = currentAlpha
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, sheenPaint)
            }

            glowPaint.alpha = (Color.alpha(glowColor) * currentAlpha) / 255
            canvas.drawRoundRect(glowRect, cornerRadiusPx, cornerRadiusPx, glowPaint)

            strokePaint.alpha = currentAlpha
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, strokePaint)
        }

        override fun setAlpha(alpha: Int) {
            if (currentAlpha == alpha) return
            currentAlpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            glowPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val GLOW_ALPHA = 0x42
        private const val GLIDE_DURATION_MS = 210L
        private const val FADE_IN_DURATION_MS = 130L
        private const val FADE_OUT_DURATION_MS = 120L
        private const val SHEEN_DURATION_MS = 280L

        fun attach(
            container: ViewGroup,
            @ColorRes strokeColorRes: Int,
            @DimenRes cornerRadiusRes: Int,
        ): SlidingFocusHighlight = SlidingFocusHighlight(container, strokeColorRes, cornerRadiusRes)
    }
}
