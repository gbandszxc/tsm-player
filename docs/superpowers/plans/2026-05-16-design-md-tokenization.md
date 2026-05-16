# DESIGN.md Migration And UI Tokenization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将旧设计草图按 Google Labs Code 的 `design.md` 规范升级并迁移为根目录 `DESIGN.md`，同步最新 UI 状态，并把 Android XML 中的通用颜色、圆角、描边、间距等抽取为公共 token / style 资源后统一引用。

**Architecture:** 以根目录 `DESIGN.md` 作为当前 UI/交互设计规范，文件格式遵循 <https://github.com/google-labs-code/design.md>：YAML front matter 提供机器可读 token，正文按 Overview、Colors、Typography、Layout、Elevation & Depth、Shapes、Components、Do's and Don'ts 的固定顺序说明设计理由。Android 资源层新增语义化 token：`colors.xml` 管颜色，`dimens.xml` 管尺寸/圆角/描边/字号，`styles.xml` 管可复用文本与控件样式；现有 drawable/layout/theme 改为引用这些 token，避免后续直接写硬编码样式值。

**Tech Stack:** Android XML resources, Leanback, Kotlin Activity/Fragment, PowerShell, Gradle Debug build.

---

## Subagent Assignment

实现时建议使用 3 个子代理并行探索或分工执行，主代理负责整合与最终验证：

- **子代理 A：文档迁移与 design.md 规范整理**  
  负责 `DESIGN.md`、`AGENTS.md`、`docs/MANUAL.md` 和旧引用路径扫描；必须按 Google Labs Code `design.md` 规范组织 `DESIGN.md` 的 YAML front matter 与正文固定章节顺序。不得修改 Android 资源。
- **子代理 B：资源 token 抽取**  
  负责 `app/src/main/res/values/colors.xml`、新增 `dimens.xml`、新增/更新 `styles.xml`，以及 drawable selector 的 token 引用。不得改布局结构。
- **子代理 C：布局 token 引用替换**  
  负责 layout/theme 中颜色、尺寸、字号、padding/margin 的 token 引用替换。不得改变交互逻辑或 Kotlin 行为。
- **主代理：审查与验证**  
  统一检查 selector 状态顺序、文档路径、Debug 打包和 git diff。

---

### Task 1: 迁移并更新设计规范文档

**Files:**
- Move: `D:\ProjectSpace\github\tsm-player\docs\spec\design.md` -> `D:\ProjectSpace\github\tsm-player\DESIGN.md`
- Modify: `D:\ProjectSpace\github\tsm-player\DESIGN.md`
- Modify: `D:\ProjectSpace\github\tsm-player\docs\MANUAL.md`
- Modify: `D:\ProjectSpace\github\tsm-player\docs\spec\problem\v1.0.0.md`
- Modify: `D:\ProjectSpace\github\tsm-player\docs\spec\plan\2026-04-06-playback-locate-plan.md`
- Optional historical prompt update: `D:\ProjectSpace\github\tsm-player\docs\spec\prompt\ReqInit.txt`

- [ ] **Step 1: Confirm current references**

Run:

```powershell
rg -n "docs/spec/design\.md|@docs/spec/design\.md|DESIGN\.md|视觉规范" .
```

Expected: show current direct references, including `docs/MANUAL.md`, `docs/spec/problem/v1.0.0.md`, and `docs/spec/plan/2026-04-06-playback-locate-plan.md`.

- [ ] **Step 2: Move the design document**

Use a git-aware move:

```powershell
git mv <旧设计草图路径> DESIGN.md
```

Expected: `git status --short` shows the old design sketch renamed to `DESIGN.md`.

- [ ] **Step 3: Rewrite `DESIGN.md` to the Google Labs Code `design.md` format**

Replace the old free-form document header with YAML front matter followed by ordered Markdown sections. Use this skeleton exactly as the target shape; fill the page sketches from the old design sketch into the `Components` section instead of creating extra top-level sections:

```markdown
---
description: "Android TV SMB 音乐播放器的当前 UI/交互设计规范与 Android XML 样式 token 来源。"
tokens:
  colors:
    ui_bg_app: "#0F0F23"
    ui_bg_app_deep: "#0B1220"
    ui_bg_sidebar: "#0D1A2E"
    ui_bg_panel: "#1B2437"
    ui_bg_panel_deep: "#111827"
    ui_bg_panel_muted: "#14233A"
    ui_bg_item: "#2A2E39"
    ui_bg_item_focus: "#2B3548"
    ui_bg_overlay: "#DD0F172A"
    ui_transparent: "#00000000"
    ui_text_primary: "#EAF2FF"
    ui_text_on_accent: "#FFFFFF"
    ui_text_secondary: "#CBD5E1"
    ui_text_muted: "#94A3B8"
    ui_text_warning_dark: "#3A2A00"
    ui_accent_blue: "#2563EB"
    ui_accent_blue_focus: "#1D4ED8"
    ui_accent_blue_text: "#60A5FA"
    ui_accent_blue_stroke: "#93C5FD"
    ui_accent_blue_stroke_soft: "#BFDBFE"
    ui_accent_green: "#16A34A"
    ui_accent_green_focus: "#059669"
    ui_accent_green_stroke: "#10B981"
    ui_accent_green_text: "#A7F3D0"
    ui_accent_amber: "#F59E0B"
    ui_accent_amber_focus: "#D97706"
    ui_accent_amber_stroke: "#FDE68A"
    ui_accent_yellow: "#FCD34D"
    ui_accent_yellow_focus: "#F59E0B"
    ui_accent_yellow_stroke: "#FEF3C7"
    ui_divider: "#1E3A5F"
    ui_tag_dir: "#1E3A5F"
    ui_tag_audio: "#064E3B"
  spacing:
    ui_space_xs: "4dp"
    ui_space_sm: "6dp"
    ui_space_md: "8dp"
    ui_space_lg: "10dp"
    ui_space_xl: "12dp"
    ui_space_2xl: "14dp"
    ui_space_3xl: "16dp"
    ui_space_4xl: "20dp"
    ui_space_5xl: "24dp"
    ui_space_screen: "28dp"
  radii:
    ui_radius_indicator: "2dp"
    ui_radius_tag: "3dp"
    ui_radius_item: "4dp"
    ui_radius_button: "6dp"
    ui_radius_panel: "8dp"
  typography:
    ui_text_caption: "12sp"
    ui_text_body: "14sp"
    ui_text_body_large: "16sp"
    ui_text_title: "18sp"
    ui_text_section_title: "20sp"
    ui_text_page_title: "22sp"
    ui_text_screen_title: "26sp"
    ui_text_hero: "28sp"
---

# Android TV UI/交互设计规范

> 最后同步：2026-05-16，基于当前 `main` 实现状态。

## Overview

TSM Player 是面向 Android TV 的 SMB 本地音乐播放器。界面优先服务遥控器操作、远距离阅读、稳定焦点反馈和低干扰播放体验。

## Colors

颜色以深色背景、蓝色焦点、绿色播放操作和黄色提醒操作组成。Android XML 使用 `@color/ui_*`，不得新增通用硬编码颜色。

## Typography

字体使用 MiSans。Android XML 字号使用 `@dimen/ui_text_*`，页面标题、分区标题、正文和辅助文本保持清晰层级。

## Layout

页面按 TV 横屏优先设计，内容区使用稳定尺寸、明确焦点链和可滚动容器；现有 ASCII 页面结构草图放在 `Components` 中维护。

## Elevation & Depth

项目不使用复杂投影层级。深浅背景、描边、焦点框和浮层透明背景承担视觉层次。

## Shapes

圆角使用 `@dimen/ui_radius_*`；常用规则为标签 3dp、列表项 4dp、按钮 6dp、面板 8dp、选中指示条 2dp。

## Components

### 公共样式类

- `@style/TsmTextPrimary`：主文本。
- `@style/TsmTextSecondary`：辅助文本。
- `@style/TsmTextPageTitle`：页面大标题。
- `@style/TsmTextSectionTitle`：内容区标题。
- `@style/TsmButtonPrimary`：主操作按钮。
- `@style/TsmButtonDark`：低强调操作按钮。
- `@style/TsmButtonSuccess`：成功/播放类操作按钮。
- `@style/TsmButtonWarning`：恢复、继续播放、定位等提醒型操作按钮。

### 页面结构草图

将旧文档中的首页、连接管理弹窗、SMB 配置弹窗、播放页、全屏歌词页、设置页 ASCII 草图迁移到这里，并同步当前实现状态。

### 遥控器与焦点规则

在这里记录 D-Pad、OK、Back、Menu、SeekBar、快速定位模式、设置页 selected/focused 双语义。

### 缓存与恢复行为

在这里记录上次播放、封面/歌词缓存、目录锚点、清缓存影响。

## Do's and Don'ts

- Do: UI 修改前先读取 `DESIGN.md`。
- Do: XML 通用样式使用 `@color/ui_*`、`@dimen/ui_*`、`@style/Tsm*` 和可复用 `@drawable/bg_*`。
- Do: 设置页分类保持 selected 和 focused 的视觉语义分离。
- Don't: 在 XML 中新增通用硬编码颜色、圆角、描边或字号。
- Don't: 调整 selector 时破坏 `state_focused + state_selected` 的优先级。
- Don't: 为 TV 工作流添加会抢走遥控器焦点的装饰性元素。
```

