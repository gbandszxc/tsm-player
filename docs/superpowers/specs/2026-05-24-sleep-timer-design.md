# 睡眠定时器设计

日期：2026-05-24

## 背景

TSM Player 是面向 Android TV 的 SMB 本地音乐播放器。用户希望在睡前播放音乐时开启睡眠模式，支持 15、30、60、120 分钟预设和手动指定时间；倒计时结束后停止播放、退出应用，并让内置 Android TV 进入睡眠或息屏。

Android 普通三方应用不能稳定直接关机，真正断电关机通常依赖系统签名权限、厂商能力或系统级 HDMI-CEC 能力。当前功能面向内置 Android TV 电视，采用设备管理能力执行睡眠/息屏，不承诺真正关机。

## 目标

1. 首次启动应用时引导用户授予设备管理权限。
2. 设置页提供睡眠权限状态和手动重新授权入口。
3. 播放页提供紧凑的睡眠按钮，开启后显示剩余分钟。
4. 新增独立睡眠设置页面，支持预设时间和小时/分钟手动选择。
5. 播放器后台播放仍存活时，睡眠倒计时继续生效。
6. 到点后停止播放、退出应用，并调用电视睡眠/息屏。

## 非目标

1. 不实现真正断电关机。
2. 不承诺外接 Android TV 盒子通过 HDMI-CEC 关闭电视。
3. 不使用 Android 12+ 精确闹钟权限，不要求 App 进程被杀后仍继续执行倒计时。
4. 不把睡眠功能做成系统级闹钟或跨重启任务。

## 用户体验

### 首次授权

首次进入 `MainActivity` 时检查设备管理权限：

```text
MainActivity
└─ 检查 DevicePolicyManager 是否已激活本 App 的 DeviceAdminReceiver
   ├─ 已激活：不打扰用户
   └─ 未激活：显示项目风格说明弹窗
      ├─ 去授权：打开系统 ACTION_ADD_DEVICE_ADMIN 页面
      └─ 暂不授权：继续使用播放器
```

弹窗文案应明确说明：该权限用于睡眠定时结束时让电视进入睡眠或息屏；若不授权，睡眠到点仍会停止播放并退出应用，但无法保证电视息屏。

### 设置页

在“应用设置”中新增睡眠权限项：

```text
睡眠权限：已授权 / 未授权
说明：用于睡眠定时结束时让电视进入睡眠或息屏
[去授权 / 重新授权]
```

如果用户首次启动时跳过授权，可从这里重新进入系统授权页。这样可以避免倒计时结束时才发现权限缺失。

### 播放页按钮

在播放页底部按钮栏新增睡眠按钮。按钮紧凑显示：

```text
未开启：睡眠图标
已开启：睡眠图标 + 剩余分钟数字
示例：图标 42
```

剩余分钟按向上取整显示，避免刚开启 30 分钟时马上显示 29。按钮文字不显示长句，避免挤压现有播放控制栏。图标使用项目内 VectorDrawable，样式优先复用 `@style/Tsm*`、`@color/ui_*`、`@dimen/ui_*` 与现有按钮背景。

点击睡眠按钮打开独立 `SleepTimerActivity`。如果当前已有倒计时，页面展示剩余时间，并允许修改或关闭。

### SleepTimerActivity

页面职责是设置、修改、关闭睡眠倒计时。

**默认状态：**

```text
睡眠时间
[15 分] [30 分] [60 分] [120 分] [自定义]

[开启睡眠] [关闭睡眠] [返回]
```

**选中自定义后：**

```text
睡眠时间
[自定义]  ← 收起四个预设，仅保留自定义（选中态）

   ┌─────┐    ┌─────┐
   │ 01  │    │ 29  │
 > │▓02▓│  > │▓30▓│  <  左右键切换小时/分钟滚轮焦点
   │ 03  │    │ 31  │
   └─────┘    └─────┘
    小时        分钟

[开启睡眠] [关闭睡眠] [返回]
```

**行为规则：**

