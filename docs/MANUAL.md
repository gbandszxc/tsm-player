# Android TV（arm64 + armv7）项目手册

## 已完成事项

详见 [CHANGELOG.md](./CHANGELOG.md)

**请自觉维护**

## 1. 当前项目定位

当前工程是“可编译、可侧载、可在 TV 上浏览骨架 UI”的第一阶段版本。

- 包名：`com.github.gbandszxc.tvmediaplayer`
- `minSdk`：`21`
- `targetSdk`：`34`
- `compileSdk`：`34`
- ABI：`armeabi-v7a`、`arm64-v8a`
- UI：Leanback

## 2. 关键文件结构（当前）

```text
tsm-player/
├─ DESIGN.md
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
└─ docs/archive/
   ├─ plan/plan.md
   └─ prompt/ReqInit.txt
```

## 3. 常用命令

在项目根目录执行（Windows PowerShell）：

```powershell
# Debug（生成 `armeabi-v7a` / `arm64-v8a` 两个调试 APK，包名后缀 `.debug`）
.\gradlew.bat assembleDebug

# Release（生成 `armeabi-v7a` / `arm64-v8a` 两个签名 APK）
.\gradlew.bat assembleRelease

# Release（规避 lintVital 依赖下载问题）
.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease

# 发版（默认读取 app/build.gradle 当前版本，创建 v<versionName> Release 并上传两个 ABI APK）
.\scripts\release.ps1

# 清理
.\gradlew.bat clean
```

若当前终端未切到 JDK 17，可临时指定：

```powershell
cmd /c "set JAVA_HOME=C:\D\Develop\Java\jdk-17.0.16+8&& set PATH=%JAVA_HOME%\bin;%PATH%&& .\gradlew.bat assembleDebug"
```

## 4. 产物位置

