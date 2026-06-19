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
│     │  ├─ sleep/{SleepTimerState.kt,SleepTimerStore.kt,SleepTimerCustomDurationStore.kt,SleepTimerStartupCoordinator.kt,SleepTimerManager.kt,SleepDeviceAdminReceiver.kt,SleepDeviceController.kt,SleepAppExitController.kt}
│     │  └─ ui/
│     │     ├─ TvBrowseFragment.kt
│     │     ├─ TvBrowserViewModel.kt
│     │     ├─ SleepTimerActivity.kt
│     │     ├─ AppFonts.kt
│     │     └─ modal/{TsmModalCoordinator.kt,TsmModalModels.kt,TsmModalBuilders.kt,TsmModalFormValidators.kt}
│     └─ res/
│        ├─ layout/{activity_main.xml,dialog_tsm_modal_shell.xml,item_tsm_modal_list_row.xml,item_tsm_modal_action_row.xml,item_tsm_modal_form_field.xml,view_tsm_modal_progress.xml}
│        ├─ font/{misans_regular.ttf,misans_medium.ttf,misans_bold.ttf,misans_family.xml}
│        ├─ values/{strings.xml,colors.xml,themes.xml}
│        ├─ drawable/{ic_launcher_foreground.xml,tv_banner.xml,bg_modal_panel.xml,bg_modal_surface.xml,bg_modal_surface_focused.xml,bg_modal_list_row.xml,bg_modal_input.xml}
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

构建需要使用 JDK 17。推荐把本机 JDK 路径写入用户级 Gradle 配置：

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.gradle"
Add-Content "$env:USERPROFILE\.gradle\gradle.properties" "org.gradle.java.home=<本机 JDK 17 目录>"
```

也可以只在当前 PowerShell 会话临时指定：

```powershell
$env:JAVA_HOME="<本机 JDK 17 目录>"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug
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
3. 当前播放能力已支持临时队列、默认收藏夹、自定义播放列表和播放历史的本地 SQLite 持久化；项目持久化正在收束到主库 `tsm-player.db`，设置页已支持该主库的本地全量导入/导出与 WebDAV 手动全量上传/下载；`1.0.9` 发布范围包含播放历史、备份恢复、播放页 metadata 回显修复和定位锚点冲突修复；尚未实现复杂歌单管理（重命名、排序、批量移动、自动云同步等）。
4. 内嵌封面（APIC/FLAC PICTURE）与精确标签解析尚未接入专用解析器。
5. 歌词 UI 当前为三行窗口（上一行/当前行高亮/下一行），尚未实现整段滚动歌词与全屏歌词模式。
6. 播放页已支持睡眠定时器、播放模式切换、定位按钮，但相比设计图仍缺少更多遥控器快捷交互。
7. 已可产出已签名 release，但仍建议补充签名校验与安装回归测试流程。
8. 浏览页会为当前激活的 SMB 连接持久化最近浏览目录；应用退到后台或页面重建后，会优先恢复到离开前层级，而不是仅回到连接初始路径。

