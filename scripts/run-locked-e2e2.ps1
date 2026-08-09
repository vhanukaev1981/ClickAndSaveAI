param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$SourceSha = "ac2105098d698df06159f929f41595f91505c855",

    [Parameter(Mandatory = $true)]
    [ValidateSet("clickandsaveai-staging")]
    [string]$ConfirmProject
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$DeployScript = Join-Path $RepoRoot "scripts\deploy-locked-staging.ps1"
$DevicePreflightScript = Join-Path $RepoRoot "scripts\staging-device-preflight.ps1"

if ($ConfirmProject -ne "clickandsaveai-staging") {
    throw "E2E #2 is staging-only. ConfirmProject must be clickandsaveai-staging."
}

if ($SourceSha -notmatch '^[0-9a-f]{40}$') {
    throw "SourceSha must be an exact lowercase 40-character Git commit SHA."
}

if (-not (Test-Path $DeployScript)) {
    throw "Missing locked staging deploy helper: $DeployScript"
}

if (-not (Test-Path $DevicePreflightScript)) {
    throw "Missing staging device preflight helper: $DevicePreflightScript"
}

$ResolvedApk = (Resolve-Path $ApkPath).Path

Write-Host "============================================================"
Write-Host " Click&SaveAI locked staging E2E correction cycle #2"
Write-Host " Backend SHA: $SourceSha"
Write-Host " Project: clickandsaveai-staging"
Write-Host " APK: $ResolvedApk"
Write-Host "============================================================"
Write-Host ""

Write-Host "PHASE 1/2 — deploy exact locked backend" -ForegroundColor Cyan
& $DeployScript -SourceSha $SourceSha
if ($LASTEXITCODE -ne 0) {
    throw "Locked staging backend deploy failed. Device installation was not attempted."
}

Write-Host ""
Write-Host "PHASE 2/2 — verify/install paired APK and capture focused startup logs" -ForegroundColor Cyan
& $DevicePreflightScript -ApkPath $ResolvedApk
if ($LASTEXITCODE -ne 0) {
    throw "Device preflight failed after backend deployment."
}

Write-Host ""
Write-Host "PASS: locked backend and paired APK are aligned for E2E #2." -ForegroundColor Green
Write-Host "Continue with Google/Firebase sign-in -> Gmail readonly -> parser upgrade/backfill -> Financial Home -> Push/Savings validation."
