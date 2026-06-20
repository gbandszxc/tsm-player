# WAV SMB 封面快速探测设计

## 背景

`SmbAudioMetadataProbe` 已对 MP3、FLAC、MP4/M4A、OGG/Opus 使用局部读取，避免为提取标签和内嵌封面而完整下载音频。WAV 当前未进入快速路径，会从 SMB 完整复制文件；未压缩 PCM WAV 较大时会导致封面回显延迟。

## 目标

- WAV 文件通过 RIFF chunk 结构探测标签和内嵌封面，避免复制大段 PCM 数据。
- 支持包含 ID3/APIC 封面的 WAV，且 `ID3 ` chunk 位于 `data` chunk 前后均可处理。
- 保留现有完整下载回退，异常或无法从快速探测结果提取元数据时不降低兼容性。
- 不改变 MP3、FLAC、MP4/M4A、OGG/Opus 的现有探测行为。

## 方案

为 WAV 增加专用 RIFF 分块复制逻辑。先校验 `RIFF`/`WAVE` 头，再逐个读取 chunk header。元数据相关 chunk（至少 `fmt `、`LIST`、`ID3 `/`id3 `）完整写入临时探测文件；`data` 只保留解析器识别文件所需的有限音频字节，并跳过其余 PCM 数据。处理 RIFF 规定的奇数字节 padding，并对 chunk 长度、截断输入和无效签名设置边界检查。

快速探测临时文件会重写必要的 RIFF/chunk 长度，使其结构自洽并可被 jaudiotagger 读取。若结构不合法、快速复制不可用，或 jaudiotagger 未从快速结果提取到任何元数据，则沿用现有逻辑重新完整复制 WAV 并解析。

## 测试

新增 JVM 单元测试，使用内存构造的最小 WAV fixture，不依赖用户媒体文件：

- `ID3 ` chunk 位于 `data` 前时，快速结果保留 ID3/APIC 数据且不复制完整 PCM。
- `ID3 ` chunk 位于 `data` 后时，仍可定位并保留 ID3/APIC 数据。
- RIFF chunk 奇数长度 padding 正确处理。
- 非 WAV 或损坏 RIFF 输入不会被误判为成功快速探测。
- 现有格式分派保持不变。

完成后运行相关单元测试、完整 JVM 测试（若环境允许）以及 `assembleDebug`，确认两个 ABI 的 Debug APK 正常生成。

## 文档影响

在 `docs/MANUAL.md` 追加 WAV 内嵌封面 SMB 快速探测及完整下载回退说明。本次不涉及 UI、交互或视觉规范，因此不修改 `DESIGN.md`。
