# iOS External Configuration Requirements

**Repository**: `vhanukaev1981/ClickAndSaveAI`  
**Target Workstream**: iOS Foundation Preparation & External Asset Inventory  
**Audience**: Engineering Lead, DevOps, Claude Code (iOS Developer)  
**Date**: September 2026  

---

## 1. Executive Summary

To run Click & Save AI on iOS against the canonical Firebase backend, external configurations must be established in:
1. **Apple Developer Portal** (Identifiers, Capabilities, Keys, Profiles)
2. **Firebase Console** (Staging & Production iOS App registrations, APNs setup, App Check setup)
3. **Google Cloud Console** (OAuth 2.0 Client IDs for iOS)
4. **GitHub Repository Secrets** (CI/CD build and test variables)

Strict environment isolation must be maintained: **Staging** and **Production** must never share bundle IDs, certificates, APNs keys, OAuth client IDs, or Firebase configuration files.

---

## 2. Configuration Inventory Matrix

| Asset / Configuration | Owner | Configuration Location | Staging (`clickandsaveai-staging`) | Production (`click-save-ai-production`) | Security Policy |
|---|---|---|---|---|---|
| **Apple Developer Team ID** | Account Owner | Apple Developer Portal | 10-character Team ID (e.g. `ABC123XYZ0`) | Identical Apple Team ID | Public within code signing, but treated with confidentiality. |
| **App Bundle Identifier** | iOS Lead | Apple Developer Portal | `PROPOSED: com.aistudio.clickandsaveai.staging` (pending approval) | `PROPOSED: com.aistudio.clickandsaveai.app` (pending approval) | Defined in project build configurations. NOT yet externally provisioned. |
| **Associated Domains** | iOS Lead | Apple Developer -> Identifiers | `applinks:clickandsaveai-staging.web.app` | `applinks:click-save-ai-production.web.app` | Universal links domain configuration. |
| **APNs Capability & Entitlements** | iOS Lead | Xcode / App Identifiers | Enabled; Development & Sandbox APNs | Enabled; Production APNs | Tracked in `.entitlements` file. |
| **Firebase iOS App Entry** | DevOps / Firebase Owner | Firebase Console -> Project Settings | iOS App registered with Staging Bundle ID | iOS App registered with Production Bundle ID | Created via Firebase Console. |
| **`GoogleService-Info.plist`** | DevOps | Firebase Console -> Download | Downloaded from `clickandsaveai-staging` | Downloaded from `click-save-ai-production` | **NEVER COMMIT TO GIT REPOSITORY**. Injected via CI secret or local untracked file. |
| **Google Cloud iOS OAuth Client ID** | DevOps | Google Cloud Console -> APIs & Services -> Credentials | Created for Staging Bundle ID & Team ID | Created for Production Bundle ID & Team ID | Client ID is embedded in client; Web Client ID is used for serverAuthCode exchange. |
| **Reversed Client ID URL Scheme** | iOS Lead | `Info.plist` (Xcode) | `com.googleusercontent.apps.{STAGING_IOS_CLIENT_ID}` | `com.googleusercontent.apps.{PROD_IOS_CLIENT_ID}` | Configured per build target / xcconfig. |
| **Google Web OAuth Client ID** | Backend Lead | GCP Console -> Credentials | `716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com` | Production Web OAuth Client ID (stored in Cloud Secret Manager) | Public client ID used for `serverClientID` parameter in Google Sign-In. |
| **APNs Authentication Key (.p8)** | Account Owner | Apple Developer -> Keys -> APNs Key | Single `.p8` key with Key ID and Team ID | Same or dedicated `.p8` key with Key ID and Team ID | **CRITICAL PRIVATE KEY**. Uploaded to Firebase Console. Never stored in git. |
| **Firebase Cloud Messaging APNs Upload** | DevOps | Firebase Console -> Cloud Messaging | Upload `.p8` key, Key ID, Team ID | Upload `.p8` key, Key ID, Team ID | Configured once per project. |
| **App Check Provider (Production)** | DevOps | Firebase Console -> App Check -> Apps | App Attest (iOS 17+ default) / Debug in Simulator | App Attest configured with Apple Team ID & Private Key | Enforced at 100% on all backend callables. DeviceCheck retained only as documented contingency. |
| **App Check Debug Tokens** | iOS Dev | Firebase Console -> App Check -> Apps -> Debug Tokens | Simulator/Device UUID registered for developer | NEVER register debug tokens in Production | Local to developer; printed in Xcode console. |
| **GitHub Actions CI Secrets** | Repo Admin | GitHub -> Settings -> Secrets & Variables -> Actions | Staging base64 plist and signing secrets | Production signing credentials (dispatch only) | Strictly scoped; protected by branch environments. |

---

## 3. Environment-Specific Details

### A. Staging (`clickandsaveai-staging`)
- **Primary Use**: CI automated tests, simulator testing, PR validation, TestFlight internal testing.
- **Firebase Project ID**: `clickandsaveai-staging`
- **Bundle ID**: `PROPOSED: com.aistudio.clickandsaveai.staging` (pending owner/architecture approval; NOT externally provisioned yet)
- **Google Web Client ID**: `716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com`
- **App Check Mode**:
  - CI / Simulator: `AppCheckDebugProviderFactory` with secret CI debug token.
  - TestFlight / Device: `AppAttestProviderFactory` (production default for iOS 17+; DeviceCheck retained only as documented contingency).
- **CI Secret Names**:
  - `STAGING_GOOGLE_SERVICE_INFO_PLIST_B64`: Base64-encoded `GoogleService-Info.plist` for staging.
  - `STAGING_IOS_DEBUG_TOKEN`: App Check debug token for staging simulator runs.

### B. Production (`click-save-ai-production`)
- **Primary Use**: App Store distribution, TestFlight external beta, real user traffic.
- **Firebase Project ID**: `click-save-ai-production`
- **Bundle ID**: `PROPOSED: com.aistudio.clickandsaveai.app` (pending owner/architecture approval; NOT externally provisioned yet)
- **Google Web Client ID**: Loaded dynamically or configured from production GCP credentials.
- **App Check Mode**:
  - Strictly `AppAttestProviderFactory` (default for iOS 17+ target; DeviceCheck retained only as an optional documented contingency if a real need is identified).
  - Zero debug tokens permitted in production environment.
- **CI Secret Names**:
  - `PROD_GOOGLE_SERVICE_INFO_PLIST_B64`: Base64-encoded `GoogleService-Info.plist` for production.
  - `APPLE_CERTIFICATE_P12_B64`: Apple Distribution Certificate for release signing.
  - `APPLE_CERTIFICATE_PASSWORD`: Password for distribution certificate.
  - `APPLE_PROVISIONING_PROFILE_B64`: App Store Distribution Provisioning Profile.

---

## 4. Security & Guard Rules

1. **Tracked Credentials Guard**:
   - The repository has an active preflight guard in CI (`git grep -nE -- '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'`).
   - Any commit attempting to add `.p8`, `.p12`, `.mobileprovision`, or `GoogleService-Info.plist` directly to git will immediately fail CI and close the gate.
2. **Staging vs. Production String Isolation**:
   - Staging client IDs and URLs must never appear in `Release` build configurations or production asset catalogs.
3. **App Check Release Compilation Guard**:
   - Debug App Check tokens must be wrapped in `#if DEBUG` preprocessor macros so they cannot compile into release binaries.
