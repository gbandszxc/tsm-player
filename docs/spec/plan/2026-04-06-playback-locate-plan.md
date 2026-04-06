# 播放目录定位与浏览页布局调整 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 调整 SMB 浏览器页布局，并新增“定位”能力，使用户可从播放页或上次播放记录准确跳回歌曲所在目录。

**Architecture:** 通过增强 `LastPlaybackStore` 快照字段，补齐“当前播放目录 + 来源连接/配置”的持久化能力；在浏览页 ViewModel 内统一处理目录定位与跨连接切换；播放页只负责发起定位请求与确认交互。

**Tech Stack:** Kotlin, Android View XML, AndroidX Media3, DataStore, JUnit4, kotlinx-coroutines-test

---

## File Map

- Modify: `app/src/main/res/layout/fragment_tv_browser.xml`
- Modify: `app/src/main/res/layout/activity_playback.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModel.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/LastPlaybackStore.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackService.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/SmbConfigStore.kt`
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackLocationResolver.kt`
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackLocationResolverTest.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/LastPlaybackStoreSnapshotTest.kt`
- Modify: `docs/spec/design.md`
- Modify: `docs/MANUAL.md`

### Task 1: 路径解析与快照模型

**Files:**
- Create: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackLocationResolver.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/LastPlaybackStore.kt`
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackLocationResolverTest.kt`
- Create: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/playback/LastPlaybackStoreSnapshotTest.kt`

- [ ] Step 1: 先写解析器单测，覆盖同连接目录、跨连接目录、旧快照回退三种场景
- [ ] Step 2: 运行目标单测，确认按预期失败
- [ ] Step 3: 实现 `PlaybackLocationResolver` 与 `LastPlaybackStore` 新字段、兼容读取
- [ ] Step 4: 重新运行目标单测，确认通过
- [ ] Step 5: 自检 API 命名与字段兼容性

### Task 2: 浏览页定位状态流

**Files:**
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModel.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/data/repo/SmbConfigStore.kt`
- Modify: `app/src/test/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowserViewModelTest.kt`

- [ ] Step 1: 先写 ViewModel 单测，覆盖同连接定位与跨连接切换定位
- [ ] Step 2: 运行目标单测，确认按预期失败
- [ ] Step 3: 在 ViewModel 中加入定位目标解析、跨连接切换和路径持久化逻辑
- [ ] Step 4: 重新运行目标单测，确认通过
- [ ] Step 5: 自检状态字段、toast、确认请求事件是否职责清晰

### Task 3: 播放页与浏览页 UI 接入

**Files:**
- Modify: `app/src/main/res/layout/fragment_tv_browser.xml`
- Modify: `app/src/main/res/layout/activity_playback.xml`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/PlaybackActivity.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/ui/TvBrowseFragment.kt`
- Modify: `app/src/main/java/com/github/gbandszxc/tvmediaplayer/playback/PlaybackService.kt`

- [ ] Step 1: 调整浏览页布局顺序，并在播放页增加“定位”按钮
- [ ] Step 2: 接入播放页定位入口、跨连接确认框和返回浏览页跳转
- [ ] Step 3: 保证播放页与服务侧保存快照时写入新增字段
- [ ] Step 4: 运行受影响单测，确认无回归
- [ ] Step 5: 自检焦点导航、按钮显示条件和旧入口行为是否保持一致

### Task 4: 文档同步与整体验证

**Files:**
- Modify: `docs/spec/design.md`
- Modify: `docs/MANUAL.md`

- [ ] Step 1: 更新 `docs/spec/design.md` 中浏览页和播放页 ASCII 设计
- [ ] Step 2: 更新 `docs/MANUAL.md`，记录“播放目录定位”和浏览页布局变化
- [ ] Step 3: 运行完整单测命令
- [ ] Step 4: 运行 Debug 打包命令
- [ ] Step 5: 检查结果并整理风险说明

