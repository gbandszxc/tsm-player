---
name: tsm-release
description: Project-specific release workflow for tsm-player. Use this whenever the user asks to publish a new version, bump the app version, create or update a GitHub Release, upload release APK assets, rerun the release script, overwrite existing release assets, or mentions release/package/upload tasks for this project. This skill covers changelog handling, optional commit-log summarization, same-version asset overwrite releases, version updates, docs maintenance, Debug/Release builds, git push, GitHub Release creation, and two-ABI APK asset verification.
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

先判断用户意图：

- 如果用户表达的是“新版本发版 / 迭代版本号 / 发布下一版”，按标准新发版处理，通常需要更新日志。
- 如果用户表达的是“覆盖发版 / 覆盖附件 / 重新打包同版本 / 同版本重新上传 / 修正 Release assets”，按覆盖同版本附件处理，不强制要求更新日志。

### 标准新发版

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

### 覆盖同版本附件

覆盖发版的目标是基于最新代码重新构建同一个 `versionName` 的两个 APK，并覆盖同一个 GitHub Release tag 下的同名附件。

1. 如果用户明确要求覆盖已有 Release 附件：
   - 可以不要求用户提供更新日志。
   - 不要递增 `versionName`。
   - 不要递增 `versionCode`，除非用户明确要求。
   - 默认不修改 GitHub Release body/title。
2. 覆盖前仍需确认：
   - 当前 `versionName` 对应的 `v<versionName>` Release 已存在。
   - 当前代码是用户希望覆盖发布的代码。
   - 若存在未提交改动，先说明 `scripts/release.ps1` 要求工作区干净，并让用户确认是先提交这些改动再覆盖，还是先停止。

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

如果用户是覆盖同版本附件：

- 使用当前 `app/build.gradle` 中已有的 `versionName/versionCode`。
- 要求用户传入或确认相同版本号时，不要把相同版本号视为错误。
- 覆盖同版本附件不会触发应用内“新版本”检测；它只修正同一 Release 下的下载包。

### 3. 修改项目文件

标准新发版至少更新：

- `app/build.gradle`
  - `versionName`
  - `versionCode`
- `docs/CHANGELOG.md`
  - 追加版本里程碑或发版事项。
- `docs/MANUAL.md`
  - 如果发版流程、产物命名、脚本参数或项目级行为变化，必须同步维护。

保留现有风格，中文记录，避免无关重构。

覆盖同版本附件通常不需要修改版本号、更新日志或手册；只有代码本身有变更时，按项目约定维护必要文档。

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

覆盖同版本附件时，如果只是重新打包当前已提交代码且工作区干净，可以不创建新提交；如果当前代码有改动，则需要先提交并推送再覆盖附件，确保 Release 资产对应可追溯的代码。

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

如果 Release 已存在且需要覆盖资产，并且要基于最新代码重新打包：

```powershell
.\scripts\release.ps1 -Clobber
```

如果 Release 已存在且只需要上传当前已有构建产物：

```powershell
.\scripts\release.ps1 -SkipBuild -Clobber
```

谨慎使用 `-Clobber`，先确认 tag 与版本号确实匹配。覆盖同版本附件时通常使用当前 `app/build.gradle` 的相同版本号，并让脚本重新构建后覆盖上传。

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
- 标准新发版时，`body` 包含本次更新日志。
- 覆盖同版本附件时，默认不要求 `body` 变化，只确认 Release 仍可正常读取。
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
- 标准新发版不要复用旧版本号创建新 Release。
- 覆盖同版本附件可以复用旧版本号，但必须使用 `-Clobber` 覆盖同名 assets。
- 标准新发版不要在用户未提供更新日志且拒绝智能总结时继续发版；覆盖同版本附件不强制更新日志。
- 不要跳过 `assembleDebug`，项目约定要求修改完成后 Debug 打包。
