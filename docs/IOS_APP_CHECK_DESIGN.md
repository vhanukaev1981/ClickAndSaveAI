# iOS App Check Architecture & Gmail OAuth Compatibility Design

**Repository**: `vhanukaev1981/ClickAndSaveAI`  
**Target Workstream**: iOS Security, Attestation, and Identity Integration  
**Audience**: Claude Code (iOS Developer), Security Reviewers  
**Date**: September 2026  

---

## 1. Executive Summary

Click & Save AI enforces strict zero-trust security boundaries across all client-to-backend operations:
1. **App Check**: 100% of the 24 callable Cloud Functions enforce App Check (`enforceAppCheck: true`). Unattested requests are rejected at the Google Cloud infrastructure layer before executing any backend code.
2. **Gmail Readonly OAuth**: The Gmail integration requires user authorization strictly under `https://www.googleapis.com/auth/gmail.readonly`. The backend exchanges an offline `serverAuthCode` against Google's token endpoint using the Web OAuth Client ID.

This design document provides the exact Swift architectural patterns for App Check and Gmail OAuth to ensure the iOS app integrates seamlessly and securely with the existing backend.

---

## 2. Firebase App Check Architecture on iOS

### A. Provider Strategy Matrix

| Environment | Target Form Factor | App Check Provider Factory | Attestation Mechanism |
|---|---|---|---|
| **Local Development** | Xcode iOS Simulator | `AppCheckDebugProviderFactory` | Firebase Console registered Debug Token |
| **Local Development** | Physical Test Device | `AppCheckDebugProviderFactory` | Firebase Console registered Debug Token |
| **CI / Automated Tests** | GitHub Actions macOS runner | `AppCheckDebugProviderFactory` | Ephemeral CI Debug Token via Environment Variable |
| **Staging TestFlight** | Physical Device (Internal Testers) | `AppAttestProviderFactory` | Apple App Attest API (iOS 17+ default; DeviceCheck as contingency) |
| **Production Release** | App Store / External Users | `AppAttestProviderFactory` | Hardware Secure Enclave via App Attest (iOS 17+ default) |

### B. Fail-Safe Swift Implementation Pattern

The application entry point must configure App Check BEFORE `FirebaseApp.configure()` is invoked. For an **iOS 17+ deployment target**, App Attest is the production default. DeviceCheck is not required as an OS-version fallback and is retained only as an optional documented contingency. A compile-time guard ensures debug code is physically stripped from release builds.

```swift
import SwiftUI
import FirebaseCore
import FirebaseAppCheck

class ClickAndSaveAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        #if DEBUG
        // In Debug builds, use the Debug provider.
        // On first run, a UUID debug token will be logged to the Xcode console.
        // Register this token in Firebase Console -> App Check -> Apps -> Manage debug tokens.
        return AppCheckDebugProvider(app: app)
        #else
        // For iOS 17+ target, App Attest is the default production provider.
        return AppAttestProvider(app: app)
        // Note: DeviceCheckProvider is retained only as an optional contingency if needed.
        #endif
    }
}

@main
struct ClickAndSaveAIApp: App {
    init() {
        // App Check factory MUST be configured before FirebaseApp.configure()
        let providerFactory = ClickAndSaveAppCheckProviderFactory()
        AppCheck.setAppCheckProviderFactory(providerFactory)
        
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### C. Release Build Security Guard (Unit Test)

To guarantee that debug tokens or debug provider factories can never leak into release binaries, an automated source guard test must be implemented in the iOS test suite:

```swift
import XCTest

