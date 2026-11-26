package com.github.gbandszxc.tvmediaplayer.ui

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 统一的液态玻璃动效与微交互 helper。
 *
 * 严格隔离原则：触屏反馈与遥控器（焦点导航）反馈互斥，不共用任何视觉路径。
 * - 触屏点按（涟漪 + 加深/提亮）：由 [MotionEvent] 驱动，遥控器 OK 键不产生 MotionEvent，
 *   因此物理上不可能在遥控器触发。
 * - 遥控器焦点交互：由焦点变化驱动，使用阻尼弹簧物理插值，且仅在非触屏模式下才播动画。
 */
internal object UiMotion {

    /** 阻尼弹簧插值器（轻量零依赖，t=1 精确收敛到 1f）。 */
    private class SpringInterpolator(
        dampingRatio: Float,
        private val angularFrequency: Float,
    ) : Interpolator {
        private val dampedFreq = angularFrequency * sqrt((1f - dampingRatio * dampingRatio).coerceAtLeast(0.01f))
        private val decay = dampingRatio * angularFrequency
        private val sinCoeff = dampingRatio / sqrt((1f - dampingRatio * dampingRatio).coerceAtLeast(0.01f))

        override fun getInterpolation(t: Float): Float {
            if (t <= 0f) return 0f
            if (t >= 1f) return 1f
            val env = exp(-decay * t)
            return (1f - env * (cos(dampedFreq * t) + sinCoeff * sin(dampedFreq * t))).coerceAtMost(1.06f)
        }
    }

    /** 聚焦展开/抬升弹簧（轻微柔和弹性，240ms）。 */
    private val SPRING_EXPAND: Interpolator = SpringInterpolator(dampingRatio = 0.80f, angularFrequency = 6.2f)

    /** 滑动聚焦与弹层展开弹簧（沉稳顺滑，几乎无过冲，210ms）。 */
    private val SPRING_GLIDE: Interpolator = SpringInterpolator(dampingRatio = 0.90f, angularFrequency = 6.8f)

