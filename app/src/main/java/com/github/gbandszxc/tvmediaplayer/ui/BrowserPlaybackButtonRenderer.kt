package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
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
    ) {
        button.text = spec.text
        button.contentDescription = spec.contentDescription

        val icon = ContextCompat.getDrawable(context, spec.iconResId)?.mutate()
        val wrapped = icon?.let(DrawableCompat::wrap)
        if (wrapped != null) {
            val iconColor = ContextCompat.getColor(context, R.color.ui_text_on_accent)
            DrawableCompat.setTint(wrapped, iconColor)
            wrapped.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                iconColor,
                BlendModeCompat.SRC_IN,
            )
            wrapped.setBounds(0, 0, wrapped.intrinsicWidth, wrapped.intrinsicHeight)
        }
        button.setCompoundDrawables(wrapped, null, null, null)

        val width = if (hasFocus) {
            context.resources.getDimensionPixelSize(R.dimen.ui_playback_mode_button_expanded_min_width)
        } else {
            context.resources.getDimensionPixelSize(R.dimen.ui_playback_mode_button_collapsed_width)
        }
        button.minWidth = width
        button.layoutParams = button.layoutParams.apply { this.width = width }
    }
}
