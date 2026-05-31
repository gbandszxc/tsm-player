# Browser Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 TV 文件列表页增加名称/大小/最后修改时间展示，以及“刷新 + 排序”工具栏和轻量排序下拉选择能力，默认文件名升序，目录始终在文件前，`..` 始终固定最上。

**Architecture:** 在 `SmbEntry` 和 SMB 仓库层补齐原始元信息，在 `TvBrowserViewModel` 统一维护排序状态并生成最终展示顺序，`TvBrowseFragment` 只负责渲染、焦点恢复和轻量下拉面板交互。列表布局、格式化和跑马灯行为通过 XML + Fragment 辅助函数落地，并补齐 ViewModel / 布局单测和 Debug 打包验证。

**Tech Stack:** Kotlin、Android XML、Robolectric、JUnit4、Coroutines Test、Gradle

---

## File Structure

- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/domain/model/SmbEntry.kt`
  - 扩展浏览实体，增加 `sizeBytes` 与 `lastModifiedAt`。
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/JcifsSmbRepository.kt`
  - 从 `SmbFile` 读取大小与最后修改时间，并对单项元数据异常做保守降级。
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/FakeSmbRepository.kt`
  - 补齐假数据元信息，避免调试和测试编译失败。
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModel.kt`
  - 新增排序枚举、状态字段、排序函数、选择排序入口和按排序后的条目恢复焦点。
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
  - 删除“重试连接”按钮逻辑，增加排序按钮、轻量排序面板、格式化辅助函数和名称跑马灯控制。
- Modify: `app/src/main/res/layout/fragment_tv_browser.xml`
  - 调整顶部工具区为“刷新 + 排序”。
- Modify: `app/src/main/res/layout/item_file_entry.xml`
  - 扩展列表行为类型标识、名称、大小、时间四段式布局。
- Modify: `app/src/main/res/values/strings.xml`
  - 新增排序选项文案、未知值占位符和可能的描述文本。
- Modify: `app/src/main/res/values/dimens.xml`
  - 新增文件大小列、时间列、排序按钮或轻量面板相关尺寸。
- Create: `app/src/main/res/layout/view_browser_sort_dropdown.xml`
  - 轻量排序下拉面板布局。
- Create: `app/src/main/res/layout/item_browser_sort_option.xml`
  - 排序项布局，复用 TV 选中/聚焦语义。
- Create: `app/src/main/res/drawable/bg_browser_sort_dropdown.xml`
  - 下拉面板背景。
- Create: `app/src/main/res/drawable/bg_browser_sort_option.xml`
  - 排序项默认/选中背景。
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`
  - 覆盖默认排序、目录优先、空值排末尾和排序切换焦点恢复。
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt`
  - 覆盖工具区顺序、重试按钮移除、列表新增列和排序下拉视图结构。
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/BrowserSortFormattingTest.kt`
  - 覆盖大小/时间格式化与排序标签文案。
- Modify: `DESIGN.md`
  - 同步浏览页“刷新 + 排序 + 三列元信息”的规范描述与 ASCII 草图。
- Modify: `docs/MANUAL.md`
  - 同步浏览页新增排序与元信息展示的项目记忆。

## Task 1: 扩展 SMB 元信息模型和仓库输出

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/domain/model/SmbEntry.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/JcifsSmbRepository.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/FakeSmbRepository.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackQueueBuilderTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`

- [ ] **Step 1: 先写会因 `SmbEntry` 缺少元信息而失败的编译型测试改动**

```kotlin
// app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt
private fun sampleFile(
    name: String,
    fullPath: String,
    sizeBytes: Long?,
    lastModifiedAt: Long?,
): SmbEntry = SmbEntry(
    name = name,
    fullPath = fullPath,
    isDirectory = false,
    streamUri = "smb://$fullPath",
    sizeBytes = sizeBytes,
    lastModifiedAt = lastModifiedAt,
)
```