Important: the Google `design.md` linter expects the major headings above and warns on unknown ordering. Keep custom content as subsections under `Components` or bullet content under the known headings.

- [ ] **Step 4: Add current behavior content under the required sections**

Do not add extra top-level sections such as `## 遥控器与焦点规则` or `## 缓存与恢复行为`. Put them under `## Components` as `###` subsections:

```markdown
### 遥控器与焦点规则

- D-Pad 上下左右用于在当前页面可聚焦控件间移动；OK/确认用于进入目录、播放曲目、切换设置或触发按钮。
- Back 在浏览页优先返回上级目录，在设置页退出设置，在播放页返回上一层页面；全屏图片预览中 Back 退出预览。
- Menu 在浏览页任意焦点位置都可打开连接管理。
- 播放页默认焦点停在进度条，进度条按上可聚焦封面图；封面图获得焦点时显示直角高亮边框，OK 进入图片全屏预览。
- 设置页左侧分类区分“当前已进入分类”和“遥控器焦点”：当前分类使用左侧蓝色竖线 + 低亮底色，焦点项使用亮蓝填充 + 明亮描边。

### 缓存与恢复行为

- 浏览页按连接与目录路径保存目录焦点锚点；再次进入同一路径时优先恢复上次焦点，条目失效时回落到最近可用位置。
- 清理缓存会清空歌词缓存、封面缓存和浏览焦点锚点。
- 播放页支持继续上次播放；黄色按钮在无活跃播放且存在上次播放记录时显示“继续上次播放”。
- 歌词与封面支持磁盘缓存；播放页和全屏歌词页复用同一缓存键策略。

### 公共样式 Token

Android XML 不应直接写通用颜色、圆角、描边、字号、间距值。新增或修改 UI 时优先使用：

- 颜色：`@color/ui_*`
- 尺寸/圆角/描边/字号：`@dimen/ui_*`
- 可复用文本和控件样式：`@style/Tsm*`
- 可复用背景：`@drawable/bg_*`

图标、launcher 资产和一次性插画允许保留自身颜色；页面背景、面板、按钮、焦点、设置项、列表项、标签等必须使用公共 token。
```

- [ ] **Step 5: Lint `DESIGN.md` format**

Run:

```powershell
npx @google/design.md lint DESIGN.md
```

Expected: no lint errors. If the command needs to download the package, allow `npx` to fetch it. If the linter reports section ordering warnings, keep the required top-level headings in this exact order: Overview, Colors, Typography, Layout, Elevation & Depth, Shapes, Components, Do's and Don'ts.

- [ ] **Step 6: Update moved references**

Replace direct references:

```text
旧设计草图路径 -> DESIGN.md
旧设计草图引用 -> @DESIGN.md
```

Update these known files:

```powershell
rg -n "docs/spec/design\.md|@docs/spec/design\.md" docs README.md AGENTS.md
```

Expected after replacement:

```powershell
rg -n "docs/spec/design\.md|@docs/spec/design\.md" docs README.md AGENTS.md
```

returns no active direct references, except historical text intentionally left unchanged with an explanatory note.

- [ ] **Step 7: Update `docs/MANUAL.md` file tree**

Change the file structure section so `DESIGN.md` appears at repo root and the old design sketch entry is removed. Also correct the stale plan entry to `docs/spec/plan/plan.md` if present.

- [ ] **Step 8: Run documentation reference check**

Run:

```powershell
rg -n "docs/spec/design\.md|@docs/spec/design\.md|DESIGN\.md" .
```

Expected: only `DESIGN.md` references remain for the current design spec.

- [ ] **Step 9: Commit Task 1**

