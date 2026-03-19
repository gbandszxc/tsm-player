package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * 始终保持宽高相等的正方形 FrameLayout。
 * 高度跟随测量后的宽度，超出部分由 ImageView 的 centerCrop 裁剪。
 */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 先用原始 widthSpec 确定宽度，再把高度强制等于宽度
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
