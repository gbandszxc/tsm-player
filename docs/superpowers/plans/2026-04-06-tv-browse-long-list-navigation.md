# TV 浏览页长列表导航 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 TV 文件浏览页增加“长按确认进入快速定位模式”和“目录级焦点锚点恢复”能力，并让设置页“清理缓存”同步清空这些锚点数据。

**Architecture:** 通过把“快速定位计算”和“目录锚点恢复”下沉为可测试的 ViewModel/Store 逻辑，UI 层只负责模式切换、按键映射和临时提示；目录锚点按“连接 + 目录路径”持久化到现有 `SmbConfigStore`，并由设置页统一通过清缓存入口重置。实现时保持现有目录浏览与播放定位能力不回退。

**Tech Stack:** Kotlin, Android View XML, AndroidX Fragment/ViewModel, DataStore Preferences, JUnit4, kotlinx-coroutines-test, Gradle

---

## File Map

- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/SmbConfigStore.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/domain/model/BrowseFocusAnchor.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateState.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateCalculator.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModel.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt`
- Modify: `app/src/main/res/layout/fragment_tv_browser.xml`
- Modify: `app/src/main/res/layout/item_file_entry.xml`
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateCalculatorTest.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`
- Modify: `docs/MANUAL.md`

## UI Sketch

```text
默认浏览态
┌─────────────────────────────────┐
│ 当前路径：/Albums/Jay/          │
│                                 │
│  Track 116.flac                 │
│▶ Track 117.flac                 │
│  Track 118.flac                 │
│                                 │
│ 长按确认：进入快速定位模式       │
└─────────────────────────────────┘

快速定位模式
┌─────────────────────────────────┐   ┌──┐
│ 当前路径：/Albums/Jay/          │   │  │
│                                 │   │██│
│  Track 101.flac                 │   │  │
│▶ Track 117.flac                 │   │  │
│  Track 133.flac                 │   └──┘
│                                 │
│ 快速定位模式 42%                │
│ ↑/↓ 整屏跳  ←/→ 10% 分段跳       │
│ 确认接受  返回取消               │
└─────────────────────────────────┘
```

## Baseline Note

- 该计划撰写时曾临时记录 3 个 `Track9` 相关单测为“基线失败”。
- 截至 `2026-04-06` 已重新核验：`.\gradlew.bat testDebugUnitTest` 通过，这 3 项不再视为本计划的遗留基线问题。

### Task 1: 快速定位纯逻辑与单测

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateState.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateCalculator.kt`
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateCalculatorTest.kt`

- [ ] **Step 1: 写失败单测，覆盖整屏跳、10% 分段跳、短列表禁用**

```kotlin
@Test
fun `page jump moves by visible window size`() {
    val state = BrowseFastLocateState(
        totalCount = 200,
        currentIndex = 40,
        visibleWindowSize = 8
    )

    val jumped = BrowseFastLocateCalculator.jumpPage(state, direction = 1)

    assertEquals(48, jumped.currentIndex)
}

@Test
fun `segment jump moves by ten percent of list`() {
    val state = BrowseFastLocateState(
        totalCount = 200,
        currentIndex = 40,
        visibleWindowSize = 8
    )

    val jumped = BrowseFastLocateCalculator.jumpSegment(state, direction = 1)

    assertEquals(60, jumped.currentIndex)
}

@Test
fun `fast locate disabled when list is shorter than two windows`() {
    assertFalse(BrowseFastLocateCalculator.canEnter(totalCount = 11, visibleWindowSize = 6))
}
```

- [ ] **Step 2: 运行目标单测，确认先红**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowseFastLocateCalculatorTest"`

Expected: FAIL，报 `BrowseFastLocateState` / `BrowseFastLocateCalculator` 未定义。

- [ ] **Step 3: 写最小实现**

```kotlin
data class BrowseFastLocateState(
    val totalCount: Int,
    val currentIndex: Int,
    val visibleWindowSize: Int
) {
    val progressPercent: Int
        get() = if (totalCount <= 1) 0 else (currentIndex * 100f / (totalCount - 1)).toInt()
}

object BrowseFastLocateCalculator {
    fun canEnter(totalCount: Int, visibleWindowSize: Int): Boolean =
        visibleWindowSize > 0 && totalCount >= visibleWindowSize * 2

    fun jumpPage(state: BrowseFastLocateState, direction: Int): BrowseFastLocateState =
        state.copy(currentIndex = clamp(state.currentIndex + direction * state.visibleWindowSize, state.totalCount))

    fun jumpSegment(state: BrowseFastLocateState, direction: Int): BrowseFastLocateState {
        val delta = maxOf(1, (state.totalCount * 0.1f).toInt())
        return state.copy(currentIndex = clamp(state.currentIndex + direction * delta, state.totalCount))
    }

    private fun clamp(index: Int, totalCount: Int): Int =
        index.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
}
```

