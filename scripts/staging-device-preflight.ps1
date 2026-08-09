param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ExpectedApkSha256 = "cbd841ed22f51f7fc1216ee1dd23148fb468965659e9ff3d5aa1831736adfd0a"
$PackageName = "com.aistudio.clickandsaveai.app"
$ActivityName = "com.example.MainActivity"
$Adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

function Assert-LastExitCode([string]$Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

$ResolvedApk = (Resolve-Path $ApkPath).Path
if (-not (Test-Path $ResolvedApk)) {
    throw "APK not found: $ApkPath"
}

if (-not (Test-Path $Adb)) {
    throw "adb.exe was not found at $Adb"
}

Write-Host "[1/6] Verifying locked staging APK SHA-256..."
$ActualHash = (Get-FileHash -Algorithm SHA256 $ResolvedApk).Hash.ToLowerInvariant()
if ($ActualHash -ne $ExpectedApkSha256) {
    throw "APK hash mismatch. Expected $ExpectedApkSha256 but got $ActualHash. Do not install this APK for locked E2E #2."
}

Write-Host "[2/6] Verifying one Android device is connected..."
$DeviceLines = @(& $Adb devices) | Where-Object { $_ -match "`tdevice$" }
Assert-LastExitCode "adb devices"
if ($DeviceLines.Count -ne 1) {
    throw "Expected exactly one authorized Android device, found $($DeviceLines.Count)."
}

Write-Host "[3/6] Installing locked staging APK..."
& $Adb install -r $ResolvedApk
Assert-LastExitCode "adb install"

Write-Host "[4/6] Verifying installed package..."
$PackagePath = @(& $Adb shell pm path $PackageName)
Assert-LastExitCode "adb shell pm path"
if (-not ($PackagePath -join "`n").Contains("package:")) {
    throw "Package $PackageName is not installed after adb install."
}

Write-Host "[5/6] Starting ClickAndSaveAI with a clean log buffer..."
& $Adb logcat -c
Assert-LastExitCode "adb logcat -c"
& $Adb shell am force-stop $PackageName
Assert-LastExitCode "adb force-stop"
& $Adb shell am start -n "$PackageName/$ActivityName"
Assert-LastExitCode "adb start"
Start-Sleep -Seconds 5

Write-Host "[6/6] Capturing focused E2E startup logs..."
$FocusedLogs = & $Adb logcat -d | Select-String "AppCheck|DebugAppCheckProvider|FirebaseAppCheck|MainActivity|PushRegistration|ObservedBills|GmailRepository|TestPush|ClickAndSave"
Assert-LastExitCode "adb logcat -d"
$FocusedLogs | ForEach-Object { $_.Line }

Write-Host ""
Write-Host "PASS: locked staging APK installed and started on the connected device." -ForegroundColor Green
Write-Host "APK SHA-256: $ActualHash"
Write-Host "Package: $PackageName"
