# Android TV SMB 音乐播放器需求与实现计划

## 1. 需求总结
这是一个专为 Android TV 侧载场景设计的极简离线音乐播放器：直接连接 NAS 的 SMB 共享并以文件树方式浏览播放，重点解决大屏遥控交互、封面显示和本地歌词同步显示。

## 2. 技术栈推荐
- 语言：Kotlin
- UI 框架：Leanback（BrowseSupportFragment + RowsSupportFragment）
- 架构：MVVM + Repository
- 异步：Kotlin Coroutines + StateFlow
- SMB：`jcifs-ng`（首选，SMB2/3）
- 播放核心：AndroidX Media3（ExoPlayer + MediaSession + MediaLibraryService）
- 元数据/封面/歌词解析：`jaudiotagger`（ID3/FLAC 标签），LRC 自研解析器
- 图片加载：Coil（本地 Bitmap/字节流封面）
- 持久化：DataStore（仅保存 SMB 连接配置与最近路径）

## 3. 主要 Activity/Fragment/Composable 结构
- `MainActivity`
  - 容器 Activity，仅承载 TV 首页 Fragment
- `TvBrowseFragment : BrowseSupportFragment`
  - Row A: SMB 连接配置入口（服务器、共享名、子路径、账号密码、Guest）
  - Row B: 当前目录文件列表（文件夹 + 音频文件）
  - Row C: 操作项（返回上级、刷新、整目录播放）
- `PlaybackActivity`（后续）
  - 全屏封面 + 标题信息 + 进度条 + 歌词区
- `PlaybackService : MediaLibraryService`（后续）
  - 后台播放、通知控制、媒体会话

## 4. SMB 连接 & 文件浏览实现思路
1. 用户首次进入先填写 `SmbConfig`：host/share/path/username/password/guest。
2. 由 `SmbRepository` 统一拼接 SMB URL（`smb://host/share/path`）。
3. 使用 `jcifs-ng` 发起连接：
   - Guest: 匿名凭据
   - 账号模式: NTLM 用户名密码
4. 调用 listFiles() 获取目录项并映射为 `SmbEntry`：
   - `isDirectory=true` 作为可进入目录
   - 音频扩展名白名单：mp3/flac/aac/m4a/wav/ogg
5. 进入子目录时仅更新 `currentPath` 并重新拉取。
6. 错误分级映射：
   - 认证失败、网络不可达、共享不存在、路径不存在、超时
   - 统一转用户可读中文提示。

## 5. 播放器 + 封面 + 歌词解析关键代码片段
```kotlin
// SMB 文件点击播放（后续接入真实流）
fun playFile(entry: SmbEntry) {
    val mediaItem = MediaItem.Builder()
        .setUri(entry.streamUri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(entry.name).build()
        )
        .build()
    player.setMediaItem(mediaItem)
    player.prepare()
    player.playWhenReady = true
}
```

```kotlin
// LRC 解析核心（时间戳 + 文本）
private val TAG = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?]")
fun parseLrc(content: String): List<LyricLine> {
    return content.lineSequence().flatMap { line ->
        val text = line.replace(TAG, "").trim()
        TAG.findAll(line).map { m ->
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val ms = m.groupValues[3].padEnd(3, '0').ifBlank { "0" }.toLong()
            LyricLine(min * 60_000 + sec * 1000 + ms, text)
        }
    }.sortedBy { it.timestampMs }.toList()
}
```

```kotlin
// 封面提取优先级
// 1) 音频内嵌封面(APIC/PICTURE)
// 2) 同目录 folder.jpg / cover.jpg / front.jpg
// 3) 默认占位图
```

## 6. 潜在坑点 & 解决方案
- Android TV 字体与中日韩显示
  - 使用系统 Unicode 字体优先；必要时内置 Noto Sans CJK 子集字体。
  - 文本组件统一 `maxLines + ellipsize`，避免超长标题抖动。
- SMB 认证兼容
  - 默认 SMB2/3；保留可选 SMB1 兼容开关。
  - 对 NAS 的工作组/域参数留扩展字段。
- LRC 同步精度
  - 统一毫秒时间轴；支持 offset 标签（[offset:+/-xx]）。
  - 当前行定位使用二分查找，避免大歌词文件滚动卡顿。
- 大文件与弱网
  - 目录读取与元数据解析放 IO 线程。
  - 播放采用流式，不做整文件下载。

## 7. 打包为可侧载 APK 的简单步骤
1. 配置本机 SDK 与 JDK17。
2. 执行：`./gradlew assembleDebug`（Windows: `./gradlew.bat assembleDebug`）。
3. 产物：`app/build/outputs/apk/debug/app-debug.apk`。
4. TV 安装：`adb install -r app-debug.apk`。
5. 如需发布：配置签名后执行 `assembleRelease`。
