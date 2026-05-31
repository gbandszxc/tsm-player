# TV Modal Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用统一的 TV 自绘 modal 替换项目中的原生对话框和原生下载进度框，并完成遥控器焦点、业务回调、测试与文档回归。

**Architecture:** 先在 `ui/modal` 下建立一套轻量的自定义 `Dialog` + XML 视图体系，统一处理遮罩、面板、标题区、内容区、操作区、焦点恢复和表单错误展示；再把播放页、收藏页、SMB 浏览页、设置页、首页提示和更新流程逐步迁移到这套公共能力上。所有视觉资源继续复用 `DESIGN.md` 中既有 token，仅补充 modal 专用 layout/drawable/string/dimen。

**Tech Stack:** Kotlin、Android View/XML、AppCompat Dialog、自定义 drawable、Robolectric、Gradle

---

## File Map

### 新建文件

- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalCoordinator.kt`
  - 公共入口：创建、展示、关闭 modal，缓存并恢复打开前焦点。
- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalModels.kt`
  - 定义 `ActionModalSpec`、`ListModalSpec`、`FormModalSpec`、`ConfirmModalSpec`、`ProgressModalHandle` 等模型。
- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalBuilders.kt`
  - 负责把 spec 渲染成统一 shell + 内容区视图，并绑定按钮回调与错误更新。
- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalFormValidators.kt`
  - 收口“播放列表名称不能为空/重复”“歌词字号范围”“歌词间距范围”“SMB 主机必填”等同步校验。
- `app/src/main/res/layout/dialog_tsm_modal_shell.xml`
  - 统一 modal 外壳：遮罩、标题区、内容区、操作区。
- `app/src/main/res/layout/item_tsm_modal_list_row.xml`
  - 列表型 modal 行项。
- `app/src/main/res/layout/item_tsm_modal_action_row.xml`
  - 连接管理等命令型按钮行。
- `app/src/main/res/layout/item_tsm_modal_form_field.xml`
  - 表单项：label、输入框容器、错误文案。
- `app/src/main/res/layout/view_tsm_modal_progress.xml`
  - 更新下载进度内容区。
- `app/src/main/res/drawable/bg_modal_panel.xml`
  - 面板背景，基于现有 `ui_bg_panel` / `ui_radius_panel`。
- `app/src/main/res/drawable/bg_modal_surface.xml`
  - 内容项默认背景。
- `app/src/main/res/drawable/bg_modal_surface_focused.xml`
  - 列表项 / 命令项聚焦背景。
- `app/src/main/res/drawable/bg_modal_input.xml`
  - 输入框背景，含默认态与聚焦态。
- `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalCoordinatorTest.kt`
  - 测试公共 modal shell、焦点恢复、内容挂载。
- `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalFormValidatorsTest.kt`
  - 测试表单校验逻辑。

### 修改文件

- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`
  - 替换播放页的收藏列表、新建播放列表、失效歌曲确认框。
- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivity.kt`
  - 替换收藏页的新建播放列表与失效歌曲确认框。
- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
  - 替换连接管理、切换连接、SMB 连接配置框。
- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/MainActivity.kt`
  - 替换首次启动睡眠权限提示。
- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt`
  - 替换歌词字号/间距输入框。
- `app/src/main/java/com/github/gbandszxc/tvmediaplayer/update/AppUpdateManager.kt`
  - 替换更新确认框和下载进度框。
- `app/src/main/res/values/strings.xml`
  - 补 modal 公共文案、设置页/更新页/SMB 配置相关标题和错误提示。
- `app/src/main/res/values/dimens.xml`
  - 补 modal 宽高、输入框最小宽度、进度条高度等专用尺寸。
- `app/src/main/res/values/styles.xml`
  - 增加 modal 标题、副标题、输入框文本等样式。
- `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivityLayoutTest.kt`
  - 改成验证新的 modal 数据/入口行为，不再依赖 `ArrayAdapter`。
- `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivityLayoutTest.kt`
  - 增加新建歌单 modal 入口或数据验证。
- `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt`
  - 增加连接管理 modal 入口或数据验证。