- [ ] **Step 2: 运行单测，确认当前代码因 `SmbEntry` 构造参数不存在而失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest"
```

Expected:

```text
Compilation failed: No parameter with name 'sizeBytes'
```

- [ ] **Step 3: 最小化扩展 `SmbEntry`，保证现有调用方大多无需修改**

```kotlin
// app/src/main/java/com/github/gbandszxc/tvmediaplayer/domain/model/SmbEntry.kt
data class SmbEntry(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val streamUri: String? = null,
    val sizeBytes: Long? = null,
    val lastModifiedAt: Long? = null,
)
```

- [ ] **Step 4: 在 SMB 仓库中补齐元信息，单项失败时降级为 `null`**

```kotlin
// app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/JcifsSmbRepository.kt
private fun mapToEntry(file: SmbFile, currentPath: String): SmbEntry? {
    val name = file.name.trimEnd('/').trim()
    if (name.isBlank()) return null

    val fullPath = combinePath(currentPath, name)
    val lastModifiedAt = runCatching { file.lastModified() }.getOrNull()

    return if (file.isDirectory) {
        SmbEntry(
            name = name,
            fullPath = fullPath,
            isDirectory = true,
            sizeBytes = null,
            lastModifiedAt = lastModifiedAt,
        )
    } else {
        if (!isAudioFile(name)) return null
        SmbEntry(
            name = name,
            fullPath = fullPath,
            isDirectory = false,
            streamUri = file.canonicalPath,
            sizeBytes = runCatching { file.length() }.getOrNull(),
            lastModifiedAt = lastModifiedAt,
        )
    }
}
```

- [ ] **Step 5: 更新假仓库与受影响测试数据，显式给出样例元信息**

```kotlin
// app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/FakeSmbRepository.kt
SmbEntry(name = "ACG", fullPath = "${prefix}ACG", isDirectory = true, lastModifiedAt = 1_717_000_000_000)
SmbEntry(
    name = demoTrack1,
    fullPath = "${prefix}${demoTrack1}",
    isDirectory = false,
    streamUri = "${config.rootUrl()}/${Uri.encode(demoTrack1)}",
    sizeBytes = 26_634_854,
    lastModifiedAt = 1_716_000_000_000,
)
```

- [ ] **Step 6: 运行受影响单测，确认模型扩展没有破坏现有逻辑**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.playback.PlaybackQueueBuilderTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: 提交这一组基础模型改动**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/domain/model/SmbEntry.kt app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/JcifsSmbRepository.kt app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/FakeSmbRepository.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackQueueBuilderTest.kt
git commit -m "feat: 扩展浏览条目元信息"
```

## Task 2: 在 ViewModel 收口排序状态和稳定排序规则

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModel.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`

- [ ] **Step 1: 先补 ViewModel 失败测试，锁定默认排序和目录优先规则**

```kotlin
@Test
fun `load current path sorts directories first and defaults to name asc`() = runTest(dispatcher) {
    val entries = listOf(
        SmbEntry(name = "b.mp3", fullPath = "Music/b.mp3", isDirectory = false, sizeBytes = 3, lastModifiedAt = 3),
        SmbEntry(name = "Album Z", fullPath = "Music/Album Z", isDirectory = true, lastModifiedAt = 2),
        SmbEntry(name = "a.mp3", fullPath = "Music/a.mp3", isDirectory = false, sizeBytes = 1, lastModifiedAt = 1),
        SmbEntry(name = "Album A", fullPath = "Music/Album A", isDirectory = true, lastModifiedAt = 4),
    )
    val viewModel = buildViewModel(entriesByPath = mapOf("Music" to entries))
    advanceUntilIdle()

    assertEquals(listOf("Album A", "Album Z", "a.mp3", "b.mp3"), viewModel.state.value.entries.map { it.name })
    assertEquals(BrowserSortOption.NAME_ASC, viewModel.state.value.sortOption)
}
```

- [ ] **Step 2: 补第二组失败测试，锁定大小/时间排序和空值固定排末尾**

```kotlin
@Test
fun `select sort option keeps null size and null modified values at end of each group`() = runTest(dispatcher) {
    val entries = listOf(
        SmbEntry(name = "Dir B", fullPath = "Music/Dir B", isDirectory = true, lastModifiedAt = null),
        SmbEntry(name = "Dir A", fullPath = "Music/Dir A", isDirectory = true, lastModifiedAt = 10),
        SmbEntry(name = "track-big.flac", fullPath = "Music/track-big.flac", isDirectory = false, sizeBytes = 300, lastModifiedAt = null),
        SmbEntry(name = "track-small.flac", fullPath = "Music/track-small.flac", isDirectory = false, sizeBytes = 100, lastModifiedAt = 1),
    )
    val viewModel = buildViewModel(entriesByPath = mapOf("Music" to entries))
    advanceUntilIdle()

    viewModel.selectSortOption(BrowserSortOption.SIZE_DESC)
    assertEquals(listOf("Dir A", "Dir B", "track-big.flac", "track-small.flac"), viewModel.state.value.entries.map { it.name })

    viewModel.selectSortOption(BrowserSortOption.MODIFIED_ASC)
    assertEquals(listOf("Dir A", "Dir B", "track-small.flac", "track-big.flac"), viewModel.state.value.entries.map { it.name })
}
```

- [ ] **Step 3: 补第三组失败测试，锁定排序切换后同一路径焦点恢复**

```kotlin
@Test
fun `select sort option keeps restored focus on same full path when entry still exists`() = runTest(dispatcher) {
    val entries = listOf(
        SmbEntry(name = "b.mp3", fullPath = "Music/b.mp3", isDirectory = false, sizeBytes = 200, lastModifiedAt = 20),
        SmbEntry(name = "a.mp3", fullPath = "Music/a.mp3", isDirectory = false, sizeBytes = 100, lastModifiedAt = 10),
    )
    val viewModel = buildViewModel(entriesByPath = mapOf("Music" to entries))
    advanceUntilIdle()

    viewModel.onItemFocused(index = 0, entry = viewModel.state.value.entries.first { it.fullPath == "Music/a.mp3" })
    advanceUntilIdle()
    viewModel.selectSortOption(BrowserSortOption.SIZE_DESC)

    assertEquals("Music/a.mp3", viewModel.state.value.entries[viewModel.state.value.restoredFocusIndex!!].fullPath)
}
```

- [ ] **Step 4: 运行测试，确认 `BrowserSortOption` 等符号尚不存在而失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest"
```

