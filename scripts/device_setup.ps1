param(
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot',
    [string]$SdkRoot = 'D:\AndroidSDK',
    [string]$PackageName = 'com.getupandgetlit.dingshihai'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$apkPath = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'

if (-not (Test-Path $apkPath)) {
    throw "APK not found: $apkPath"
}

$env:GRADLE_USER_HOME = Join-Path $repoRoot '.gradle-user-home'
$env:JAVA_HOME = $JavaHome
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:HTTP_PROXY = ''
$env:HTTPS_PROXY = ''
$env:ALL_PROXY = ''
$env:NO_PROXY = ''

Write-Host '== adb devices =='
& (Join-Path $SdkRoot 'platform-tools\adb.exe') devices -l

Write-Host '== install apk =='
& (Join-Path $SdkRoot 'platform-tools\adb.exe') install -r $apkPath

Write-Host '== root check =='
& (Join-Path $SdkRoot 'platform-tools\adb.exe') shell su -c id

Write-Host '== whitelist before =='
& (Join-Path $SdkRoot 'platform-tools\adb.exe') shell su -c "dumpsys deviceidle whitelist"

Write-Host '== try add whitelist =='
& (Join-Path $SdkRoot 'platform-tools\adb.exe') shell su -c "dumpsys deviceidle whitelist +$PackageName"

Write-Host '== whitelist after =='
& (Join-Path $SdkRoot 'platform-tools\adb.exe') shell su -c "dumpsys deviceidle whitelist"

Write-Host '== flyme usb install allow =='
& (Join-Path $SdkRoot 'platform-tools\adb.exe') shell su -c "settings put secure usb_install_item_$PackageName '定时嗨:1'"
& (Join-Path $SdkRoot 'platform-tools\adb.exe') shell su -c "settings get secure usb_install_item_$PackageName"

Write-Host '== launch app =='
& (Join-Path $SdkRoot 'platform-tools\adb.exe') shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1

Write-Host '== optional doze test commands =='
Write-Host "adb shell dumpsys deviceidle force-idle"
Write-Host "adb shell dumpsys deviceidle step"
