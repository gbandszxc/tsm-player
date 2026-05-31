package com.github.gbandszxc.tvmediaplayer.ui.modal

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