    /** 收起（离场）插值：Material accelerate ≈ ease-in，cubic-bezier(0.4,0,1,1)。 */
    private val COLLAPSE: Interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)

    private const val EXPAND_DURATION_MS = 240L
    private const val COLLAPSE_DURATION_MS = 160L
    private const val DROPDOWN_ENTER_DURATION_MS = 200L
    private const val MODAL_ENTER_DURATION_MS = 220L
    private const val TOAST_ENTER_DURATION_MS = 200L
    private const val TOAST_EXIT_DURATION_MS = 150L

    /** 释放后清除 foreground 的延迟，需覆盖涟漪淡出时间。 */
    private const val CLEAR_FOREGROUND_DELAY_MS = 380L

    private val widthAnimators = WeakHashMap<View, ValueAnimator>()
    private val clearForegroundRunnables = WeakHashMap<View, Runnable>()
    private val focusScaleAnimators = WeakHashMap<View, ValueAnimator>()

    /** 液态玻璃焦点缩放：轻微弹性抬升，卡片/宫格/封面专用。 */
    private const val FOCUS_SCALE = 1.045f
    private const val FOCUS_SCALE_IN_DURATION_MS = 190L
    private const val FOCUS_SCALE_OUT_DURATION_MS = 140L
    private const val FOCUS_ELEVATION_DP = 8f

    fun springGlideInterpolator(): Interpolator = SPRING_GLIDE

    /**
     * 绑定触屏点按反馈（涟漪 + 加深/提亮）。仅由真实触摸触发；遥控器 OK 永不触发。
     */
    fun applyPressFeedback(view: View, overlayColorResId: Int) {
        val overlayColor = ContextCompat.getColor(view.context, overlayColorResId)
        val mask = view.background?.constantState?.newDrawable(view.resources)?.mutate()
        val ripple = RippleDrawable(ColorStateList.valueOf(overlayColor), null, mask)

        view.setOnTouchListener { v, event ->
            if (v.isEnabled) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        setForeground(v, ripple)
                        ripple.setHotspot(event.x, event.y)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        scheduleClearForeground(v)
                    }
                }
            }
            false
        }
    }

    private fun scheduleClearForeground(view: View) {
        clearForegroundRunnables.remove(view)?.let(view::removeCallbacks)
        val runnable = Runnable { setForeground(view, null) }
        clearForegroundRunnables[view] = runnable
        view.postDelayed(runnable, CLEAR_FOREGROUND_DELAY_MS)
    }

    private fun setForeground(view: View, drawable: Drawable?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.foreground = drawable
        }
    }

    /**
     * 以弹簧物理插值动画改变可聚焦展开按钮的宽度（液态胶囊形变）。仅遥控器（非触屏）使用。
     */
    fun animateWidthTo(view: View, targetSpec: Int, expand: Boolean) {
        widthAnimators.remove(view)?.cancel()

        if (view.isInTouchMode || view.width <= 0) {
            applyWidthSpec(view, targetSpec)
            return
        }

        val targetPx = if (targetSpec == ViewGroup.LayoutParams.WRAP_CONTENT) {
            measureWrapWidth(view)
        } else {
            targetSpec
        }
        val fromPx = view.width
        if (fromPx == targetPx) {
            applyWidthSpec(view, targetSpec)
            return
        }

        applyWidth(view, fromPx)

        val animator = ValueAnimator.ofInt(fromPx, targetPx).apply {
            interpolator = if (expand) SPRING_EXPAND else COLLAPSE
            duration = if (expand) EXPAND_DURATION_MS else COLLAPSE_DURATION_MS
            addUpdateListener { applyWidth(view, it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var canceled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (widthAnimators[view] === animation) {
                        widthAnimators.remove(view)
                    }
                    if (canceled) return
                    applyWidthSpec(view, targetSpec)
                }
            })
        }
        widthAnimators[view] = animator
        animator.start()
    }

    private fun applyWidth(view: View, widthPx: Int) {
        if (view.layoutParams.width == widthPx) return
        view.layoutParams.width = widthPx
        view.requestLayout()
    }

    /**
     * 遥控器焦点弹性抬升（液态玻璃动效）：聚焦 190ms 弹簧放大至 1.045 + 轻阴影抬升，
     * 若背景为 [LiquidGlassDrawable] 自动触发一次镜面掠光。适用于宫格卡、歌单 tile、封面与小按钮。
     */
    fun animateFocusScale(view: View, focused: Boolean) {
        focusScaleAnimators.remove(view)?.cancel()

        val targetScale = if (focused) FOCUS_SCALE else 1f
        val targetElevation = if (focused) FOCUS_ELEVATION_DP * view.resources.displayMetrics.density else 0f
        if (view.isInTouchMode || view.width <= 0 || view.height <= 0) {
            applyScale(view, targetScale)
            view.elevation = targetElevation
            return
        }

        if (focused) {
            (view.background as? LiquidGlassDrawable)?.triggerSheen()
        }
        view.elevation = targetElevation

        val fromScale = view.scaleX
        if (fromScale == targetScale) return

        val animator = ValueAnimator.ofFloat(fromScale, targetScale).apply {
            interpolator = if (focused) SPRING_EXPAND else COLLAPSE
            duration = if (focused) FOCUS_SCALE_IN_DURATION_MS else FOCUS_SCALE_OUT_DURATION_MS
            addUpdateListener { applyScale(view, it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (focusScaleAnimators[view] === animation) {
                        focusScaleAnimators.remove(view)
                    }
                }
            })
        }
        focusScaleAnimators[view] = animator
        animator.start()
    }

    /**
     * 全宽列表行焦点微动效预留（与 [SlidingFocusHighlight] 滑行框保持像素级对齐，避免错位重影）。
     */
    fun animateRowFocus(view: View, @Suppress("UNUSED_PARAMETER") focused: Boolean) {
        if (view.translationX != 0f) {
            view.translationX = 0f
        }
    }

    /** 排序下拉浮层顶部锚定展开动效。 */
    fun animateDropdownEnter(dropdown: View) {
        dropdown.post {
            if (!dropdown.isAttachedToWindow || dropdown.width <= 0) return@post
            dropdown.pivotX = dropdown.width * 0.25f
            dropdown.pivotY = 0f
            dropdown.alpha = 0f
            dropdown.scaleX = 0.96f
            dropdown.scaleY = 0.88f
            dropdown.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(DROPDOWN_ENTER_DURATION_MS)
                .setInterpolator(SPRING_GLIDE)
                .start()
        }
    }

    /** Modal 居中面板弹性浮现动效。 */
    fun animateModalEnter(panel: View) {
        panel.post {
            if (!panel.isAttachedToWindow || panel.width <= 0) return@post
            val offsetPx = 16f * panel.resources.displayMetrics.density
            panel.alpha = 0f
            panel.scaleX = 0.94f
            panel.scaleY = 0.94f
            panel.translationY = offsetPx
            panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(MODAL_ENTER_DURATION_MS)
                .setInterpolator(SPRING_GLIDE)
                .start()
        }
    }

    /** 右上角胶囊轻提示滑入动效。 */
    fun animateToastEnter(toast: View) {
        toast.animate().cancel()
        toast.visibility = View.VISIBLE
        if (toast.width <= 0) {
            toast.alpha = 1f
            toast.translationY = 0f
            return
        }
        val offsetPx = -10f * toast.resources.displayMetrics.density
        toast.alpha = 0f
        toast.translationY = offsetPx
        toast.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(TOAST_ENTER_DURATION_MS)
            .setInterpolator(SPRING_GLIDE)
            .start()
    }

    /** 右上角胶囊轻提示淡出隐藏。 */
    fun animateToastExit(toast: View) {
        toast.animate().cancel()
        if (toast.visibility != View.VISIBLE) return
        if (toast.width <= 0) {
            toast.visibility = View.GONE
            return
        }
        toast.animate()
            .alpha(0f)
            .translationY(-6f * toast.resources.displayMetrics.density)
            .setDuration(TOAST_EXIT_DURATION_MS)
            .setInterpolator(COLLAPSE)
            .withEndAction {
                toast.visibility = View.GONE
                toast.alpha = 1f
                toast.translationY = 0f
            }
            .start()
    }

    private fun applyScale(view: View, scale: Float) {
        if (view.scaleX != scale) {
            view.scaleX = scale
            view.scaleY = scale
        }
    }

    private fun applyWidthSpec(view: View, widthSpec: Int) {
        if (view.layoutParams.width != widthSpec) {
            view.layoutParams.width = widthSpec
            view.requestLayout()
        }
    }

    private fun measureWrapWidth(view: View): Int {
        val parentWidth = (view.parent as? View)?.width?.takeIf { it > 0 } ?: 16384
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(view.height.coerceAtLeast(0), View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredWidth.coerceAtLeast(0)
    }
}
