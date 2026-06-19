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

/**
 * 统一的动效与微交互 helper。
 *
 * 严格隔离原则：触屏反馈与遥控器（焦点导航）反馈互斥，不共用任何视觉路径。
 * - 触屏点按（涟漪 + 加深）：由 [MotionEvent] 驱动，遥控器 OK 键不产生 MotionEvent，
 *   因此物理上不可能在遥控器触发。
 * - 遥控器聚焦展开：由焦点变化驱动，且仅在非触屏模式下才播动画。
 *
 * 两个颜色 token 决定按压 overlay：
 * - [R.color.ui_press_overlay_dark]（加深）用于彩色/高亮面（蓝/绿/红/黄/琥珀）；
 * - [R.color.ui_press_overlay_light]（提亮）用于深色中性面（文件行、暗按钮、modal 面等）。
 */
internal object UiMotion {

    /** 聚焦展开（入场）插值：Material decelerate ≈ ease-out，cubic-bezier(0,0,0.2,1)。 */
    private val EXPAND: Interpolator = PathInterpolator(0f, 0f, 0.2f, 1f)

    /** 收起（离场）插值：Material accelerate ≈ ease-in，cubic-bezier(0.4,0,1,1)。 */
    private val COLLAPSE: Interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)

    private const val EXPAND_DURATION_MS = 200L
    private const val COLLAPSE_DURATION_MS = 150L

    /** 释放后清除 foreground 的延迟，需覆盖涟漪淡出时间。 */
    private const val CLEAR_FOREGROUND_DELAY_MS = 380L

    private val widthAnimators = WeakHashMap<View, ValueAnimator>()
    private val clearForegroundRunnables = WeakHashMap<View, Runnable>()

    /**
     * 绑定触屏点按反馈（涟漪 + 加深）。仅由真实触摸触发；遥控器 OK 永不触发。
     *
     * 实现：按下时挂一层 overlay 色 [RippleDrawable] 作为 foreground，并设置 hotspot；
     * 视图自身的 pressed 态驱动涟漪扩散并维持 overlay（即“涟漪 + 加深”）。
     * 释放后延迟清除 foreground，确保默认 [View.getForeground] 为 null —— 遥控器 pressed
     * 没有可触发的 ripple drawable，从而永不涟漪。
     *
     * 该方法会设置 view 自身的 [View.OnTouchListener]（与 [View.OnClickListener] 互不影响，
     * listener 返回 false 让 view 继续处理点击与 pressed 态）。因此不要对已依赖自定义
     * OnTouchListener 的控件（如睡眠滚轮）调用本方法。
     */
    fun applyPressFeedback(view: View, overlayColorResId: Int) {
        val overlayColor = ContextCompat.getColor(view.context, overlayColorResId)
        val ripple = RippleDrawable(ColorStateList.valueOf(overlayColor), null, null)

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

    /** 设置 foreground；API 23+ 才支持 View.foreground，更低版本为 no-op（无涟漪，仅触屏可见时无影响）。 */
    private fun setForeground(view: View, drawable: Drawable?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.foreground = drawable
        }
    }

    /**
     * 以非线性插值动画改变可聚焦展开按钮的宽度。仅遥控器（非触屏）使用。
     *
     * 守卫：处于触屏模式 / 尚未布局 / 宽度为 0 时直接落定 [targetSpec] 并返回，
     * 因此对 Robolectric 单测（视图未布局）与触屏场景都不产生动画，保持调用方同步契约。
     */
    fun animateWidthTo(view: View, targetSpec: Int, expand: Boolean) {
        // 触屏模式 / 尚未布局（width<=0 即代表未完成布局，兼容 API 21）时直接落定目标宽度。
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

        // 先把宽度钉在起始值，避免 requestLayout 在动画首帧前把按钮闪到目标宽度。
        applyWidth(view, fromPx)

        widthAnimators.remove(view)?.cancel()
        val animator = ValueAnimator.ofInt(fromPx, targetPx).apply {
            interpolator = if (expand) EXPAND else COLLAPSE
            duration = if (expand) EXPAND_DURATION_MS else COLLAPSE_DURATION_MS
            addUpdateListener { applyWidth(view, it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var canceled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    widthAnimators.remove(view)
                    // 被取消（通常是被同 view 的新动画取代）时不落定 spec，交由新动画处理，避免连按方向键时一帧闪到目标宽度。
                    if (canceled) return
                    applyWidthSpec(view, targetSpec)
                }
            })
        }
        widthAnimators[view] = animator
        animator.start()
    }

    private fun applyWidth(view: View, widthPx: Int) {
        view.layoutParams.width = widthPx
        view.requestLayout()
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
