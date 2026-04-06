# Android TV (arm64) 项目手册

## 已完成事项

详见 [CHANGELOG.md](./CHANGELOG.md)

**请自觉维护**

## 1. 当前项目定位

当前工程是“可编译、可侧载、可在 TV 上浏览骨架 UI”的第一阶段版本。

- 包名：`com.github.gbandszxc.tvmediaplayer`
- `minSdk`：`21`
- `targetSdk`：`34`
- `compileSdk`：`34`
- ABI：`arm64-v8a`
- UI：Leanback

## 2. 关键文件结构（当前）

```text
tsm-player/
├─ docs/MANUAL.md
├─ settings.gradle
├─ build.gradle
├─ gradle.properties
├─ local.properties
├─ gradlew
├─ gradlew.bat
├─ gradle/
│  └─ wrapper/
│     ├─ gradle-wrapper.jar
│     └─ gradle-wrapper.properties
├─ app/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/github/gbandszxc/tvmediaplayer/
│     │  ├─ MainActivity.kt
│     │  ├─ data/repo/{FakeSmbRepository.kt,JcifsSmbRepository.kt,SmbConfigStore.kt,SmbFailureMapper.kt}
│     │  ├─ domain/model/{SmbConfig.kt,SmbEntry.kt,SavedSmbConnection.kt}
│     │  ├─ domain/repo/SmbRepository.kt
│     │  ├─ lyrics/{LrcParser.kt,SmbLyricsRepository.kt}
│     │  ├─ playback/{PlaybackService.kt,PlaybackQueueBuilder.kt,SmbMediaItemFactory.kt}
│     │  └─ ui/
│     │     ├─ TvBrowseFragment.kt
│     │     ├─ TvBrowserViewModel.kt
│     │     └─ AppFonts.kt
│     └─ res/
│        ├─ layout/activity_main.xml
│        ├─ font/{misans_regular.ttf,misans_medium.ttf,misans_bold.ttf,misans_family.xml}
│        ├─ values/{strings.xml,colors.xml,themes.xml}
│        ├─ drawable/{ic_launcher_foreground.xml,tv_banner.xml}
│        └─ mipmap-anydpi-v26/{ic_launcher.xml,ic_launcher_round.xml}
└─ docs/spec/
   ├─ plan.md
   ├─ next-steps.md
   ├─ design.md
   └─ release-checklist.md
```

## 3. 常用命令

在项目根目录执行（Windows PowerShell）：

```powershell
# Debug（TV 与手机/平板均可侧载，包名后缀 .debug）
.\gradlew.bat assembleDebug

# Release（签名包，正式发布用）
.\gradlew.bat assembleRelease

# Release（规避 lintVital 依赖下载问题）
.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease

# 清理
.\gradlew.bat clean
```

若当前终端未切到 JDK 17，可临时指定：

```powershell
cmd /c "set JAVA_HOME=C:\D\Develop\Java\jdk-17.0.16+8&& set PATH=%JAVA_HOME%\bin;%PATH%&& .\gradlew.bat assembleDebug"
```

## 4. 产物位置

```text
Debug:   app\build\outputs\apk\debug\tsm-player-debug-<versionName>.apk
Release: app\build\outputs\apk\release\tsm-player-release-<versionName>.apk
```

## 5. Release 命令差异说明

1. `.\gradlew.bat assembleRelease`
直接执行 release 构建，沿用当前缓存；会包含 `lintVitalAnalyzeRelease`。

2. `.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease`
先清理再全量构建，且跳过 `lintVitalAnalyzeRelease` 任务。适合当前机器偶发的 TLS 握手失败场景（下载 lint 依赖中断）时使用。

3. 什么时候用哪条
- 网络正常时优先用标准命令：`assembleRelease`
- 出现 `lintVitalAnalyzeRelease` 依赖下载失败时，用 `-x lintVitalAnalyzeRelease` 兜底打包

## 6. 构建变体说明

项目只有 `debug` 和 `release` 两个构建变体，不再区分 flavor：

1. `debug`
- 包名：`com.github.gbandszxc.tvmediaplayer.debug`（带 `.debug` 后缀，可与 release 并行安装）
- `android.software.leanback required=false`，可在手机/平板安装侧载调试

