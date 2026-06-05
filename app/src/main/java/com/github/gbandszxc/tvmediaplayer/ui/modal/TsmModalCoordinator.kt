package com.github.gbandszxc.tvmediaplayer.ui.modal

import android.app.Activity
import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.update.DownloadProgressFormatter
import com.github.gbandszxc.tvmediaplayer.update.DownloadProgressState

/**
 * TV Modal 统一协调器。
 *
 * 负责将各种 Modal 规格（[ActionModalSpec]、[FormModalSpec]、[ConfirmModalSpec]、
 * [ListModalSpec]、[ProgressModalSpec]）转换为带焦点管理的 Dialog。
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

        lateinit var dialog: Dialog

        // 操作按钮
        val container = content.findViewById<LinearLayout>(R.id.container_modal_content)
        container.removeAllViews()
        spec.actions.forEach { action ->
            val row = LayoutInflater.from(host)
                .inflate(R.layout.item_tsm_modal_action_row, container, false) as Button
            row.text = action.label
            row.isEnabled = action.isEnabled
            row.setOnClickListener {
                if (!action.isEnabled) return@setOnClickListener
                action.onClick?.invoke()
                dialog.dismiss()
            }
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

        dialog = Dialog(host, android.R.style.Theme_NoTitleBar_Fullscreen).apply {
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
        return dialog
    }

    /**
     * 展示表单型 Modal。
     *
     * 行为：
     * 1. 使用 shell 布局，在内容区逐个 inflate 表单字段
     * 2. 在 actions 容器中放置主/次按钮
     * 3. 自动聚焦第一个字段输入框
     *
     * @param spec 表单型 Modal 规格
     * @return 已 show 的 Dialog 实例
     */
    fun showFormModal(spec: FormModalSpec): Dialog {
        lastFocusedView = host.currentFocus

        val content = LayoutInflater.from(host)
            .inflate(R.layout.dialog_tsm_modal_shell, null, false)

        // 分区标签
        content.findViewById<TextView>(R.id.tv_modal_section).text = spec.sectionLabel

        // 标题
        content.findViewById<TextView>(R.id.tv_modal_title).text = spec.title

        // 隐藏消息行（表单不需要）
        content.findViewById<TextView>(R.id.tv_modal_message).visibility = View.GONE

        // 表单字段
        val fieldContainer = content.findViewById<LinearLayout>(R.id.container_modal_content)
        fieldContainer.removeAllViews()
        spec.fields.forEach { field ->
            when (field.type) {
                FormFieldSpecType.TEXT -> {
                    val fieldView = LayoutInflater.from(host)
                        .inflate(R.layout.item_tsm_modal_form_field, fieldContainer, false)
                    // 用 field.key 作为 tag，便于后续查找
                    fieldView.tag = field.key

                    fieldView.findViewById<TextView>(R.id.tv_modal_field_label).text = field.label
                    val inputView = fieldView.findViewById<EditText>(R.id.et_modal_field_input)
                    inputView.setText(field.initialValue)
                    inputView.hint = field.hint
                    inputView.inputType = field.inputType

                    // 初始错误
                    val errorView = fieldView.findViewById<TextView>(R.id.tv_modal_field_error)
                    if (field.error != null) {
                        errorView.text = field.error
                        errorView.visibility = View.VISIBLE
                    }

                    fieldContainer.addView(fieldView)
                }
                FormFieldSpecType.CHECKBOX -> {
                    val checkBox = CheckBox(host).apply {
                        tag = field.key
                        text = field.label
                        isChecked = field.initialValue.toBooleanStrictOrNull() ?: false
                        typeface = null
                        setTextColor(ContextCompat.getColor(host, R.color.ui_text_primary))
                        textSize = 14f
                        setPadding(0, host.resources.getDimensionPixelSize(R.dimen.ui_space_sm), 0, host.resources.getDimensionPixelSize(R.dimen.ui_space_sm))
                    }
                    fieldContainer.addView(checkBox)
                }
            }
        }

        // 操作按钮
        val actionsContainer = content.findViewById<LinearLayout>(R.id.container_modal_actions)
        actionsContainer.visibility = View.VISIBLE
        actionsContainer.removeAllViews()

        spec.leadingAction?.let { leading ->
            val leadingBtn = createActionButton(leading)
            leadingBtn.tag = TAG_ACTION_LEADING
            actionsContainer.addView(leadingBtn)
        }

        // 主操作按钮
        val primaryBtn = createActionButton(spec.primaryAction)
        primaryBtn.tag = TAG_ACTION_PRIMARY
        actionsContainer.addView(primaryBtn)

        // 次要操作按钮（点击后自动关闭弹窗，与 AlertDialog neutralButton 行为一致）
        spec.secondaryAction?.let { secondary ->
            val secondaryBtn = createActionButton(secondary)
            secondaryBtn.tag = TAG_ACTION_SECONDARY
            actionsContainer.addView(secondaryBtn)
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

            spec.leadingAction?.let { leading ->
                actionsContainer.findViewWithTag<Button>(TAG_ACTION_LEADING)?.setOnClickListener {
                    leading.onClick?.invoke()
                }
            }

            // 次要按钮点击后自动关闭
            spec.secondaryAction?.let { sec ->
                actionsContainer.findViewWithTag<Button>(TAG_ACTION_SECONDARY)?.setOnClickListener {
                    sec.onClick?.invoke()
                    dismiss()
                }
            }

            // 自动聚焦第一个字段的输入框
            fieldContainer.getChildAt(0)
                ?.findViewById<EditText>(R.id.et_modal_field_input)
                ?.requestFocus()

            content.post {
                applyFormPanelLayout(content)
            }
        }
    }

    /**
     * 展示确认型 Modal。
     *
     * @param spec 确认型 Modal 规格
     * @return 已 show 的 Dialog 实例
     */
    fun showConfirmModal(spec: ConfirmModalSpec): Dialog {
        lastFocusedView = host.currentFocus

        val content = LayoutInflater.from(host)
            .inflate(R.layout.dialog_tsm_modal_shell, null, false)

        // 分区标签
        content.findViewById<TextView>(R.id.tv_modal_section).text = spec.sectionLabel

        // 标题
        content.findViewById<TextView>(R.id.tv_modal_title).text = spec.title

        // 消息
        val messageView = content.findViewById<TextView>(R.id.tv_modal_message)
        messageView.text = spec.message
        messageView.visibility = View.VISIBLE

        // 内容区清空（确认弹窗不需要额外内容）
        content.findViewById<LinearLayout>(R.id.container_modal_content).removeAllViews()

        // 操作按钮
        val actionsContainer = content.findViewById<LinearLayout>(R.id.container_modal_actions)
        actionsContainer.visibility = View.VISIBLE
        actionsContainer.removeAllViews()

        val confirmBtn = createActionButton(spec.confirmAction)
        confirmBtn.tag = TAG_ACTION_PRIMARY
        actionsContainer.addView(confirmBtn)

        spec.cancelAction?.let { cancel ->
            val cancelBtn = createActionButton(cancel)
            cancelBtn.tag = TAG_ACTION_SECONDARY
            actionsContainer.addView(cancelBtn)
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

            // 确认/取消按钮点击后自动关闭
            confirmBtn.setOnClickListener {
                spec.confirmAction.onClick?.invoke()
                dismiss()
            }
            spec.cancelAction?.let {
                actionsContainer.findViewWithTag<Button>(TAG_ACTION_SECONDARY)?.setOnClickListener {
                    spec.cancelAction.onClick?.invoke()
                    dismiss()
                }
            }

            // 聚焦确认按钮
            confirmBtn.requestFocus()
        }
    }

    /**
     * 展示列表选择型 Modal。
     *
     * @param spec 列表型 Modal 规格
     * @return 已 show 的 Dialog 实例
     */
    fun showListModal(spec: ListModalSpec): Dialog {
        lastFocusedView = host.currentFocus

        val content = LayoutInflater.from(host)
            .inflate(R.layout.dialog_tsm_modal_shell, null, false)

        // 分区标签
        content.findViewById<TextView>(R.id.tv_modal_section).text = spec.sectionLabel

        // 标题
        content.findViewById<TextView>(R.id.tv_modal_title).text = spec.title

        // 消息（可选）
        val messageView = content.findViewById<TextView>(R.id.tv_modal_message)
        if (spec.message.isNullOrBlank()) {
            messageView.visibility = View.GONE
        } else {
            messageView.text = spec.message
            messageView.visibility = View.VISIBLE
        }

        // 列表行
        val listContainer = content.findViewById<LinearLayout>(R.id.container_modal_content)
        lateinit var dialog: Dialog
        renderListRows(listContainer, spec.rows) { row ->
            row.onClick?.invoke()
            if (row.dismissOnClick) dialog.dismiss()
        }

        // 隐藏 actions 容器
        content.findViewById<LinearLayout>(R.id.container_modal_actions).visibility = View.GONE

        dialog = Dialog(host, android.R.style.Theme_NoTitleBar_Fullscreen).apply {
            setContentView(content)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(null)
            setOnDismissListener {
                lastFocusedView?.requestFocus()
            }
            show()
            requestListFocus(listContainer, spec.rows)
        }
        return dialog
    }

    /**
     * 更新已打开列表 Modal 的行内容。
     * 用于播放页等场景在弹窗保持打开时即时刷新列表状态。
     */
    fun updateListRows(
        dialog: Dialog,
        rows: List<ModalListRow>,
        focusRowKey: String? = null,
    ) {
        val contentView = dialog.findViewById<View>(android.R.id.content) ?: return
        val listContainer = contentView.findViewById<LinearLayout>(R.id.container_modal_content) ?: return
        renderListRows(listContainer, rows) { row ->
            row.onClick?.invoke()
        }
        requestListFocus(listContainer, rows, focusRowKey)
    }

    /**
     * 展示进度型 Modal。
     *
     * @param spec 进度型 Modal 规格
     * @return [ProgressModalHandle]，包含 Dialog 和进度更新回调
     */
    fun showProgressModal(spec: ProgressModalSpec): ProgressModalHandle {
        lastFocusedView = host.currentFocus

        val content = LayoutInflater.from(host)
            .inflate(R.layout.dialog_tsm_modal_shell, null, false)

        // 分区标签
        content.findViewById<TextView>(R.id.tv_modal_section).text = spec.sectionLabel

        // 标题
        content.findViewById<TextView>(R.id.tv_modal_title).text = spec.title

        // 隐藏消息行
        content.findViewById<TextView>(R.id.tv_modal_message).visibility = View.GONE

        // 进度视图
        val contentContainer = content.findViewById<LinearLayout>(R.id.container_modal_content)
        contentContainer.removeAllViews()

        val progressView = LayoutInflater.from(host)
            .inflate(R.layout.view_tsm_modal_progress, contentContainer, false)
        contentContainer.addView(progressView)

        // 填充初始值
        progressView.findViewById<TextView>(R.id.tv_modal_progress_filename).text = spec.fileName
        val progressBar = progressView.findViewById<ProgressBar>(R.id.pb_modal_progress)
        val speedText = progressView.findViewById<TextView>(R.id.tv_modal_progress_speed)
        val sizeText = progressView.findViewById<TextView>(R.id.tv_modal_progress_size)
        progressView.findViewById<TextView>(R.id.tv_modal_progress_message).text = spec.message

        fun renderProgress(state: DownloadProgressState) {
            progressBar.isIndeterminate = false
            progressBar.progress = DownloadProgressFormatter.progressPermille(state)
            speedText.text = DownloadProgressFormatter.formatSpeed(state)
            sizeText.text = DownloadProgressFormatter.formatBytes(state)
        }
        renderProgress(spec.initialState)

        // 隐藏 actions 容器
        content.findViewById<LinearLayout>(R.id.container_modal_actions).visibility = View.GONE

        val dialog = Dialog(host, android.R.style.Theme_NoTitleBar_Fullscreen).apply {
            setContentView(content)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawable(null)
            setOnDismissListener {
                lastFocusedView?.requestFocus()
            }
            show()
        }

        val onProgress: (DownloadProgressState) -> Unit = { state ->
            renderProgress(state)
        }

        val onDismiss: () -> Unit = {
            dialog.dismiss()
        }

        return ProgressModalHandle(
            dialog = dialog,
            onProgress = onProgress,
            onDismiss = onDismiss,
        )
    }

    /**
     * 更新表单字段的行内错误提示。
     *
     * @param dialog   表单型 Dialog 实例
     * @param fieldKey 字段 key（对应 [FormFieldSpec.key]）
     * @param error    错误文案，null 或空字符串则隐藏
     */
    fun updateFieldError(dialog: Dialog, fieldKey: String, error: String?) {
        val field = dialog.findViewById<View>(android.R.id.content)
            ?.findViewWithTag<View>(fieldKey) ?: return
        val errorView = field.findViewById<TextView>(R.id.tv_modal_field_error)
        errorView.text = error.orEmpty()
        errorView.visibility = if (error.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /**
     * 绑定表单主按钮的提交逻辑。
     *
     * 点击主按钮时，从所有指定字段收集当前值，调用 [onSubmit]。
     * 若 [onSubmit] 返回 false，保持弹窗打开（校验失败）；
     * 返回 true 则正常关闭弹窗。
     *
     * @param dialog    表单型 Dialog 实例
     * @param fieldKeys 需要收集值的字段 key 列表
     * @param onSubmit  提交回调，返回 true 表示成功可关闭
     */
    fun bindFormPrimaryAction(
        dialog: Dialog,
        vararg fieldKeys: String,
        onSubmit: (Map<String, String>) -> Boolean,
    ) {
        val contentView = dialog.findViewById<View>(android.R.id.content) ?: return
        val primaryBtn = contentView.findViewWithTag<View>(TAG_ACTION_PRIMARY) as? Button ?: return

        primaryBtn.setOnClickListener {
            val values = mutableMapOf<String, String>()
            for (key in fieldKeys) {
                val field = contentView.findViewWithTag<View>(key)
                // 优先尝试 EditText（TEXT 字段）
                val input = field?.findViewById<EditText>(R.id.et_modal_field_input)
                if (input != null) {
                    values[key] = input.text.toString()
                    continue
                }
                // 其次尝试 CheckBox（CHECKBOX 字段）——field 本身就是 CheckBox
                val checkBox = field as? CheckBox
                if (checkBox != null) {
                    values[key] = checkBox.isChecked.toString()
                    continue
                }
                // 兼容：field 是 item_tsm_modal_form_field 容器中嵌套的 CheckBox
                if (field is CheckBox) {
                    values[key] = field.isChecked.toString()
                }
            }
            if (onSubmit(values)) {
                dialog.dismiss()
            }
        }
    }

    /**
     * 绑定文本字段与复选框的互斥关系。
     * 当任一文本字段输入了非空内容时，自动取消指定复选框。
     */
    fun bindTextFieldsToClearCheckbox(
        dialog: Dialog,
        checkboxKey: String,
        vararg textFieldKeys: String,
    ) {
        val contentView = dialog.findViewById<View>(android.R.id.content) ?: return
        val checkbox = contentView.findViewWithTag<CheckBox>(checkboxKey) ?: return
        textFieldKeys.forEach { key ->
            val field = contentView.findViewWithTag<View>(key) ?: return@forEach
            val input = field.findViewById<EditText>(R.id.et_modal_field_input) ?: return@forEach
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!s.isNullOrBlank()) {
                        checkbox.isChecked = false
                    }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
    }

    /**
     * 创建 Modal 操作按钮。
     * 使用 [TsmModalActionButton] 样式并设置样式和点击事件。
     */
    private fun createActionButton(action: ModalAction): Button {
        return Button(host, null, 0, R.style.TsmModalActionButton).apply {
            text = action.label
            isEnabled = action.isEnabled
            setOnClickListener { action.onClick?.invoke() }
            setBackgroundResource(
                when {
                    action.isDanger -> R.drawable.bg_button_red
                    action.isPrimary -> R.drawable.bg_button_primary
                    else -> R.drawable.bg_button_dark
                }
            )
            setTextColor(ContextCompat.getColor(host, R.color.ui_text_on_accent))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.marginEnd = host.resources.getDimensionPixelSize(R.dimen.ui_space_md)
            layoutParams = params
        }
    }

    private fun renderListRows(
        container: LinearLayout,
        rows: List<ModalListRow>,
        onRowClick: (ModalListRow) -> Unit,
    ) {
        container.removeAllViews()
        rows.forEach { row ->
            val rowView = LayoutInflater.from(host)
                .inflate(R.layout.item_tsm_modal_list_row, container, false)
            rowView.tag = row.key

            val labelView = rowView.findViewById<TextView>(R.id.tv_modal_row_label)
            labelView.text = row.label
            rowView.isEnabled = row.enabled

            rowView.setOnClickListener {
                if (row.enabled) onRowClick(row)
            }

            container.addView(rowView)
        }
    }

    private fun requestListFocus(
        container: LinearLayout,
        rows: List<ModalListRow>,
        focusRowKey: String? = null,
    ) {
        val preferredIndex = focusRowKey
            ?.let { key -> rows.indexOfFirst { it.key == key && it.enabled } }
            ?.takeIf { it >= 0 }
        val firstEnabled = rows.indexOfFirst { it.enabled }.takeIf { it >= 0 }
        val targetIndex = preferredIndex ?: firstEnabled ?: return
        container.getChildAt(targetIndex)?.requestFocus()
    }

    /**
     * 在表单内容过高时，将中间 ScrollView 切换为占满剩余空间的滚动区，
     * 保证标题区和底部操作区始终留在屏幕可视范围内。
     */
    private fun applyFormPanelLayout(root: View) {
        val viewportHeight = root.height.takeIf { it > 0 } ?: root.measuredHeight
        if (viewportHeight <= 0) return

        val panel = root.findViewById<LinearLayout>(R.id.panel_modal) ?: return
        val scrollView = root.findViewById<ScrollView>(R.id.scroll_modal_content) ?: return
        val panelMeasuredHeight = panel.measuredHeight.takeIf { it > 0 } ?: panel.height
        if (panelMeasuredHeight <= 0) return

        val verticalMargin = host.resources.getDimensionPixelSize(R.dimen.ui_space_screen)
        val layout = computeFormPanelLayout(panelMeasuredHeight, viewportHeight, verticalMargin)

        (panel.layoutParams as? FrameLayout.LayoutParams)?.let { panelParams ->
            panelParams.height = layout.panelHeight
            panel.layoutParams = panelParams
        }

        (scrollView.layoutParams as? LinearLayout.LayoutParams)?.let { scrollParams ->
            scrollParams.height = layout.scrollHeight
            scrollParams.weight = layout.scrollWeight
            scrollView.layoutParams = scrollParams
        }
        scrollView.isFillViewport = layout.shouldConstrainPanel
    }

    companion object {
        internal data class FormPanelLayout(
            val shouldConstrainPanel: Boolean,
            val panelHeight: Int,
            val scrollHeight: Int,
            val scrollWeight: Float,
        )

        private fun computeFormPanelLayout(
            panelMeasuredHeight: Int,
            viewportHeight: Int,
            verticalMargin: Int,
        ): FormPanelLayout {
            val maxPanelHeight = (viewportHeight - verticalMargin * 2).coerceAtLeast(0)
            return if (panelMeasuredHeight > maxPanelHeight && maxPanelHeight > 0) {
                FormPanelLayout(
                    shouldConstrainPanel = true,
                    panelHeight = maxPanelHeight,
                    scrollHeight = 0,
                    scrollWeight = 1f,
                )
            } else {
                FormPanelLayout(
                    shouldConstrainPanel = false,
                    panelHeight = LinearLayout.LayoutParams.WRAP_CONTENT,
                    scrollHeight = LinearLayout.LayoutParams.WRAP_CONTENT,
                    scrollWeight = 0f,
                )
            }
        }

        @JvmStatic
        internal fun computeFormPanelLayoutForTest(
            panelMeasuredHeight: Int,
            viewportHeight: Int,
            verticalMargin: Int,
        ): FormPanelLayout = computeFormPanelLayout(panelMeasuredHeight, viewportHeight, verticalMargin)

        internal const val TAG_ACTION_PRIMARY = "action_primary"
        internal const val TAG_ACTION_SECONDARY = "action_secondary"
        internal const val TAG_ACTION_LEADING = "action_leading"
    }
}