```powershell
git add DESIGN.md docs/MANUAL.md docs/spec/problem/v1.0.0.md docs/spec/plan/2026-04-06-playback-locate-plan.md docs/spec/prompt/ReqInit.txt
git commit -m "docs: 迁移设计规范到根目录" -m "- 将旧设计草图更名为 DESIGN.md`n- 按 Google design.md 规范组织设计文档`n- 同步当前 UI、焦点与缓存行为说明`n- 更新旧设计文档引用路径"
```

---

### Task 2: 定义公共 UI Token 与样式类

**Files:**
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\values\colors.xml`
- Create: `D:\ProjectSpace\github\tsm-player\app\src\main\res\values\dimens.xml`
- Create or Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\values\styles.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\values\themes.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\DESIGN.md`

- [ ] **Step 1: Expand `colors.xml` with semantic tokens**

Keep `ic_launcher_background`, then add:

```xml
<color name="ui_bg_app">#0F0F23</color>
<color name="ui_bg_app_deep">#0B1220</color>
<color name="ui_bg_sidebar">#0D1A2E</color>
<color name="ui_bg_panel">#1B2437</color>
<color name="ui_bg_panel_deep">#111827</color>
<color name="ui_bg_panel_muted">#14233A</color>
<color name="ui_bg_item">#2A2E39</color>
<color name="ui_bg_item_focus">#2B3548</color>
<color name="ui_bg_overlay">#DD0F172A</color>
<color name="ui_transparent">#00000000</color>

<color name="ui_text_primary">#EAF2FF</color>
<color name="ui_text_on_accent">#FFFFFF</color>
<color name="ui_text_secondary">#CBD5E1</color>
<color name="ui_text_muted">#94A3B8</color>
<color name="ui_text_warning_dark">#3A2A00</color>

<color name="ui_accent_blue">#2563EB</color>
<color name="ui_accent_blue_focus">#1D4ED8</color>
<color name="ui_accent_blue_text">#60A5FA</color>
<color name="ui_accent_blue_stroke">#93C5FD</color>
<color name="ui_accent_blue_stroke_soft">#BFDBFE</color>
<color name="ui_accent_green">#16A34A</color>
<color name="ui_accent_green_focus">#059669</color>
<color name="ui_accent_green_stroke">#10B981</color>
<color name="ui_accent_green_text">#A7F3D0</color>
<color name="ui_accent_amber">#F59E0B</color>
<color name="ui_accent_amber_focus">#D97706</color>
<color name="ui_accent_amber_stroke">#FDE68A</color>
<color name="ui_accent_yellow">#FCD34D</color>
<color name="ui_accent_yellow_focus">#F59E0B</color>
<color name="ui_accent_yellow_stroke">#FEF3C7</color>

<color name="ui_divider">#1E3A5F</color>
<color name="ui_tag_dir">#1E3A5F</color>
<color name="ui_tag_audio">#064E3B</color>
```

- [ ] **Step 2: Add `dimens.xml`**

Create `app/src/main/res/values/dimens.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="ui_radius_indicator">2dp</dimen>
    <dimen name="ui_radius_tag">3dp</dimen>
    <dimen name="ui_radius_item">4dp</dimen>
    <dimen name="ui_radius_button">6dp</dimen>
    <dimen name="ui_radius_panel">8dp</dimen>

    <dimen name="ui_stroke_focus">2dp</dimen>
    <dimen name="ui_stroke_artwork_focus">3dp</dimen>
    <dimen name="ui_indicator_width">4dp</dimen>

    <dimen name="ui_space_xs">4dp</dimen>
    <dimen name="ui_space_sm">6dp</dimen>
    <dimen name="ui_space_md">8dp</dimen>
    <dimen name="ui_space_lg">10dp</dimen>
    <dimen name="ui_space_xl">12dp</dimen>
    <dimen name="ui_space_2xl">14dp</dimen>
    <dimen name="ui_space_3xl">16dp</dimen>
    <dimen name="ui_space_4xl">20dp</dimen>
    <dimen name="ui_space_5xl">24dp</dimen>
    <dimen name="ui_space_screen">28dp</dimen>

    <dimen name="ui_text_caption">12sp</dimen>
    <dimen name="ui_text_body">14sp</dimen>
    <dimen name="ui_text_body_large">16sp</dimen>
    <dimen name="ui_text_title">18sp</dimen>
    <dimen name="ui_text_section_title">20sp</dimen>
    <dimen name="ui_text_page_title">22sp</dimen>
    <dimen name="ui_text_screen_title">26sp</dimen>
    <dimen name="ui_text_hero">28sp</dimen>
