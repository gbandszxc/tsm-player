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

页面职责是设置、修改、关闭睡眠倒计时：

```text
预设时间
[15 分钟] [30 分钟] [60 分钟] [120 分钟]

手动指定
小时：上下调整
分钟：上下调整

[开启睡眠 / 更新睡眠] [关闭睡眠] [返回]
```

手动选择采用遥控器友好的小时/分钟上下选择，不依赖软键盘。页面使用横屏 TV 布局，焦点顺序保持稳定：预设按钮、小时选择、分钟选择、确认按钮、关闭按钮、返回按钮。

## 运行边界

睡眠倒计时只保证在 App 进程存活，尤其是 `PlaybackService` 后台播放仍运行时生效。若用户或系统杀掉 App 进程，不承诺继续执行睡眠。

这个选择避免引入精确闹钟权限和高版本 API，符合电视厂商 Android 版本适配不积极的现实情况，也贴合音乐播放器后台播放的核心场景。

## 架构设计

### 模块划分

```text
sleep/
├─ SleepTimerStore
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

持久化使用 `SharedPreferences` 即可。读取时如果 `targetEpochMillis` 已过期，由 manager 清理并执行或视调用场景返回已过期状态。

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
   - Activity 读取到过期状态时应触发 manager 清理，避免显示负数。

4. App 进程被杀：
   - 不恢复执行已错过的睡眠动作。
   - 下次启动时如果存储中存在过期目标时间，直接清除。

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
   - 过期状态可被清理。

### 手动回归

1. 首次启动未授权时出现说明弹窗。
2. 跳过授权后可在设置页重新授权。
3. 播放页睡眠按钮未开启时只显示图标。
4. 开启 15 分钟后按钮显示图标 + 分钟数字。
5. 再次点击睡眠按钮进入 `SleepTimerActivity` 并可关闭倒计时。
6. 后台播放时倒计时继续，到点后停止播放、退出应用并尝试睡眠/息屏。
7. 未授权时到点仍停止播放并退出应用。
8. 修改完成后执行 Debug 打包验证。

## 文档维护

实现该功能时需同步更新：

1. `DESIGN.md`：记录睡眠按钮、`SleepTimerActivity`、设置页授权入口和焦点规则。
2. `docs/MANUAL.md`：记录睡眠定时器能力、运行边界和权限说明。