class AppCheckSecurityGuardTest: XCTestCase {
    func testReleaseBuildsDoNotUseDebugProvider() {
        #if !DEBUG
        let factory = ClickAndSaveAppCheckProviderFactory()
        let app = FirebaseApp.app()!
        let provider = factory.createProvider(with: app)
        
        XCTAssertFalse(
            provider is AppCheckDebugProvider,
            "CRITICAL SECURITY FAILURE: AppCheckDebugProvider must never be instantiated in Release builds"
        )
        #endif
    }
}
```

---

## 3. Gmail Readonly OAuth Compatibility Analysis

### A. Architectural Compatibility Verdict
**Verdict: `COMPATIBLE_WITH_CLIENT_CONFIGURATION`**  
Zero backend code modifications are required. The existing backend callable `connectGmail` works out-of-the-box with standard Google Sign-In SDK on iOS when configured with the Web Client ID.

### B. Deep-Dive: Why It Works
1. **The Backend Contract (`functions/src/gmailConnectFunctions.js`)**:
   - Expects request payload: `{ serverAuthCode, consentAccepted: true, consentVersion: "gmail-readonly-v1" }`.
   - The backend uses `google.auth.OAuth2` with:
     - `clientId`: `googleOAuthClientId.value()` (Google Web Client ID)
     - `clientSecret`: `googleOAuthClientSecret.value()`
     - `redirectUri`: `""` (empty string for offline native serverAuthCode flow).
2. **The iOS Client Contract (`GoogleSignIn` SDK)**:
   - On iOS, `GIDSignIn.sharedInstance.configuration` accepts two IDs:
     - `clientID`: The iOS OAuth Client ID (`...apps.googleusercontent.com`) associated with the iOS bundle ID.
     - `serverClientID`: The **Web OAuth Client ID** (`716864421960-...apps.googleusercontent.com`).
   - When requesting additional scopes (`https://www.googleapis.com/auth/gmail.readonly`) with `serverClientID` set, Google's authorization server issues a `serverAuthCode` specifically cryptographically bound to the Web Client ID.
   - The backend then exchanges this `serverAuthCode` using its Web Client credentials with `redirect_uri: ""` and obtains the encrypted refresh token.

### C. Swift Client Implementation Pattern

```swift
import GoogleSignIn
import FirebaseFunctions

class GmailConnectionService {
    private let webClientId = "716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com"
    private let gmailReadonlyScope = "https://www.googleapis.com/auth/gmail.readonly"
    private lazy var functions = Functions.functions(region: "europe-west1")

    @MainActor
    func connectGmail(presentingViewController: UIViewController) async throws -> (connected: Bool, email: String) {
        // 1. Configure GIDSignIn with both iOS client ID (from Info.plist) and backend Web client ID
        guard let iosClientId = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String else {
            throw GmailAuthError.missingConfiguration("GIDClientID missing from Info.plist")
        }
        
        let config = GIDConfiguration(clientID: iosClientId, serverClientID: webClientId)
        GIDSignIn.sharedInstance.configuration = config

        // 2. Prompt user for Gmail readonly access
        let result = try await GIDSignIn.sharedInstance.signIn(
            withPresenting: presentingViewController,
            hint: nil,
            additionalScopes: [gmailReadonlyScope]
        )

        // 3. Extract offline serverAuthCode
        guard let serverAuthCode = result.serverAuthCode else {
            throw GmailAuthError.serverAuthCodeMissing("Google Sign-In did not return an offline serverAuthCode")
        }

        // 4. Send serverAuthCode to canonical backend callable
        let parameters: [String: Any] = [
            "serverAuthCode": serverAuthCode,
            "consentAccepted": true,
            "consentVersion": "gmail-readonly-v1"
        ]

        let callableResult = try await functions.httpsCallable("connectGmail").call(parameters)
        guard let data = callableResult.data as? [String: Any],
              let connected = data["connected"] as? Bool,
              let email = data["email"] as? String else {
            throw GmailAuthError.invalidBackendResponse
        }

        return (connected: connected, email: email)
    }
}

enum GmailAuthError: LocalizedError {
    case missingConfiguration(String)
    case serverAuthCodeMissing(String)
    case invalidBackendResponse
}
```

---

## 4. Preflight & Verification Checklist

1. [ ] Staging iOS App registered in Firebase Console under `clickandsaveai-staging`.
2. [ ] Debug tokens registered in Firebase Console -> App Check for all development simulators.
3. [ ] Apple App Attest / DeviceCheck enabled in Firebase Console for production.
4. [ ] Google Cloud Console has an iOS OAuth Client ID with bundle ID `com.aistudio.clickandsaveai.app`.
5. [ ] URL Scheme `com.googleusercontent.apps.{IOS_CLIENT_ID}` configured in `Info.plist`.
6. [ ] Unit test verifies `#if !DEBUG` enforces App Attest / DeviceCheck.
7. [ ] Backend tests confirm `connectGmail` successfully exchanges `serverAuthCode` generated with `serverClientID = webClientId`.