- [ ] **Step 4: 重跑目标单测，确认转绿**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowseFastLocateCalculatorTest"`

Expected: PASS

- [ ] **Step 5: 提交**

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateState.kt app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateCalculator.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/BrowseFastLocateCalculatorTest.kt
git commit -m "test: add fast locate calculator coverage"
```

### Task 2: 目录锚点存储、缓存清理联动与 ViewModel 行为

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/domain/model/BrowseFocusAnchor.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/SmbConfigStore.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModel.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`

- [ ] **Step 1: 先补失败单测，覆盖锚点恢复、冲突回退到头部、清缓存清空锚点**

```kotlin
@Test
fun `restores single anchor when re-entering directory`() = runTest(dispatcher) {
    val store = FakeBrowserConfigStore(
        state = sampleState(activeBrowsePath = "Music/Albums"),
        anchors = mapOf("conn-1|Music/Albums" to BrowseFocusAnchor("Track 118.flac", 5, 10L))
    )
    val repository = FakeSmbRepository(
        entriesByPath = mapOf("Music/Albums" to listOf(
            SmbEntry(name = "Track 113.flac", fullPath = "Music/Albums/Track 113.flac", isDirectory = false),
            SmbEntry(name = "Track 118.flac", fullPath = "Music/Albums/Track 118.flac", isDirectory = false)
        ))
    )

    val viewModel = TvBrowserViewModel(repository, store)
    advanceUntilIdle()

    assertEquals(1, viewModel.state.value.restoredFocusIndex)
    assertEquals("已恢复上次位置", viewModel.state.value.inlineMessage)
}

@Test
fun `falls back to top when anchor conflicts with playback target`() = runTest(dispatcher) {
    // 锚点和最近播放目录冲突时，列表焦点回到 0
}

@Test
fun `clear cache clears browse anchors`() = runTest(dispatcher) {
    val store = FakeBrowserConfigStore(state = sampleState(), anchors = mutableMapOf("conn-1|Music" to BrowseFocusAnchor("Track", 3, 1L)))

    store.clearBrowseCache()

    assertTrue(store.anchors.isEmpty())
}
```

- [ ] **Step 2: 运行目标单测，确认先红**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest"`

Expected: FAIL，报新的状态字段、锚点存储接口或清缓存接口缺失。

- [ ] **Step 3: 以最小实现扩展数据模型和 Store 接口**

```kotlin
data class BrowseFocusAnchor(
    val itemKey: String,
    val index: Int,
    val updatedAt: Long
)

interface BrowserConfigStore {
    suspend fun loadBrowseAnchor(connectionId: String?, directoryPath: String): BrowseFocusAnchor?
    suspend fun saveBrowseAnchor(connectionId: String?, directoryPath: String, anchor: BrowseFocusAnchor)
    suspend fun clearBrowseCache()
}
```

```kotlin
data class TvBrowserState(
    val restoredFocusIndex: Int? = null,
    val inlineMessage: String? = null,
    val fastLocate: BrowseFastLocateState? = null,
    val isFastLocateMode: Boolean = false
)
```

- [ ] **Step 4: 在 ViewModel 中补最小行为**

```kotlin
fun enterFastLocate(visibleWindowSize: Int): Boolean
fun jumpFastLocateByPage(direction: Int)
fun jumpFastLocateBySegment(direction: Int)
fun acceptFastLocate()
fun cancelFastLocate()
fun onItemFocused(index: Int, entry: SmbEntry)
```

实现约束：
- 单一明确锚点时恢复 `restoredFocusIndex`
- 多强锚点冲突时恢复为 `0`
- 清缓存入口只清锚点，不改 SMB 连接信息

- [ ] **Step 5: 重跑目标单测并提交**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest"`

Expected: PASS

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/domain/model/BrowseFocusAnchor.kt app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/SmbConfigStore.kt app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModel.kt app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt
git commit -m "feat: persist browse anchors for tv browser"
```

### Task 3: 浏览页 UI 接入快速定位模式

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Modify: `app/src/main/res/layout/fragment_tv_browser.xml`
- Modify: `app/src/main/res/layout/item_file_entry.xml`

- [ ] **Step 1: 先加失败测试或最小可验证断言**

由于当前没有 Fragment UI 测试基础，本任务以“先接入 ViewModel 已存在状态 + 编译验证 + 手工焦点路径自检”为准。不要新增脆弱的 instrumentation 测试。

- [ ] **Step 2: 扩展布局，为模式提示和比例条预留区域**

```xml
<LinearLayout
    android:id="@+id/panel_fast_locate"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:visibility="gone">

    <TextView
        android:id="@+id/tv_fast_locate_hint"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="快速定位模式 42%" />

    <View
        android:id="@+id/view_fast_locate_bar"
        android:layout_width="12dp"
        android:layout_height="72dp" />