</resources>
```

- [ ] **Step 3: Add reusable style classes in `styles.xml`**

Create or extend `app/src/main/res/values/styles.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="TsmTextPrimary">
        <item name="android:textColor">@color/ui_text_primary</item>
        <item name="android:fontFamily">@font/misans_regular</item>
    </style>

    <style name="TsmTextSecondary">
        <item name="android:textColor">@color/ui_text_secondary</item>
        <item name="android:fontFamily">@font/misans_regular</item>
    </style>

    <style name="TsmTextPageTitle">
        <item name="android:textColor">@color/ui_accent_blue_text</item>
        <item name="android:textSize">@dimen/ui_text_screen_title</item>
        <item name="android:fontFamily">@font/misans_bold</item>
    </style>

    <style name="TsmTextSectionTitle">
        <item name="android:textColor">@color/ui_accent_blue_text</item>
        <item name="android:textSize">@dimen/ui_text_page_title</item>
        <item name="android:fontFamily">@font/misans_bold</item>
    </style>

    <style name="TsmButtonBase">
        <item name="android:minHeight">44dp</item>
        <item name="android:paddingStart">@dimen/ui_space_3xl</item>
        <item name="android:paddingEnd">@dimen/ui_space_3xl</item>
        <item name="android:paddingTop">@dimen/ui_space_lg</item>
        <item name="android:paddingBottom">@dimen/ui_space_lg</item>
        <item name="android:textSize">@dimen/ui_text_body_large</item>
        <item name="android:fontFamily">@font/misans_medium</item>
        <item name="android:focusable">true</item>
        <item name="android:clickable">true</item>
    </style>

    <style name="TsmButtonPrimary" parent="TsmButtonBase">
        <item name="android:background">@drawable/bg_button_primary</item>
        <item name="android:textColor">@color/ui_text_on_accent</item>
    </style>

    <style name="TsmButtonDark" parent="TsmButtonBase">
        <item name="android:background">@drawable/bg_button_dark</item>
        <item name="android:textColor">@color/ui_text_on_accent</item>
    </style>

    <style name="TsmButtonSuccess" parent="TsmButtonBase">
        <item name="android:background">@drawable/bg_button_green</item>
        <item name="android:textColor">@color/ui_text_on_accent</item>
    </style>

    <style name="TsmButtonWarning" parent="TsmButtonBase">
        <item name="android:background">@drawable/bg_button_light_yellow</item>
        <item name="android:textColor">@color/ui_text_warning_dark</item>
    </style>
 </resources>
```

- [ ] **Step 4: Update `themes.xml` token references**

Change hardcoded theme colors:

```xml
<item name="android:windowBackground">@color/ui_bg_app</item>
<item name="defaultBrandColor">@color/ui_accent_green</item>
<item name="defaultSearchColor">@color/ui_accent_blue_focus</item>
```

- [ ] **Step 5: Keep `DESIGN.md` front matter tokens synchronized**

When adding or renaming Android resource tokens, update the YAML front matter in `DESIGN.md` in the same step. The front matter must remain the source that agents can parse before editing XML:

```yaml
tokens:
  colors:
    ui_bg_app: "#0F0F23"
  spacing:
    ui_space_xs: "4dp"
  radii:
    ui_radius_panel: "8dp"
  typography:
    ui_text_body: "14sp"
```

Then keep `### 公共样式类` under `## Components` with this content:

```markdown
### 公共样式类

- `@style/TsmTextPrimary`：主文本。
- `@style/TsmTextSecondary`：辅助文本。
- `@style/TsmTextPageTitle`：页面大标题。
- `@style/TsmTextSectionTitle`：内容区标题。
- `@style/TsmButtonPrimary`：主操作按钮。
- `@style/TsmButtonDark`：低强调操作按钮。
- `@style/TsmButtonSuccess`：成功/播放类操作按钮。
- `@style/TsmButtonWarning`：恢复、继续播放、定位等提醒型操作按钮。

新增 XML UI 样式必须优先引用 `@color/ui_*`、`@dimen/ui_*`、`@style/Tsm*` 和既有 `@drawable/bg_*`，不得新增散落的通用硬编码颜色或圆角。
```

- [ ] **Step 6: Validate `DESIGN.md` and resources**

Run:

```powershell
npx @google/design.md lint DESIGN.md
.\gradlew.bat :app:mergeDebugResources
```

Expected: design lint has no errors, Gradle returns `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Task 2**

```powershell
git add app/src/main/res/values/colors.xml app/src/main/res/values/dimens.xml app/src/main/res/values/styles.xml app/src/main/res/values/themes.xml DESIGN.md
git commit -m "style: 增加公共 UI 样式 token" -m "- 定义颜色、尺寸、圆角和描边 token`n- 增加可复用文本与按钮样式类`n- DESIGN.md 记录公共样式规则"
```

---

### Task 3: Drawable selector 使用 token 引用

**Files:**
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_button_primary.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_button_green.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_button_dark.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_button_amber.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_button_light_yellow.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_file_item.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_panel.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_settings_category.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_settings_category_indicator.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_settings_entry.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_artwork_focus.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_tag_audio.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\drawable\bg_tag_dir.xml`