```text
Debug:
  app\build\outputs\apk\debug\tsm-player-debug-armeabi-v7a-<versionName>.apk
  app\build\outputs\apk\debug\tsm-player-debug-arm64-v8a-<versionName>.apk

Release:
  app\build\outputs\apk\release\tsm-player-release-armeabi-v7a-<versionName>.apk
  app\build\outputs\apk\release\tsm-player-release-arm64-v8a-<versionName>.apk
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

项目只有 `debug` 和 `release` 两个构建变体，不再区分 flavor；每个构建变体都会按 ABI 拆成两个 APK：

1. `debug`
- 包名：`com.github.gbandszxc.tvmediaplayer.debug`（带 `.debug` 后缀，可与 release 并行安装）
- `android.software.leanback required=false`，可在手机/平板安装侧载调试
- 产物：`armeabi-v7a` 与 `arm64-v8a` 各一包

2. `release`
- 包名：`com.github.gbandszxc.tvmediaplayer`（正式包名）
- 使用签名配置，`required=false` 同样兼容多设备安装
- 产物：`armeabi-v7a` 与 `arm64-v8a` 各一包
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
3. 播放页默认进入时焦点停在播放进度条，进度条按上可选中封面图；封面选中时会显示 3px 高亮直角边框，也支持点击/确认键进入图片全屏预览。播放页非全屏封面仍保留方形 `centerCrop` 裁切；全屏预览改用独立覆盖层，背景为同图铺满屏幕后的模糊图层，前景为原图 `fitCenter` 适应屏幕显示，不裁切原图内容。音频无封面时会全屏显示缺省封面图，触屏点击全屏图片任意位置可退出预览，遥控器返回键可退出预览。
4. 设置页左侧分类已区分“当前已进入分类”和“遥控器焦点”：当前分类显示左侧蓝色竖线与低亮底色，焦点项显示亮蓝填充与描边；当两者重合时同时保留竖线和焦点框，避免用户把未确认的焦点移动误认为已切换菜单。
5. 根目录 `DESIGN.md` 现在是当前 UI/交互设计规范，Android XML 样式已抽取为 `@color/ui_*`、`@dimen/ui_*`、`@style/Tsm*` 与可复用 `@drawable/bg_*`。后续 UI 修改需优先引用这些 token，避免新增通用硬编码样式。
6. 播放页 SeekBar 长按左右键快进/快退已改为“轻量预览 + 节流提交”：进度条与时间文本会跟随遥控 repeat 连续更新，实际 `seekTo` 会合并为约 250ms 一次，并在松手或短暂停顿后提交最终位置；预览态和最终提交后的短暂窗口内会屏蔽播放器旧进度回调对 UI 的覆盖，避免向右快进/向左快退时出现反向跳变；进度条精度提升到 10000 档，以改善一小时以上长音频在低配电视上的跳变感。
7. 播放页底部按钮栏已在“下一首”和“歌词全屏”之间新增 `btn_play_mode`。播放模式默认为顺序播放，按 OK 依次切换“顺序播放 / 单曲循环 / 列表循环 / 随机播放”；按钮常驻 VectorDrawable 图标，采用播放中“暂停”同系颜色，未聚焦时为 icon 居中的紧凑矩形，获得焦点时展开显示短文字，切换后在播放页右上角显示 2 秒项目风格轻提示。播放模式不持久化，新建普通播放队列会重置为顺序播放。`btn_locate` 也改为紧凑图标按钮，未聚焦显示准星图标，聚焦时展开“定位”，继续使用黄色提醒型背景。

## 10. 浏览导航增强

当前在 TV 浏览页中，当目录项过多时可以长按确认进入快速定位模式。模式提示改为根布局右下角悬浮层，不再跟随长列表一起滚动，因此长按进入后会稳定显示“快速定位模式 xx%”和临时比例条；方向键仍保持上下整屏跳、左右 10% 分段跳，确认键接受落点、返回键取消。快速定位的计算与状态由 `TvBrowserViewModel` 驱动，UI 仅负责展现悬浮提示层以及进度数字。

为兼容电视实机遥控器的长按重复事件，浏览页在“长按确认进入快速定位模式”后，会在该次确认键释放前临时屏蔽残余确认事件，避免模式刚出现就被同一次长按立刻接受退出。

文件列表渲染已增加“目录内容指纹”门控：只有目录路径或条目内容真正变化时才重建整表；单纯的焦点移动、锚点写回、快速定位落点变更只做焦点更新，不再反复 `removeAllViews + inflate`，以降低电视端长按方向键连续滚动时的焦点丢失与卡顿风险。

浏览页已保证 `MENU` 键在任意焦点位置（文件项、顶部按钮、快速定位模式）都可触发“连接管理”。

目录焦点会在 `SmbConfigStore` 中以“连接命名空间 + 目录路径”为粒度保存锚点，返回同一路径时由 ViewModel 恢复焦点到离开前的条目；若条目已失效则按历史 `anchor.index` 回到最近可用位置（clamp 到列表范围），并继续提示“目录内容已变化，已回到开头”。

当 `activeConnectionId == null`（未保存/临时配置）时，ViewModel 会基于 `host/share/path/username/guest/smb1Enabled` 生成稳定且不含密码的指纹命名空间，避免不同临时连接共享同一空锚点空间。

设置页的 “清理缓存” 会调用 `SmbConfigStore.clearBrowseCache()`，除了歌词与封面磁盘缓存还会重置这些目录焦点锚点，缓存清除后需要重新浏览一次才能再生成新锚点。

## 11. 单测基线状态
截至 `2026-04-06`，此前记录在长列表导航计划中的 3 个 `Track9` 相关“基线失败”已不再复现，当前 `.\gradlew.bat testDebugUnitTest` 可通过，不再将它们视为已知失败项。

## 12. 应用内更新检测

应用启动进入主界面时会在本进程内自动检查一次 GitHub 最新 Release；设置页“关于”分类下也提供“检查更新”手动入口。检测逻辑直接解析公开 Release 页面：先访问 `/releases/latest` 获取最新 tag，再访问 `/releases/expanded_assets/<tag>` 读取 assets HTML，不依赖 `api.github.com` REST API。随后读取当前 `BuildConfig.VERSION_NAME` 与设备 ABI，在 Release assets 中匹配命名为 `tsm-player-release-<abi>-<versionName>.apk` 且版本号更高的安装包。

若发现新版本，会弹窗询问是否下载；用户确认后前台显示下载进度条，下载完成后通过系统 APK 安装页面继续安装。APK 临时保存在 `cacheDir/updates/`，并通过 `FileProvider` 只授权该缓存目录给系统安装器读取。

## 13. GitHub Release 发版脚本

项目根目录提供 `scripts/release.ps1`。脚本会读取 `app/build.gradle` 中的 `versionName/versionCode`，默认执行：

```powershell
.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease
```

随后校验以下两个 Release APK 存在且非空，并通过已登录的 `gh` 创建 `v<versionName>` Release：

```text
app\build\outputs\apk\release\tsm-player-release-armeabi-v7a-<versionName>.apk
app\build\outputs\apk\release\tsm-player-release-arm64-v8a-<versionName>.apk
```

常用参数：

```powershell
# 标准发版
.\scripts\release.ps1

# 只上传已有构建产物
.\scripts\release.ps1 -SkipBuild

# 更新已存在 Release 的 APK 资产
.\scripts\release.ps1 -SkipBuild -Clobber

# 指定自定义 tag 或标题
.\scripts\release.ps1 -TagName v1.0.7 -Title "v1.0.7"
```

## 14. 项目级发版 Skill

仓库内新增项目级 skill：

```text
.agents\skills\tsm-release\SKILL.md
```

后续由 Agent 进行本项目发版、版本迭代、GitHub Release 创建或上传 release APK 时，应优先调用 `tsm-release`。该 skill 会复用 `scripts\release.ps1`，并固定执行更新日志确认、版本号递增、文档维护、Debug/Release 构建、提交推送、GitHub Release 创建与两个 ABI 资产校验。

如果用户没有提供更新日志，skill 会先询问是否需要根据上次发版后的 git 提交记录智能总结；若用户不需要智能总结，则必须由用户补充更新日志后再继续发版。

