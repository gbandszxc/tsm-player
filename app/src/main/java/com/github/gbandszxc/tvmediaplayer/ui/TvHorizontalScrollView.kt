package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.HorizontalScrollView

class TvHorizontalScrollView(context: Context, attrs: AttributeSet) : HorizontalScrollView(context, attrs) {
    private val focusBounds = Rect()

    init {
        isFocusable = false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val focused = findFocus() ?: return
        if (focused === this || isInTouchMode) return

        // 焦点胶囊在获得焦点后还会改变宽度，重排完成时重新露出完整按钮。
        focused.getDrawingRect(focusBounds)
        focused.requestRectangleOnScreen(focusBounds, true)
    }
}
