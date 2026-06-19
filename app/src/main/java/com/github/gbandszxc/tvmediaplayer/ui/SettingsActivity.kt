package com.github.gbandszxc.tvmediaplayer.ui

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.gbandszxc.tvmediaplayer.BuildConfig
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.backup.BackupOperationStatus
import com.github.gbandszxc.tvmediaplayer.backup.SqliteBackupManager
import com.github.gbandszxc.tvmediaplayer.backup.WebDavBackupClient
import com.github.gbandszxc.tvmediaplayer.backup.WebDavClientMessages
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
import com.github.gbandszxc.tvmediaplayer.ui.modal.ListModalSpec
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalAction
import com.github.gbandszxc.tvmediaplayer.ui.modal.ModalListRow
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
                title = getString(R.string.settings_category_display),
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = getString(R.string.settings_global_scale),
                            descriptionProvider = { getString(R.string.settings_global_scale_desc) },
                            valueProvider = { "${UiSettingsStore.globalScalePercent(this)}%" }
                        ) { showGlobalScaleDialog() },
                        SettingsItem(
                            title = getString(R.string.settings_language),
                            descriptionProvider = { getString(R.string.settings_language_desc) },
                            valueProvider = { appLanguageLabel(UiSettingsStore.appLanguage(this)) },
                            action = { showLanguageDialog() }
                        )
                    )
                }
            ),
            SettingsCategory(
                title = getString(R.string.settings_category_playback),
                itemsProvider = {
                    listOf(
                        SettingsItem(title = getString(R.string.settings_lyrics_group), isGroupHeader = true),
                        SettingsItem(
                            title = getString(R.string.settings_playback_lyrics_font),
                            descriptionProvider = { getString(R.string.settings_lyrics_font_desc, UiSettingsStore.defaultPlaybackLyricsFontSp) },
                            valueProvider = { "${UiSettingsStore.playbackLyricsFontSp(this)}sp" }
                        ) {
                            showLyricsFontDialog(
                                title = getString(R.string.settings_playback_lyrics_font_title),
                                currentValue = UiSettingsStore.playbackLyricsFontSp(this),
                                defaultValue = UiSettingsStore.defaultPlaybackLyricsFontSp,
                                onSave = { value -> UiSettingsStore.setPlaybackLyricsFontSp(this, value) }
                            )
                        },
                        SettingsItem(
                            title = getString(R.string.settings_fullscreen_lyrics_font),
                            descriptionProvider = { getString(R.string.settings_lyrics_font_desc, UiSettingsStore.defaultFullscreenLyricsFontSp) },
                            valueProvider = { "${UiSettingsStore.fullscreenLyricsFontSp(this)}sp" }
                        ) {
                            showLyricsFontDialog(
                                title = getString(R.string.settings_fullscreen_lyrics_font_title),
                                currentValue = UiSettingsStore.fullscreenLyricsFontSp(this),
                                defaultValue = UiSettingsStore.defaultFullscreenLyricsFontSp,
                                onSave = { value -> UiSettingsStore.setFullscreenLyricsFontSp(this, value) }
                            )
                        },
                        SettingsItem(
                            title = getString(R.string.settings_playback_lyrics_spacing),
                            descriptionProvider = { getString(R.string.settings_lyrics_spacing_desc, "%.1f".format(UiSettingsStore.defaultPlaybackLyricsLineSpacing)) },
                            valueProvider = { "${"%.1f".format(UiSettingsStore.playbackLyricsLineSpacing(this))}x" }
                        ) {
                            showLyricsSpacingDialog(
                                title = getString(R.string.settings_playback_lyrics_spacing_title),
                                currentValue = UiSettingsStore.playbackLyricsLineSpacing(this),
                                defaultValue = UiSettingsStore.defaultPlaybackLyricsLineSpacing,
                                onSave = { value -> UiSettingsStore.setPlaybackLyricsLineSpacing(this, value) }
                            )
                        },
                        SettingsItem(
                            title = getString(R.string.settings_fullscreen_lyrics_spacing),
                            descriptionProvider = { getString(R.string.settings_lyrics_spacing_desc, "%.1f".format(UiSettingsStore.defaultFullscreenLyricsLineSpacing)) },
                            valueProvider = { "${"%.1f".format(UiSettingsStore.fullscreenLyricsLineSpacing(this))}x" }
                        ) {
                            showLyricsSpacingDialog(
                                title = getString(R.string.settings_fullscreen_lyrics_spacing_title),
                                currentValue = UiSettingsStore.fullscreenLyricsLineSpacing(this),
                                defaultValue = UiSettingsStore.defaultFullscreenLyricsLineSpacing,
                                onSave = { value -> UiSettingsStore.setFullscreenLyricsLineSpacing(this, value) }
                            )
                        },
                        SettingsItem(title = getString(R.string.settings_other_group), isGroupHeader = true),
                        SettingsItem(
                            title = getString(R.string.settings_remember_last_playback),
                            descriptionProvider = { getString(R.string.settings_remember_last_playback_desc) },
                            valueProvider = {
                                getString(if (UiSettingsStore.rememberLastPlayback(this)) R.string.common_enabled else R.string.common_disabled)
                            }
                        ) {
                            val next = !UiSettingsStore.rememberLastPlayback(this)
                            UiSettingsStore.setRememberLastPlayback(this, next)
                            if (!next) LastPlaybackStore.clear(this)
                            Toast.makeText(
                                this,
                                getString(if (next) R.string.settings_remember_enabled else R.string.settings_remember_disabled),
                                Toast.LENGTH_SHORT
                            ).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = getString(R.string.settings_category_app),
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = getString(R.string.settings_keep_screen_awake),
                            descriptionProvider = { getString(R.string.settings_keep_screen_awake_desc) },
                            valueProvider = {
                                getString(if (UiSettingsStore.keepScreenAwake(this)) R.string.common_enabled else R.string.common_disabled)
                            }
                        ) {
                            val next = !UiSettingsStore.keepScreenAwake(this)
                            UiSettingsStore.setKeepScreenAwake(this, next)
                            UiSettingsApplier.applyKeepScreenAwake(this)
                            Toast.makeText(
                                this,
                                getString(if (next) R.string.settings_keep_screen_awake_enabled else R.string.settings_keep_screen_awake_disabled),
                                Toast.LENGTH_SHORT
                            ).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        },
                        SettingsItem(
                            title = getString(R.string.settings_sleep_permission),
                            descriptionProvider = { getString(R.string.settings_sleep_permission_desc) },
                            valueProvider = {
                                getString(if (SleepDeviceController(this).isDeviceAdminActive()) R.string.common_authorized else R.string.common_unauthorized)
                            }
                        ) {
                            SleepDeviceController(this).openDeviceAdminSettings(this)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = getString(R.string.settings_category_other),
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = getString(R.string.settings_clear_cache),
                            descriptionProvider = {
                                getString(R.string.settings_clear_cache_desc)
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
                                getString(R.string.settings_cache_cleared),
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
                title = getString(R.string.settings_category_backup),
                itemsProvider = {
                    val webDavConfig = WebDavConfigStore(this).load()
                    listOf(
                        SettingsItem(
                            title = getString(R.string.settings_export_local_backup),
                            descriptionProvider = { getString(R.string.settings_export_local_backup_desc) },
                            valueProvider = { getString(R.string.settings_choose_save_location) },
                            action = { launchExportBackupPicker() }
                        ),
                        SettingsItem(
                            title = getString(R.string.settings_import_local_backup),
                            descriptionProvider = { getString(R.string.settings_import_local_backup_desc) },
                            valueProvider = { getString(R.string.settings_choose_backup_file) },
                            action = { launchImportBackupPicker() }
                        ),
                        SettingsItem(title = getString(R.string.settings_webdav_group), isGroupHeader = true),
                        SettingsItem(
                            title = getString(R.string.settings_webdav_config),
                            descriptionProvider = { getString(R.string.settings_webdav_config_desc) },
                            valueProvider = { getString(if (webDavConfig.isReady()) R.string.common_configured else R.string.common_not_configured) },
                            action = { showWebDavConfigDialog() }
                        ),
                        SettingsItem(
                            title = getString(R.string.settings_upload_webdav),
                            descriptionProvider = { getString(R.string.settings_upload_webdav_desc) },
                            valueProvider = { if (webDavConfig.isReady()) webDavConfig.url else getString(R.string.settings_configure_first) },
                            action = { uploadWebDavBackup() }
                        ),
                        SettingsItem(
                            title = getString(R.string.settings_download_webdav),
                            descriptionProvider = { getString(R.string.settings_download_webdav_desc) },
                            valueProvider = { if (webDavConfig.isReady()) webDavConfig.url else getString(R.string.settings_configure_first) },
                            action = { confirmDownloadWebDavBackup() }
                        )
                    )
                }
            ),
            SettingsCategory(
                title = getString(R.string.settings_category_about),
                itemsProvider = {
                    buildList {
                        add(
                            SettingsItem(
                                title = getString(R.string.settings_project_description),
                                descriptionProvider = {
                                    getString(R.string.settings_project_description_value)
                                },
                                valueProvider = { "v${BuildConfig.VERSION_NAME} / ${AppUpdateManager.currentAbi()}" }
                            )
                        )
                        add(
                            SettingsItem(
                                title = getString(R.string.settings_check_update),
                                descriptionProvider = { getString(R.string.settings_check_update_desc) },
                                action = {
                                    AppUpdateManager.checkAndPrompt(this@SettingsActivity, silentWhenNoUpdate = false)
                                }
                            )
                        )
                        if (BuildConfig.DEBUG) {
                            add(
                                SettingsItem(
                                    title = getString(R.string.settings_preview_startup_update),
                                    descriptionProvider = { getString(R.string.settings_preview_startup_update_desc) },
                                    action = {
                                        AppUpdateManager.previewStartupUpdatePrompt(this@SettingsActivity)
                                    }
                                )
                            )
                            add(
                                SettingsItem(
                                    title = getString(R.string.settings_preview_download_progress),
                                    descriptionProvider = { getString(R.string.settings_preview_download_progress_desc) },
                                    action = {
                                        AppUpdateManager.previewDownloadProgress(this@SettingsActivity)
                                    }
                                )
                            )
                        }
                        add(
                            SettingsItem(
                                title = "GitHub",
                                descriptionProvider = { getString(R.string.settings_github_desc) },
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
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.ui_accent_blue_text))
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
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.ui_text_muted))
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
                    Toast.makeText(this, R.string.common_not_supported_display_only, Toast.LENGTH_SHORT).show()
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
                sectionLabel = getString(R.string.settings_category_display),
                title = title,
                fields = listOf(
                    FormFieldSpec(
                        key = "value",
                        label = getString(R.string.settings_field_font_size),
                        initialValue = currentValue.toString(),
                        hint = "${UiSettingsStore.minLyricsFontSp}-${UiSettingsStore.maxLyricsFontSp}",
                        inputType = InputType.TYPE_CLASS_NUMBER,
                    )
                ),
                primaryAction = ModalAction(getString(R.string.common_save), isPrimary = true),
                secondaryAction = ModalAction(getString(R.string.settings_restore_default, defaultValue.toString())) {
                    onSave(defaultValue)
                    Toast.makeText(this, getString(R.string.settings_font_default_restored, defaultValue), Toast.LENGTH_SHORT).show()
                    rebuildCurrentCategory(moveFocusToDetail = false)
                },
            )
        )
        modalCoordinator.bindFormPrimaryAction(dialog, "value") { values ->
            val parsed = values["value"]?.trim()?.toIntOrNull()
            val error = TsmModalFormValidators.validateLyricsFont(
                parsed,
                UiSettingsStore.minLyricsFontSp,
                UiSettingsStore.maxLyricsFontSp,
                invalidNumberMessage = getString(R.string.validation_number_required),
                rangeMessage = getString(
                    R.string.validation_lyrics_font_range,
                    UiSettingsStore.minLyricsFontSp,
                    UiSettingsStore.maxLyricsFontSp,
                ),
            )
            if (error != null) {
                modalCoordinator.updateFieldError(dialog, "value", error)
                return@bindFormPrimaryAction false
            }
            onSave(parsed!!)
            Toast.makeText(this, getString(R.string.settings_font_saved, parsed), Toast.LENGTH_SHORT).show()
            rebuildCurrentCategory(moveFocusToDetail = false)
            true
        }
    }

    private fun showGlobalScaleDialog() {
        val lastFocusedView = currentFocus
        val originalScale = UiSettingsStore.globalScalePercent(this)
        var confirmed = false
        val content = LayoutInflater.from(this)
            .inflate(R.layout.dialog_tsm_modal_shell, null, false)

        content.findViewById<TextView>(R.id.tv_modal_section).apply {
            text = getString(R.string.settings_category_display)
            visibility = View.VISIBLE
        }
        content.findViewById<TextView>(R.id.tv_modal_title).text = getString(R.string.settings_global_scale)
        content.findViewById<TextView>(R.id.tv_modal_message).apply {
            text = getString(R.string.settings_global_scale_desc)
            visibility = View.VISIBLE
        }
        val actionsContainer = content.findViewById<LinearLayout>(R.id.container_modal_actions).apply {
            visibility = View.VISIBLE
            removeAllViews()
        }

        val container = content.findViewById<LinearLayout>(R.id.container_modal_content)
        container.removeAllViews()

        val minusButton = createScaleStepButton("-").apply {
            id = View.generateViewId()
        }
        val plusButton = createScaleStepButton("+").apply {
            id = View.generateViewId()
        }
        minusButton.nextFocusRightId = plusButton.id
        plusButton.nextFocusLeftId = minusButton.id

        val valueView = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.ui_text_primary))
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.ui_text_page_title),
            )
            gravity = android.view.Gravity.CENTER
            minWidth = dp(160)
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setBackgroundResource(R.drawable.bg_modal_input)
        }

        fun refreshControls() {
            val current = UiSettingsStore.globalScalePercent(this)
            valueView.text = "$current%"
            minusButton.isEnabled = current > UiSettingsStore.globalScalePresets.first()
            plusButton.isEnabled = current < UiSettingsStore.globalScalePresets.last()
        }

        fun applyDialogScale() {
            val root = content.parent as? ViewGroup ?: return
            UiSettingsApplier.applyGlobalScaleToContent(
                root = root,
                content = content,
                scalePercent = UiSettingsStore.globalScalePercent(this),
            )
        }

        fun applyScale(value: Int) {
            UiSettingsStore.setGlobalScalePercent(this, value)
            UiSettingsApplier.applyGlobalScale(this)
            applyDialogScale()
            rebuildCurrentCategory(moveFocusToDetail = false)
            refreshControls()
        }

        minusButton.setOnClickListener {
            applyScale(UiSettingsStore.previousGlobalScalePreset(UiSettingsStore.globalScalePercent(this)))
        }
        plusButton.setOnClickListener {
            applyScale(UiSettingsStore.nextGlobalScalePreset(UiSettingsStore.globalScalePercent(this)))
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            addView(minusButton)
            addView(valueView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.ui_space_3xl)
                marginEnd = resources.getDimensionPixelSize(R.dimen.ui_space_3xl)
            })
            addView(plusButton)
        }
        container.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))

        lateinit var dialog: Dialog
        val confirmButton = createScaleActionButton(
            label = getString(R.string.common_confirm),
            isPrimary = true,
        ).apply {
            setOnClickListener {
                confirmed = true
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_global_scale_changed, UiSettingsStore.globalScalePercent(this@SettingsActivity)),
                    Toast.LENGTH_SHORT,
                ).show()
                dialog.dismiss()
            }
        }
        val cancelButton = createScaleActionButton(
            label = getString(R.string.common_cancel),
            isPrimary = false,
        ).apply {
            setOnClickListener {
                confirmed = false
                dialog.dismiss()
            }
        }
        actionsContainer.addView(confirmButton)
        actionsContainer.addView(cancelButton)

        dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen).apply {
            setContentView(content)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(null)
            setOnDismissListener {
                if (!confirmed && UiSettingsStore.globalScalePercent(this@SettingsActivity) != originalScale) {
                    UiSettingsStore.setGlobalScalePercent(this@SettingsActivity, originalScale)
                    UiSettingsApplier.applyGlobalScale(this@SettingsActivity)
                    rebuildCurrentCategory(moveFocusToDetail = false)
                }
                lastFocusedView?.requestFocus()
            }
            show()
        }

        refreshControls()
        dialog.findViewById<FrameLayout>(android.R.id.content)
            ?.post {
                applyDialogScale()
                minusButton.requestFocus()
            }
    }

    private fun createScaleStepButton(label: String): Button =
        Button(this, null, 0, R.style.TsmModalActionButton).apply {
            text = label
            minWidth = dp(72)
            setBackgroundResource(R.drawable.bg_button_dark)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.ui_text_on_accent))
            layoutParams = LinearLayout.LayoutParams(
                dp(88),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun createScaleActionButton(label: String, isPrimary: Boolean): Button =
        Button(this, null, 0, R.style.TsmModalActionButton).apply {
            text = label
            setBackgroundResource(if (isPrimary) R.drawable.bg_button_primary else R.drawable.bg_button_dark)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.ui_text_on_accent))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.ui_space_md)
            }
        }

    private fun showLanguageDialog() {
        val current = UiSettingsStore.appLanguage(this)
        modalCoordinator.showListModal(
            ListModalSpec(
                sectionLabel = getString(R.string.settings_category_display),
                title = getString(R.string.settings_language),
                rows = AppLanguage.entries.map { language ->
                    val label = appLanguageLabel(language)
                    ModalListRow(
                        key = language.storageValue,
                        label = label,
                        enabled = language != current,
                        dismissOnClick = true,
                        onClick = {
                            if (language != UiSettingsStore.appLanguage(this)) {
                                UiSettingsStore.setAppLanguage(this, language)
                                Toast.makeText(
                                    this,
                                    getString(R.string.settings_language_changed, appLanguageLabel(language)),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                recreate()
                            }
                        },
                    )
                },
            )
        )
    }

    private fun appLanguageLabel(language: AppLanguage): String =
        when (language) {
            AppLanguage.SYSTEM -> getString(R.string.settings_language_system)
            AppLanguage.CHINESE -> getString(R.string.settings_language_chinese)
            AppLanguage.ENGLISH -> getString(R.string.settings_language_english)
        }

    private fun showLyricsSpacingDialog(
        title: String,
        currentValue: Float,
        defaultValue: Float,
        onSave: (Float) -> Unit
    ) {
        val dialog = modalCoordinator.showFormModal(
            FormModalSpec(
                sectionLabel = getString(R.string.settings_category_display),
                title = title,
                fields = listOf(
                    FormFieldSpec(
                        key = "value",
                        label = getString(R.string.settings_field_spacing),
                        initialValue = "%.1f".format(currentValue),
                        hint = "${"%.1f".format(UiSettingsStore.minLyricsLineSpacing)} - ${"%.1f".format(UiSettingsStore.maxLyricsLineSpacing)}",
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
                    )
                ),
                primaryAction = ModalAction(getString(R.string.common_save), isPrimary = true),
                secondaryAction = ModalAction(getString(R.string.settings_restore_default, "%.1f".format(defaultValue))) {
                    onSave(defaultValue)
                    Toast.makeText(this, getString(R.string.settings_spacing_default_restored, "%.1f".format(defaultValue)), Toast.LENGTH_SHORT).show()
                    rebuildCurrentCategory(moveFocusToDetail = false)
                },
            )
        )
        modalCoordinator.bindFormPrimaryAction(dialog, "value") { values ->
            val parsed = values["value"]?.trim()?.toFloatOrNull()
            val error = TsmModalFormValidators.validateLyricsSpacing(
                parsed,
                UiSettingsStore.minLyricsLineSpacing,
                UiSettingsStore.maxLyricsLineSpacing,
                invalidNumberMessage = getString(R.string.validation_number_required),
                rangeMessage = getString(
                    R.string.validation_lyrics_spacing_range,
                    "%.1f".format(UiSettingsStore.minLyricsLineSpacing),
                    "%.1f".format(UiSettingsStore.maxLyricsLineSpacing),
                ),
            )
            if (error != null) {
                modalCoordinator.updateFieldError(dialog, "value", error)
                return@bindFormPrimaryAction false
            }
            onSave(parsed!!)
            Toast.makeText(this, getString(R.string.settings_spacing_saved, "%.1f".format(parsed)), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, R.string.common_no_file_saver, Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, R.string.common_no_file_picker, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportLocalBackup(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val output = contentResolver.openOutputStream(uri)
                    ?: return@withContext com.github.gbandszxc.tvmediaplayer.backup.BackupOperationResult(
                        BackupOperationStatus.FAILED,
                        File(FavoritesDbHelper.DB_NAME),
                        getString(R.string.settings_backup_open_target_failed)
                    )
                SqliteBackupManager(applicationContext).exportToStream(output)
            }
            Toast.makeText(
                this@SettingsActivity,
                backupResultToast(getString(R.string.settings_backup_export_local), result.status, result.file, includePath = false),
                Toast.LENGTH_LONG
            ).show()
            rebuildCurrentCategory(moveFocusToDetail = false)
        }
    }

    private fun confirmImportLocalBackup(uri: Uri) {
        pendingImportUri = uri
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = getString(R.string.settings_category_backup),
                title = getString(R.string.settings_confirm_import_title),
                message = getString(R.string.settings_confirm_import_message),
                confirmAction = ModalAction(getString(R.string.settings_restore), isPrimary = true) {
                    pendingImportUri?.let { importLocalBackup(it) }
                    pendingImportUri = null
                },
                cancelAction = ModalAction(getString(R.string.common_cancel)) {
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
                        getString(R.string.settings_backup_open_source_failed)
                    )
                SqliteBackupManager(applicationContext).importFromStream(input)
            }
            Toast.makeText(
                this@SettingsActivity,
                backupResultToast(getString(R.string.settings_backup_import_local), result.status, result.file, includePath = false),
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
                sectionLabel = getString(R.string.settings_category_backup),
                title = getString(R.string.settings_webdav_config),
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
                        label = getString(R.string.browser_form_username),
                        initialValue = current.username,
                        hint = getString(R.string.common_optional),
                        inputType = InputType.TYPE_CLASS_TEXT
                    ),
                    FormFieldSpec(
                        key = "password",
                        label = getString(R.string.browser_form_password),
                        initialValue = current.password,
                        hint = getString(R.string.common_optional),
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    )
                ),
                leadingAction = ModalAction(getString(R.string.settings_webdav_test)) {
                    testWebDavConnectionFromDialog(dialog)
                },
                primaryAction = ModalAction(getString(R.string.common_save), isPrimary = true),
                secondaryAction = ModalAction(getString(R.string.common_cancel))
            )
        )
        modalCoordinator.bindFormPrimaryAction(dialog, "url", "username", "password") { values ->
            val config = WebDavConfig(
                url = values["url"].orEmpty(),
                username = values["username"].orEmpty(),
                password = values["password"].orEmpty()
            )
            if (!config.isReady()) {
                modalCoordinator.updateFieldError(dialog, "url", getString(R.string.settings_webdav_url_required))
                return@bindFormPrimaryAction false
            }
            store.save(config)
            Toast.makeText(this, R.string.settings_webdav_saved, Toast.LENGTH_SHORT).show()
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
            modalCoordinator.updateFieldError(dialog, "url", getString(R.string.settings_webdav_url_required))
            Toast.makeText(this, R.string.settings_webdav_configure_url_first, Toast.LENGTH_SHORT).show()
            return
        }
        modalCoordinator.updateFieldError(dialog, "url", null)
        Toast.makeText(this, R.string.settings_webdav_testing, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                WebDavBackupClient(messages = webDavClientMessages()).testConnection(config)
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
            Toast.makeText(this, R.string.settings_webdav_configure_first, Toast.LENGTH_SHORT).show()
            return
        }
        val backupManager = SqliteBackupManager(this)
        val progress = modalCoordinator.showProgressModal(
            ProgressModalSpec(
                sectionLabel = getString(R.string.settings_category_backup),
                title = getString(R.string.settings_upload_backup_title),
                fileName = backupManager.localBackupFile().name,
                initialState = DownloadProgressState(0L, 1L, 0L),
                message = getString(R.string.settings_upload_backup_message)
            )
        )
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val exportResult = backupManager.exportToLocalBackup()
                    if (exportResult.status != BackupOperationStatus.SUCCESS) {
                        error(backupResultToast(getString(R.string.settings_backup_export_local), exportResult.status, exportResult.file))
                    }
                    WebDavBackupClient(messages = webDavClientMessages()).upload(
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
                    Toast.makeText(this@SettingsActivity, R.string.settings_webdav_upload_done, Toast.LENGTH_LONG).show()
                }
                .onFailure { ex ->
                    progress.onDismiss()
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_webdav_upload_failed, ex.message.orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    private fun confirmDownloadWebDavBackup() {
        modalCoordinator.showConfirmModal(
            ConfirmModalSpec(
                sectionLabel = getString(R.string.settings_category_backup),
                title = getString(R.string.settings_confirm_download_webdav_title),
                message = getString(R.string.settings_confirm_download_webdav_message),
                confirmAction = ModalAction(getString(R.string.settings_download_and_restore), isPrimary = true) {
                    downloadWebDavBackup()
                },
                cancelAction = ModalAction(getString(R.string.common_cancel))
            )
        )
    }

    private fun downloadWebDavBackup() {
        val config = WebDavConfigStore(this).load()
        if (!config.isReady()) {
            Toast.makeText(this, R.string.settings_webdav_configure_first, Toast.LENGTH_SHORT).show()
            return
        }
        val tempFile = File(cacheDir, "webdav-${FavoritesDbHelper.DB_NAME}")
        val progress = modalCoordinator.showProgressModal(
            ProgressModalSpec(
                sectionLabel = getString(R.string.settings_category_backup),
                title = getString(R.string.settings_download_backup_title),
                fileName = FavoritesDbHelper.DB_NAME,
                initialState = DownloadProgressState(0L, 1L, 0L),
                message = getString(R.string.settings_download_backup_message)
            )
        )
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    WebDavBackupClient(messages = webDavClientMessages()).download(
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
                        backupResultToast(getString(R.string.settings_backup_restore_webdav), result.status, result.file),
                        Toast.LENGTH_LONG
                    ).show()
                    rebuildCurrentCategory(moveFocusToDetail = false)
                }
                .onFailure { ex ->
                    progress.onDismiss()
                    tempFile.delete()
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_webdav_download_failed, ex.message.orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
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
            BackupOperationStatus.SUCCESS -> {
                val suffix = if (includePath) {
                    getString(R.string.settings_backup_done_with_path, file.path)
                } else {
                    getString(R.string.settings_backup_done)
                }
                "$operation$suffix"
            }
            BackupOperationStatus.MISSING_SOURCE -> "$operation${getString(R.string.settings_backup_missing_source)}"
            BackupOperationStatus.INVALID_SOURCE -> "$operation${getString(R.string.settings_backup_invalid_source)}"
            BackupOperationStatus.FAILED -> "$operation${getString(R.string.settings_backup_failed)}"
        }

    private fun webDavClientMessages(): WebDavClientMessages =
        WebDavClientMessages(
            uploadFailedHttp = { getString(R.string.webdav_upload_failed_http, it) },
            downloadFailedHttp = { getString(R.string.webdav_download_failed_http, it) },
            missingUrl = getString(R.string.webdav_connection_missing_url),
            createdDirectory = { getString(R.string.webdav_connection_created_dir, it) },
            connectionSuccess = { getString(R.string.webdav_connection_success, it) },
            connectionFailed = { getString(R.string.webdav_connection_failed, it) },
            createLoop = getString(R.string.webdav_create_loop),
            invalidRoot = { getString(R.string.webdav_root_invalid, it) },
            createParentFailed = { getString(R.string.webdav_create_parent_failed, it) },
            authFailed = { getString(R.string.webdav_auth_failed, it) },
            invalidDirectory = { getString(R.string.webdav_invalid_dir, it) },
            probeNotAllowed = { getString(R.string.webdav_probe_not_allowed, it) },
            parentInaccessible = { getString(R.string.webdav_parent_inaccessible, it) },
            serverError = { getString(R.string.webdav_server_error, it) },
            createAuthFailed = { getString(R.string.webdav_create_auth_failed, it) },
            createServerError = { getString(R.string.webdav_create_server_error, it) },
            createFailed = { getString(R.string.webdav_create_failed, it) },
            unknownHost = getString(R.string.webdav_unknown_host),
            timeout = getString(R.string.webdav_timeout),
            sslError = getString(R.string.webdav_ssl_error),
        )

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
