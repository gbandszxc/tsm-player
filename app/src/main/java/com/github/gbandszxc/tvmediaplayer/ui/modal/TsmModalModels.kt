package com.github.gbandszxc.tvmediaplayer.ui.modal

import android.app.Dialog

/**
 * Modal 操作按钮的数据模型。
 *
 * @param label     按钮文案
 * @param isPrimary 是否使用主操作样式（蓝色高亮）
 * @param isDanger  是否使用危险操作样式（红色）
 * @param isEnabled 按钮是否可点击
 * @param onClick   点击回调，null 表示仅展示不可点击
 */
data class ModalAction(
    val label: String,
    val isPrimary: Boolean = false,
    val isDanger: Boolean = false,
    val isEnabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

/**
 * 操作型 Modal 的规格描述。
 * 用于 [TsmModalCoordinator.showActionModal] 的入参。
 *
 * @param sectionLabel 左上角分区标签，如"文件操作"
 * @param title        标题，如"确认删除"
 * @param message      可选说明文案，为 null 时隐藏消息行
 * @param actions      操作按钮列表
 */
data class ActionModalSpec(
    val sectionLabel: String,
    val title: String,
    val message: String? = null,
    val actions: List<ModalAction>,
)

/**
 * 表单字段规格。
 *
 * @param key         字段唯一标识，用于运行时查找和校验错误回写
 * @param label       字段标签
 * @param initialValue 初始值
 * @param hint        输入提示
 * @param inputType   Android [android.text.InputType] 常量
 * @param error       初始校验错误，null 表示无错误
 */
data class FormFieldSpec(
    val key: String,
    val label: String,
    val initialValue: String,
    val hint: String,
    val inputType: Int,
    val error: String? = null,
)

/**
 * 表单型 Modal 规格。
 *
 * @param sectionLabel    左上角分区标签
 * @param title           标题
 * @param fields          表单字段列表
 * @param primaryAction   主操作按钮（如"创建"）
 * @param secondaryAction 次要操作按钮（如"取消"），null 则不显示
 */
data class FormModalSpec(
    val sectionLabel: String,
    val title: String,
    val fields: List<FormFieldSpec>,
    val primaryAction: ModalAction,
    val secondaryAction: ModalAction? = null,
)

/**
 * 确认型 Modal 规格。
 *
 * @param sectionLabel  左上角分区标签
 * @param title         标题
 * @param message       说明文案
 * @param confirmAction 确认按钮
 * @param cancelAction  取消按钮，null 则不显示
 */
data class ConfirmModalSpec(
    val sectionLabel: String,
    val title: String,
    val message: String,
    val confirmAction: ModalAction,
    val cancelAction: ModalAction? = null,
)

/**
 * 列表选择型 Modal 规格。
 *
 * @param sectionLabel 左上角分区标签
 * @param title        标题
 * @param message      可选说明文案
 * @param rows         列表行
 */
data class ListModalSpec(
    val sectionLabel: String,
    val title: String,
    val message: String? = null,
    val rows: List<ModalListRow>,
)

/**
 * 列表选择行。
 *
 * @param key     行唯一标识
 * @param label   行文案
 * @param enabled 是否可点击
 * @param onClick 点击回调
 */
data class ModalListRow(
    val key: String,
    val label: String,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

/**
 * 进度型 Modal 规格。
 *
 * @param sectionLabel  左上角分区标签
 * @param title         标题
 * @param fileName      文件名
 * @param percent       进度百分比（0-100）
 * @param indeterminate 是否为不确定进度
 * @param message       说明文案
 */
data class ProgressModalSpec(
    val sectionLabel: String,
    val title: String,
    val fileName: String,
    val percent: Int,
    val indeterminate: Boolean,
    val message: String,
)

/**
 * 进度型 Modal 的操作句柄。
 *
 * @param dialog     Dialog 实例
 * @param onProgress 更新进度的回调
 * @param onDismiss  关闭弹窗的回调
 */
data class ProgressModalHandle(
    val dialog: Dialog,
    val onProgress: (Int) -> Unit,
    val onDismiss: () -> Unit,
)
