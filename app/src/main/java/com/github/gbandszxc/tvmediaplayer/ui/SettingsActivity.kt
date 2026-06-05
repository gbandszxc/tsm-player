package com.github.gbandszxc.tvmediaplayer.ui

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.github.gbandszxc.tvmediaplayer.BuildConfig
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.backup.BackupOperationStatus
import com.github.gbandszxc.tvmediaplayer.backup.SqliteBackupManager
import com.github.gbandszxc.tvmediaplayer.backup.WebDavBackupClient
import com.github.gbandszxc.tvmediaplayer.backup.WebDavConfig
import com.github.gbandszxc.tvmediaplayer.backup.WebDavConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStore
import com.github.gbandszxc.tvmediaplayer.favorites.FavoritesDbHelper
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackArtworkCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLyricsCache
import com.github.gbandszxc.tvmediaplayer.sleep.SleepDeviceController
import com.github.gbandszxc.tvmediaplayer.ui.modal.ConfirmModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormFieldSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormFieldSpecType
import com.github.gbandszxc.tvmediaplayer.ui.modal.FormModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalAction
import com.github.gbandszxc.tvmediaplayer.ui.modal.ProgressModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinator
import com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalFormValidators
import com.github.gbandszxc.tvmediaplayer.update.DownloadProgressState
import com.github.gbandszxc.tvmediaplayer.update.AppUpdateManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : BaseActivity() {

    private data class SettingsItem(
        val title: String,
        val isGroupHeader: Boolean = false,
        val descriptionProvider: () -> String = { "" },
        val valueProvider: (() -> String)? = null,
        val iconResId: Int? = null,
        val action: (() -> Unit)? = null
    )

    private data class SettingsCategory(
        val title: String,
        val itemsProvider: () -> List<SettingsItem>
    )

    private lateinit var containerCategories: LinearLayout
    private lateinit var containerDetail: LinearLayout
    private var selectedCategoryIndex = 0
    private val categoryViews = mutableListOf<View>()
    private val modalCoordinator by lazy { TsmModalCoordinator(this) }
    private var pendingImportUri: Uri? = null

    private val categories by lazy {
        listOf(
            SettingsCategory(
                title = "显示设置",
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = "全局缩放",
                            descriptionProvider = { "预设档位：90% / 95% / 100% / 105% / 110%" },
                            valueProvider = { "${UiSettingsStore.globalScalePercent(this)}%" }
                        ) {
                            val next = UiSettingsStore.cycleGlobalScalePreset(this)
                            UiSettingsApplier.applyGlobalScale(this)
                            Toast.makeText(this, "全局缩放已切换到 ${next}%", Toast.LENGTH_SHORT).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = "播放设置",
                itemsProvider = {
                    listOf(
                        SettingsItem(title = "歌词", isGroupHeader = true),
                        SettingsItem(
                            title = "播放页歌词字号",
                            descriptionProvider = { "默认值 ${UiSettingsStore.defaultPlaybackLyricsFontSp}sp，可手动输入" },
                            valueProvider = { "${UiSettingsStore.playbackLyricsFontSp(this)}sp" }
                        ) {
                            showLyricsFontDialog(
                                title = "设置播放页歌词字号",
                                currentValue = UiSettingsStore.playbackLyricsFontSp(this),
                                defaultValue = UiSettingsStore.defaultPlaybackLyricsFontSp,
                                onSave = { value -> UiSettingsStore.setPlaybackLyricsFontSp(this, value) }
                            )
                        },
                        SettingsItem(
                            title = "全屏歌词字号",
                            descriptionProvider = { "默认值 ${UiSettingsStore.defaultFullscreenLyricsFontSp}sp，可手动输入" },
                            valueProvider = { "${UiSettingsStore.fullscreenLyricsFontSp(this)}sp" }
                        ) {
                            showLyricsFontDialog(
                                title = "设置全屏歌词字号",
                                currentValue = UiSettingsStore.fullscreenLyricsFontSp(this),
                                defaultValue = UiSettingsStore.defaultFullscreenLyricsFontSp,
                                onSave = { value -> UiSettingsStore.setFullscreenLyricsFontSp(this, value) }
                            )
                        },
                        SettingsItem(
                            title = "播放页歌词间距",
                            descriptionProvider = { "默认值 ${"%.1f".format(UiSettingsStore.defaultPlaybackLyricsLineSpacing)}x，范围 1.0 - 3.0" },
                            valueProvider = { "${"%.1f".format(UiSettingsStore.playbackLyricsLineSpacing(this))}x" }
                        ) {
                            showLyricsSpacingDialog(
                                title = "设置播放页歌词间距",
                                currentValue = UiSettingsStore.playbackLyricsLineSpacing(this),
                                defaultValue = UiSettingsStore.defaultPlaybackLyricsLineSpacing,
                                onSave = { value -> UiSettingsStore.setPlaybackLyricsLineSpacing(this, value) }
                            )
                        },
                        SettingsItem(
                            title = "全屏歌词间距",
                            descriptionProvider = { "默认值 ${"%.1f".format(UiSettingsStore.defaultFullscreenLyricsLineSpacing)}x，范围 1.0 - 3.0" },
                            valueProvider = { "${"%.1f".format(UiSettingsStore.fullscreenLyricsLineSpacing(this))}x" }
                        ) {
                            showLyricsSpacingDialog(
                                title = "设置全屏歌词间距",
                                currentValue = UiSettingsStore.fullscreenLyricsLineSpacing(this),
                                defaultValue = UiSettingsStore.defaultFullscreenLyricsLineSpacing,
                                onSave = { value -> UiSettingsStore.setFullscreenLyricsLineSpacing(this, value) }
                            )
                        },
                        SettingsItem(title = "其它", isGroupHeader = true),
                        SettingsItem(
                            title = "记忆上次播放",
                            descriptionProvider = { "记录上次播放的曲目与进度，下次打开可一键继续" },
                            valueProvider = {
                                if (UiSettingsStore.rememberLastPlayback(this)) "已开启" else "已关闭"
                            }
                        ) {
                            val next = !UiSettingsStore.rememberLastPlayback(this)
                            UiSettingsStore.setRememberLastPlayback(this, next)
                            if (!next) LastPlaybackStore.clear(this)
                            Toast.makeText(
                                this,
                                if (next) "已开启记忆上次播放" else "已关闭记忆上次播放（已清除记录）",
                                Toast.LENGTH_SHORT
                            ).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = "应用设置",
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = "应用休眠保护",
                            descriptionProvider = { "开启后应用运行时保持常亮，不触发休眠/息屏" },
                            valueProvider = {
                                if (UiSettingsStore.keepScreenAwake(this)) "已开启" else "已关闭"
                            }
                        ) {
                            val next = !UiSettingsStore.keepScreenAwake(this)
                            UiSettingsStore.setKeepScreenAwake(this, next)
                            UiSettingsApplier.applyKeepScreenAwake(this)
                            Toast.makeText(
                                this,
                                if (next) "已开启休眠保护" else "已关闭休眠保护",
                                Toast.LENGTH_SHORT
                            ).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        },
                        SettingsItem(
                            title = "睡眠权限",
                            descriptionProvider = { "用于睡眠定时结束时让电视进入睡眠或息屏；未授权时仍会停播并退出应用" },
                            valueProvider = {
                                if (SleepDeviceController(this).isDeviceAdminActive()) "已授权" else "未授权"
                            }
                        ) {
                            SleepDeviceController(this).openDeviceAdminSettings(this)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = "其它设置",
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = "清理缓存",
                            descriptionProvider = {
                                "歌词与封面缓存提升播放页体验；浏览锚点缓存用于目录焦点恢复"
                            },
                            valueProvider = {
                                val total = PlaybackLyricsCache.diskCacheSize(this) +
                                        PlaybackArtworkCache.diskCacheSize(this)
                                val kb = total / 1024
                                if (kb < 1024) "${kb} KB" else "${"%.1f".format(kb / 1024.0)} MB"
                            }
                        ) {
                            PlaybackLyricsCache.clearDisk(this)
                            PlaybackArtworkCache.clearDisk(this)
                            Toast.makeText(
                                this,
                                "缓存已清除（包含歌词、封面与浏览锚点）",
                                Toast.LENGTH_SHORT
                            ).show()
                            lifecycleScope.launch {
                                runCatching {
                                    SmbConfigStore(applicationContext).clearBrowseCache()
                                }
                            }
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = "备份恢复",
                itemsProvider = {
                    val webDavConfig = WebDavConfigStore(this).load()
                    listOf(
                        SettingsItem(
                            title = "导出本地备份",
                            descriptionProvider = { "将当前应用持久化数据库全量导出到你选择的位置" },
                            valueProvider = { "选择保存位置" },
                            action = { launchExportBackupPicker() }
                        ),
                        SettingsItem(
                            title = "导入本地备份",
                            descriptionProvider = { "从你选择的备份文件恢复应用持久化数据库，会覆盖当前配置与收藏数据" },
                            valueProvider = { "选择备份文件" },
                            action = { launchImportBackupPicker() }
                        ),
                        SettingsItem(title = "WebDAV", isGroupHeader = true),
                        SettingsItem(
                            title = "WebDAV 配置",
                            descriptionProvider = { "配置 WebDAV 目录 URL、用户名和密码；仅用于手动上传/下载" },
                            valueProvider = { if (webDavConfig.isReady()) "已配置" else "未配置" },
                            action = { showWebDavConfigDialog() }
                        ),
                        SettingsItem(
                            title = "上传到 WebDAV",
                            descriptionProvider = { "先导出当前应用数据库，再全量上传到配置的 WebDAV URL" },
                            valueProvider = { if (webDavConfig.isReady()) webDavConfig.url else "请先配置" },
                            action = { uploadWebDavBackup() }
                        ),
                        SettingsItem(
                            title = "从 WebDAV 下载并恢复",
                            descriptionProvider = { "从 WebDAV 下载备份并覆盖当前应用数据库，不会自动同步" },
                            valueProvider = { if (webDavConfig.isReady()) webDavConfig.url else "请先配置" },
                            action = { confirmDownloadWebDavBackup() }
                        )
                    )
                }
            ),
            SettingsCategory(
                title = "关于",
                itemsProvider = {
                    buildList {
                        add(
                            SettingsItem(
                                title = "项目描述",
                                descriptionProvider = {
                                    "一款适配安卓TV，基于遥控器操作的本地SMB网络音乐播放器。"
                                },
                                valueProvider = { "v${BuildConfig.VERSION_NAME} / ${AppUpdateManager.currentAbi()}" }
                            )
                        )
                        add(
                            SettingsItem(
                                title = "检查更新",
                                descriptionProvider = { "从 GitHub Release 查找适用于当前设备架构的新版本安装包" },
                                action = {
                                    AppUpdateManager.checkAndPrompt(this@SettingsActivity, silentWhenNoUpdate = false)
                                }
                            )
                        )
                        if (BuildConfig.DEBUG) {
                            add(
                                SettingsItem(
                                    title = "预览更新下载样式",
                                    descriptionProvider = { "仅 Debug 包显示；不访问 GitHub，不安装 APK" },
                                    action = {
                                        AppUpdateManager.previewDownloadProgress(this@SettingsActivity)
                                    }
                                )
                            )
                        }
                        add(
                            SettingsItem(
                                title = "GitHub",
                                descriptionProvider = { "项目主页" },
                                iconResId = R.drawable.ic_github,
                                action = {
                                    startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/gbandszxc/tsm-player")
                                        )
                                    )
                                }
                            )
                        )
                    }
                }
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        UiSettingsApplier.applyAll(this)

        containerCategories = findViewById(R.id.container_categories)
        containerDetail = findViewById(R.id.container_detail)

        buildCategoryList()
        selectCategory(0, moveFocusToDetail = false)

        containerCategories.post {
            categoryViews.firstOrNull()?.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyAll(this)
        rebuildCurrentCategory(moveFocusToDetail = false)
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
        categoryViews.forEachIndexed { i, view ->
            view.isSelected = (i == index)
        }
        buildDetailPanel(categories[index], moveFocusToDetail)
    }

    private fun rebuildCurrentCategory(moveFocusToDetail: Boolean) {
        if (categories.isEmpty()) return
        buildDetailPanel(categories[selectedCategoryIndex], moveFocusToDetail)
    }

    private fun buildDetailPanel(category: SettingsCategory, moveFocusToDetail: Boolean) {
        val items = category.itemsProvider.invoke()
        containerDetail.removeAllViews()

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

        var firstEntryView: View? = null
        items.forEachIndexed { i, item ->
            if (item.isGroupHeader) {
                val groupHeaderView = TextView(this).apply {
                    text = item.title
                    setTextColor(0xFF94A3B8.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = AppFonts.medium(this@SettingsActivity)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = dp(if (i == 0) 0 else 16)
                    lp.bottomMargin = dp(4)
                    layoutParams = lp
                }
                containerDetail.addView(groupHeaderView)
                return@forEachIndexed
            }

            val itemView = layoutInflater.inflate(
                R.layout.item_settings_entry, containerDetail, false
            )
            itemView.findViewById<TextView>(R.id.tv_settings_title).text = item.title
            val tvDesc = itemView.findViewById<TextView>(R.id.tv_settings_desc)
            val desc = item.descriptionProvider.invoke()
            if (desc.isNotBlank()) {
                tvDesc.text = desc
                tvDesc.visibility = View.VISIBLE
            } else {
                tvDesc.visibility = View.GONE
            }

            val tvValue = itemView.findViewById<TextView>(R.id.tv_settings_value)
            val valueText = item.valueProvider?.invoke().orEmpty()
            if (valueText.isNotBlank()) {
                tvValue.text = valueText
                tvValue.visibility = View.VISIBLE
            } else {
                tvValue.visibility = View.GONE
            }

            val ivIcon = itemView.findViewById<ImageView>(R.id.iv_settings_action_icon)
            if (item.iconResId != null) {
                ivIcon.setImageResource(item.iconResId)
                ivIcon.visibility = View.VISIBLE
            } else {
                ivIcon.visibility = View.GONE
            }

            itemView.setOnClickListener {
                if (item.action != null) {
                    item.action.invoke()
                } else {
                    Toast.makeText(this, "该项仅用于展示", Toast.LENGTH_SHORT).show()
                }
            }
            containerDetail.addView(itemView)
            if (firstEntryView == null) firstEntryView = itemView
        }

        if (moveFocusToDetail) {
            val target = firstEntryView
            containerDetail.post { target?.requestFocus() }
        }
    }

    private fun showLyricsFontDialog(
        title: String,
        currentValue: Int,
        defaultValue: Int,
        onSave: (Int) -> Unit
    ) {
        val dialog = modalCoordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "显示设置",
                title = title,
                fields = listOf(
                    FormFieldSpec(
                        key = "value",
                        label = "字号 (sp)",
                        initialValue = currentValue.toString(),
                        hint = "${UiSettingsStore.minLyricsFontSp}-${UiSettingsStore.maxLyricsFontSp}",
                        inputType = InputType.TYPE_CLASS_NUMBER,
                    )
                ),
                primaryAction = ModalAction("保存", isPrimary = true),
                secondaryAction = ModalAction("恢复默认($defaultValue)") {
                    onSave(defaultValue)
                    Toast.makeText(this, "已恢复默认字号 ${defaultValue}sp", Toast.LENGTH_SHORT).show()
                    rebuildCurrentCategory(moveFocusToDetail = false)
                },
            )
        )
        modalCoordinator.bindFormPrimaryAction(dialog, "value") { values ->
            val parsed = values["value"]?.trim()?.toIntOrNull()
            val error = TsmModalFormValidators.validateLyricsFont(
                parsed, UiSettingsStore.minLyricsFontSp, UiSettingsStore.maxLyricsFontSp
            )
            if (error != null) {
                modalCoordinator.updateFieldError(dialog, "value", error)
                return@bindFormPrimaryAction false
            }
            onSave(parsed!!)
            Toast.makeText(this, "字号已设置为 ${parsed}sp", Toast.LENGTH_SHORT).show()
            rebuildCurrentCategory(moveFocusToDetail = false)
            true
        }
    }

    private fun showLyricsSpacingDialog(
        title: String,
        currentValue: Float,
        defaultValue: Float,
        onSave: (Float) -> Unit
    ) {
        val dialog = modalCoordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "显示设置",
                title = title,
                fields = listOf(
                    FormFieldSpec(
                        key = "value",
                        label = "间距 (x)",
                        initialValue = "%.1f".format(currentValue),
                        hint = "${"%.1f".format(UiSettingsStore.minLyricsLineSpacing)} - ${"%.1f".format(UiSettingsStore.maxLyricsLineSpacing)}",
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
                    )
                ),
                primaryAction = ModalAction("保存", isPrimary = true),
                secondaryAction = ModalAction("恢复默认(${"%.1f".format(defaultValue)})") {
                    onSave(defaultValue)
                    Toast.makeText(this, "已恢复默认间距 ${"%.1f".format(defaultValue)}x", Toast.LENGTH_SHORT).show()
                    rebuildCurrentCategory(moveFocusToDetail = false)
                },
            )
        )
        modalCoordinator.bindFormPrimaryAction(dialog, "value") { values ->
            val parsed = values["value"]?.trim()?.toFloatOrNull()
            val error = TsmModalFormValidators.validateLyricsSpacing(
                parsed, UiSettingsStore.minLyricsLineSpacing, UiSettingsStore.maxLyricsLineSpacing
            )
            if (error != null) {
                modalCoordinator.updateFieldError(dialog, "value", error)
                return@bindFormPrimaryAction false
            }
            onSave(parsed!!)
            Toast.makeText(this, "间距已设置为 ${"%.1f".format(parsed)}x", Toast.LENGTH_SHORT).show()
            rebuildCurrentCategory(moveFocusToDetail = false)
            true
        }
    }

    private fun launchExportBackupPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, FavoritesDbHelper.DB_NAME)
        }
        try {
            startActivityForResult(intent, REQUEST_EXPORT_BACKUP)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "当前设备没有可用的文件保存器", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchImportBackupPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, REQUEST_IMPORT_BACKUP)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "当前设备没有可用的文件选择器", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportLocalBackup(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val output = contentResolver.openOutputStream(uri)
                    ?: return@withContext com.github.gbandszxc.tvmediaplayer.backup.BackupOperationResult(
                        BackupOperationStatus.FAILED,
                        File(FavoritesDbHelper.DB_NAME),
                        "无法打开目标文件"
                    )
                SqliteBackupManager(applicationContext).exportToStream(output)
            }
            Toast.makeText(
                this@SettingsActivity,
                backupResultToast("本地备份导出", result.status, result.file, includePath = false),
                Toast.LENGTH_LONG
            ).show()
            rebuildCurrentCategory(moveFocusToDetail = false)
        }
    }

    private fun confirmImportLocalBackup(uri: Uri) {
        pendingImportUri = uri
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = "备份恢复",
                title = "从本地备份恢复？",
                message = "会用备份文件覆盖当前配置、播放状态与收藏数据。该操作不会修改 SMB 原文件。",
                confirmAction = ModalAction("恢复", isPrimary = true) {
                    pendingImportUri?.let { importLocalBackup(it) }
                    pendingImportUri = null
                },
                cancelAction = ModalAction("取消") {
                    pendingImportUri = null
                }
            )
        )
    }

    private fun importLocalBackup(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val input = contentResolver.openInputStream(uri)
                    ?: return@withContext com.github.gbandszxc.tvmediaplayer.backup.BackupOperationResult(
                        BackupOperationStatus.FAILED,
                        File(FavoritesDbHelper.DB_NAME),
                        "无法打开备份文件"
                    )
                SqliteBackupManager(applicationContext).importFromStream(input)
            }
            Toast.makeText(
                this@SettingsActivity,
                backupResultToast("本地备份恢复", result.status, result.file, includePath = false),
                Toast.LENGTH_LONG
            ).show()
            rebuildCurrentCategory(moveFocusToDetail = false)
        }
    }

    private fun showWebDavConfigDialog() {
        val store = WebDavConfigStore(this)
        val current = store.load()
        lateinit var dialog: Dialog
        dialog = modalCoordinator.showFormModal(
            FormModalSpec(
                sectionLabel = "备份恢复",
                title = "WebDAV 配置",
                fields = listOf(
                    FormFieldSpec(
                        key = "url",
                        label = "WebDAV URL",
                        initialValue = current.url,
                        hint = "https://example.com/remote.php/dav/files/user/backups",
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    ),
                    FormFieldSpec(
                        key = "username",
                        label = "用户名",
                        initialValue = current.username,
                        hint = "可留空",
                        inputType = InputType.TYPE_CLASS_TEXT
                    ),
                    FormFieldSpec(
                        key = "password",
                        label = "密码",
                        initialValue = current.password,
                        hint = "可留空",
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    )
                ),
                leadingAction = ModalAction("测试连接") {
                    testWebDavConnectionFromDialog(dialog)
                },
                primaryAction = ModalAction("保存", isPrimary = true),
                secondaryAction = ModalAction("取消")
            )
        )
        modalCoordinator.bindFormPrimaryAction(dialog, "url", "username", "password") { values ->
            val config = WebDavConfig(
                url = values["url"].orEmpty(),
                username = values["username"].orEmpty(),
                password = values["password"].orEmpty()
            )
            if (!config.isReady()) {
                modalCoordinator.updateFieldError(dialog, "url", "请填写 WebDAV URL")
                return@bindFormPrimaryAction false
            }
            store.save(config)
            Toast.makeText(this, "WebDAV 配置已保存", Toast.LENGTH_SHORT).show()
            rebuildCurrentCategory(moveFocusToDetail = false)
            true
        }
    }

    private fun testWebDavConnectionFromDialog(dialog: Dialog) {
        val config = WebDavConfig(
            url = dialog.formValue("url"),
            username = dialog.formValue("username"),
            password = dialog.formValue("password")
        )
        if (!config.isReady()) {
            modalCoordinator.updateFieldError(dialog, "url", "请填写 WebDAV URL")
            Toast.makeText(this, "请先配置 WebDAV URL", Toast.LENGTH_SHORT).show()
            return
        }
        modalCoordinator.updateFieldError(dialog, "url", null)
        Toast.makeText(this, "正在测试 WebDAV 连接...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                WebDavBackupClient().testConnection(config)
            }
            Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun Dialog.formValue(key: String): String {
        val contentView = findViewById<View>(android.R.id.content) ?: return ""
        val field = contentView.findViewWithTag<View>(key) ?: return ""
        return field.findViewById<EditText>(R.id.et_modal_field_input)?.text?.toString().orEmpty()
    }

    private fun uploadWebDavBackup() {
        val config = WebDavConfigStore(this).load()
        if (!config.isReady()) {
            Toast.makeText(this, "请先配置 WebDAV", Toast.LENGTH_SHORT).show()
            return
        }
        val backupManager = SqliteBackupManager(this)
        val progress = modalCoordinator.showProgressModal(
            ProgressModalSpec(
                sectionLabel = "备份恢复",
                title = "上传备份",
                fileName = backupManager.localBackupFile().name,
                initialState = DownloadProgressState(0L, 1L, 0L),
                message = "正在全量上传当前应用数据库备份。"
            )
        )
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val exportResult = backupManager.exportToLocalBackup()
                    if (exportResult.status != BackupOperationStatus.SUCCESS) {
                        error(backupResultToast("本地备份导出", exportResult.status, exportResult.file))
                    }
                    WebDavBackupClient().upload(
                        config,
                        FavoritesDbHelper.DB_NAME,
                        exportResult.file.inputStream()
                    )
                    exportResult.file.length()
                }
            }
            outcome
                .onSuccess { bytes ->
                    progress.onProgress(DownloadProgressState(bytes, bytes.coerceAtLeast(1L), 0L))
                    progress.onDismiss()
                    Toast.makeText(this@SettingsActivity, "WebDAV 备份上传完成", Toast.LENGTH_LONG).show()
                }
                .onFailure { ex ->
                    progress.onDismiss()
                    Toast.makeText(this@SettingsActivity, "WebDAV 上传失败：${ex.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun confirmDownloadWebDavBackup() {
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = "备份恢复",
                title = "从 WebDAV 下载并恢复？",
                message = "会下载远端备份并覆盖当前配置、播放状态与收藏数据。该操作不会修改 SMB 原文件。",
                confirmAction = ModalAction("下载并恢复", isPrimary = true) {
                    downloadWebDavBackup()
                },
                cancelAction = ModalAction("取消")
            )
        )
    }

    private fun downloadWebDavBackup() {
        val config = WebDavConfigStore(this).load()
        if (!config.isReady()) {
            Toast.makeText(this, "请先配置 WebDAV", Toast.LENGTH_SHORT).show()
            return
        }
        val tempFile = File(cacheDir, "webdav-${FavoritesDbHelper.DB_NAME}")
        val progress = modalCoordinator.showProgressModal(
            ProgressModalSpec(
                sectionLabel = "备份恢复",
                title = "下载备份",
                fileName = FavoritesDbHelper.DB_NAME,
                initialState = DownloadProgressState(0L, 1L, 0L),
                message = "正在从 WebDAV 下载备份并准备恢复。"
            )
        )
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    WebDavBackupClient().download(
                        config,
                        FavoritesDbHelper.DB_NAME,
                        tempFile.outputStream()
                    )
                    val result = SqliteBackupManager(applicationContext).importFromFile(tempFile)
                    tempFile.delete()
                    result
                }
            }
            outcome
                .onSuccess { result ->
                    progress.onProgress(DownloadProgressState(tempFile.length(), tempFile.length().coerceAtLeast(1L), 0L))
                    progress.onDismiss()
                    Toast.makeText(
                        this@SettingsActivity,
                        backupResultToast("WebDAV 备份恢复", result.status, result.file),
                        Toast.LENGTH_LONG
                    ).show()
                    rebuildCurrentCategory(moveFocusToDetail = false)
                }
                .onFailure { ex ->
                    progress.onDismiss()
                    tempFile.delete()
                    Toast.makeText(this@SettingsActivity, "WebDAV 下载失败：${ex.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun backupResultToast(
        operation: String,
        status: BackupOperationStatus,
        file: File,
        includePath: Boolean = true,
    ): String =
        when (status) {
            BackupOperationStatus.SUCCESS -> if (includePath) "${operation}完成：${file.path}" else "${operation}完成"
            BackupOperationStatus.MISSING_SOURCE -> "${operation}失败：未找到备份文件"
            BackupOperationStatus.INVALID_SOURCE -> "${operation}失败：备份文件不是有效应用数据库"
            BackupOperationStatus.FAILED -> "${operation}失败"
        }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @Deprecated("Deprecated in Android framework; kept for minSdk 21 document picker flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT_BACKUP -> exportLocalBackup(uri)
            REQUEST_IMPORT_BACKUP -> confirmImportLocalBackup(uri)
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    companion object {
        private const val REQUEST_EXPORT_BACKUP = 4001
        private const val REQUEST_IMPORT_BACKUP = 4002
    }
}