- `app/src/test/java/com/github/gbandszxc/tvmediaplayer/update/AppUpdateManagerTest.kt`
  - 增加更新 modal 状态与下载进度 handle 相关测试。
- `DESIGN.md`
  - 补公共 TV modal 规范。
- `docs/MANUAL.md`
  - 记录替换范围、公共组件与下载进度框更新。

## Task 1: 建立公共 TV Modal 基础设施

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalCoordinator.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalModels.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalBuilders.kt`
- Create: `app/src/main/res/layout/dialog_tsm_modal_shell.xml`
- Create: `app/src/main/res/layout/item_tsm_modal_list_row.xml`
- Create: `app/src/main/res/layout/item_tsm_modal_action_row.xml`
- Create: `app/src/main/res/drawable/bg_modal_panel.xml`
- Create: `app/src/main/res/drawable/bg_modal_surface.xml`
- Create: `app/src/main/res/drawable/bg_modal_surface_focused.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalCoordinatorTest.kt`

- [ ] **Step 1: 先写公共 shell 的失败测试**

```kotlin
@RunWith(RobolectricTestRunner::class)
class TsmModalCoordinatorTest {

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

        assertTrue(dialog.isShowing)
        assertEquals("连接管理", dialog.findViewById<TextView>(R.id.tv_modal_title).text.toString())
        assertEquals("请选择操作", dialog.findViewById<TextView>(R.id.tv_modal_message).text.toString())
        assertEquals(2, dialog.findViewById<LinearLayout>(R.id.container_modal_content).childCount)
    }
}
```

- [ ] **Step 2: 运行测试，确认当前缺少 modal 基建而失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinatorTest"`

Expected: FAIL，提示 `TsmModalCoordinator`、`ActionModalSpec` 或 `dialog_tsm_modal_shell` 不存在。

- [ ] **Step 3: 实现 modal model 与 coordinator 最小版本**

```kotlin
data class ModalAction(
    val label: String,
    val isPrimary: Boolean = false,
    val isDanger: Boolean = false,
    val isEnabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

data class ActionModalSpec(
    val sectionLabel: String,
    val title: String,
    val message: String? = null,
    val actions: List<ModalAction>,
)

class TsmModalCoordinator(
    private val host: Activity,
) {
    private var lastFocusedView: View? = null

    fun showActionModal(spec: ActionModalSpec): Dialog {
        lastFocusedView = host.currentFocus
        val content = LayoutInflater.from(host).inflate(R.layout.dialog_tsm_modal_shell, null, false)
        content.findViewById<TextView>(R.id.tv_modal_section).text = spec.sectionLabel
        content.findViewById<TextView>(R.id.tv_modal_title).text = spec.title
        content.findViewById<TextView>(R.id.tv_modal_message).apply {
            text = spec.message.orEmpty()
            visibility = if (spec.message.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val container = content.findViewById<LinearLayout>(R.id.container_modal_content)
        spec.actions.forEach { action ->
            val row = LayoutInflater.from(host).inflate(R.layout.item_tsm_modal_action_row, container, false) as Button
            row.text = action.label
            row.isEnabled = action.isEnabled
            row.setOnClickListener { action.onClick?.invoke() }
            container.addView(row)
        }
        return Dialog(host, R.style.Theme_AppCompat_Dialog).apply {
            setContentView(content)
            setOnDismissListener { lastFocusedView?.requestFocus() }
            show()
            container.getChildAt(0)?.requestFocus()
        }
    }
}
```

- [ ] **Step 4: 补上统一 shell 与资源**

```xml
<!-- dialog_tsm_modal_shell.xml -->
<FrameLayout ... android:background="@color/ui_bg_overlay">
    <LinearLayout
        android:id="@+id/panel_modal"
        android:layout_width="720dp"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="@drawable/bg_modal_panel"
        android:orientation="vertical">

        <TextView android:id="@+id/tv_modal_section" style="@style/TsmTextSecondary" ... />
        <TextView android:id="@+id/tv_modal_title" style="@style/TsmTextSectionTitle" ... />
        <TextView android:id="@+id/tv_modal_message" style="@style/TsmTextSecondary" ... />
        <LinearLayout android:id="@+id/container_modal_content" ... />
        <LinearLayout android:id="@+id/container_modal_actions" ... />
    </LinearLayout>
</FrameLayout>
```

