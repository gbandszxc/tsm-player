package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.github.gbandszxc.tvmediaplayer.R

object AppFonts {
    fun medium(context: Context): Typeface? = ResourcesCompat.getFont(context, R.font.misans_medium)

    fun bold(context: Context): Typeface? = ResourcesCompat.getFont(context, R.font.misans_bold)
}