Expected:

```text
Compilation failed: Unresolved reference 'BrowserSortOption'
```

- [ ] **Step 5: 在 ViewModel 中加入排序枚举、状态字段和统一排序函数**

```kotlin
enum class BrowserSortOption(
    val label: String,
) {
    NAME_ASC("文件名 ↑"),
    NAME_DESC("文件名 ↓"),
    SIZE_ASC("文件大小 ↑"),
    SIZE_DESC("文件大小 ↓"),
    MODIFIED_ASC("修改时间 ↑"),
    MODIFIED_DESC("修改时间 ↓"),
}

private fun sortEntries(entries: List<SmbEntry>, option: BrowserSortOption): List<SmbEntry> {
    val directories = entries.filter { it.isDirectory }
    val files = entries.filterNot { it.isDirectory }
    return sortGroup(directories, option) + sortGroup(files, option)
}

private fun sortGroup(entries: List<SmbEntry>, option: BrowserSortOption): List<SmbEntry> {
    val comparator = when (option) {
        BrowserSortOption.NAME_ASC -> compareBy<SmbEntry> { it.name.lowercase() }
        BrowserSortOption.NAME_DESC -> compareByDescending<SmbEntry> { it.name.lowercase() }
        BrowserSortOption.SIZE_ASC -> compareBy<SmbEntry>({ it.sizeBytes == null }, { it.sizeBytes ?: Long.MAX_VALUE }, { it.name.lowercase() })
        BrowserSortOption.SIZE_DESC -> compareBy<SmbEntry>({ it.sizeBytes == null }, { -(it.sizeBytes ?: Long.MIN_VALUE) }, { it.name.lowercase() })
        BrowserSortOption.MODIFIED_ASC -> compareBy<SmbEntry>({ it.lastModifiedAt == null }, { it.lastModifiedAt ?: Long.MAX_VALUE }, { it.name.lowercase() })
        BrowserSortOption.MODIFIED_DESC -> compareBy<SmbEntry>({ it.lastModifiedAt == null }, { -(it.lastModifiedAt ?: Long.MIN_VALUE) }, { it.name.lowercase() })
    }
    return entries.sortedWith(comparator)
}
```

- [ ] **Step 6: 在加载、切换排序和恢复焦点时统一使用排序后的列表**

```kotlin
fun selectSortOption(option: BrowserSortOption) {
    val snapshot = _state.value
    val focusedEntry = snapshot.restoredFocusIndex?.let(snapshot.entries::getOrNull)
    val sorted = sortEntries(snapshot.entries, option)
    _state.update {
        it.copy(
            sortOption = option,
            entries = sorted,
            restoredFocusIndex = focusedEntry?.let { entry -> sorted.indexOfFirst { it.fullPath == entry.fullPath } }?.takeIf { it >= 0 },
        )
    }
}
```