- 预设按钮（15/30/60/120 分）：点击直接开启/更新定时，Toast 提示。
- 自定义按钮：点击后收起四个预设，仅保留自定义按钮（选中态），下方展开时间选择滚轮。再次点击或按返回键恢复预设行。
- 自定义时间只保存最近一次成功开启/更新的自定义时长；未开启睡眠定时时，再次点击自定义默认回显该历史时长。
- 时间选择滚轮：小时 0-23（±1），分钟 0-59（±1），均支持长按/滑动加速。
- 滚轮视觉：3 行（上一行弱化、当前行高亮蓝填充+描边、下一行弱化）。
- 遥控器：上下键逐行滚动（长按加速），左右键切换小时/分钟滚轮焦点。
- 触屏：单指上下滑动，惯性滚动，松手吸附最近项。
- 自定义模式下按返回键：取消自定义，恢复预设行显示。
- 已开启定时后进入页面：显示剩余时间，自定义滚轮回显当前设定值（如设定 2小时30分，小时滚轮停在 02，分钟滚轮停在 30），优先于最近一次自定义历史。
- 开启/更新按钮：保持"开启睡眠"（未开启时）/"更新睡眠"（已开启时）文字。
- 关闭睡眠按钮：红色背景（`TsmButtonDanger`），点击后取消定时。

## 运行边界

睡眠倒计时只保证在 App 进程存活，尤其是 `PlaybackService` 后台播放仍运行时生效。若用户或系统杀掉 App 进程，不承诺继续执行睡眠。

这个选择避免引入精确闹钟权限和高版本 API，符合电视厂商 Android 版本适配不积极的现实情况，也贴合音乐播放器后台播放的核心场景。

## 架构设计

### 模块划分

```text
sleep/
├─ SleepTimerStore
├─ SleepTimerCustomDurationStore
├─ SleepTimerStartupCoordinator
├─ SleepTimerManager
├─ SleepDeviceAdminReceiver
├─ SleepDeviceController
└─ SleepAppExitController

ui/
└─ SleepTimerActivity
```

### SleepTimerStore

负责持久化睡眠倒计时状态：

```text
enabled: Boolean
targetEpochMillis: Long
durationMinutes: Int
```

持久化使用 `SharedPreferences` 即可。普通读取到过期目标时间时仅返回关闭状态给 UI，不清理底层状态；到点动作由 `SleepTimerManager.executeIfDue()` 使用 raw 状态消费并清理，避免播放页刷新提前清空定时，导致服务错过执行。

### SleepTimerCustomDurationStore

负责持久化最近一次成功开启/更新的自定义时长：

```text
durationMinutes: Int
```

该状态独立于当前睡眠倒计时，只用于自定义滚轮默认回显。默认值为 30 分钟，只保存最近一次，写入时对小于 1 分钟的值兜底为 1 分钟。

### SleepTimerStartupCoordinator

负责应用进程启动时的一次性睡眠状态清理：

```text
clearActiveTimerOnce(store)
```

`MainActivity.onCreate()` 调用进程级 `SleepTimerStartup.clearActiveTimerOnProcessStart()`。该逻辑在当前进程内只执行一次，清除未完成的睡眠倒计时，避免应用完全退出后重新打开仍显示上次睡眠状态；它不清理 `SleepTimerCustomDurationStore`，因此最近一次自定义时长继续保留。

### SleepTimerManager

负责业务状态：

```text
start(durationMinutes)
cancel()
currentState()
remainingMillis(now)
remainingMinutesCeil(now)
executeIfDue(now)
```

Activity 只读 manager 状态并发起 start/cancel，不直接计算到期执行逻辑。这样播放页、睡眠页和服务端行为保持一致。

### PlaybackService 集成

`PlaybackService` 是后台播放时最可靠的存活点。服务创建后启动轻量级检查任务：

```text
每 30-60 秒检查一次 SleepTimerManager.executeIfDue()
```

检查频率不需要秒级精确；显示只精确到分钟，到点误差在一分钟内可接受。播放页可用独立 UI 定时刷新按钮文案，不依赖服务推送。

到点执行顺序：

```text
保存当前播放快照
停止播放
清除睡眠倒计时状态
退出应用 Activity 栈
调用 DevicePolicyManager.lockNow()
```

如果睡眠权限缺失或调用失败，仍应完成保存快照、停止播放和退出应用。下一次进入设置页时展示未授权状态，引导用户重新授权。

### SleepDeviceController

封装系统睡眠能力：

```text
isDeviceAdminActive(): Boolean
openDeviceAdminSettings(activity)
sleepNow(): SleepResult
```