- [ ] **Step 1: Replace button drawable literals**

For each button drawable, replace hardcoded colors/radius/stroke with tokens:

```xml
<corners android:radius="@dimen/ui_radius_button" />
<stroke android:width="@dimen/ui_stroke_focus" android:color="@color/ui_accent_blue_stroke_soft" />
```

Use this mapping:

```text
bg_button_primary: normal @color/ui_accent_blue, focused @color/ui_accent_blue_focus, stroke @color/ui_accent_blue_stroke_soft
bg_button_green: normal @color/ui_accent_green, focused @color/ui_accent_green_focus, stroke @color/ui_accent_green_text
bg_button_dark: normal #3F3F46 should become @color/ui_bg_item, focused #374151 should become @color/ui_bg_item_focus, stroke @color/ui_text_primary
bg_button_amber: normal @color/ui_accent_amber, focused @color/ui_accent_amber_focus, stroke @color/ui_accent_amber_stroke
bg_button_light_yellow: normal @color/ui_accent_yellow, focused @color/ui_accent_yellow_focus, stroke @color/ui_accent_yellow_stroke
```

If a mapped color does not exist from Task 2, add it to `colors.xml` before replacing.

- [ ] **Step 2: Replace panel, file item, settings entry, tags**

Use:

```text
bg_panel: radius @dimen/ui_radius_panel, solid @color/ui_bg_panel
bg_file_item: radius @dimen/ui_radius_item, focused solid @color/ui_bg_item_focus, focused stroke @color/ui_accent_green_stroke, default solid @color/ui_bg_item
bg_settings_entry: radius @dimen/ui_radius_panel, focused solid @color/ui_accent_blue_focus, focused stroke @color/ui_accent_blue_stroke, default solid @color/ui_bg_panel_deep
bg_tag_audio: radius @dimen/ui_radius_tag, solid @color/ui_tag_audio
bg_tag_dir: radius @dimen/ui_radius_tag, solid @color/ui_tag_dir
```

- [ ] **Step 3: Preserve settings category state ordering**

Keep `state_focused="true" state_selected="true"` before plain focused and selected. Use:

```xml
<corners android:radius="@dimen/ui_radius_panel" />
<solid android:color="@color/ui_accent_blue_focus" />
<stroke android:width="@dimen/ui_stroke_focus" android:color="@color/ui_accent_blue_stroke" />
```

For selected-only:

```xml
<solid android:color="@color/ui_bg_panel_muted" />
```

For indicator:

```xml
<corners android:radius="@dimen/ui_radius_indicator" />
<solid android:color="@color/ui_accent_blue_text" />
```

- [ ] **Step 4: Convert artwork focus stroke**

Change `bg_artwork_focus.xml` from pixel stroke to density-aware token:

```xml
<stroke android:width="@dimen/ui_stroke_artwork_focus" android:color="@color/ui_accent_blue_stroke" />
```

Expected: visual focus border stays visible on TV and no longer depends on raw `px`.

- [ ] **Step 5: Leave icons and launcher assets unchanged**

Do not alter:

```text
app/src/main/res/drawable/ic_github.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable/tv_banner.xml
```

Reason: these are icon/launcher/branding assets and should not be accidentally themed as generic UI chrome.

- [ ] **Step 6: Validate drawable resources**

Run:

```powershell
.\gradlew.bat :app:mergeDebugResources
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Task 3**

```powershell
git add app/src/main/res/drawable app/src/main/res/values/colors.xml app/src/main/res/values/dimens.xml
git commit -m "style: 统一 drawable 使用 UI token" -m "- 按语义 token 替换按钮、面板、列表和设置项背景`n- 保留设置页 selected/focused 状态优先级`n- 将封面焦点描边改为 dp token"
```

---

### Task 4: Layout 与 Theme 引用 token / style

**Files:**
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\layout\activity_settings.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\layout\item_settings_category.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\layout\item_settings_entry.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\layout\fragment_tv_browser.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\layout\activity_playback.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\layout\activity_lyrics_fullscreen.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\layout\item_file_entry.xml`
- Modify: `D:\ProjectSpace\github\tsm-player\app\src\main\res\values\styles.xml`

- [ ] **Step 1: Replace page and panel backgrounds**

Replace common hardcoded layout backgrounds:

