---
name: tsm-release
description: 当前 tsm-player 项目的专用发版流程。每当用户要求发布新版本、更新版本号、创建 GitHub Release、上传 release APK、运行发版脚本、或说“发版/发布/打包上传/更新 release”时都要使用。该 skill 会固定执行版本迭代、更新日志处理、文档维护、Debug/Release 构建、提交推送、GitHub Release 创建与两个 ABI APK 上传校验。
---

# tsm-player 发版流程

这个 skill 只服务于当前仓库根目录对应的 `tsm-player` 项目。目标是让发版过程稳定、可复用，并避免漏掉版本号、文档、两个 ABI APK 或 GitHub Release 资产。

## 适用场景

当用户要求以下任一事项时，使用本 skill：

- 迭代版本号并发布
- 创建或更新 GitHub Release
- 上传 release APK
- 执行 `scripts/release.ps1`
- 根据最近提交生成发版说明
- 处理 tsm-player 的正式版本交付

## 前置约束

1. 始终使用中文与用户沟通。
2. 当前环境是 Windows 11 + PowerShell，不使用 Unix 命令。
3. 发版前必须确认 `gh` 可用且已登录。
4. 该项目 release 产物必须包含两个 ABI：
   - `armeabi-v7a`
   - `arm64-v8a`
5. GitHub Release asset 命名必须匹配：
   - `tsm-player-release-armeabi-v7a-<versionName>.apk`
   - `tsm-player-release-arm64-v8a-<versionName>.apk`
6. 不交付无法编译或运行就报错的代码；完成修改后必须跑 Debug 打包。

## 更新日志决策

发版说明是发布质量的一部分，不能空着猜。

1. 如果用户已经提供更新日志或明确说明发版内容：
   - 直接使用用户提供的内容。
   - 可以润色为 GitHub Release 的项目符号，但不要改变事实含义。
2. 如果用户没有提供更新日志：
   - 先询问用户：`这次没有提供更新日志，需要我根据上次发版后的 git 提交记录智能总结吗？`
   - 如果用户回答需要：
     - 用 `gh release list --limit 1` 或最新 git tag 找到上次发版 tag。
     - 用 `git log <lastTag>..HEAD --oneline` 查看上次发版后的提交。
     - 汇总为简短、面向用户的 Release Notes。
     - 如果提交记录不足以判断用户可见变化，明确说明不确定点并请用户确认。
   - 如果用户回答不需要：
     - 提醒用户必须提供更新日志后才能继续发版。
     - 停止发版流程，等待用户补充。

## 标准流程

### 1. 收集上下文

在仓库根目录执行：

```powershell
git status --short
Get-Content -LiteralPath .\AGENTS.md
Get-Content -LiteralPath .\docs\MANUAL.md
Get-Content -LiteralPath .\app\build.gradle
gh repo view --json nameWithOwner,defaultBranchRef,url
gh release list --limit 10
```

确认：

- 当前分支和工作区状态。
- 最新 GitHub Release tag。
- 当前 `versionName` 和 `versionCode`。
- `scripts\release.ps1` 是否存在。

### 2. 决定版本号

默认采用补丁版本递增：

- `1.0.6` -> `1.0.7`
- `versionCode` 加 1

如果用户明确指定版本号或版本策略，以用户要求为准。版本号必须高于当前 GitHub 最新 Release，否则会影响应用内更新检测。

### 3. 修改项目文件

至少更新：

- `app/build.gradle`
  - `versionName`
  - `versionCode`
- `docs/CHANGELOG.md`
  - 追加版本里程碑或发版事项。
- `docs/MANUAL.md`
  - 如果发版流程、产物命名、脚本参数或项目级行为变化，必须同步维护。

保留现有风格，中文记录，避免无关重构。

### 4. 验证构建

依次执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease
```

如果构建失败：

- 先读完整错误。
- 修复后重新运行失败命令。
- 不要继续提交、推送或发版。

已知但不阻塞的提示包括：

- `ProgressDialog` deprecated 警告。
- Gradle/JDK native access warning。
- `libdatastore_shared_counter.so` strip warning。

### 5. 提交并推送

发版脚本要求工作区干净，因此版本号和文档修改需要先提交。

提交信息使用项目约定格式，例如：

```powershell
git add app/build.gradle docs/CHANGELOG.md docs/MANUAL.md scripts/release.ps1
git commit -m "chore: 发布 1.0.7" -m "- versionCode 升至 8，versionName 升至 1.0.7" -m "- 更新发版说明与项目文档"
git push origin main
```

如果当前分支不是 `main`，先确认用户期望：直接在当前分支发版、合并到 `main` 后发版，还是创建 PR。

### 6. 执行发版脚本

标准发布：

```powershell
.\scripts\release.ps1
```

脚本会：

- 读取 `app/build.gradle` 中的版本。
- 执行 `clean assembleRelease -x lintVitalAnalyzeRelease`。
- 校验两个 ABI 的 release APK 存在且非空。
- 创建 `v<versionName>` GitHub Release。
- 上传两个 APK。
- 使用 Release Notes：
  - 默认脚本内置说明适合 `1.0.6`。
  - 后续版本如果说明不同，应通过 `-ReleaseNotes` 传入。

示例：

```powershell
.\scripts\release.ps1 -ReleaseNotes @(
    "修复播放页返回后状态恢复问题",
    "优化 SMB 目录加载稳定性"
)
```

如果 Release 已存在且需要覆盖资产：

```powershell
.\scripts\release.ps1 -SkipBuild -Clobber
```

谨慎使用 `-Clobber`，先确认 tag 与版本号确实匹配。

### 7. 校验 Release

发版后必须执行：

```powershell
gh release view v<versionName> --json tagName,name,url,assets,body,targetCommitish,isDraft,isPrerelease
Get-ChildItem -LiteralPath .\app\build\outputs\apk\release -Filter "tsm-player-release-*-<versionName>.apk" | Select-Object Name,Length
git status --short
```

确认：

- `tagName` 是预期版本。
- `targetCommitish` 指向刚推送的提交。
- `body` 包含本次更新日志。
- assets 中有且只有预期的两个 APK。
- 两个 APK size 大于 0。
- Release 不是 draft，除非用户明确要求。
- 工作区干净。

### 8. 最终回复格式

最终回复保持简洁，包含：

- 版本号与提交。
- Release URL。
- 已上传的两个 APK 名称。
- 跑过的验证命令。
- 任何非阻塞警告。
- 如果执行了 git commit/push，按 Codex app 规范附加对应 git 指令。

示例：

```text
已发布 v1.0.7。

Release: https://github.com/<owner>/tsm-player/releases/tag/v1.0.7

已上传：
- tsm-player-release-arm64-v8a-1.0.7.apk
- tsm-player-release-armeabi-v7a-1.0.7.apk

验证已跑：testDebugUnitTest、assembleDebug、release 脚本真实发版、gh release view 校验。
```

## 常见风险

- 不要在未提交的脏工作区执行 `scripts\release.ps1`。
- 不要只上传一个 ABI APK。
- 不要复用旧版本号创建新 Release。
- 不要在用户未提供更新日志且拒绝智能总结时继续发版。
- 不要跳过 `assembleDebug`，项目约定要求修改完成后 Debug 打包。