- [ ] **Step 7: 为测试添加 `buildViewModel` 辅助方法，避免样板代码重复**

```kotlin
private fun buildViewModel(
    browsePath: String = "Music",
    entriesByPath: Map<String, List<SmbEntry>>,
): TvBrowserViewModel {
    val config = sampleConfig(path = browsePath)
    val store = FakeBrowserConfigStore(
        state = SmbConfigStoreState(
            activeConfig = config,
            activeConnectionId = "conn-1",
            savedConnections = listOf(SavedSmbConnection("conn-1", "NAS", config)),
            activeBrowsePath = browsePath,
        ),
    )
    return TvBrowserViewModel(FakeSmbRepository(entriesByPath), store)
}
```

- [ ] **Step 8: 运行 ViewModel 单测，确认排序状态、锚点恢复和快速定位仍通过**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowseFastLocateCalculatorTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowseInteractionGuardsTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 9: 提交排序状态层改动**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModel.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt
git commit -m "feat: 增加浏览页排序状态"
```

## Task 3: 扩展浏览页布局和文件行展示

**Files:**
- Modify: `app/src/main/res/layout/fragment_tv_browser.xml`
- Modify: `app/src/main/res/layout/item_file_entry.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/BrowserSortFormattingTest.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt`

- [ ] **Step 1: 先补布局测试，锁定顶部按钮数量和顺序**

```kotlin
@Test
fun `browser file toolbar keeps refresh before sort and removes retry button`() {
    val context = RuntimeEnvironment.getApplication()
    val root = LayoutInflater.from(context)
        .inflate(R.layout.fragment_tv_browser, FrameLayout(context), false)

    val refresh = root.findViewById<View>(R.id.btn_refresh)
    val sort = root.findViewById<View>(R.id.btn_sort)
    val retry = root.findViewById<View?>(R.id.btn_retry)

    assertNotNull(refresh)
    assertNotNull(sort)
    assertNull(retry)
    assertTrue(leftOf(refresh, sort))
}
```

- [ ] **Step 2: 再补布局测试，锁定列表新增名称/大小/时间列**

```kotlin
@Test
fun `file row exposes name size and modified columns`() {
    val context = RuntimeEnvironment.getApplication()
    val row = LayoutInflater.from(context)
        .inflate(R.layout.item_file_entry, FrameLayout(context), false)

    assertNotNull(row.findViewById<View>(R.id.tv_tag))
    assertNotNull(row.findViewById<View>(R.id.tv_name))
    assertNotNull(row.findViewById<View>(R.id.tv_size))
    assertNotNull(row.findViewById<View>(R.id.tv_modified))
}
```

- [ ] **Step 3: 补格式化测试，锁定大小/时间/未知值文案**

```kotlin
@Test
fun `format size returns readable unit and unknown placeholder`() {
    assertEquals("--", TvBrowseFragment.formatSizeForTest(null, isDirectory = false))
    assertEquals("--", TvBrowseFragment.formatSizeForTest(null, isDirectory = true))
    assertEquals("1.5 KB", TvBrowseFragment.formatSizeForTest(1536, isDirectory = false))
}

@Test
fun `format modified time returns full timestamp`() {
    assertEquals("2026-05-31 08:09:10", TvBrowseFragment.formatModifiedTimeForTest(1_780_213_750_000L, java.util.TimeZone.getTimeZone("Asia/Shanghai")))
}
```

- [ ] **Step 4: 运行测试，确认新控件 ID 和格式化 helper 还不存在**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowserSortFormattingTest"
```

Expected:

```text
Compilation failed: Unresolved reference 'btn_sort'
```

- [ ] **Step 5: 修改布局，删除重试按钮，加入排序按钮和三列文件行**

```xml
<!-- app/src/main/res/layout/fragment_tv_browser.xml -->
<Button
    android:id="@+id/btn_refresh"
    style="@style/TsmButtonDark"
    android:text="@string/browser_refresh_current_dir" />

<Button
    android:id="@+id/btn_sort"
    style="@style/TsmButtonDark"
    android:layout_marginStart="@dimen/ui_space_lg"
    android:text="@string/browser_sort_name_asc" />
```

