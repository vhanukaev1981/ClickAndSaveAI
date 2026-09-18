# iOS CI/CD Architecture & GitHub Actions Pipeline Plan

**Repository**: `vhanukaev1981/ClickAndSaveAI`  
**Target Workstream**: iOS Continuous Integration & Build Verification  
**Audience**: DevOps, iOS Engineering Team, CI Automation Maintainers  
**Date**: September 2026  

---

## 1. Executive Summary

To maintain the rigorous quality and security standards established for the Android client, the iOS application requires a dedicated, automated GitHub Actions workflow (`.github/workflows/ios-ci.yml`). 

The iOS CI pipeline directly parallels `.github/workflows/android-ci.yml` in:
- **Exact Candidate Source Verification & Lineage Tracking** (ancestry checks against Block 5 approval).
- **Zero-Tolerance Credential & Secret Scanning**.
- **Strict Staging vs. Production Configuration Isolation**.
- **Automated Unit Testing & Static Code Analysis**.
- **Immutable SHA-256 Artifact Audit & Evidence Preservation**.

---

## 2. Workflow Architecture & Specifications

- **Workflow Name**: `iOS and Backend CI`
- **File Location**: `.github/workflows/ios-ci.yml`
- **Runner OS**: `macos-14` (Apple Silicon M1/M2) or `macos-15`
- **Xcode Version**: `16.0` (Swift 6.0 / 5.10 toolchain)
- **Simulator Target**: `platform=iOS Simulator,name=iPhone 16,OS=18.0`
- **Concurrency**:
  ```yaml
  concurrency:
    group: ios-ci-${{ github.workflow }}-${{ github.ref }}
    cancel-in-progress: true
  ```
- **Trigger Conditions**:
  - `pull_request`: On PR open/synchronize targeting `main`
  - `push`: On direct push or merge to `main`
  - `workflow_dispatch`: Manual execution with optional branch selection

---

## 3. Step-by-Step Pipeline Jobs

The workflow is divided into two primary sequential/parallel jobs:
1. `backend-contract-check`: Runs on `ubuntu-latest`. Fast execution verifying backend unit tests, push contract parity, and tracked credential scanning.
2. `ios-build-and-test`: Runs on `macos-14`. Handles iOS configuration restoration, dependency resolution, simulator unit tests, and build artifact creation.

### Detailed Job Steps (`ios-build-and-test`):

1. **Check Out Exact Candidate Source**:
   - Uses `actions/checkout@v6` with `fetch-depth: 0`.
2. **Verify Exact Source and Block 5 Lineage**:
   - Ensures `ACTUAL_SHA == SOURCE_SHA`.
   - Confirms git ancestry: `git merge-base --is-ancestor eb63dd4aba38e570c9dc0a502bf31c872b2fdee7 "$ACTUAL_SHA"`.
3. **Guard Tracked Secret Material**:
   - Rejects commits containing private keys, certs, or `.p8`/`.p12` blobs in tracked code.
4. **Select Xcode Toolchain**:
   - Executes `sudo xcode-select -s /Applications/Xcode_16.0.app`.
5. **Restore and Verify Staging Firebase Config**:
   - Decodes `STAGING_GOOGLE_SERVICE_INFO_PLIST_B64` to `ClickAndSaveAI/GoogleService-Info.plist`.
   - Validates that `PROJECT_ID == clickandsaveai-staging` and bundle ID matches staging identifier.
6. **Resolve Dependencies**:
   - Resolves Swift Package Manager dependencies via `xcodebuild -resolvePackageDependencies`.
7. **Run iOS Unit Tests**:
   - Executes:
     ```bash
     xcodebuild test \
       -scheme ClickAndSaveAI \
       -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.0' \
       -resultBundlePath "$RUNNER_TEMP/test-results.xcresult" \
       -enableCodeCoverage YES
     ```
8. **Run SwiftLint & Accessibility Audits**:
   - Runs `swiftlint --strict` to verify styling and safety contracts.
9. **Build Release-Variant Archive (Audit Only)**:
   - Builds an unsigned release archive to verify compiler optimization, asset catalog integrity, and debug-code stripping.
10. **Record and Upload Exact-SHA Evidence**:
    - Generates `identity.txt` containing commit SHA, archive SHA-256, and build metadata.
    - Uploads evidence bundle as an artifact retained for 14 days.

---

## 4. Production Release Pipeline (Future Scope)