```xml
<!-- bg_modal_panel.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <corners android:radius="@dimen/ui_radius_panel" />
    <solid android:color="@color/ui_bg_panel" />
    <stroke
        android:width="@dimen/ui_stroke_focus"
        android:color="@color/ui_divider" />
</shape>
```

- [ ] **Step 5: 再跑公共 modal 测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinatorTest"`

Expected: PASS，且 shell/标题/内容区断言全部通过。

- [ ] **Step 6: 提交公共 modal 基建**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal `
        app/src/main/res/layout/dialog_tsm_modal_shell.xml `
        app/src/main/res/layout/item_tsm_modal_list_row.xml `
        app/src/main/res/layout/item_tsm_modal_action_row.xml `
        app/src/main/res/drawable/bg_modal_panel.xml `
        app/src/main/res/drawable/bg_modal_surface.xml `
        app/src/main/res/drawable/bg_modal_surface_focused.xml `
        app/src/main/res/values/dimens.xml `
        app/src/main/res/values/styles.xml `
        app/src/main/res/values/strings.xml `
        app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalCoordinatorTest.kt
git commit -m "feat: 增加 TV modal 公共基础设施"
```

## Task 2: 增加表单、确认和进度能力

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalFormValidators.kt`
- Create: `app/src/main/res/layout/item_tsm_modal_form_field.xml`
- Create: `app/src/main/res/layout/view_tsm_modal_progress.xml`
- Create: `app/src/main/res/drawable/bg_modal_input.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalModels.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalBuilders.kt`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalFormValidatorsTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalCoordinatorTest.kt`

- [ ] **Step 1: 先为校验逻辑补失败测试**

```kotlin
class TsmModalFormValidatorsTest {

    @Test
    fun `validatePlaylistName rejects blank and duplicate names`() {
        assertEquals("请输入播放列表名称", TsmModalFormValidators.validatePlaylistName(" ", setOf("通勤")))
        assertEquals("播放列表已存在", TsmModalFormValidators.validatePlaylistName("通勤", setOf("通勤")))
        assertNull(TsmModalFormValidators.validatePlaylistName("夜间播放", setOf("通勤")))
    }