## 9. 最近 UI/交互更新
0. 项目已增加基础 i18n 资源结构：中文默认资源位于 `app/src/main/res/values/strings.xml`，英文资源位于 `app/src/main/res/values-en/strings.xml`。新增用户可见文案时必须同步维护两个文件，避免在 Kotlin 或 XML 中继续写死可见中文；`LocalizationResourcesTest` 会校验英文资源 key 与默认资源一致，并禁止布局 `android:text` 继续硬编码中文。设置页“显示设置 > 语言”支持“跟随系统 / 中文 / English”手动切换，偏好持久化到主库 `tsm-player.db` 的 `ui.app_language`，`BaseActivity` 会在 Activity 创建和恢复时应用或刷新语言上下文。
1. SMB 浏览页的“播放控制”面板已移到“文件浏览”上方，结构顺序调整后仍保留原始按钮状态与焦点关系，便于用户先快速触达播放相关操作再滚动到文件列表。
2. 播放页底部按钮栏在“返回文件页”后新增 `btn_locate`（文本“定位”），采用 `bg_button_light_yellow` 并复用 `#3A2A00` 字色，保持现有焦点链（`nextFocusUp` 指向进度条）。该按钮将用于快速跳回当前播放曲目的目录，补全“播放目录定位”能力。
3. 播放页默认进入时焦点停在播放进度条，进度条按上可选中封面图；封面选中时会显示 3px 高亮直角边框，也支持点击/确认键进入图片全屏预览。播放页非全屏封面仍保留方形 `centerCrop` 裁切；全屏预览改用独立覆盖层，背景为同图铺满屏幕后的模糊图层，前景为原图 `fitCenter` 适应屏幕显示，不裁切原图内容。音频无封面时会全屏显示缺省封面图，触屏点击全屏图片任意位置可退出预览，遥控器返回键可退出预览。
4. 设置页左侧分类已区分“当前已进入分类”和“遥控器焦点”：当前分类显示左侧蓝色竖线与低亮底色，焦点项显示亮蓝填充与描边；当两者重合时同时保留竖线和焦点框，避免用户把未确认的焦点移动误认为已切换菜单。
5. 根目录 `DESIGN.md` 现在是当前 UI/交互设计规范，Android XML 样式已抽取为 `@color/ui_*`、`@dimen/ui_*`、`@style/Tsm*` 与可复用 `@drawable/bg_*`。后续 UI 修改需优先引用这些 token，避免新增通用硬编码样式。
6. 播放页 SeekBar 长按左右键快进/快退已改为“轻量预览 + 节流提交”：进度条与时间文本会跟随遥控 repeat 连续更新，实际 `seekTo` 会合并为约 250ms 一次，并在松手或短暂停顿后提交最终位置；预览态和最终提交后的短暂窗口内会屏蔽播放器旧进度回调对 UI 的覆盖，避免向右快进/向左快退时出现反向跳变；进度条精度提升到 10000 档，以改善一小时以上长音频在低配电视上的跳变感。
7. 播放页底部按钮栏已在”下一首”和”收藏”之间新增 `btn_play_mode`。播放模式默认为顺序播放，按 OK 依次切换”顺序播放 / 单曲循环 / 列表循环 / 随机播放”；按钮常驻 VectorDrawable 图标，采用播放中”暂停”同系颜色，未聚焦时为 icon 居中的紧凑矩形，获得焦点时展开显示短文字，切换后在播放页右上角显示 2 秒项目风格轻提示。播放模式不持久化，新建普通播放队列会重置为顺序播放。`btn_locate` 也改为紧凑图标按钮，未聚焦显示准星图标，聚焦时展开”定位”，继续使用黄色提醒型背景。
8. 播放页底部按钮栏在”收藏”和”歌词全屏”之间提供 `btn_sleep_timer` 睡眠定时入口。未开启时显示睡眠图标（紧凑居中）；开启后显示睡眠图标与剩余分钟数字，宽度自动扩展。点击按钮进入睡眠定时设置页，可选择 15/30/60/120 分钟预设或手动指定小时和分钟。设置页提供”开启/更新/关闭”操作。应用首次启动时会弹窗提示授权设备管理权限（用于定时结束时息屏），也可在设置页”应用设置”中重新授权。
9. 播放页底部按钮栏的 `btn_prev`、`btn_play_pause`、`btn_next` 已从文字按钮改为纯 VectorDrawable 图标按钮，聚焦时也不展开文字；定稿图标为上一首 A、播放/暂停 A、下一首 A。`btn_lyrics_fullscreen` 与 `btn_back_to_browser` 改为默认紧凑图标、聚焦展开文字，定稿图标分别为歌词全屏 C（四角全屏 + 文本横线）与返回文件夹 A（文件夹 + 左箭头），颜色沿用原按钮样式。
10. 播放页底部按钮栏已在播放模式与睡眠定时之间增加“收藏”按钮，红色 danger 样式默认折叠为爱心图标，聚焦时展开文案；短按可加入/移出默认收藏夹，长按可选择已有播放列表或新建播放列表并加入当前歌曲。
11. SMB 浏览页“播放控制”行已在顺序播放前新增“收藏”入口按钮，点击会进入 `FavoritesActivity`。浏览页收藏、顺序播放、随机播放按钮未聚焦时折叠为图标，获得焦点时展开显示文案，右侧“回到当前播放/继续上次播放”按钮仍使用 `0dp + weight=1` 自适应剩余宽度。
12. `FavoritesActivity` 已实现收藏页宫格与歌曲列表：入口页第一个 tile 为“添加播放列表”，之后显示默认收藏夹与自定义播放列表；tile 会按多行宫格换行，封面优先使用最后加入歌曲 artwork，缺失时使用 `default_cover`。点击播放列表进入纵向歌曲列表，点击歌曲会从当前收藏列表构建 Media3 队列并跳到所选 index 播放；右侧删除按钮与失效歌曲确认弹窗都只移除收藏记录，不删除 SMB 原文件。复杂歌单管理（重命名、排序、批量移动、云同步等）仍未实现。
13. 播放页与全屏歌词页在自动切歌后，如果新歌首句歌词尚未到达，歌词滚动视口会先回到顶部等待首句高亮，不再沿用上一首歌曲结束时的滚动位置，避免首句出现时突兀跳回。
14. 收藏页播放队列按所选歌曲的 SMB 来源配置过滤，避免不同 NAS、账号、共享路径或 SMB 版本混入同一 Media3 队列；无来源配置的歌曲只与同样无来源配置的歌曲组队。收藏数据库使用 `track_key` 将 `mediaId` 与来源配置组合成稳定身份，因此同一路径来自不同来源时可同时收藏，默认收藏状态和移除操作不会串源。失效歌曲移除提示改为保守触发：空播放地址可直接提示，播放器错误仅在明确文件/路径不存在时提示移除；空播放列表焦点回落到右上角返回按钮。
15. SMB 浏览页“收藏 / 顺序播放 / 随机播放”三个紧凑按钮的未聚焦态已改为和播放页底部按钮栏一致的布局实现：按钮保持固定矩形宽度，折叠态图标通过 overlay 视觉居中，不再出现 icon 相对矩形向左偏的问题。
16. `FavoritesActivity` 的歌曲列表已补齐遥控器可达操作：整行继续支持触摸点击直接播放；遥控器模式下右侧新增绿色播放按钮，并将播放/删除都收敛为方形 icon-only 按钮，初始焦点会落在首行播放按钮。收藏页歌单 tile 封面也补齐了多级回显链路：先取最近有 `artworkUri` 的歌曲封面，再尝试播放页封面缓存、内嵌封面与同目录封面图，最后才回退到 `default_cover`。
17. 项目已新增统一的 TV 自绘 modal 体系，替换播放页、收藏页、SMB 连接管理、设置输入框、睡眠权限提示、更新确认框与下载进度框中的原生 `AlertDialog` / `ProgressDialog`。新 modal 统一采用深色面板、蓝色焦点和项目按钮语义，Back 关闭后会恢复打开前焦点。公共组件位于 `ui/modal` 包下，包含 `TsmModalCoordinator`（协调器）、`TsmModalModels`（数据模型）、`TsmModalBuilders`（便捷构建）和 `TsmModalFormValidators`（表单校验）。
18. 播放页长按“收藏”打开的“选择播放列表”弹窗已做两项修正：列表行之间补齐与文件列表一致的垂直留白，避免行块过于紧贴；在“加入现有播放列表”或“新建播放列表并加入”后，当前 modal 会立即刷新列表状态，新歌单和刚加入的歌单都会立刻回显“已在该播放列表中”，无需退出重进。
19. 收藏页与 SMB 连接管理的删除交互已收敛到统一 confirm 规范：收藏页歌曲行右侧”删除”按钮现在会先弹确认框，仅移除当前播放列表中的收藏记录；进入自定义播放列表后，右上角会在”返回”左侧显示红色”删除播放列表”按钮，确认后删除该自定义歌单及其收藏记录，默认”收藏夹”不显示此按钮也不可删除。SMB 浏览页”连接管理”弹窗在已保存的当前连接场景下会新增红色”删除当前连接”，确认后删除该保存连接并自动回落到其它已保存连接或未配置状态。播放页短按红色”收藏”按钮从默认收藏夹移除当前歌曲仍保持特例，不追加 confirm。
20. SMB 浏览页文件列表已增加文件大小和最后修改时间展示；顶部工具区收敛为”刷新当前目录”和排序下拉，默认按文件名升序排序，目录始终位于文件前，`..` 始终固定最上且不显示大小/修改时间占位。缺省大小或修改时间显示为居中的 `--`，真实值保持右对齐。排序下拉面板为轻量锚定式浮层，支持六种排序：文件名 ↑/↓、文件大小 ↑/↓、修改时间 ↑/↓，选中后立即应用并收起；触屏点击下拉框外区域只关闭浮层，不触发重排序，也不透传到底层列表或按钮。
21. SMB 浏览页排序下拉面板在下方可见高度不足时会自动下滚页面，保证六个排序选项完整显示；面板展开期间，上下方向键焦点限制在排序选项内部，避免在最后一项继续向下时误跳到文件列表。
22. 设置页新增“备份恢复”分类。备份范围为应用主 SQLite 数据库 `tsm-player.db`，当前包含收藏/歌单、播放历史、SMB 连接、浏览锚点、UI 设置、播放恢复状态、睡眠设置和 WebDAV 配置；歌词/封面磁盘缓存仍视作缓存，不进入备份。旧 `favorites.db`、`smb_config` DataStore 和旧 SharedPreferences 会在对应新表/键为空时渐进迁移进主库，迁移后新读写以 `tsm-player.db` 为权威源。本地“导出本地备份”会打开系统文件保存器，由用户选择保存位置后写入当前主库；“导入本地备份”会打开系统文件选择器，由用户选择备份文件后校验主库核心表结构，确认后覆盖当前主库。WebDAV 配置只包含 URL、用户名和密码；URL 表示 WebDAV 目录地址，需要子目录时直接写进 URL，不再单独配置远端目录。设置页提供“测试 WebDAV 连接”，通过 HEAD 请求确认目录是否真实存在，并提示 HTTP 状态、认证失败、URL 不存在、服务器不支持 WebDAV、网络超时或 TLS/SSL 等详细原因；当 URL 指向的目录不存在时，会递归确认上级目录并通过 MKCOL 自动创建缺失目录，成功后提示已创建目录，创建失败会提示权限、上级目录或服务器错误。坚果云等服务可能对不存在目录的 OPTIONS 仍返回 200，因此目录存在性不得仅依赖 OPTIONS。“上传到 WebDAV”会先确保 URL 目录存在，再生成本地全量备份，并以固定文件名 `tsm-player.db` 手动上传到该 URL 下；“从 WebDAV 下载并恢复”会下载同名文件到临时位置，校验后覆盖当前主库。该功能不做自动同步、后台轮询或启动时自动恢复。
23. SMB 浏览页连接失败策略已调整为“自动尝试 + 失败熔断 + 手动刷新重试”：进入当前连接时网络类失败会按指数退避最多尝试 3 次，仍失败则 Toast 提示并在页面内显示错误；随后当前连接进入进程内失败锁定，自动路径恢复、目录切换和定位不会继续刷新 SMB。用户点击“刷新当前目录”时才会触发一次新的 3 次退避重试；退出软件后锁定状态随进程重置。
24. SMB 连接配置回显已优化：在用户名或密码输入非空内容时会自动取消“访客 / 匿名”；连接管理的“编辑/新建/切换/删除”操作点击后会关闭旧操作面板，避免保存、切换或删除完成后仍停留在旧连接页；切换连接成功后列表弹窗即时关闭；删除最后一个已保存连接后浏览页回到未配置/根目录连接管理状态，便于直接新建连接。
25. SMB 浏览页“播放控制”行已在“收藏”右侧新增黄色“历史”入口按钮，未聚焦时只显示历史图标，遥控器聚焦时展开“历史”文字。历史记录写入主库 `tsm-player.db` 的 `play_history` 表，最多保留最近 1000 条，同一来源同一路径歌曲重复播放会更新时间而不新增重复记录；历史页按最近播放时间倒序分页展示，一页 50 条，支持按歌曲文件名、标签歌曲名、歌手名、专辑名模糊搜索。
26. 播放页“定位”按钮现在会以当前 Media3 播放项作为显式焦点目标：回到 SMB 浏览页加载当前播放文件所在目录后，优先聚焦当前播放文件，并覆盖该目录旧焦点锚点，避免从播放列表第 1 首进入后切到第 3 首时仍回到第 1 首。
27. 播放页“定位”、列表页“历史”和“回到当前播放/继续上次播放”三类按钮配色已统一到 `TsmButtonWarning`：默认黄色、聚焦深黄色加描边，文字和图标统一使用深色提醒文本色，避免同一语义按钮出现白色图标与深色文字混用。
28. SMB 浏览页播放控制区的收藏、历史、顺序播放、随机播放四个紧凑按钮在聚焦展开时改为按当前语言文案 `wrap_content` 自适应宽度，并保留最小宽度约束：收藏、历史等短按钮使用较短的 96dp 最小宽度，顺序播放、随机播放等长文案按钮使用 132dp 最小宽度；英文模式下 `History`、`Play in Order`、`Shuffle` 等文案不再因为固定宽度过窄而换行。
29. “继续上次播放”重建 Media3 队列时会恢复当前歌曲保存的标题、艺术家和专辑；若艺术家或专辑缺少真实元信息，则按文件列表播放同一规则回退为 SMB 共享名/主机和父目录名。播放页暂停或切到全屏歌词等其它页面前保存快照时，会优先保存当前页面最终显示的 metadata，避免返回播放页后艺术家/专辑丢失或从真实标签退回为空值。
30. 设置页左侧分类列表已改为标题固定、分类列表单独滚动；小屏触摸设备可纵向滑动触达“关于”等底部分类，遥控器焦点移动仍会自动滚动到可见区域。统一沉浸式全屏逻辑在主题层与 Activity `setContentView` 前共同声明 display cutout 策略：Android P/Q 使用 `shortEdges`，Android R 及以上使用 `always` 并关闭 decor 的系统窗口适配，避免刘海屏横屏左侧继续被系统预留为只有背景、没有页面内容的安全区。
31. 设置页“显示设置 > 全局缩放”已从点击条目循环下一档，改为点击后打开统一 modal 外壳中的步进控件；中间以文本框样式回显当前倍率，左右 `-` / `+` 按钮按 5% 即时调整，支持 80% 到 120%。调整时设置页和当前 modal 会同步缩放以提供即时预览；底部“确认”保留当前倍率，“取消”、Back 或外部关闭会恢复打开前倍率。设置项说明文案改为直白解释“倍率高则字大信息密度低，反之字少信息密度高”。
32. 全部交互界面补齐动效与微交互，且触屏与遥控器严格隔离，可复用逻辑收敛到 `ui/UiMotion.kt`。触屏点按按钮/文件行/设置项/modal 行时，由真实触摸（`MotionEvent`）驱动一层 overlay 色 `RippleDrawable`（`foreground`）产生“涟漪 + 加深/提亮”，遥控器 OK 键不产生 `MotionEvent`，因此该反馈物理上不会在遥控器触发；释放后清除 `foreground` 使其默认为 `null`，遥控器 pressed 态无可触发的 ripple，永不涟漪。彩色/高亮面（蓝/绿/红/黄/琥珀、modal 主/危险按钮）使用 `@color/ui_press_overlay_dark`（加深），深色中性面（文件行、暗按钮、歌单 tile、modal surface/list row/input、设置项、睡眠预设）使用 `@color/ui_press_overlay_light`（提亮）。遥控器聚焦“展开文字”按钮（浏览页紧凑按钮、播放页底部展开按钮）时，宽度以 Material decelerate（展开约 200ms）/ accelerate（收起约 150ms）非线性动画过渡；触屏模式、未布局或宽度为 0 时直接落定目标宽度不播动画，保证单测同步契约与触屏互斥。动效自动遵守系统“动画时长缩放”。`foreground` 涟漪在 API 23+ 渲染，API 21–22 为 no-op。详细规范见 `DESIGN.md`“动效与微交互”小节。

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

