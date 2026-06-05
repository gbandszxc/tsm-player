package com.github.gbandszxc.tvmediaplayer.ui.modal

import android.annotation.SuppressLint
import android.app.Activity
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.update.DownloadProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TsmModalCoordinator 的公共 modal 基建测试。
 * 验证各类 Modal 的布局填充、焦点管理和交互行为。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TsmModalCoordinatorTest {

    /** 测试用宿主 Activity，避免依赖真实 Activity */
    @SuppressLint("RegisteredActivity")
    class FakeModalHostActivity : Activity()

    // ─── ActionModal 测试 ───

    @Test
    fun `showActionModal inflates shell with title content and actions`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showActionModal(
            ActionModalSpec(
                sectionLabel = "测试",
                title = "连接管理",
                message = "请选择操作",
                actions = listOf(
                    ModalAction("编辑当前连接"),
                    ModalAction("新建连接"),
                ),
            )
        )

        assertTrue("Dialog should be showing", dialog.isShowing)

        val titleView = dialog.findViewById<TextView>(R.id.tv_modal_title)
        assertNotNull("Title view should exist", titleView)
        assertEquals("连接管理", titleView?.text.toString())

        val messageView = dialog.findViewById<TextView>(R.id.tv_modal_message)
        assertNotNull("Message view should exist", messageView)
        assertEquals("请选择操作", messageView?.text.toString())

        val container = dialog.findViewById<LinearLayout>(R.id.container_modal_content)
        assertNotNull("Content container should exist", container)
        assertEquals("Should have 2 action buttons", 2, container?.childCount)
    }

    @Test
    fun `showActionModal hides message when null`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showActionModal(
            ActionModalSpec(
                sectionLabel = "测试",
                title = "确认删除",
                message = null,
                actions = listOf(
                    ModalAction("删除", isDanger = true),
                ),
            )
        )

        val messageView = dialog.findViewById<TextView>(R.id.tv_modal_message)
        assertNotNull(messageView)
        assertEquals(
            "Message should be GONE when null",
            View.GONE,
            messageView?.visibility,
        )
    }

    @Test
    fun `showActionModal sets section label`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showActionModal(
            ActionModalSpec(
                sectionLabel = "文件操作",
                title = "确认",
                actions = listOf(ModalAction("确定")),
            )
        )

        val sectionView = dialog.findViewById<TextView>(R.id.tv_modal_section)
        assertNotNull(sectionView)
        assertEquals("文件操作", sectionView?.text.toString())
    }

    @Test
    fun `showActionModal dismisses after action click so stale manager does not remain`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)
        var clicked = false

        val dialog = coordinator.showActionModal(
            ActionModalSpec(
                sectionLabel = "SMB",
                title = "连接管理",
                actions = listOf(
                    ModalAction("新建连接") { clicked = true },
                ),
            )
        )

        val container = dialog.findViewById<LinearLayout>(R.id.container_modal_content)
        container.getChildAt(0).performClick()

        assertTrue(clicked)
        assertFalse("Action modal should close after an action is accepted", dialog.isShowing)
    }

    // ─── FormModal 测试 ───

    @Test
    fun `showFormModal inflates fields and actions`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "收藏",
                title = "新建播放列表",
                fields = listOf(
                    FormFieldSpec(
                        key = "playlist_name",
                        label = "名称",
                        initialValue = "",
                        hint = "播放列表名称",
                        inputType = InputType.TYPE_CLASS_TEXT,
                    ),
                ),
                primaryAction = ModalAction("创建", isPrimary = true),
                secondaryAction = ModalAction("取消"),
            )
        )

        assertTrue("Dialog should be showing", dialog.isShowing)

        // 验证标题
        val titleView = dialog.findViewById<TextView>(R.id.tv_modal_title)
        assertEquals("新建播放列表", titleView?.text.toString())

        // 验证字段存在
        val fieldContainer = dialog.findViewById<LinearLayout>(R.id.container_modal_content)
        assertNotNull(fieldContainer)
        assertEquals("Should have 1 field", 1, fieldContainer?.childCount)

        // 验证字段内容
        val fieldView = fieldContainer!!.getChildAt(0)
        assertEquals("playlist_name", fieldView.tag)
        val labelView = fieldView.findViewById<TextView>(R.id.tv_modal_field_label)
        assertEquals("名称", labelView.text.toString())
        val inputView = fieldView.findViewById<EditText>(R.id.et_modal_field_input)
        assertEquals("", inputView.text.toString())

        // 验证 actions 容器可见
        val actionsContainer = dialog.findViewById<LinearLayout>(R.id.container_modal_actions)
        assertNotNull(actionsContainer)
        assertEquals(View.VISIBLE, actionsContainer?.visibility)
        // 主按钮 + 取消按钮
        assertEquals("Should have 2 action buttons", 2, actionsContainer?.childCount)
    }

    @Test
    fun `computeFormPanelLayout constrains tall content into scroll area`() {
        val result = TsmModalCoordinator.computeFormPanelLayoutForTest(
            panelMeasuredHeight = 980,
            viewportHeight = 720,
            verticalMargin = 28,
        )

        assertTrue("Tall form panel should be constrained", result.shouldConstrainPanel)
        assertEquals("Panel height should keep top and bottom safe margins", 664, result.panelHeight)
        assertEquals("Scroll area should switch to weighted fill mode", 1f, result.scrollWeight)
    }

    @Test
    fun `computeFormPanelLayout keeps wrap content when content already fits`() {
        val result = TsmModalCoordinator.computeFormPanelLayoutForTest(
            panelMeasuredHeight = 520,
            viewportHeight = 720,
            verticalMargin = 28,
        )

        assertFalse("Fitting form panel should keep natural height", result.shouldConstrainPanel)
        assertEquals("Unconstrained panel should stay wrap content", LinearLayout.LayoutParams.WRAP_CONTENT, result.panelHeight)
        assertEquals("Scroll area should not consume extra space", 0f, result.scrollWeight)
    }

    @Test
    @Config(sdk = [28], qualifiers = "w640dp-h360dp-xhdpi")
    fun `showFormModal keeps footer visible on 720p tv layout`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "SMB",
                title = "SMB 连接配置",
                fields = listOf(
                    FormFieldSpec("name", "连接名称", "", "可留空", InputType.TYPE_CLASS_TEXT),
                    FormFieldSpec("host", "SMB 服务器", "", "", InputType.TYPE_CLASS_TEXT),
                    FormFieldSpec("share", "共享名", "", "可留空", InputType.TYPE_CLASS_TEXT),
                    FormFieldSpec("path", "子路径", "", "可留空", InputType.TYPE_CLASS_TEXT),
                    FormFieldSpec("username", "用户名", "", "可留空", InputType.TYPE_CLASS_TEXT),
                    FormFieldSpec("password", "密码", "", "可留空", InputType.TYPE_CLASS_TEXT),
                    FormFieldSpec("guest", "访客 / 匿名", "false", "", 0, FormFieldSpecType.CHECKBOX),
                    FormFieldSpec("smb1", "启用 SMB1 兼容（默认关闭）", "false", "", 0, FormFieldSpecType.CHECKBOX),
                    FormFieldSpec("saveAsNew", "另存为新连接", "true", "", 0, FormFieldSpecType.CHECKBOX),
                ),
                primaryAction = ModalAction("保存并连接", isPrimary = true),
                secondaryAction = ModalAction("取消"),
            )
        )

        val contentView = dialog.findViewById<View>(android.R.id.content)
        val width = activity.resources.displayMetrics.widthPixels
        val height = activity.resources.displayMetrics.heightPixels
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        contentView.layout(0, 0, width, height)

        val scrollView = dialog.findViewById<ScrollView>(R.id.scroll_modal_content)
        val actionsContainer = dialog.findViewById<LinearLayout>(R.id.container_modal_actions)

        assertNotNull(scrollView)
        assertNotNull(actionsContainer)
        assertTrue(
            "Footer actions should stay visible within the dialog viewport",
            actionsContainer!!.bottom <= contentView.height,
        )
    }

    @Test
    fun `text input can auto clear checked guest checkbox when credentials are entered`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "SMB",
                title = "SMB 连接配置",
                fields = listOf(
                    FormFieldSpec("username", "用户名", "", "可留空", InputType.TYPE_CLASS_TEXT),
                    FormFieldSpec("password", "密码", "", "可留空", InputType.TYPE_CLASS_TEXT),
                    FormFieldSpec("guest", "访客 / 匿名", "true", "", 0, FormFieldSpecType.CHECKBOX),
                ),
                primaryAction = ModalAction("保存并连接", isPrimary = true),
            )
        )
        coordinator.bindTextFieldsToClearCheckbox(dialog, "guest", "username", "password")

        val contentView = dialog.findViewById<View>(android.R.id.content)
        val guest = contentView.findViewWithTag<CheckBox>("guest")
        assertTrue(guest.isChecked)

        contentView.findViewWithTag<View>("username")
            .findViewById<EditText>(R.id.et_modal_field_input)
            .setText("alice")

        assertFalse("Entering a username should switch off guest access", guest.isChecked)
    }

    @Test
    fun `updateFieldError toggles inline error text`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "收藏",
                title = "新建播放列表",
                fields = listOf(
                    FormFieldSpec(
                        key = "playlist_name",
                        label = "名称",
                        initialValue = "",
                        hint = "播放列表名称",
                        inputType = InputType.TYPE_CLASS_TEXT,
                    ),
                ),
                primaryAction = ModalAction("创建", isPrimary = true),
            )
        )

        // 设置错误
        coordinator.updateFieldError(dialog, "playlist_name", "播放列表已存在")

        val errorView = dialog.findViewById<View>(android.R.id.content)
            .findViewWithTag<View>("playlist_name")
            .findViewById<TextView>(R.id.tv_modal_field_error)
        assertEquals("播放列表已存在", errorView.text.toString())
        assertEquals(View.VISIBLE, errorView.visibility)

        // 清除错误
        coordinator.updateFieldError(dialog, "playlist_name", null)
        assertEquals(View.GONE, errorView.visibility)
    }

    @Test
    fun `updateFieldError with blank string hides error`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "测试",
                title = "测试",
                fields = listOf(
                    FormFieldSpec(
                        key = "field_a",
                        label = "A",
                        initialValue = "",
                        hint = "",
                        inputType = InputType.TYPE_CLASS_TEXT,
                    ),
                ),
                primaryAction = ModalAction("OK", isPrimary = true),
            )
        )

        coordinator.updateFieldError(dialog, "field_a", "错误信息")
        val errorView = dialog.findViewById<View>(android.R.id.content)
            .findViewWithTag<View>("field_a")
            .findViewById<TextView>(R.id.tv_modal_field_error)
        assertEquals(View.VISIBLE, errorView.visibility)

        coordinator.updateFieldError(dialog, "field_a", "")
        assertEquals(View.GONE, errorView.visibility)
    }

    // ─── ConfirmModal 测试 ───

    @Test
    fun `showConfirmModal shows message and confirm button`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = "文件操作",
                title = "确认删除",
                message = "删除后不可恢复",
                confirmAction = ModalAction("删除", isDanger = true),
                cancelAction = ModalAction("取消"),
            )
        )

        assertTrue("Dialog should be showing", dialog.isShowing)

        val titleView = dialog.findViewById<TextView>(R.id.tv_modal_title)
        assertEquals("确认删除", titleView?.text.toString())

        val messageView = dialog.findViewById<TextView>(R.id.tv_modal_message)
        assertEquals(View.VISIBLE, messageView?.visibility)
        assertEquals("删除后不可恢复", messageView?.text.toString())

        val actionsContainer = dialog.findViewById<LinearLayout>(R.id.container_modal_actions)
        assertEquals(View.VISIBLE, actionsContainer?.visibility)
        assertEquals(2, actionsContainer?.childCount)
    }

    // ─── ListModal 测试 ───

    @Test
    fun `showListModal inflates rows`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showListModal(
            ListModalSpec(
                sectionLabel = "连接",
                title = "选择连接",
                rows = listOf(
                    ModalListRow(key = "conn_1", label = "客厅 NAS"),
                    ModalListRow(key = "conn_2", label = "卧室 NAS"),
                ),
            )
        )

        assertTrue("Dialog should be showing", dialog.isShowing)

        val container = dialog.findViewById<LinearLayout>(R.id.container_modal_content)
        assertEquals(2, container?.childCount)

        // 验证行内容
        val firstRow = container!!.getChildAt(0)
        assertEquals("conn_1", firstRow.tag)
        val firstLabel = firstRow.findViewById<TextView>(R.id.tv_modal_row_label)
        assertEquals("客厅 NAS", firstLabel.text.toString())
    }

    @Test
    fun `showListModal dismisses after enabled row click so switch feedback is immediate`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)
        var selectedKey: String? = null

        val dialog = coordinator.showListModal(
            ListModalSpec(
                sectionLabel = "SMB",
                title = "切换连接",
                rows = listOf(
                    ModalListRow(key = "conn_1", label = "客厅 NAS", dismissOnClick = true) { selectedKey = "conn_1" },
                ),
            )
        )

        val container = dialog.findViewById<LinearLayout>(R.id.container_modal_content)
        container.getChildAt(0).performClick()

        assertEquals("conn_1", selectedKey)
        assertFalse("List modal should close after selecting a row", dialog.isShowing)
    }

    @Test
    fun `modal list row keeps vertical gap between adjacent choices`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val parent = LinearLayout(activity)
        val row = LayoutInflater.from(activity)
            .inflate(R.layout.item_tsm_modal_list_row, parent, false)

        val params = row.layoutParams as LinearLayout.LayoutParams
        val expectedMargin = activity.resources.getDimensionPixelSize(R.dimen.ui_space_sm)

        assertEquals("List rows should keep a bottom gap for TV readability", expectedMargin, params.bottomMargin)
    }

    @Test
    fun `modal action row and list row use the same vertical rhythm`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val parent = LinearLayout(activity)
        val actionRow = LayoutInflater.from(activity)
            .inflate(R.layout.item_tsm_modal_action_row, parent, false)
        val listRow = LayoutInflater.from(activity)
            .inflate(R.layout.item_tsm_modal_list_row, parent, false)

        val actionParams = actionRow.layoutParams as LinearLayout.LayoutParams
        val listParams = listRow.layoutParams as LinearLayout.LayoutParams

        assertEquals(
            "Action and list rows should keep the same TV list spacing",
            actionParams.bottomMargin,
            listParams.bottomMargin,
        )
    }

    @Test
    fun `updateListRows replaces visible choices in opened list modal`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showListModal(
            ListModalSpec(
                sectionLabel = "收藏",
                title = "选择播放列表",
                rows = listOf(
                    ModalListRow(key = "default", label = "收藏夹"),
                    ModalListRow(key = "create_new", label = "+ 新建播放列表"),
                ),
            )
        )

        coordinator.updateListRows(
            dialog = dialog,
            rows = listOf(
                ModalListRow(key = "default", label = "收藏夹（已在该播放列表中）", enabled = false),
                ModalListRow(key = "new_playlist", label = "新列表（已在该播放列表中）", enabled = false),
                ModalListRow(key = "create_new", label = "+ 新建播放列表"),
            ),
            focusRowKey = "create_new",
        )

        val container = dialog.findViewById<LinearLayout>(R.id.container_modal_content)
        assertEquals(3, container?.childCount)
        assertEquals("新列表（已在该播放列表中）", container?.getChildAt(1)?.findViewById<TextView>(R.id.tv_modal_row_label)?.text?.toString())
        assertTrue(container?.getChildAt(2)?.isFocused == true)
    }

    // ─── ProgressModal 测试 ───

    @Test
    fun `progress modal handle updates visible progress`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val handle = coordinator.showProgressModal(
            ProgressModalSpec(
                sectionLabel = "更新",
                title = "正在下载更新",
                fileName = "demo.apk",
                initialState = DownloadProgressState(
                    downloadedBytes = 0L,
                    totalBytes = 100L * 1024L * 1024L,
                    speedBytesPerSecond = 0L,
                ),
                message = "请稍候，下载完成后将进入安装流程。",
            )
        )

        assertTrue("Dialog should be showing", handle.dialog.isShowing)

        // 验证初始内容
        val contentView = handle.dialog.findViewById<View>(android.R.id.content)
        val filename = contentView.findViewById<TextView>(R.id.tv_modal_progress_filename)
        assertEquals("demo.apk", filename.text.toString())
        val message = contentView.findViewById<TextView>(R.id.tv_modal_progress_message)
        assertEquals("请稍候，下载完成后将进入安装流程。", message.text.toString())

        val progressBar = contentView.findViewById<ProgressBar>(R.id.pb_modal_progress)
        assertFalse(progressBar.isIndeterminate)
        assertEquals(0, progressBar.progress)

        // 更新进度
        handle.onProgress(
            DownloadProgressState(
                downloadedBytes = 68L * 1024L * 1024L,
                totalBytes = 100L * 1024L * 1024L,
                speedBytesPerSecond = 2L * 1024L * 1024L,
            )
        )
        assertEquals(680, progressBar.progress)

        val speedText = contentView.findViewById<TextView>(R.id.tv_modal_progress_speed)
        assertEquals("2.0 MB/s", speedText.text.toString())
        val sizeText = contentView.findViewById<TextView>(R.id.tv_modal_progress_size)
        assertEquals("68.0 / 100.0 MB", sizeText.text.toString())

        // 关闭
        handle.onDismiss()
        assertFalse("Dialog should be dismissed", handle.dialog.isShowing)
    }

    @Test
    fun `progress modal unknown total keeps stable determinate bar`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val handle = coordinator.showProgressModal(
            ProgressModalSpec(
                sectionLabel = "更新",
                title = "正在处理",
                fileName = "data.bin",
                initialState = DownloadProgressState(
                    downloadedBytes = 1024L * 1024L,
                    totalBytes = -1L,
                    speedBytesPerSecond = 0L,
                ),
                message = "处理中",
            )
        )

        val contentView = handle.dialog.findViewById<View>(android.R.id.content)
        val progressBar = contentView.findViewById<ProgressBar>(R.id.pb_modal_progress)
        assertFalse(progressBar.isIndeterminate)
        assertEquals(0, progressBar.progress)

        val speedText = contentView.findViewById<TextView>(R.id.tv_modal_progress_speed)
        assertEquals("-- MB/s", speedText.text.toString())
        val sizeText = contentView.findViewById<TextView>(R.id.tv_modal_progress_size)
        assertEquals("已下载 1.0 MB", sizeText.text.toString())
    }

    // ─── bindFormPrimaryAction 测试 ───

    @Test
    fun `bindFormPrimaryAction collects values and dismisses on success`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        var submittedValues: Map<String, String>? = null

        val dialog = coordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "测试",
                title = "新建",
                fields = listOf(
                    FormFieldSpec(
                        key = "name",
                        label = "名称",
                        initialValue = "我的列表",
                        hint = "",
                        inputType = InputType.TYPE_CLASS_TEXT,
                    ),
                ),
                primaryAction = ModalAction("创建", isPrimary = true),
            )
        )

        coordinator.bindFormPrimaryAction(dialog, "name") { values ->
            submittedValues = values
            true // 返回 true 表示校验通过，关闭弹窗
        }

        // 模拟点击主按钮
        val contentView = dialog.findViewById<View>(android.R.id.content)
        val primaryBtn = contentView.findViewWithTag<Button>("action_primary")
        primaryBtn.performClick()

        // 验证收集的值
        assertNotNull(submittedValues)
        assertEquals("我的列表", submittedValues!!["name"])

        // 弹窗应已关闭
        assertFalse("Dialog should be dismissed after successful submit", dialog.isShowing)
    }

    @Test
    fun `bindFormPrimaryAction keeps dialog open on validation failure`() {
        val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java)
            .setup()
            .get()
        val coordinator = TsmModalCoordinator(activity)

        val dialog = coordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "测试",
                title = "新建",
                fields = listOf(
                    FormFieldSpec(
                        key = "name",
                        label = "名称",
                        initialValue = "",
                        hint = "",
                        inputType = InputType.TYPE_CLASS_TEXT,
                    ),
                ),
                primaryAction = ModalAction("创建", isPrimary = true),
            )
        )

        coordinator.bindFormPrimaryAction(dialog, "name") { values ->
            // 返回 false 表示校验失败，保持弹窗
            if (values["name"]?.isBlank() == true) {
                coordinator.updateFieldError(dialog, "name", "请输入名称")
                false
            } else {
                true
            }
        }

        val contentView = dialog.findViewById<View>(android.R.id.content)
        val primaryBtn = contentView.findViewWithTag<Button>("action_primary")
        primaryBtn.performClick()

        // 弹窗应保持打开
        assertTrue("Dialog should remain open on validation failure", dialog.isShowing)

        // 错误提示应可见
        val errorView = contentView.findViewWithTag<View>("name")
            .findViewById<TextView>(R.id.tv_modal_field_error)
        assertEquals(View.VISIBLE, errorView.visibility)
        assertEquals("请输入名称", errorView.text.toString())
    }
}
