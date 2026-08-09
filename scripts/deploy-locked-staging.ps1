param(
    [string]$SourceSha = "ac2105098d698df06159f929f41595f91505c855"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectId = "clickandsaveai-staging"
$ExpectedStreamBranch = "origin/agent/ai-native-financial-core"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$FirebaseCmd = Join-Path $env:APPDATA "npm\firebase.cmd"
$NpmCmd = (Get-Command npm.cmd -ErrorAction SilentlyContinue)?.Source
$WorktreeRoot = Join-Path $env:TEMP ("ClickAndSaveAI-staging-" + $SourceSha.Substring(0, [Math]::Min(8, $SourceSha.Length)))
$LocalStagingEnv = Join-Path $RepoRoot "functions\.env.clickandsaveai-staging"

function Assert-LastExitCode([string]$Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

if ($SourceSha -notmatch '^[0-9a-f]{40}$') {
    throw "SourceSha must be an exact lowercase 40-character Git commit SHA."
}

if (-not (Test-Path $FirebaseCmd)) {
    throw "firebase.cmd was not found at $FirebaseCmd"
}

if (-not $NpmCmd) {
    throw "npm.cmd was not found in PATH."
}

Push-Location $RepoRoot
try {
    Write-Host "[1/8] Fetching locked Stream A lineage..."
    & git fetch origin agent/ai-native-financial-core
    Assert-LastExitCode "git fetch"

    Write-Host "[2/8] Verifying exact commit exists..."
    & git cat-file -e "$SourceSha`^{commit}"
    Assert-LastExitCode "git cat-file"

    Write-Host "[3/8] Verifying commit belongs to Stream A..."
    & git merge-base --is-ancestor $SourceSha $ExpectedStreamBranch
    Assert-LastExitCode "Stream A lineage check"

    if (Test-Path $WorktreeRoot) {
        Write-Host "Removing stale temporary worktree directory $WorktreeRoot"
        & git worktree remove --force $WorktreeRoot 2>$null
        Remove-Item -Recurse -Force $WorktreeRoot -ErrorAction SilentlyContinue
    }

    Write-Host "[4/8] Creating detached worktree at exact SHA $SourceSha..."
    & git worktree add --detach $WorktreeRoot $SourceSha
    Assert-LastExitCode "git worktree add"

    $ActualSha = (& git -C $WorktreeRoot rev-parse HEAD).Trim()
    Assert-LastExitCode "git rev-parse"
    if ($ActualSha -ne $SourceSha) {
        throw "Worktree verification failed. Expected $SourceSha but got $ActualSha"
    }

    if (Test-Path $LocalStagingEnv) {
        Write-Host "[5/8] Copying local staging Functions environment into isolated worktree..."
        Copy-Item $LocalStagingEnv (Join-Path $WorktreeRoot "functions\.env.clickandsaveai-staging") -Force
    }
    else {
        Write-Warning "No local functions\.env.clickandsaveai-staging found. Deployment can continue only if the deployed Functions do not require local parameter values from that file."
    }

    Write-Host "[6/8] Installing backend dependencies and running tests..."
    Push-Location (Join-Path $WorktreeRoot "functions")
    try {
        & $NpmCmd install --ignore-scripts
        Assert-LastExitCode "npm install"
        & $NpmCmd test
        Assert-LastExitCode "backend tests"
    }
    finally {
        Pop-Location
    }

    Write-Host "[7/8] Verifying Firebase target is staging only..."
    if ($ProjectId -ne "clickandsaveai-staging") {
        throw "Deployment target guard failed."
    }

    Write-Host "[8/8] Deploying exact SHA $SourceSha to $ProjectId..."
    Push-Location $WorktreeRoot
    try {
        & $FirebaseCmd deploy `
            --project $ProjectId `
            --only "firestore:rules,firestore:indexes,functions" `
            --non-interactive
        Assert-LastExitCode "Firebase staging deploy"
    }
    finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "PASS: deployed locked Stream A SHA $SourceSha to $ProjectId" -ForegroundColor Green
}
finally {
    Pop-Location
    if (Test-Path $WorktreeRoot) {
        Push-Location $RepoRoot
        try {
            & git worktree remove --force $WorktreeRoot 2>$null
        }
        finally {
            Pop-Location
        }
        Remove-Item -Recurse -Force $WorktreeRoot -ErrorAction SilentlyContinue
    }
}