应用启动进入主界面时会在本进程内自动检查一次 GitHub 最新 Release；启动自动检查为单次、无重试，未发现新版本或网络异常时保持静默。若同次启动需要展示睡眠权限提示，自动检查会等权限提示关闭后再执行，避免启动期多个全屏 modal 叠加导致弹窗闪现或焦点被抢。设置页“关于”分类下也提供“检查更新”手动入口，手动检查不受稍后策略影响。检测逻辑直接解析公开 Release 页面：先访问 `/releases/latest` 获取最新 tag，再访问 `/releases/expanded_assets/<tag>` 读取 assets HTML，不依赖 `api.github.com` REST API。随后读取当前 `BuildConfig.VERSION_NAME` 与设备 ABI，在 Release assets 中匹配命名为 `tsm-player-release-<abi>-<versionName>.apk` 且版本号更高的安装包。

若发现新版本，会弹窗询问是否下载；更新提示和稍后时间选择不显示左上角分区标签。提示框必须等待用户选择“稍后”或“下载并安装”，不允许 Back 或外部触摸直接取消。点击“稍后”后会打开不可取消的时间选择列表：选择“本次”只跳过当前进程中的下一次自动提示；选择“7天”会在到期前跳过所有自动更新提示，即使期间发布了更高版本也不提示，到期后重新检测最新版本；选择“下个版本”只跳过当前发现版本，若之后检测到更高版本会重新提示。用户确认下载后前台显示下载进度弹窗，内容包含文件名、实时网速、已下载/总大小和蓝色从左向右填充的进度条，不再显示百分比，也不使用系统流动进度背景。下载任务会从 APK 响应头读取 `Content-Length` 作为总大小；若服务器未返回总大小，则保守显示已下载大小并保持稳定轨道。下载完成后通过系统 APK 安装页面继续安装。APK 临时保存在 `cacheDir/updates/`，并通过 `FileProvider` 只授权该缓存目录给系统安装器读取。

