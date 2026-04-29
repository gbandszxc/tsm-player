# TV Media Player 交付清单

## 1. 构建与产物

1. 执行 `.\gradlew.bat clean testDebugUnitTest assembleDebug`，确保调试构建和单元测试通过。
2. 执行 `.\gradlew.bat assembleRelease` 产出电视发布包；若 `lintVitalAnalyzeRelease` 因网络问题失败，可改用 `.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease`。
3. 产物路径确认：
   - `app\build\outputs\apk\release\tsm-player-release-armeabi-v7a-<versionName>.apk`
   - `app\build\outputs\apk\release\tsm-player-release-arm64-v8a-<versionName>.apk`

## 2. 功能验收

1. SMB 连接
- 验证匿名与账号密码两种连接方式。
- 验证错误分级提示：认证失败、服务器不可达、共享名不存在、路径无效、超时。

2. 播放稳定性
- 单曲播放可用。
- 整目录顺序/随机播放可用。
- 退到后台后通知栏仍可控，继续播放不中断。

3. 封面与元数据
- 标题/艺术家/专辑显示合理。
- 同目录封面回退链路有效：`folder.jpg` -> `cover.jpg` -> `front.jpg`。

4. 歌词
- 同名外挂 LRC 可读取。
- 内嵌歌词字段（`LYRICS`）可读取。
- `offset` 与当前行二分定位逻辑正确。

5. D-Pad 导航
- 上下左右/确认操作连贯。
- `Back` 返回上级目录。
- `Menu` 打开 SMB 配置弹窗。
