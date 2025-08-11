# Clean Build Script for Clipboard Sync APP
Write-Host "=== Cleaning and Building Clipboard Sync APP ===" -ForegroundColor Green

# Step 1: Clean project
Write-Host "Step 1: Cleaning project..." -ForegroundColor Yellow
& ./gradlew clean

if ($LASTEXITCODE -ne 0) {
    Write-Host "Clean failed!" -ForegroundColor Red
    exit 1
}

# Step 2: Clean Gradle cache
Write-Host "Step 2: Cleaning Gradle cache..." -ForegroundColor Yellow
& ./gradlew cleanBuildCache

# Step 3: Build debug APK
Write-Host "Step 3: Building debug APK..." -ForegroundColor Yellow
& ./gradlew assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

# Step 4: Verify APK
Write-Host "Step 4: Verifying APK..." -ForegroundColor Yellow
$apkPath = "app/build/outputs/apk/debug/app-debug.apk"
if (Test-Path $apkPath) {
    $apkInfo = Get-Item $apkPath
    $sizeInMB = [math]::Round($apkInfo.Length / 1MB, 2)
    Write-Host "SUCCESS: APK built successfully!" -ForegroundColor Green
    Write-Host "APK Size: $sizeInMB MB" -ForegroundColor Cyan
    Write-Host "Location: $apkPath" -ForegroundColor Cyan
} else {
    Write-Host "ERROR: APK not found!" -ForegroundColor Red
    exit 1
}

Write-Host "`n=== Build Completed Successfully! ===" -ForegroundColor Green
