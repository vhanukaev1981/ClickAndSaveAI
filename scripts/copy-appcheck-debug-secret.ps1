$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$Adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$PackageName = "com.aistudio.clickandsaveai.app"
$ActivityName = "com.example.MainActivity"

function Assert-LastExitCode([string]$Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

if (-not (Test-Path $Adb)) {
    throw "adb.exe was not found at $Adb"
}

$DeviceLines = @(& $Adb devices) | Where-Object { $_ -match "`tdevice$" }
Assert-LastExitCode "adb devices"
if ($DeviceLines.Count -ne 1) {
    throw "Expected exactly one authorized Android device, found $($DeviceLines.Count)."
}

Write-Host "Starting the staging app and collecting App Check debug output locally..."
& $Adb logcat -c
Assert-LastExitCode "adb logcat -c"
& $Adb shell am force-stop $PackageName
Assert-LastExitCode "adb force-stop"
& $Adb shell am start -n "$PackageName/$ActivityName" | Out-Null
Assert-LastExitCode "adb start"
Start-Sleep -Seconds 5

$LogLines = @(& $Adb logcat -d)
Assert-LastExitCode "adb logcat -d"

$Candidates = New-Object System.Collections.Generic.HashSet[string]
$UuidPattern = '\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b'
foreach ($Line in $LogLines) {
    if ($Line -notmatch '(?i)AppCheck|DebugAppCheckProvider') { continue }
    foreach ($Match in [regex]::Matches($Line, $UuidPattern)) {
        [void]$Candidates.Add($Match.Value)
    }
}

if ($Candidates.Count -eq 0) {
    throw "No App Check debug secret was found. Confirm this is the debug staging APK, then run the helper again."
}
if ($Candidates.Count -gt 1) {
    throw "More than one App Check debug secret was found. Clear logcat and retry before registering anything."
}

$Secret = @($Candidates)[0]
Set-Clipboard -Value $Secret

# Never print the credential itself. This message is safe to paste into support/chat.
Write-Host "PASS: one App Check debug secret was copied to the Windows clipboard." -ForegroundColor Green
Write-Host "Paste it directly into Firebase Console -> App Check -> Android app -> Manage debug tokens."
Write-Host "Do not paste the secret into chat, GitHub, logs or screenshots."

Remove-Variable Secret -ErrorAction SilentlyContinue