    @Test
    fun `validateLyricsFont keeps configured bounds`() {
        assertEquals("字号范围需在 14-36sp", TsmModalFormValidators.validateLyricsFont(40, 14, 36))
        assertNull(TsmModalFormValidators.validateLyricsFont(22, 14, 36))
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalFormValidatorsTest"`

Expected: FAIL，提示 `TsmModalFormValidators` 缺失。

- [ ] **Step 3: 实现 validator 与 form/progress spec**

```kotlin
object TsmModalFormValidators {
    fun validatePlaylistName(name: String, existing: Set<String>): String? {
        val normalized = name.trim()
        if (normalized.isBlank()) return "请输入播放列表名称"
        if (normalized in existing) return "播放列表已存在"
        return null
    }

    fun validateLyricsFont(value: Int?, min: Int, max: Int): String? {
        if (value == null) return "请输入有效数字"
        if (value !in min..max) return "字号范围需在 ${min}-${max}sp"
        return null
    }

    fun validateLyricsSpacing(value: Float?, min: Float, max: Float): String? {
        if (value == null) return "请输入有效数字"
        if (value < min || value > max) return "间距范围需在 %.1f - %.1f".format(min, max)
        return null
    }
}
```

```kotlin
data class FormFieldSpec(
    val key: String,
    val label: String,
    val initialValue: String,
    val hint: String,
    val inputType: Int,
    val error: String? = null,
)

data class ProgressModalSpec(
    val sectionLabel: String,
    val title: String,
    val fileName: String,
    val percent: Int,
    val indeterminate: Boolean,
    val message: String,
)
```

- [ ] **Step 4: 给 coordinator 增加 form/confirm/progress 渲染与错误刷新能力**

```kotlin
fun updateFieldError(dialog: Dialog, fieldKey: String, error: String?) {
    val field = dialog.findViewWithTag<View>(fieldKey) ?: return
    val errorView = field.findViewById<TextView>(R.id.tv_modal_field_error)
    errorView.text = error.orEmpty()
    errorView.visibility = if (error.isNullOrBlank()) View.GONE else View.VISIBLE
}

fun showProgressModal(spec: ProgressModalSpec): ProgressModalHandle {
    val dialog = Dialog(host, R.style.Theme_AppCompat_Dialog)
    val view = LayoutInflater.from(host).inflate(R.layout.view_tsm_modal_progress, null, false)
    dialog.setContentView(wrapInShell(spec.sectionLabel, spec.title, view))
    dialog.setCancelable(false)
    dialog.show()
    return ProgressModalHandle(
        dialog = dialog,
        onProgress = { percent ->
            view.findViewById<ProgressBar>(R.id.progress_modal_download).progress = percent
            view.findViewById<TextView>(R.id.tv_modal_progress_percent).text = "$percent%"
        },
        onDismiss = { dialog.dismiss() },
    )
}
```

- [ ] **Step 5: 补 form/progress 的 Robolectric 断言**

```kotlin
@Test
fun `updateFieldError toggles inline error text`() {
    val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java).setup().get()
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
                )
            ),
        )
    )

    coordinator.updateFieldError(dialog, "playlist_name", "播放列表已存在")

    val errorView = dialog.findViewWithTag<View>("playlist_name")
        .findViewById<TextView>(R.id.tv_modal_field_error)
    assertEquals("播放列表已存在", errorView.text.toString())
    assertEquals(View.VISIBLE, errorView.visibility)
}
```

- [ ] **Step 6: 运行 modal 基建相关测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.modal.*"`

Expected: PASS，validator、form 和 progress 相关测试通过。

- [ ] **Step 7: 提交 modal 能力扩展**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/modal `
        app/src/main/res/layout/item_tsm_modal_form_field.xml `
        app/src/main/res/layout/view_tsm_modal_progress.xml `
        app/src/main/res/drawable/bg_modal_input.xml `
        app/src/main/res/values/dimens.xml `
        app/src/main/res/values/strings.xml `
        app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/modal
git commit -m "feat: 增加 TV modal 表单确认和进度能力"
```

## Task 3: 替换播放页与收藏页的收藏相关原生框

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivity.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivityLayoutTest.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivityLayoutTest.kt`

- [ ] **Step 1: 先写播放页与收藏页入口测试**

```kotlin
@Test
fun `favorite playlist choice model keeps create new item after playlists`() {
    val choices = PlaybackActivity.buildFavoritePlaylistChoicesForTest(
        playlists = listOf("收藏夹", "夜间播放"),
        containedPlaylists = setOf("收藏夹"),
    )

    assertEquals("+ 新建播放列表", choices.last().label)
    assertTrue(choices.first().disabled)
    assertFalse(choices[1].disabled)
}
```

```kotlin
@Test
fun `add playlist tile remains first entry in favorites grid`() {
    val activity = Robolectric.buildActivity(FavoritesActivity::class.java).setup().get()
    val grid = activity.findViewById<GridLayout>(R.id.grid_playlists)
    assertEquals(activity.getString(R.string.favorites_add_playlist), grid.getChildAt(0).contentDescription)
}
```

- [ ] **Step 2: 运行测试确认当前缺少新 helper 或断言不成立**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.PlaybackActivityLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.FavoritesActivityLayoutTest"`

Expected: FAIL，提示 `buildFavoritePlaylistChoicesForTest` 不存在或断言不满足。

- [ ] **Step 3: 把 PlaybackActivity 的 3 处原生框替换为公共 modal**

```kotlin
private val modalCoordinator by lazy { TsmModalCoordinator(this) }

private fun showFavoritePlaylistDialog() {
    val track = currentFavoriteTrack() ?: run {
        showPlaybackToast(getString(R.string.favorites_empty_current_track))
        return
    }
    val choices = buildFavoritePlaylistChoices(track)
    modalCoordinator.showListModal(
        ListModalSpec(
            sectionLabel = getString(R.string.favorites_title),
            title = getString(R.string.favorites_select_playlist),
            message = "将当前歌曲加入以下播放列表",
            rows = choices.map { choice ->
                ModalListRow(
                    key = choice.playlistId ?: "create_new",
                    label = choice.label,
                    enabled = !choice.disabled,
                    onClick = {
                        when {
                            choice.createNew -> showCreatePlaylistAndAddDialog(track)
                            choice.disabled -> showPlaybackToast(getString(R.string.favorites_already_in_playlist))
                            choice.playlistId != null -> addTrackToPlaylist(choice.playlistId, track)
                        }
                    },
                )
            },
        )
    )
}
```

- [ ] **Step 4: 把 FavoritesActivity 的新建歌单与失效确认替换为公共 modal**

```kotlin
private val modalCoordinator by lazy { TsmModalCoordinator(this) }

private fun showCreatePlaylistDialog() {
    val existing = repository.getPlaylists().map { it.name }.toSet()
    val dialog = modalCoordinator.showFormModal(
        FormModalSpec(
            sectionLabel = getString(R.string.favorites_title),
            title = getString(R.string.favorites_new_playlist),
            fields = listOf(
                FormFieldSpec(
                    key = "playlist_name",
                    label = "名称",
                    initialValue = "",
                    hint = getString(R.string.favorites_playlist_name_hint),
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                )
            ),
            primaryAction = ModalAction("创建", isPrimary = true),
        )
    )
    modalCoordinator.bindFormPrimaryAction(dialog, "playlist_name") { values ->
        val name = values.getValue("playlist_name")
        val error = TsmModalFormValidators.validatePlaylistName(name, existing)
        if (error != null) {
            modalCoordinator.updateFieldError(dialog, "playlist_name", error)
            return@bindFormPrimaryAction false
        }
        repository.createPlaylist(name.trim())
        showPlaylistGrid()
        true
    }
}
```

- [ ] **Step 5: 再跑页面层测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.PlaybackActivityLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.FavoritesActivityLayoutTest"`

Expected: PASS，播放页和收藏页的 modal 数据构造与基础布局测试通过。

- [ ] **Step 6: 提交收藏相关 modal 替换**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt `
        app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivity.kt `
        app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivityLayoutTest.kt `
        app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivityLayoutTest.kt
git commit -m "feat: 统一收藏相关 TV modal"
```

## Task 4: 替换 SMB 浏览页的连接管理与配置原生框

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 先补 SMB modal 数据构造测试**

```kotlin
@Test
fun `connection manager actions keep edit create and switch order`() {
    val labels = TvBrowseFragment.buildConnectionManagerActionLabelsForTest(hasEditableConnection = true)
    assertEquals(listOf("编辑当前连接", "新建连接", "切换连接"), labels)
}
```

- [ ] **Step 2: 跑测试验证 helper 尚不存在**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest"`

Expected: FAIL，提示 `buildConnectionManagerActionLabelsForTest` 不存在。

- [ ] **Step 3: 用 Action/List/Form modal 重写 TvBrowseFragment 的 3 个入口**

```kotlin
private val modalCoordinator by lazy { TsmModalCoordinator(requireActivity()) }

private fun showConnectionManagerDialog() {
    modalCoordinator.showActionModal(
        ActionModalSpec(
            sectionLabel = "SMB",
            title = "连接管理",
            message = "当前连接：${configText(viewModel.state.value.config)}",
            actions = listOfNotNull(
                if (viewModel.state.value.config.host.isNotBlank()) {
                    ModalAction("编辑当前连接", isPrimary = true) { showConfigDialog(false) }
                } else null,
                ModalAction("新建连接") { showConfigDialog(true) },
                ModalAction("切换连接") { showSwitchDialog() },
            ),
        )
    )
}
```

- [ ] **Step 4: 对 SMB 配置表单接入字段校验**

```kotlin
private fun validateSmbConfig(values: Map<String, String>): Pair<SmbConfig?, Map<String, String>> {
    val host = values.getValue("host").trim()
    val errors = linkedMapOf<String, String>()
    if (host.isBlank()) {
        errors["host"] = "请输入 SMB 服务器地址"
    }
    val config = if (errors.isEmpty()) {
        SmbConfig(
            host = host,
            share = values.getValue("share").trim(),
            path = values.getValue("path").trim(),
            username = values.getValue("username").trim(),
            password = values.getValue("password"),
            guest = values.getValue("guest") == "true",
            smb1Enabled = values.getValue("smb1") == "true",
        )
    } else null
    return config to errors
}
```

- [ ] **Step 5: 跑 SMB 浏览页测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest"`

Expected: PASS，连接管理动作顺序测试与既有浏览页布局测试都通过。

- [ ] **Step 6: 提交 SMB modal 替换**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt `
        app/src/main/res/values/strings.xml `
        app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt
git commit -m "feat: 统一 SMB 连接管理 TV modal"
```

## Task 5: 替换首页、设置页和更新流程的原生框

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/MainActivity.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/update/AppUpdateManager.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/update/AppUpdateManagerTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 先为更新进度 handle 写失败测试**

```kotlin
@Test
fun `progress modal handle updates visible progress`() {
    val activity = Robolectric.buildActivity(FakeModalHostActivity::class.java).setup().get()
    val coordinator = TsmModalCoordinator(activity)
    val handle = coordinator.showProgressModal(
        ProgressModalSpec(
            sectionLabel = "更新",
            title = "正在下载更新",
            fileName = "demo.apk",
            percent = 0,
            indeterminate = false,
            message = "请稍候，下载完成后将进入安装流程。",
        )
    )

    handle.onProgress(68)

    assertEquals("68%", handle.dialog.findViewById<TextView>(R.id.tv_modal_progress_percent).text.toString())
}
```

- [ ] **Step 2: 跑更新测试验证失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.update.AppUpdateManagerTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinatorTest"`

Expected: 如果尚未实现 progress handle，则 FAIL。

- [ ] **Step 3: 替换 MainActivity 与 SettingsActivity 的原生框**

```kotlin
private fun maybePromptSleepDeviceAdmin() {
    val controller = SleepDeviceController(this)
    if (controller.isDeviceAdminActive() || UiSettingsStore.sleepAdminPromptShown(this)) return
    UiSettingsStore.setSleepAdminPromptShown(this, true)
    TsmModalCoordinator(this).showActionModal(
        ActionModalSpec(
            sectionLabel = "权限",
            title = "开启睡眠权限",
            message = "授权后，睡眠定时结束时可以让电视进入睡眠或息屏。",
            actions = listOf(
                ModalAction("暂不授权"),
                ModalAction("去授权", isPrimary = true) { controller.openDeviceAdminSettings(this) },
            ),
        )
    )
}
```

```kotlin
private fun showLyricsFontDialog(...) {
    val dialog = modalCoordinator.showFormModal(...)
    modalCoordinator.bindFormPrimaryAction(dialog, "value") { values ->
        val parsed = values["value"]?.trim()?.toIntOrNull()
        val error = TsmModalFormValidators.validateLyricsFont(parsed, UiSettingsStore.minLyricsFontSp, UiSettingsStore.maxLyricsFontSp)
        if (error != null) {
            modalCoordinator.updateFieldError(dialog, "value", error)
            return@bindFormPrimaryAction false
        }
        onSave(parsed!!)
        rebuildCurrentCategory(moveFocusToDetail = false)
        true
    }
}
```

- [ ] **Step 4: 替换 AppUpdateManager 的确认框与 ProgressDialog**

```kotlin
private fun showUpdateDialog(activity: Activity, update: UpdateInfo) {
    val coordinator = TsmModalCoordinator(activity)
    coordinator.showActionModal(
        ActionModalSpec(
            sectionLabel = "更新",
            title = "发现新版本 ${update.versionName}",
            message = "检测到适用于 ${currentAbi()} 的安装包：${update.assetName}",
            actions = listOf(
                ModalAction("稍后"),
                ModalAction("下载并安装", isPrimary = true) { downloadAndInstall(activity, update) },
            ),
        )
    )
}

private fun downloadAndInstall(activity: Activity, update: UpdateInfo) {
    val progressHandle = TsmModalCoordinator(activity).showProgressModal(
        ProgressModalSpec(
            sectionLabel = "更新",
            title = "正在下载更新",
            fileName = update.assetName,
            percent = 0,
            indeterminate = update.sizeBytes <= 0L,
            message = "请稍候，下载完成后将进入安装流程。",
        )
    )
    ...
    progressHandle.onProgress(percent)
    ...
    progressHandle.onDismiss()
}
```

- [ ] **Step 5: 跑更新与设置相关测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.update.AppUpdateManagerTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinatorTest"`

Expected: PASS，更新相关既有测试继续通过，新进度 modal 断言通过。

- [ ] **Step 6: 提交首页/设置/更新 modal 替换**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/MainActivity.kt `
        app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt `
        app/src/main/java/com/github/gbandszxc/tvmediaplayer/update/AppUpdateManager.kt `
        app/src/test/java/com/github/gbandszxc/tvmediaplayer/update/AppUpdateManagerTest.kt `
        app/src/main/res/values/strings.xml
git commit -m "feat: 统一系统提示与更新 TV modal"
```

## Task 6: 同步设计文档、项目手册并做整体验证

**Files:**
- Modify: `DESIGN.md`
- Modify: `docs/MANUAL.md`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/modal/TsmModalCoordinatorTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivityLayoutTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/FavoritesActivityLayoutTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/update/AppUpdateManagerTest.kt`

- [ ] **Step 1: 更新 `DESIGN.md` 的 modal 规范**

```md
### 公共 TV Modal

- 全局弹层统一使用居中面板 + 深色遮罩，不再使用 Android 原生 AlertDialog / ProgressDialog。
- modal 统一分为标题区、内容区、操作区；内容区可滚动，标题区和操作区固定。
- Action/List/Form/Confirm/Progress 五类 modal 复用同一套视觉 token 和焦点规则。
- Back 关闭并恢复打开前焦点；遮罩层不可聚焦。
```

- [ ] **Step 2: 更新 `docs/MANUAL.md` 的最近 UI/交互更新**

```md
17. 项目已新增统一的 TV 自绘 modal 体系，替换播放页、收藏页、SMB 连接管理、设置输入框、睡眠权限提示、更新确认框与下载进度框中的原生 AlertDialog / ProgressDialog。新 modal 统一采用深色面板、蓝色焦点和项目按钮语义，Back 关闭后会恢复打开前焦点。
```

- [ ] **Step 3: 运行核心单测集合**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.modal.TsmModalCoordinatorTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.PlaybackActivityLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.FavoritesActivityLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.update.AppUpdateManagerTest"`

Expected: PASS，所有 modal 相关测试通过。

- [ ] **Step 4: 运行全量单测**

Run: `.\gradlew.bat testDebugUnitTest`

Expected: PASS，项目单测基线继续通过。

- [ ] **Step 5: 生成 Debug 包验证资源和编译链路**

Run: `.\gradlew.bat assembleDebug`

Expected: BUILD SUCCESSFUL，生成两个 ABI 的 debug APK。

- [ ] **Step 6: 生成 Release 包做最终编译回归**

Run: `.\gradlew.bat assembleRelease`

Expected: BUILD SUCCESSFUL；若遇到 `lintVitalAnalyzeRelease` 依赖下载问题，再执行 `.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease` 作为兜底。

- [ ] **Step 7: 提交文档与验证收尾**

```powershell
git add DESIGN.md docs/MANUAL.md
git commit -m "docs: 同步 TV modal 设计与手册"
```

## Self-Review

- **Spec coverage:** 已覆盖公共 modal 基建、播放页/收藏页、SMB 浏览页、首页睡眠权限、设置页字号/间距、更新确认与下载进度、文档同步和测试/打包验证。
- **Placeholder scan:** 计划中没有 `TODO/TBD/implement later` 等占位词；每个 task 都给了明确文件路径、测试命令和代码片段。
- **Type consistency:** 全文统一使用 `TsmModalCoordinator`、`ActionModalSpec`、`ListModalSpec`、`FormModalSpec`、`ConfirmModalSpec`、`ProgressModalSpec`、`ModalAction`、`FormFieldSpec` 等命名；后续执行时保持一致，不要临时改名。

