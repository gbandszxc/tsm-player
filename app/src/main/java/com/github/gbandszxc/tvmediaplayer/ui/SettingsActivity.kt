package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.github.gbandszxc.tvmediaplayer.R

/**
 * 应用设置页：左侧分类面板 + 右侧详情面板，优先支持遥控器操作。
 * 焦点流转：左右方向键在两侧面板间自然跳转，上下键在同侧列表中导航，
 * BACK 键关闭设置页。
 */
class SettingsActivity : FragmentActivity() {

    private data class SettingsItem(
        val title: String,
        val description: String = "",
        val action: (() -> Unit)? = null
    )

    private data class SettingsCategory(
        val title: String,
        val items: List<SettingsItem>
    )

    private lateinit var containerCategories: LinearLayout
    private lateinit var containerDetail: LinearLayout
    private var selectedCategoryIndex = 0
    private val categoryViews = mutableListOf<View>()

    private val categories by lazy {
        listOf(
            SettingsCategory(
                "显示设置",
                listOf(
                    SettingsItem("全局缩放", "调整界面文字与按钮的整体缩放比例")
                )
            ),
            SettingsCategory(
                "播放设置",
                listOf(
                    SettingsItem("播放歌词字号", "调整播放页中歌词的显示字号"),
                    SettingsItem("全屏歌词字号", "调整全屏歌词模式下的字号大小")
                )
            ),
            SettingsCategory(
                "应用设置",
                listOf(
                    SettingsItem("休眠设置", "设置无操作后自动休眠的等待时间")
                )
            ),
            SettingsCategory(
                "关于",
                listOf(
                    SettingsItem(
                        title = "项目主页",
                        description = "https://github.com/gbandszxc/tms-player"
                    ) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/gbandszxc/tms-player")
                            )
                        )
                    }
                )
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        containerCategories = findViewById(R.id.container_categories)
        containerDetail = findViewById(R.id.container_detail)

        buildCategoryList()
        selectCategory(0, moveFocusToDetail = false)

        // 初始焦点落在第一个分类项
        containerCategories.post {
            categoryViews.firstOrNull()?.requestFocus()
        }
    }

    private fun buildCategoryList() {
        categories.forEachIndexed { index, category ->
            val itemView = layoutInflater.inflate(
                R.layout.item_settings_category, containerCategories, false
            )
            itemView.findViewById<TextView>(R.id.tv_category_title).text = category.title
            itemView.setOnClickListener { selectCategory(index, moveFocusToDetail = true) }
            containerCategories.addView(itemView)
            categoryViews.add(itemView)
        }
    }

    private fun selectCategory(index: Int, moveFocusToDetail: Boolean) {
        selectedCategoryIndex = index

        // 高亮当前分类
        categoryViews.forEachIndexed { i, view ->
            view.isSelected = (i == index)
        }

        buildDetailPanel(categories[index], moveFocusToDetail)
    }

    private fun buildDetailPanel(category: SettingsCategory, moveFocusToDetail: Boolean) {
        containerDetail.removeAllViews()

        // 分类标题
        val headerView = TextView(this).apply {
            text = category.title
            setTextColor(0xFF60A5FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = AppFonts.bold(this@SettingsActivity)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(20)
            layoutParams = lp
        }
        containerDetail.addView(headerView)

        // 条目列表
        var firstEntryView: View? = null
        category.items.forEachIndexed { i, item ->
            val itemView = layoutInflater.inflate(
                R.layout.item_settings_entry, containerDetail, false
            )
            itemView.findViewById<TextView>(R.id.tv_settings_title).text = item.title
            val tvDesc = itemView.findViewById<TextView>(R.id.tv_settings_desc)
            if (item.description.isNotBlank()) {
                tvDesc.text = item.description
                tvDesc.visibility = View.VISIBLE
            }
            itemView.setOnClickListener {
                if (item.action != null) {
                    item.action.invoke()
                } else {
                    Toast.makeText(this, "「${item.title}」功能开发中", Toast.LENGTH_SHORT).show()
                }
            }
            containerDetail.addView(itemView)
            if (i == 0) firstEntryView = itemView
        }

        // 切换分类后，焦点移至右侧第一个条目
        if (moveFocusToDetail) {
            val target = firstEntryView
            containerDetail.post { target?.requestFocus() }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