```xml
<!-- app/src/main/res/layout/item_file_entry.xml -->
<TextView
    android:id="@+id/tv_name"
    android:layout_width="0dp"
    android:layout_weight="1"
    android:ellipsize="end"
    android:marqueeRepeatLimit="marquee_forever"
    android:singleLine="true" />

<TextView
    android:id="@+id/tv_size"
    android:layout_width="@dimen/ui_browser_size_column_width"
    android:gravity="end" />

<TextView
    android:id="@+id/tv_modified"
    android:layout_width="@dimen/ui_browser_time_column_width"
    android:gravity="end" />
```

- [ ] **Step 6: 在 Fragment 中接线新按钮和格式化 helper，并为名称列启用焦点态跑马灯**

```kotlin
private lateinit var btnSort: Button

private fun bindViews(root: View) {
    btnSort = root.findViewById(R.id.btn_sort)
}

private fun bindFileRowText(tvName: TextView, tvSize: TextView, tvModified: TextView, entry: SmbEntry) {
    tvName.text = entry.name
    tvSize.text = formatFileSize(entry.sizeBytes, entry.isDirectory)
    tvModified.text = formatModifiedTime(entry.lastModifiedAt)
    tvName.isSelected = false
}
```

- [ ] **Step 7: 添加可测试的格式化辅助函数**

```kotlin
@VisibleForTesting
internal fun formatFileSize(sizeBytes: Long?, isDirectory: Boolean): String {
    if (isDirectory || sizeBytes == null) return "--"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = sizeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    val display = if (unitIndex == 0 || value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
    return "$display ${units[unitIndex]}"
}

@VisibleForTesting
internal fun formatModifiedTime(timestamp: Long?): String {
    if (timestamp == null) return "--"
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timestamp))
}

companion object {
    @VisibleForTesting
    fun formatSizeForTest(sizeBytes: Long?, isDirectory: Boolean): String =
        TvBrowseFragment().formatFileSize(sizeBytes, isDirectory)

    @VisibleForTesting
    fun formatModifiedTimeForTest(timestamp: Long?, timeZone: java.util.TimeZone): String {
        if (timestamp == null) return "--"
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        formatter.timeZone = timeZone
        return formatter.format(java.util.Date(timestamp))
    }
}
```

- [ ] **Step 8: 运行布局与格式化测试，确认资源结构和 helper 工作正常**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowserSortFormattingTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 9: 提交布局和显示层改动**

```powershell
git add app/src/main/res/layout/fragment_tv_browser.xml app/src/main/res/layout/item_file_entry.xml app/src/main/res/values/strings.xml app/src/main/res/values/dimens.xml app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/BrowserSortFormattingTest.kt
git commit -m "feat: 扩展浏览页文件行信息展示"
```

## Task 4: 实现轻量排序下拉面板和排序交互

**Files:**
- Create: `app/src/main/res/layout/view_browser_sort_dropdown.xml`
- Create: `app/src/main/res/layout/item_browser_sort_option.xml`
- Create: `app/src/main/res/drawable/bg_browser_sort_dropdown.xml`
- Create: `app/src/main/res/drawable/bg_browser_sort_option.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`

- [ ] **Step 1: 先补布局测试，锁定轻量下拉面板不是全屏 modal 外壳**

```kotlin
@Test
fun `browser sort dropdown uses dedicated lightweight layout instead of modal shell`() {
    val context = RuntimeEnvironment.getApplication()
    val dropdown = LayoutInflater.from(context)
        .inflate(R.layout.view_browser_sort_dropdown, FrameLayout(context), false)

    assertNotNull(dropdown.findViewById<View>(R.id.container_sort_options))
    assertNull(dropdown.findViewById<View?>(R.id.container_modal_content))
}
```

- [ ] **Step 2: 补 ViewModel/Fragment 行为测试，锁定排序按钮标签随状态更新**

```kotlin
@Test
fun `sort label follows selected sort option`() = runTest(dispatcher) {
    val viewModel = buildViewModel(entriesByPath = mapOf("Music" to emptyList()))
    advanceUntilIdle()

    viewModel.selectSortOption(BrowserSortOption.MODIFIED_DESC)

    assertEquals(BrowserSortOption.MODIFIED_DESC, viewModel.state.value.sortOption)
    assertEquals("修改时间 ↓", viewModel.state.value.sortOption.label)
}
```