`sleepNow()` 使用 `DevicePolicyManager.lockNow()`。该 API 从低版本 Android 已可用，符合当前 `minSdk 21`。文案统一为“睡眠/息屏”，不使用“关机”作为按钮或设置项主文案。

### SleepAppExitController

负责退出应用。优先方案是在 `BaseActivity` 中维护当前 Activity 注册表：

```text
BaseActivity.onCreate -> register
BaseActivity.onDestroy -> unregister
SleepAppExitController.finishAll()
```

当前主页面、播放页、歌词页、设置页均继承 `BaseActivity`，适合集中管理。到点时由服务触发 `finishAll()`，完成 Activity 栈退出。若存在未继承 `BaseActivity` 的未来页面，需要补充接入。

## UI 与设计规范

涉及 UI、交互、焦点和播放页按钮栏变更，需同步维护根目录 `DESIGN.md`。

实现时遵循：

1. 颜色使用 `@color/ui_*`。
2. 尺寸、间距、圆角、字号使用 `@dimen/ui_*`。
3. 按钮和文本优先复用 `@style/Tsm*`。
4. 背景优先复用现有 `@drawable/bg_*`。
5. 睡眠按钮图标可新增一次性 VectorDrawable；通用颜色仍引用 token。

播放页按钮栏示意：

```text
[上一首] [播放/暂停] [下一首] [播放模式] [睡眠图标 42] [歌词全屏] [返回] [定位]
```

## 错误处理

1. 用户未授权设备管理权限：
   - 首次启动和设置页均可引导授权。
   - 到点时仍停止播放并退出应用，但无法保证睡眠/息屏。

2. 用户关闭倒计时：
   - 清除 `SleepTimerStore` 状态。
   - 播放页按钮恢复为仅图标状态。

3. 目标时间已过：
   - 服务检查到期时执行睡眠流程。
   - Activity 读取到过期状态时仅显示未开启，避免显示负数，不消费服务执行动作所需状态。

4. App 进程被杀：
   - 不恢复执行已错过的睡眠动作。
   - 下次冷启动进入主界面时清除未完成或已过期的睡眠倒计时。
   - 最近一次自定义时长不随冷启动清理。

## 测试计划

### 单元测试

1. `SleepTimerManager.remainingMinutesCeil()`：
   - 30 分钟刚开启显示 30。
   - 剩余 29 分 1 秒显示 30。
   - 剩余 29 分整显示 29。
   - 已过期不显示负数。

2. `SleepTimerStore`：
   - start 后可读取目标时间和时长。
   - cancel 后状态为空。
   - 普通读取过期状态不会消费到点动作状态。

3. `SleepTimerCustomDurationStore`：
   - 未保存时返回默认 30 分钟。
   - 多次保存后只读取最近一次自定义时长。
   - 小于 1 分钟的写入值按 1 分钟兜底。

4. `SleepTimerStartupCoordinator`：
   - 进程启动清理只执行一次。
   - 清理未完成睡眠倒计时时保留最近一次自定义时长。

### 手动回归

1. 首次启动未授权时出现说明弹窗。
2. 跳过授权后可在设置页重新授权。
3. 播放页睡眠按钮未开启时只显示图标。
4. 开启 15 分钟后按钮显示图标 + 分钟数字。
5. 再次点击睡眠按钮进入 `SleepTimerActivity` 并可关闭倒计时。
6. 后台播放时倒计时继续，到点后停止播放、退出应用并尝试睡眠/息屏。
7. 未授权时到点仍停止播放并退出应用。
8. 自定义开启/更新后关闭页面，未开启睡眠定时时再次进入自定义模式应回显最近一次自定义时长。
9. 已有睡眠定时时进入自定义模式应优先回显当前定时值，而不是历史自定义时长。
10. 设置睡眠定时后完全退出应用，再重新打开，睡眠状态应为未开启。
11. 完全退出重开后，点击自定义仍应回显最近一次自定义时长。
12. 修改完成后执行 Debug 打包验证。

## 文档维护

实现该功能时需同步更新：

1. `DESIGN.md`：记录睡眠按钮、`SleepTimerActivity`、自定义时间持久化、设置页授权入口和焦点规则。
2. `docs/MANUAL.md`：记录睡眠定时器能力、运行边界和权限说明。