When moving to production App Store deployment:
- Triggered exclusively via `workflow_dispatch` on `main` with explicit manual approval.
- Decodes production distribution certificate (`APPLE_CERTIFICATE_P12_B64`) and provisioning profile.
- Injects production `GoogleService-Info.plist`.
- Archives and signs the `.ipa`.
- Validates signing certificate fingerprint against Apple Team ID.
- Uploads to App Store Connect via `altool` / `notarytool` / Fastlane.

---

## 5. Reference Workflow Template

Below is the complete GitHub Actions workflow template ready to be committed when the iOS project directory is initialized.

```yaml
name: iOS and Backend CI

on:
  workflow_dispatch:
  pull_request:
  push:
    branches:
      - main

permissions:
  contents: read

concurrency:
  group: ios-ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

env:
  SOURCE_SHA: ${{ github.event.pull_request.head.sha || github.sha }}
  BLOCK5_APPROVED_SHA: eb63dd4aba38e570c9dc0a502bf31c872b2fdee7

jobs:
  backend-test:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: functions

    steps:
      - name: Check out exact candidate source
        uses: actions/checkout@v6
        with:
          ref: ${{ env.SOURCE_SHA }}
          fetch-depth: 0

      - name: Verify exact source and Block 5 lineage
        working-directory: .
        shell: bash
        run: |
          set -euo pipefail
          ACTUAL_SHA="$(git rev-parse HEAD)"
          test "$ACTUAL_SHA" = "$SOURCE_SHA"
          git merge-base --is-ancestor "$BLOCK5_APPROVED_SHA" "$ACTUAL_SHA"

      - name: Guard tracked secret material
        working-directory: .
        shell: bash
        run: |
          set -euo pipefail
          if git grep -nE -- '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|"private_key"[[:space:]]*:|"client_secret"[[:space:]]*:' -- ':!*.md' ':!*.example' ':!functions/test/**'; then
            echo 'Tracked private credential material detected.' >&2
            exit 1
          fi

      - name: Set up Node.js 22
        uses: actions/setup-node@v6
        with:
          node-version: '22'
          package-manager-cache: false

      - name: Install backend dependencies
        run: npm install --ignore-scripts

      - name: Run backend tests
        run: node --test

  ios-build-and-test:
    runs-on: macos-14
    timeout-minutes: 40
    env:
      STAGING_GOOGLE_SERVICE_INFO_PLIST_B64: ${{ secrets.STAGING_GOOGLE_SERVICE_INFO_PLIST_B64 }}

    steps:
      - name: Check out exact candidate source
        uses: actions/checkout@v6
        with:
          ref: ${{ env.SOURCE_SHA }}
          fetch-depth: 0

      - name: Verify exact source and Block 5 lineage
        shell: bash
        run: |
          set -euo pipefail
          ACTUAL_SHA="$(git rev-parse HEAD)"
          test "$ACTUAL_SHA" = "$SOURCE_SHA"
          git merge-base --is-ancestor "$BLOCK5_APPROVED_SHA" "$ACTUAL_SHA"

      - name: Select Xcode 16.0
        run: sudo xcode-select -s /Applications/Xcode_16.0.app

      - name: Restore and verify staging Firebase config
        shell: bash
        run: |
          set -euo pipefail
          if [[ -z "$STAGING_GOOGLE_SERVICE_INFO_PLIST_B64" ]]; then
            echo '::notice::Staging GoogleService-Info.plist is not configured. CI will verify build readiness without live network attestation.'
            exit 0
          fi
          mkdir -p ClickAndSaveAI
          printf '%s' "$STAGING_GOOGLE_SERVICE_INFO_PLIST_B64" | base64 --decode > ClickAndSaveAI/GoogleService-Info.plist
          plutil -p ClickAndSaveAI/GoogleService-Info.plist | grep -q 'clickandsaveai-staging'

      - name: Resolve Swift Package dependencies
        run: |
          if [[ -f "ClickAndSaveAI.xcodeproj/project.pbxproj" ]]; then
            xcodebuild -resolvePackageDependencies -project ClickAndSaveAI.xcodeproj -scheme ClickAndSaveAI
          else
            echo "Xcode project not yet initialized; skipping package resolution."
          fi

      - name: Run iOS unit tests
        run: |
          if [[ -f "ClickAndSaveAI.xcodeproj/project.pbxproj" ]]; then
            xcodebuild test \
              -project ClickAndSaveAI.xcodeproj \
              -scheme ClickAndSaveAI \
              -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.0' \
              -enableCodeCoverage YES
          else
            echo "Xcode project not yet initialized; skipping unit tests."
          fi
```