2. `release`
- 包名：`com.github.gbandszxc.tvmediaplayer`（正式包名）
- 使用签名配置，`required=false` 同样兼容多设备安装
## 7. 签名配置与安全

1. 项目已在 `app/build.gradle` 配置 `signingConfigs.release`。
2. 本地使用根目录 `key.properties` 读取签名参数。
3. `key.properties` 已加入 `.gitignore`，不会被提交。

## 8. 当前实现边界

1. 已完成 Media3 播放与后台通知主链路（含 SMB 流读取），并已落地首版播放页元信息/进度/歌词/封面展示。
2. 当前 SMB 浏览按目录逐级访问，尚未实现全库递归扫描聚合能力。
3. 当前播放能力为“临时队列”，尚未实现长期播放列表管理与播放历史。
4. 内嵌封面（APIC/FLAC PICTURE）与精确标签解析尚未接入专用解析器。
5. 歌词 UI 当前为三行窗口（上一行/当前行高亮/下一行），尚未实现整段滚动歌词与全屏歌词模式。
6. 播放页与设计图相比仍缺少更完整的大屏信息层级（如歌词全屏、更多遥控器快捷交互）。
7. 已可产出已签名 release，但仍建议补充签名校验与安装回归测试流程。
8. 浏览页会为当前激活的 SMB 连接持久化最近浏览目录；应用退到后台或页面重建后，会优先恢复到离开前层级，而不是仅回到连接初始路径。

## 9. 最近 UI/交互更新
1. SMB 浏览页的“播放控制”面板已移到“文件浏览”上方，结构顺序调整后仍保留原始按钮状态与焦点关系，便于用户先快速触达播放相关操作再滚动到文件列表。
2. 播放页底部按钮栏在“返回文件页”后新增 `btn_locate`（文本“定位”），采用 `bg_button_light_yellow` 并复用 `#3A2A00` 字色，保持现有焦点链（`nextFocusUp` 指向进度条）。该按钮将用于快速跳回当前播放曲目的目录，补全“播放目录定位”能力。

## 10. 浏览导航增强

当前在 TV 浏览页中，当目录项过多时可以长按确认进入快速定位模式，模式面板会展示当前滚动百分比、上下整屏跳与左右 10% 分段跳动的提示，确认键接受落点、返回键取消。快速定位的计算与状态由 `TvBrowserViewModel` 驱动，UI 仅负责展现面板以及进度数字。

浏览页已保证 `MENU` 键在任意焦点位置（文件项、顶部按钮、快速定位模式）都可触发“连接管理”。

目录焦点会在 `SmbConfigStore` 中以“连接命名空间 + 目录路径”为粒度保存锚点，返回同一路径时由 ViewModel 恢复焦点到离开前的条目；若条目已失效则按历史 `anchor.index` 回到最近可用位置（clamp 到列表范围），并继续提示“目录内容已变化，已回到开头”。

当 `activeConnectionId == null`（未保存/临时配置）时，ViewModel 会基于 `host/share/path/username/guest/smb1Enabled` 生成稳定且不含密码的指纹命名空间，避免不同临时连接共享同一空锚点空间。

设置页的 “清理缓存” 会调用 `SmbConfigStore.clearBrowseCache()`，除了歌词与封面磁盘缓存还会重置这些目录焦点锚点，缓存清除后需要重新浏览一次才能再生成新锚点。

## 11. 当前已知基线问题
在 `2026-04-06` 于 worktree `feature/tv-browse-long-list-navigation` 执行 `.\gradlew.bat testDebugUnitTest` 时，存在以下既有失败，当前作为基线问题记录，后续长列表导航功能开发默认不以修复它们为本次范围：

1. `com.github.gbandszxc.tvmediaplayer.playback.SampleMediaValidationTest.lrcSampleShouldBeParsable`
2. `com.github.gbandszxc.tvmediaplayer.playback.SampleMediaValidationTest.mp3SampleShouldContainEmbeddedArtwork`
3. `com.github.gbandszxc.tvmediaplayer.playback.SmbPathResolverTest.track9SampleShouldParseTimestamps`

若后续整体验证仍需跑全量单测，应在结果说明中单独标注这 3 个已知失败，避免和本轮功能回归混淆。

