package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/** 容器跳过遥控器焦点，内部控件和触屏滑动仍可用。 */
class NonFocusableScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle
) : ScrollView(context, attrs, defStyleAttr) {
    private val focusBounds = Rect()

    init {
        // ScrollView 初始化时会重新开启 focusable，XML 属性不足以覆盖。
        isFocusable = false
    }

    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        revealFocusInsidePadding(focused)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        findFocus()?.let(::revealFocusInsidePadding)
    }

    private fun revealFocusInsidePadding(focused: View) {
        val viewportHeight = height - paddingTop - paddingBottom
        if (focused === this || isInTouchMode || !clipToPadding || viewportHeight <= 0 ||
            paddingTop + paddingBottom == 0
        ) return

        // 原生焦点滚动按整个 ScrollView 高度计算，实际绘制还会裁掉内边距。
        focused.getDrawingRect(focusBounds)
        offsetDescendantRectToMyCoords(focused, focusBounds)
        val top = scrollY + paddingTop
        val bottom = top + viewportHeight
        val delta = when {
            focusBounds.top < top || focusBounds.height() > viewportHeight -> focusBounds.top - top
            focusBounds.bottom > bottom -> focusBounds.bottom - bottom
            else -> 0
        }
        if (delta != 0) scrollBy(0, delta)
    }
}