</LinearLayout>
```

- [ ] **Step 3: 在 Fragment 里接长按确认和模式按键**

```kotlin
itemView.setOnLongClickListener {
    viewModel.enterFastLocate(visibleWindowSize = estimateVisibleWindowSize())
}

root.setOnKeyListener { _, keyCode, event ->
    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
    if (!viewModel.state.value.isFastLocateMode) return@setOnKeyListener false
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> viewModel.jumpFastLocateByPage(-1)
        KeyEvent.KEYCODE_DPAD_DOWN -> viewModel.jumpFastLocateByPage(1)
        KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.jumpFastLocateBySegment(-1)
        KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.jumpFastLocateBySegment(1)
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> viewModel.acceptFastLocate()
        KeyEvent.KEYCODE_BACK -> viewModel.cancelFastLocate()
        else -> return@setOnKeyListener false
    }
    true
}
```

- [ ] **Step 4: 在 render 中按状态更新 UI**

```kotlin
panelFastLocate.visibility = if (state.isFastLocateMode) View.VISIBLE else View.GONE
tvFastLocateHint.text = "快速定位模式 ${state.fastLocate?.progressPercent ?: 0}%"

state.restoredFocusIndex?.let { index ->
    filesContainer.post { filesContainer.getChildAt(index)?.requestFocus() }
}
```

- [ ] **Step 5: 编译验证并提交**

Run: `.\gradlew.bat compileDebugKotlin`

Expected: BUILD SUCCESSFUL

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt app/src/main/res/layout/fragment_tv_browser.xml app/src/main/res/layout/item_file_entry.xml
git commit -m "feat: add tv browser fast locate mode"
```

### Task 4: 设置页清缓存联动、文档与整体验证

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt`
- Modify: `docs/MANUAL.md`

- [ ] **Step 1: 写失败单测或伪造调用验证清缓存契约**

如果不方便给 `SettingsActivity` 写单测，则先给 Store 加可验证 API，并在已有 ViewModel/Store 测试中断言 `clearBrowseCache()` 被调用。

- [ ] **Step 2: 接入设置页清缓存**

```kotlin
SettingsItem(
    title = "清理缓存",
    descriptionProvider = { "歌词、封面和浏览锚点缓存" }
) {
    PlaybackLyricsCache.clearDisk(this)
    PlaybackArtworkCache.clearDisk(this)
    lifecycleScope.launch {
        SmbConfigStore(applicationContext).clearBrowseCache()
    }
    Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 3: 更新文档**

在 `docs/MANUAL.md` 追加：
- 长列表快速定位模式
- 目录焦点锚点恢复
- 清缓存会重置浏览锚点
- 单测基线状态随验证结果更新

- [ ] **Step 4: 跑受影响单测和 Debug 打包**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.github.gbandszxc.tvmediaplayer.ui.BrowseFastLocateCalculatorTest" --tests "com.github.gbandszxc.tvmediaplayer.ui.TvBrowserViewModelTest"
.\gradlew.bat assembleDebug
```

Expected:
- 新增/修改的目标单测 PASS
- `assembleDebug` SUCCESS

- [ ] **Step 5: 跑全量单测并记录结果**

Run: `.\gradlew.bat testDebugUnitTest`

Expected:
- 新功能相关测试 PASS
- 若无失败，则同步清理 `Baseline Note` 与手册中的过期记录

```powershell
git add app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/SettingsActivity.kt docs/MANUAL.md
git commit -m "docs: record tv browser fast locate behavior"
```

## Self-Review

- Spec coverage:
  - 快速定位模式：Task 1 + Task 2 + Task 3
  - 目录锚点恢复：Task 2 + Task 3
  - 清缓存清空锚点：Task 2 + Task 4
  - 文档同步与基线问题记录：Task 4
- Placeholder scan:
  - 未使用 `TODO` / `TBD`
  - UI 任务明确说明当前不新增 instrumentation 测试，改用编译验证和状态单测支撑
- Type consistency:
  - 统一使用 `BrowseFocusAnchor`、`BrowseFastLocateState`、`clearBrowseCache()`、`restoredFocusIndex`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-06-tv-browse-long-list-navigation.md`.

Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

User already selected Subagent-Driven execution for this feature, so proceed with fresh subagents per task in the isolated worktree.
