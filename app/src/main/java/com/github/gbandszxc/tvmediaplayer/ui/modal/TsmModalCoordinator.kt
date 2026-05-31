package com.github.gbandszxc.tvmediaplayer.ui.modal

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.gbandszxc.tvmediaplayer.R

/**
 * TV Modal 统一协调器。
 *
 * 负责将 [ActionModalSpec] 等规格对象转换为带焦点管理的 Dialog。
 * 所有项目中需要弹出的 AlertDialog / 自定义弹窗最终都应收敛到该协调器，
 * 以保证统一的视觉风格、焦点恢复和遥控器交互。
 *
 * @param host 宿主 Activity，用于获取 LayoutInflater 和创建 Dialog
 */
class TsmModalCoordinator(
    private val host: Activity,
) {
    /** 记录弹窗打开前的焦点 View，关闭后恢复 */
    private var lastFocusedView: View? = null

    /**
     * 展示操作型 Modal。
     *
     * 行为：
     * 1. 记录当前焦点，弹窗关闭后恢复
     * 2. 填充 shell 布局，设置分区标签、标题、消息
     * 3. 为每个 action 生成按钮行并添加到内容容器
     * 4. 自动聚焦第一个操作按钮
     *
     * @param spec 操作型 Modal 规格
     * @return 已 show 的 Dialog 实例，调用方可用于 dismiss
     */
    fun showActionModal(spec: ActionModalSpec): Dialog {
        lastFocusedView = host.currentFocus

        val content = LayoutInflater.from(host)
            .inflate(R.layout.dialog_tsm_modal_shell, null, false)

        // 分区标签
        content.findViewById<TextView>(R.id.tv_modal_section).text = spec.sectionLabel

        // 标题
        content.findViewById<TextView>(R.id.tv_modal_title).text = spec.title

        // 消息（null 或空白时隐藏）
        val messageView = content.findViewById<TextView>(R.id.tv_modal_message)
        messageView.text = spec.message.orEmpty()
        messageView.visibility = if (spec.message.isNullOrBlank()) View.GONE else View.VISIBLE

        // 操作按钮
        val container = content.findViewById<LinearLayout>(R.id.container_modal_content)
        container.removeAllViews()
        spec.actions.forEach { action ->
            val row = LayoutInflater.from(host)
                .inflate(R.layout.item_tsm_modal_action_row, container, false) as Button
            row.text = action.label
            row.isEnabled = action.isEnabled
            row.setOnClickListener { action.onClick?.invoke() }
            row.setBackgroundResource(
                when {
                    action.isDanger -> R.drawable.bg_button_red
                    action.isPrimary -> R.drawable.bg_button_primary
                    else -> R.drawable.bg_button_dark
                }
            )
            row.setTextColor(ContextCompat.getColor(host, R.color.ui_text_on_accent))
            container.addView(row)
        }

        return Dialog(host, android.R.style.Theme_NoTitleBar_Fullscreen).apply {
            setContentView(content)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(null)
            setOnDismissListener {
                lastFocusedView?.requestFocus()
            }
            show()
            // 自动聚焦第一个操作按钮，方便遥控器立即操作
            container.getChildAt(0)?.requestFocus()
        }
    }
}
