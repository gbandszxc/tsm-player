# 列表页播放历史设计

## 背景

SMB 浏览页的播放控制区域已经提供收藏、顺序播放、随机播放和回到当前播放入口。新增历史入口后，用户应能从浏览页快速查看最近听过的歌曲，并通过歌曲文件名、标签歌曲名、歌手名、专辑名模糊搜索历史记录。

## UI 与交互

- 在 SMB 浏览页播放控制行中，将“历史”按钮放在“收藏”按钮右侧。
- 历史按钮复用收藏、顺序播放、随机播放的紧凑图标按钮交互：未聚焦时只显示图标，遥控器聚焦时展开为图标 + “历史”文字。
- 图标使用用户提供的 lucide history SVG，转换为 Android VectorDrawable，颜色走 `currentColor` 对应的按钮文字颜色。
- 历史按钮采用项目已有黄色提醒语义，优先复用 `TsmButtonWarning` / `bg_button_light_yellow` 及 `ui_accent_yellow*`、`ui_text_warning_dark` token。
- 点击历史按钮进入独立 `HistoryActivity`。页面结构贴近收藏页歌曲列表：顶部标题、搜索框/分页操作、历史歌曲列表、返回按钮。
- 列表按最近听歌时间倒序展示，每页 50 条。用户可通过上一页/下一页按钮分页，空态显示“暂无历史记录”或“没有匹配的历史记录”。

## 数据模型

历史记录存储在主库 `tsm-player.db`，新增 `play_history` 表：

- `id`：主键。
- `track_key`：歌曲稳定身份，用于同一来源同一路径去重。
- `media_id`、`stream_uri`：播放定位和队列构建需要的基础信息。
- `title`、`artist`、`album`、`artwork_uri`：搜索和展示使用的标签信息。
- `source_connection_id` 以及 SMB 来源配置字段：与收藏一致，用于播放时恢复对应 SMB 来源。
- `played_at`：最近听歌时间。

同一 `track_key` 再次播放时更新 `played_at` 和元信息，而不是新增重复行。每次写入后保留最近 1000 条，超过上限时淘汰最旧记录。

## 记录时机

历史写入收口到 `PlaybackService` 的播放项切换监听。这样浏览页播放、收藏页播放、继续上次播放和后台自动切歌都能统一记录。

写入内容优先读取当前 `MediaItem` 的 `mediaMetadata`，并结合 `PlaybackConfigStore.current()` 保存来源配置。标签后续被播放页解析得更完整时，可在播放页标签解析完成后补写当前歌曲元信息，避免历史列表长期只显示文件名。

## 搜索与分页

- 默认查询按 `played_at DESC`，分页大小固定 50。
- 搜索关键词为空时返回完整历史分页。
- 搜索关键词非空时，对文件名、`title`、`artist`、`album` 执行 SQLite `LIKE` 模糊匹配。
- 文件名由 `media_id` 或 `stream_uri` 的最后路径段推导，不另依赖 SMB 网络访问。

## 播放历史歌曲

点击历史歌曲时，以当前搜索结果和当前页附近记录构建队列，至少包含当前页的历史歌曲；按所选歌曲对应 SMB 来源更新 `PlaybackConfigStore`，再启动 `PlaybackActivity` 播放。

若历史记录缺少播放地址，或播放器明确返回文件不存在类错误，本阶段只提示播放失败，不自动删除历史记录。

## 文档与验证

- 同步维护 `DESIGN.md` 的浏览页播放控制、历史页、缓存与恢复行为章节。
- 同步维护 `docs/MANUAL.md` 当前实现边界和最近 UI/交互更新。
- 完成后执行 `.\gradlew.bat assembleDebug`，确认 Debug 双 ABI APK 可打包。