Debug 包在设置页“关于”分类下提供“预览启动更新提示”和“预览更新下载样式”入口。“预览启动更新提示”会展示与开屏自动更新一致的新版本提示和“稍后”时间选择效果，不访问 GitHub Release、不下载真实 APK，也不会写入真实稍后策略；点击预览弹窗的“下载并安装”只会提示预览模式不会下载或安装。“预览更新下载样式”只模拟下载状态并展示同一套进度弹窗，不访问 GitHub Release、不下载真实 APK，也不会触发安装；Release 包不会显示这些入口。

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

## 15. 睡眠定时器

播放页底部按钮栏提供睡眠定时入口（位于"收藏"和"歌词全屏"之间）。未开启时显示睡眠图标（紧凑居中）；开启后显示睡眠图标与剩余分钟数字，分钟向上取整，宽度自动扩展。点击按钮进入睡眠定时设置页，可选择 15、30、60、120 分钟预设，或手动选择小时和分钟。自定义小时/分钟滚轮支持遥控器上下键，也支持触屏单指上下滑动；触屏采用反向滚动，上划数字变大，下划数字变小。遥控器在分钟滚轮按右键会进入"开启睡眠/更新睡眠"按钮，而不是落到返回按钮。应用会保存最近一次成功开启/更新的自定义时长，未开启睡眠定时时再次点击"自定义"会默认回显该时长；已有定时优先回显当前定时值。

