package com.github.gbandszxc.tvmediaplayer.ui.modal

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.R

/**
 * Modal 便捷构建工具。
 *
 * 提供常用 Modal 场景的 DSL 风格构建方法，
 * 减少调用方构造 [ActionModalSpec] 和 [ModalAction] 的样板代码。
 */
object TsmModalBuilders {

    /**
     * 创建一个确认型 ModalSpec。
     * 包含一个主操作按钮和一个取消按钮。
     *
     * @param context      用于获取字符串资源
     * @param sectionLabel 分区标签
     * @param title        标题
     * @param message      说明文案
     * @param confirmLabel 确认按钮文案
     * @param onConfirm    确认回调
     * @param cancelLabel  取消按钮文案
     * @param onCancel     取消回调
     */
    fun confirm(
        context: Context,
        sectionLabel: String,
        title: String,
        message: String,
        confirmLabel: String = context.getString(R.string.modal_default_confirm),
        onConfirm: () -> Unit = {},
        cancelLabel: String = context.getString(R.string.modal_default_cancel),
        onCancel: () -> Unit = {},
    ): ActionModalSpec = ActionModalSpec(
        sectionLabel = sectionLabel,
        title = title,
        message = message,
        actions = listOf(
            ModalAction(confirmLabel, isPrimary = true, onClick = onConfirm),
            ModalAction(cancelLabel, onClick = onCancel),
        ),
    )

    /**
     * 创建一个危险操作确认型 ModalSpec。
     * 确认按钮使用红色 danger 样式。
     *
     * @param context      用于获取字符串资源
     * @param sectionLabel 分区标签
     * @param title        标题
     * @param message      说明文案
     * @param confirmLabel 确认按钮文案
     * @param onConfirm    确认回调
     * @param cancelLabel  取消按钮文案
     */
    fun dangerConfirm(
        context: Context,
        sectionLabel: String,
        title: String,
        message: String,
        confirmLabel: String = context.getString(R.string.modal_default_delete),
        onConfirm: () -> Unit = {},
        cancelLabel: String = context.getString(R.string.modal_default_cancel),
    ): ActionModalSpec = ActionModalSpec(
        sectionLabel = sectionLabel,
        title = title,
        message = message,
        actions = listOf(
            ModalAction(confirmLabel, isDanger = true, onClick = onConfirm),
            ModalAction(cancelLabel, onClick = {}),
        ),
    )
}
