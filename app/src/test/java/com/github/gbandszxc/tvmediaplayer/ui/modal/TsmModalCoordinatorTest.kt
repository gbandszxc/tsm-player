package com.github.gbandszxc.tvmediaplayer.ui.modal

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.github.gbandszxc.tvmediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TsmModalCoordinator 的公共 modal 基建测试。
 * 验证 showActionModal 能正确填充 shell 布局中的标题、消息和操作按钮。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TsmModalCoordinatorTest {

    /** 测试用宿主 Activity，避免依赖真实 Activity */
    @SuppressLint("RegisteredActivity")
    class FakeModalHostActivity : Activity()

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
}
