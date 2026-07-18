package com.github.gbandszxc.tvmediaplayer.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import kotlin.math.roundToInt

object UiSettingsApplier {

    fun applyAll(activity: Activity) {
        applyGlobalScale(activity)
        applyKeepScreenAwake(activity)
        applyImmersiveFullscreen(activity)
    }

    fun applyFullscreenWindowLayout(activity: Activity) {
        applyFullscreenWindowLayout(activity.window)
    }

    fun applyFullscreenWindowLayout(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
    }

    fun applyImmersiveFullscreen(activity: Activity) {
        applyFullscreenWindowLayout(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    fun applyGlobalScale(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val content = root.getChildAt(0) ?: return
        applyGlobalScaleToContent(
            root = root,
            content = content,
            scalePercent = UiSettingsStore.globalScalePercent(activity),
        )
    }

    fun applyGlobalScaleToContent(root: ViewGroup, content: View, scalePercent: Int) {
        content.post {
            val parentWidth = root.width
            val parentHeight = root.height
            val params = content.layoutParams
            val layout = computeGlobalScaleLayout(parentWidth, parentHeight, scalePercent)
            params.width = layout.width
            params.height = layout.height
            content.layoutParams = params
            content.pivotX = 0f
            content.pivotY = 0f
            content.scaleX = layout.scale
            content.scaleY = layout.scale
        }
    }

    fun applyKeepScreenAwake(activity: Activity) {
        if (UiSettingsStore.keepScreenAwake(activity)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun computeGlobalScaleLayout(
        parentWidth: Int,
        parentHeight: Int,
        scalePercent: Int,
    ): GlobalScaleLayout {
        val scale = scalePercent / 100f
        return if (scale != 1f && parentWidth > 0 && parentHeight > 0) {
            GlobalScaleLayout(
                width = (parentWidth / scale).roundToInt(),
                height = (parentHeight / scale).roundToInt(),
                scale = scale,
            )
        } else {
            GlobalScaleLayout(
                width = MATCH_PARENT,
                height = MATCH_PARENT,
                scale = scale,
            )
        }
    }

    data class GlobalScaleLayout(
        val width: Int,
        val height: Int,
        val scale: Float,
    )

    @JvmStatic
    internal fun computeGlobalScaleLayoutForTest(
        parentWidth: Int,
        parentHeight: Int,
        scalePercent: Int,
    ): GlobalScaleLayout = computeGlobalScaleLayout(parentWidth, parentHeight, scalePercent)
}