睡眠定时依赖后台播放服务存活。只要播放器仍在后台播放，倒计时会继续，到点后保存当前播放快照、停止播放、退出应用，并通过设备管理权限请求电视睡眠或息屏。如果设备管理权限未授权，到点仍会停止播放并退出应用，但不能保证电视息屏。应用完全退出后重新冷启动时会清除未完成的睡眠倒计时，不恢复上次睡眠状态；最近一次自定义时长会继续保留。该功能不承诺真正断电关机，也不使用 Android 12+ 精确闹钟权限。

首次启动应用时会弹窗提示授权设备管理权限，用户可选择"去授权"或"暂不授权"。未授权时仍可正常使用播放器和睡眠定时功能（仅不能息屏）。设置页"应用设置"分类下提供"睡眠权限"条目，可查看当前授权状态并重新打开授权页面。

## 16. Launcher 图标资源

当前优化版图标源图为 `docs/icon/icon_raw_v2.png`，保留了“TV 音乐播放器 + 可爱看板娘 + 二次元手游图标”的主旨，并将看板娘、耳机、遥控器和云朵主体收进 Android adaptive icon 安全区，减少电视 Launcher 圆形或圆角遮罩裁切。原始 Gemini 版本仍保留在 `docs/icon/icon_raw.png`，便于回退或对比。

图标候选和裁切预览保存在：

```text
docs/icon/icon_candidate_1.png
docs/icon/icon_candidate_2.png
docs/icon/icon_candidate_3.png
docs/icon/icon_candidates_preview.png
docs/icon/icon_final_preview.png
```

重新生成 Android 图标资源时，在项目根目录执行：

```powershell
.\docs\icon\generate_android_icons.ps1 -SourcePath docs/icon/icon_raw_v2.png -ResRoot app/src/main/res
```

脚本会更新 `mipmap-mdpi` 到 `mipmap-xxxhdpi` 下的 `ic_launcher.png`、`ic_launcher_round.png` 和 `ic_launcher_foreground.png`。生成后需至少执行 `.\gradlew.bat assembleDebug` 确认 Debug APK 可打包。

