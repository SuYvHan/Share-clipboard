# 自动发布脚本 (PowerShell版本)
# 用法: .\scripts\release.ps1 -Version "v1.0.0" [-ReleaseNotes "发布说明"]

param(
    [Parameter(Mandatory=$true)]
    [string]$Version,
    
    [Parameter(Mandatory=$false)]
    [string]$ReleaseNotes = ""
)

# 颜色函数
function Write-Info {
    param([string]$Message)
    Write-Host "ℹ️  $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

# 验证版本号格式
if ($Version -notmatch '^v\d+\.\d+\.\d+$') {
    Write-Error "版本号格式错误，应为 vX.Y.Z 格式，如 v1.0.0"
    exit 1
}

Write-Info "准备发布版本: $Version"

# 检查是否在git仓库中
try {
    git rev-parse --git-dir | Out-Null
} catch {
    Write-Error "当前目录不是git仓库"
    exit 1
}

# 检查工作目录是否干净
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Warning "工作目录有未提交的更改"
    $continue = Read-Host "是否继续？(y/N)"
    if ($continue -ne 'y' -and $continue -ne 'Y') {
        Write-Info "发布已取消"
        exit 1
    }
}

# 检查标签是否已存在
try {
    git rev-parse $Version | Out-Null
    Write-Error "标签 $Version 已存在"
    exit 1
} catch {
    # 标签不存在，继续
}

# 获取当前分支
$currentBranch = git branch --show-current

Write-Info "当前分支: $currentBranch"

# 确保在main或master分支
if ($currentBranch -ne "main" -and $currentBranch -ne "master") {
    Write-Warning "建议在main或master分支进行发布"
    $continue = Read-Host "是否继续？(y/N)"
    if ($continue -ne 'y' -and $continue -ne 'Y') {
        Write-Info "发布已取消"
        exit 1
    }
}

# 拉取最新代码
Write-Info "拉取最新代码..."
git pull origin $currentBranch

# 运行测试
Write-Info "运行测试..."
if (Test-Path ".\gradlew.bat") {
    .\gradlew.bat test
    Write-Success "测试通过"
} else {
    Write-Warning "未找到gradlew.bat，跳过测试"
}

# 更新版本号
Write-Info "更新版本号..."
$versionName = $Version.Substring(1)  # 移除v前缀
$versionCode = [int][double]::Parse((Get-Date -UFormat %s))

# 更新build.gradle.kts中的版本号
if (Test-Path "app\build.gradle.kts") {
    $content = Get-Content "app\build.gradle.kts"
    $content = $content -replace 'versionName = ".*"', "versionName = `"$versionName`""
    $content = $content -replace 'versionCode = \d+', "versionCode = $versionCode"
    Set-Content "app\build.gradle.kts" $content
    Write-Success "版本号已更新: $versionName (code: $versionCode)"
} else {
    Write-Warning "未找到app\build.gradle.kts文件"
}

# 提交版本更新
Write-Info "提交版本更新..."
git add .
try {
    git commit -m "chore: bump version to $Version"
} catch {
    Write-Warning "没有需要提交的更改"
}

# 创建标签
Write-Info "创建标签: $Version"
if ($ReleaseNotes) {
    git tag -a $Version -m $ReleaseNotes
} else {
    git tag -a $Version -m "Release $Version"
}

# 推送代码和标签
Write-Info "推送代码和标签..."
git push origin $currentBranch
git push origin $Version

Write-Success "标签 $Version 已创建并推送"
Write-Info "GitHub Actions将自动构建并创建Release"

# 显示GitHub Actions链接
$repoUrl = git config --get remote.origin.url
$repoUrl = $repoUrl -replace '\.git$', ''
if ($repoUrl -match '^git@github\.com:') {
    $repoUrl = $repoUrl -replace '^git@github\.com:', 'https://github.com/'
}

Write-Info "查看构建状态: $repoUrl/actions"
Write-Info "查看发布页面: $repoUrl/releases"

Write-Success "发布流程已启动！🎉"
