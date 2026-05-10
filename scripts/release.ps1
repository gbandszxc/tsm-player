param(
    [string]$TagName,
    [string]$Title,
    [string[]]$ReleaseNotes = @(
        "增加自动检查更新与手动检查入口",
        "播放页面图片区域支持选中全屏查看"
    ),
    [switch]$SkipBuild,
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$Clobber
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-RepoRoot {
    $root = (& git rev-parse --show-toplevel 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
        throw "当前目录不在 Git 仓库中。"
    }
    return $root.Trim()
}

function Read-GradleVersion {
    param([string]$BuildFile)

    $content = Get-Content -LiteralPath $BuildFile -Raw
    $nameMatch = [regex]::Match($content, 'versionName\s+"([^"]+)"')
    $codeMatch = [regex]::Match($content, 'versionCode\s+(\d+)')
    if (-not $nameMatch.Success -or -not $codeMatch.Success) {
        throw "无法从 $BuildFile 读取 versionName/versionCode。"
    }

    [pscustomobject]@{
        Name = $nameMatch.Groups[1].Value
        Code = [int]$codeMatch.Groups[1].Value
    }
}

function Assert-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "未找到命令：$Name"
    }
}

function Assert-CleanWorktree {
    $status = (& git status --porcelain)
    if ($LASTEXITCODE -ne 0) {
        throw "无法读取 Git 状态。"
    }
    if ($status) {
        throw "工作区存在未提交改动，请先提交或暂存后再发版。"
    }
}

function New-ReleaseNotesFile {
    param(
        [string]$RepoRoot,
        [string[]]$Lines
    )

    $notesPath = Join-Path $RepoRoot "build\release-notes.md"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $notesPath) | Out-Null
    $body = ($Lines | ForEach-Object { "- $_" }) -join [Environment]::NewLine
    Set-Content -LiteralPath $notesPath -Value $body -Encoding UTF8
    return $notesPath
}

function Get-ReleaseApks {
    param(
        [string]$RepoRoot,
        [string]$Name
    )

    $releaseDir = Join-Path $RepoRoot "app\build\outputs\apk\release"
    $expected = @(
        "tsm-player-release-armeabi-v7a-$Name.apk",
        "tsm-player-release-arm64-v8a-$Name.apk"
    )

    $paths = foreach ($fileName in $expected) {
        $path = Join-Path $releaseDir $fileName
        if (-not (Test-Path -LiteralPath $path)) {
            throw "缺少 Release APK：$path"
        }
        $item = Get-Item -LiteralPath $path
        if ($item.Length -le 0) {
            throw "Release APK 文件为空：$path"
        }
        $path
    }

    return $paths
}

$repoRoot = Resolve-RepoRoot
Set-Location -LiteralPath $repoRoot

Assert-Command "git"
Assert-Command "gh"

$buildFile = Join-Path $repoRoot "app\build.gradle"
$version = Read-GradleVersion -BuildFile $buildFile
if ([string]::IsNullOrWhiteSpace($TagName)) {
    $TagName = "v$($version.Name)"
}
if ([string]::IsNullOrWhiteSpace($Title)) {
    $Title = $TagName
}

Assert-CleanWorktree

Write-Host "Release: $TagName (versionCode $($version.Code))"

if (-not $SkipBuild) {
    & .\gradlew.bat clean assembleRelease -x lintVitalAnalyzeRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Release 构建失败。"
    }
}

$apks = Get-ReleaseApks -RepoRoot $repoRoot -Name $version.Name
$notesFile = New-ReleaseNotesFile -RepoRoot $repoRoot -Lines $ReleaseNotes
$target = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($target)) {
    throw "无法读取当前提交。"
}

$releaseExists = $false
& gh release view $TagName *> $null
if ($LASTEXITCODE -eq 0) {
    $releaseExists = $true
}

if ($releaseExists) {
    if (-not $Clobber) {
        throw "GitHub Release $TagName 已存在；如需覆盖资产请加 -Clobber。"
    }
    & gh release upload $TagName @apks --clobber
    if ($LASTEXITCODE -ne 0) {
        throw "上传 Release 资产失败。"
    }
    Write-Host "已更新 Release 资产：$TagName"
    exit 0
}

$ghArgs = @("release", "create", $TagName)
$ghArgs += $apks
$ghArgs += @("--title", $Title, "--notes-file", $notesFile, "--target", $target, "--latest")
if ($Draft) {
    $ghArgs += "--draft"
}
if ($Prerelease) {
    $ghArgs += "--prerelease"
}

& gh @ghArgs
if ($LASTEXITCODE -ne 0) {
    throw "创建 GitHub Release 失败。"
}

Write-Host "已创建 GitHub Release：$TagName"
