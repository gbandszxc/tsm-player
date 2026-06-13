package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.graphics.drawable.Drawable
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
        button.ellipsize = TextUtils.TruncateAt.END

        val icon = ContextCompat.getDrawable(context, spec.iconResId)?.mutate()
        val wrapped = icon?.let(DrawableCompat::wrap)
        if (wrapped != null) {
            val iconColor = ContextCompat.getColor(context, iconColorResId)
            DrawableCompat.setTint(wrapped, iconColor)
            wrapped.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                iconColor,
                BlendModeCompat.SRC_IN,
            )
            wrapped.setBounds(0, 0, wrapped.intrinsicWidth, wrapped.intrinsicHeight)
        }

        val minWidth = context.resources.getDimensionPixelSize(expandedWidthResId(spec, hasFocus))
        button.minWidth = minWidth
        button.layoutParams = button.layoutParams.apply {
            width = if (hasFocus) {
                ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                minWidth
            }
        }

        if (hasFocus) {
            button.overlay.clear()
            button.setCompoundDrawables(wrapped, null, null, null)
        } else {
            button.setCompoundDrawables(null, null, null, null)
            button.post { drawCenteredIcon(button, wrapped, spec) }
        }
    }

    private fun drawCenteredIcon(
        button: Button,
        icon: Drawable?,
        spec: PlaybackButtonSpec,
    ) {
        if (icon == null || !PlaybackButtonPresentation.shouldDrawCenteredIcon(spec, button.hasFocus())) return
        button.overlay.clear()
        val iconWidth = icon.intrinsicWidth.coerceAtLeast(1)
        val iconHeight = icon.intrinsicHeight.coerceAtLeast(1)
        val left = (button.width - iconWidth) / 2
        val top = (button.height - iconHeight) / 2
        icon.setBounds(left, top, left + iconWidth, top + iconHeight)
        button.overlay.add(icon)
    }

    private fun expandedWidthResId(spec: PlaybackButtonSpec, hasFocus: Boolean): Int {
        if (!hasFocus) return R.dimen.ui_playback_mode_button_collapsed_width
        return spec.browserExpandedWidthResId ?: R.dimen.ui_playback_mode_button_expanded_min_width
    }
}