```text
#0F0F23 -> @color/ui_bg_app
#0B1220 -> @color/ui_bg_app_deep
#0D1A2E -> @color/ui_bg_sidebar
#1E3A5F divider -> @color/ui_divider
#DD0F172A -> @color/ui_bg_overlay
```

Do not change view ids, focus attributes, `nextFocus*`, layout weights, or orientation.

- [ ] **Step 2: Replace text colors and title text sizes**

Use:

```text
#EAF2FF / #F8FAFC / #FFFFFF -> @color/ui_text_primary or @color/ui_text_on_accent depending on background
#CBD5E1 -> @color/ui_text_secondary
#94A3B8 -> @color/ui_text_muted
#60A5FA -> @color/ui_accent_blue_text
26sp -> @dimen/ui_text_screen_title
22sp -> @dimen/ui_text_page_title
20sp -> @dimen/ui_text_section_title
18sp -> @dimen/ui_text_title
16sp -> @dimen/ui_text_body_large
14sp -> @dimen/ui_text_body
12sp -> @dimen/ui_text_caption
```

Where a TextView is a reusable page title or section title, prefer `style="@style/TsmTextPageTitle"` or `style="@style/TsmTextSectionTitle"` instead of repeating `textColor/textSize/fontFamily`.

- [ ] **Step 3: Replace button attributes with style classes**

For XML buttons that already use these backgrounds, switch to style classes:

```xml
style="@style/TsmButtonPrimary"
style="@style/TsmButtonDark"
style="@style/TsmButtonSuccess"
style="@style/TsmButtonWarning"
```

Keep button-specific attributes such as `android:id`, `android:text`, `android:visibility`, `android:nextFocusUp`, `android:singleLine`, `android:ellipsize`, and `android:marqueeRepeatLimit`.

- [ ] **Step 4: Replace reusable spacing values**

Replace repeated margins/paddings with `@dimen/ui_space_*` only where the value matches a token exactly. Do not invent new layout behavior. Examples:

```text
4dp -> @dimen/ui_space_xs
6dp -> @dimen/ui_space_sm
8dp -> @dimen/ui_space_md
10dp -> @dimen/ui_space_lg
12dp -> @dimen/ui_space_xl
14dp -> @dimen/ui_space_2xl
16dp -> @dimen/ui_space_3xl
20dp -> @dimen/ui_space_4xl
24dp -> @dimen/ui_space_5xl
28dp -> @dimen/ui_space_screen
```

Preserve special dimensions that are behavioral, not stylistic, such as sidebar width `260dp`, artwork aspect layout, progress bar dimensions, and list item minimum heights unless a named token already exists for that exact semantic purpose.

- [ ] **Step 5: Preserve settings category layout stability**

In `item_settings_category.xml`, keep:

```xml
android:minHeight="58dp"
android:duplicateParentState="true"
```

Only replace matching style values with tokens:

```xml
android:layout_width="@dimen/ui_indicator_width"
android:layout_marginEnd="@dimen/ui_space_xl"
android:textSize="@dimen/ui_text_title"
```

- [ ] **Step 6: Search for remaining generic literals**

Run:

```powershell
Get-ChildItem -Path app\src\main\res -Recurse -Include *.xml | Select-String -Pattern '#[0-9A-Fa-f]{6,8}|android:radius="[0-9]+dp"|android:textSize="[0-9]+sp"'
```

Expected: remaining matches should be limited to launcher/icon/vector assets, truly one-off dimensions, or values intentionally not tokenized. If a remaining match is a generic UI color/radius/text size, replace it.

- [ ] **Step 7: Validate layout resources**

Run:

```powershell
.\gradlew.bat :app:mergeDebugResources
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit Task 4**

```powershell
git add app/src/main/res/layout app/src/main/res/values/styles.xml app/src/main/res/values/dimens.xml app/src/main/res/values/colors.xml
git commit -m "style: 布局引用公共样式 token" -m "- 页面背景、文本颜色、字号和间距改为 token 引用`n- 按钮统一使用 TsmButton 样式类`n- 保持遥控器焦点链和布局结构不变"
```

---

### Task 5: AGENTS 规则与最终验证

**Files:**
- Modify: `D:\ProjectSpace\github\tsm-player\AGENTS.md`
- Modify: `D:\ProjectSpace\github\tsm-player\docs\MANUAL.md`
- Modify: `D:\ProjectSpace\github\tsm-player\DESIGN.md`

- [ ] **Step 1: Add design rules to `AGENTS.md`**

Insert after current MANUAL maintenance rules and before commit-log rules:

```text
4. 涉及 UI、交互、遥控器焦点、页面布局、视觉样式、设置页结构、播放页/浏览页行为变更时，务必先读取并同步维护根目录 @DESIGN.md。该文件作为当前 UI/交互设计规范，格式遵循 https://github.com/google-labs-code/design.md；历史方案、计划和问题记录仍保留在 @docs/spec/ 下。
5. 设计样式时必须优先使用 @DESIGN.md YAML front matter 和正文中注明的公共样式 token：颜色使用 @color/ui_*，尺寸/圆角/描边/字号使用 @dimen/ui_*，可复用文本和按钮样式使用 @style/Tsm*，背景优先复用 @drawable/bg_*。除图标、launcher、一次性插画或确有说明的特殊尺寸外，不要在 XML 中新增通用硬编码颜色、圆角、描边、字号。
6. 文档迁移后，不再新增对旧设计草图路径的引用；需要引用设计规范时统一使用 @DESIGN.md。
```

Renumber the existing commit-log rule after these additions.

- [ ] **Step 2: Update `docs/MANUAL.md` recent UI note**

Add a note under recent UI / docs maintenance:

```markdown
5. 根目录 `DESIGN.md` 现在是当前 UI/交互设计规范，Android XML 样式已抽取为 `@color/ui_*`、`@dimen/ui_*`、`@style/Tsm*` 与可复用 `@drawable/bg_*`。后续 UI 修改需优先引用这些 token，避免新增通用硬编码样式。
```

- [ ] **Step 3: Run reference and hardcoded-value audits**

Run:

```powershell
rg -n "docs/spec/design\.md|@docs/spec/design\.md" .
npx @google/design.md lint DESIGN.md
Get-ChildItem -Path app\src\main\res -Recurse -Include *.xml | Select-String -Pattern '#[0-9A-Fa-f]{6,8}|android:radius="[0-9]+dp"|android:textSize="[0-9]+sp"'
```

Expected:

- First command returns no current design-doc references to the old design sketch path.
- `DESIGN.md` lint returns no errors.
- Second command returns only accepted exceptions: launcher/vector/icon assets, intentionally one-off layout measurements, or values explicitly documented in `DESIGN.md`.

- [ ] **Step 4: Run Debug build**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Existing warnings such as deprecated `ProgressDialog` may remain, but no new resource or compile errors are allowed.

- [ ] **Step 5: Manual UI smoke check**

Install or run the debug build on a TV/emulator when available and verify:

```text
首页：面板、按钮、文件项焦点颜色与改造前一致或更统一。
播放页：按钮栏、SeekBar、封面焦点框、图片全屏预览入口正常。
全屏歌词页：背景、标题、歌词颜色和返回按钮正常。
设置页：当前分类仍是左侧竖线，遥控器焦点仍是亮蓝填充与描边；未确认移动时右侧内容不切换。
```

- [ ] **Step 6: Final commit**

```powershell
git add AGENTS.md DESIGN.md docs/MANUAL.md
git commit -m "docs: 强化设计规范与 token 使用规则" -m "- AGENTS 增加 DESIGN.md 优先读取规则`n- 明确 XML 样式必须使用公共 token`n- 记录 UI token 化后的维护方式"
```

- [ ] **Step 7: Final status check**

Run:

```powershell
git status --short
```

Expected: clean working tree, unless the implementer intentionally keeps uncommitted review changes.

---

## Acceptance Criteria

- 根目录存在 `DESIGN.md`，且旧设计草图路径不再存在。
- `DESIGN.md` 遵循 Google Labs Code `design.md` 规范：包含 YAML front matter，正文顶级章节按 Overview、Colors、Typography、Layout、Elevation & Depth、Shapes、Components、Do's and Don'ts 顺序组织，并通过 `npx @google/design.md lint DESIGN.md`。
- `DESIGN.md` 同步当前 UI：浏览页快速定位、目录焦点锚点、播放页封面全屏预览、设置页当前分类竖线与焦点框、清缓存影响、应用内更新入口。
- `AGENTS.md` 明确要求 UI 设计先读 `DESIGN.md`，并要求 XML 样式使用公共 token。
- `colors.xml`、`dimens.xml`、`styles.xml` 承载公共 token / style 类。
- 通用 drawable 和 layout 不再新增散落的通用硬编码颜色、圆角、描边、字号。
- selector 状态行为不回退，尤其 `bg_settings_category.xml` 保持 `focused + selected` 优先级。
- `.\gradlew.bat assembleDebug` 成功。