- [ ] **Step 3: 运行测试，确认轻量下拉布局和容器 ID 尚不存在**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest"
```

Expected:

```text
Android resource linking failed: layout/view_browser_sort_dropdown not found
```

- [ ] **Step 4: 新建轻量下拉布局和排序项布局**

```xml
<!-- app/src/main/res/layout/view_browser_sort_dropdown.xml -->
<LinearLayout
    android:id="@+id/container_sort_dropdown"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@drawable/bg_browser_sort_dropdown">

    <LinearLayout
        android:id="@+id/container_sort_options"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical" />
</LinearLayout>
```

```xml
<!-- app/src/main/res/layout/item_browser_sort_option.xml -->
<TextView
    android:id="@+id/tv_sort_option"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_browser_sort_option"
    android:focusable="true"
    android:singleLine="true" />
```

- [ ] **Step 5: 在 Fragment 中实现展开、自动收起、Back 关闭和当前选项聚焦**

```kotlin
private var sortDropdownView: View? = null
private lateinit var rootOverlayContainer: FrameLayout

private fun showSortDropdown() {
    if (sortDropdownView != null) return
    val dropdown = layoutInflater.inflate(R.layout.view_browser_sort_dropdown, rootOverlayContainer, false)
    renderSortOptions(dropdown.findViewById(R.id.container_sort_options))
    rootOverlayContainer.addView(dropdown)
    sortDropdownView = dropdown
}

private fun onSortOptionSelected(option: BrowserSortOption) {
    viewModel.selectSortOption(option)
    dismissSortDropdown(requestFocusToSortButton = true)
}
```

- [ ] **Step 6: 在 `render` 中回写排序按钮文案，并让列表重建指纹包含排序状态**

```kotlin
private fun render(state: TvBrowserState) {
    btnSort.text = state.sortOption.label
    if (browseListRenderGate.shouldRebuild("${state.currentPath}|${state.sortOption.name}", displayEntries)) {
        renderFileItems(state, displayEntries)
    }
}
```

- [ ] **Step 7: 运行相关单测，确认下拉结构、自动应用和标签回写都通过**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 提交排序交互层改动**

```powershell
git add app/src/main/res/layout/view_browser_sort_dropdown.xml app/src/main/res/layout/item_browser_sort_option.xml app/src/main/res/drawable/bg_browser_sort_dropdown.xml app/src/main/res/drawable/bg_browser_sort_option.xml app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt
git commit -m "feat: 增加浏览页排序下拉交互"
```

## Task 5: 同步文档并做完整验证

**Files:**
- Modify: `DESIGN.md`
- Modify: `docs/MANUAL.md`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragmentLayoutTest.kt`
- Test: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/BrowserSortFormattingTest.kt`

- [ ] **Step 1: 更新设计规范，补充浏览页“刷新 + 排序 + 元信息列”描述和 ASCII 草图**

```md
### 首页：SMB 浏览器

- 文件浏览顶部工具区仅保留“刷新当前目录”和“排序”两个可聚焦控件。
- 排序控件按 OK 展开轻量锚定式下拉面板，选中后立即收起并应用。
- 文件行显示类型、名称、大小、修改时间；大小和时间列固定宽度，名称列聚焦时跑马灯。
```

- [ ] **Step 2: 更新项目手册，记录浏览页新增排序与元信息展示**

```md
## 9. 最近 UI/交互更新
20. SMB 浏览页文件列表已增加名称、文件大小和最后修改时间展示；顶部工具区收敛为“刷新当前目录”和排序下拉，默认按文件名升序排序，目录始终位于文件前，`..` 始终固定最上。
```

- [ ] **Step 3: 运行浏览页相关单测集合**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragmentLayoutTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowserSortFormattingTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowseFastLocateCalculatorTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowseInteractionGuardsTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: 执行 Debug 打包验证资源和 Kotlin 改动可编译**

Run:

```powershell
$env:JAVA_HOME="C:\D\Develop\Java\jdk-17.0.16+8"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: 提交文档与最终验证改动**

```powershell
git add DESIGN.md docs/MANUAL.md
git commit -m "docs: 同步浏览页排序与元信息设计"
```

- [ ] **Step 6: 整理最终变更并准备交付**

```powershell
git status --short
git log -5 --oneline
```

Expected:

```text
工作区仅包含本次实现相关改动，最近提交包含 feat/docs 前缀
```
