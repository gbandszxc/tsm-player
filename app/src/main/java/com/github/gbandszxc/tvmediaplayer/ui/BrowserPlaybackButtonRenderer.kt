package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.github.gbandszxc.tvmediaplayer.R

internal object BrowserPlaybackButtonRenderer {

    fun apply(
        context: Context,
        button: Button,
        spec: PlaybackButtonSpec,
        hasFocus: Boolean,
        iconColorResId: Int = R.color.ui_text_on_accent,
    ) {
        button.text = spec.text
        button.contentDescription = spec.contentDescription
        button.maxLines = 1
        button.setSingleLine(true)
        button.ellipsize = TextUtils.TruncateAt.END

        val icon = ContextCompat.getDrawable(context, spec.iconResId)?.mutate()
        if (icon != null) {
            val iconColor = ContextCompat.getColor(context, iconColorResId)
            DrawableCompat.setTint(icon, iconColor)
            icon.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                iconColor,
                BlendModeCompat.SRC_IN,
            )
            icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
        }

        val showsLabel = hasFocus && spec.text.isNotEmpty()
        val basePadding = context.resources.getDimensionPixelSize(R.dimen.ui_space_3xl)
        if (showsLabel) {
            button.text = spec.text
            button.setCompoundDrawables(icon, null, null, null)
            button.setPaddingRelative(basePadding, button.paddingTop, basePadding, button.paddingBottom)
        } else {
            button.text = ""
            button.setCompoundDrawables(icon, null, null, null)
            val centerCorrection = context.resources.getDimensionPixelSize(R.dimen.ui_space_sm) / 2
            button.setPaddingRelative(
                basePadding + centerCorrection,
                button.paddingTop,
                basePadding - centerCorrection,
                button.paddingBottom,
            )
        }
        button.compoundDrawablePadding = if (showsLabel) {
            context.resources.getDimensionPixelSize(R.dimen.ui_space_sm)
        } else {
            0
        }

        val minWidth = context.resources.getDimensionPixelSize(expandedWidthResId(spec, hasFocus))
        button.minWidth = minWidth
        // 同步落定目标宽度（保持单测同步契约：聚焦时为 WRAP_CONTENT），
        // 再交由 UiMotion 在“已布局 + 非触屏”时接管为非线性展开动画。
        val targetSpec = if (hasFocus) ViewGroup.LayoutParams.WRAP_CONTENT else minWidth
        if (button.layoutParams.width != targetSpec) {
            button.layoutParams = button.layoutParams.apply { width = targetSpec }
        }
        // 图标与文字都就位后再测量 WRAP_CONTENT，避免展开目标漏算图标宽度。
        UiMotion.animateWidthTo(button, targetSpec, expand = hasFocus && spec.expandsOnFocus)
    }

    private fun expandedWidthResId(spec: PlaybackButtonSpec, hasFocus: Boolean): Int {
        if (!hasFocus) return R.dimen.ui_playback_mode_button_collapsed_width
        return spec.browserExpandedWidthResId ?: R.dimen.ui_playback_mode_button_expanded_min_width
    }
}
