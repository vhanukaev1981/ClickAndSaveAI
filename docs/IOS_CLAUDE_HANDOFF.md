# Click & Save AI — Native iOS Developer Handoff Package

**Document ID**: `IOS_CLAUDE_HANDOFF.md`  
**Author**: Antigravity (iOS Integration & Validation Agent)  
**Target Recipient**: Claude Code (Primary Native iOS Implementation Owner)  
**Repository**: `vhanukaev1981/ClickAndSaveAI`  
**Base Lineage**: `30da5dc0ca67cba6232f05a591a4d49dd534583b` (`origin/main`) + `feat/ios-foundation-prep`  
**Date**: September 2026  

---

## 1. Role Definition & Multi-Agent Collaboration Protocol

Welcome to the Click & Save AI iOS workstream.

- **Claude Code**: You are the **Primary Native iOS Implementation Owner**. You own creating the Xcode project, authoring the SwiftUI views, ViewModels, Services, and Repositories, managing Swift Package Manager dependencies, implementing localization/RTL layouts, and writing iOS unit and UI tests.
- **Antigravity**: I am the **Independent Integration & Validation Agent**. My responsibility is to verify platform parity against Android, ensure backend contracts remain unbroken, audit App Check and credential security, review diffs, test cross-platform compatibility, and validate that no architectural drift occurs.

You do not need to build backend endpoints or calculate financial metrics on the client. The backend has already been hardened and prepared for you.

---

## 2. Core Architectural Principles (Non-Negotiable)

1. **Backend is Canonical, Clients are Edge Renderers**:
   - The Firebase backend (`functions/src/` running in `europe-west1`) is the single source of truth for all financial data, recurring bill classification, opportunity matching, and commercial lead attribution.
   - Android and iOS are sibling clients. Never create parallel backend logic, alternate databases (e.g. Supabase), or shadow user models.
2. **Zero Client-Side Financial Calculations**:
   - Never compute potential annual savings, VAT additions, inflation adjustments, or health scores in Swift code.
   - Always display the pre-calculated numbers provided in the backend's `FinancialHome` and `SavingsOpportunity` payloads.
3. **Fail-Closed Privacy & Push Lifecycles**:
   - Sign-out on iOS MUST call `unregisterPushToken` and await a successful response before executing `FirebaseAuth.signOut()`.
   - Account deletion MUST call the `deleteAccount` callable, ensuring all personal records and push subscriptions are scrubbed.
4. **Zero Tracked Secrets**:
   - Never commit `GoogleService-Info.plist`, `.p8`, `.p12`, or API keys into git.

---

## 3. Recommended Project Structure

Place all native iOS source files inside `ClickAndSaveAI/` at the root of the repository:

```
ClickAndSaveAI/
├── ClickAndSaveAI.xcodeproj
├── ClickAndSaveAI/
│   ├── App/
│   │   ├── ClickAndSaveAIApp.swift            # @main app entry, App Check setup
│   │   └── AppDelegate.swift                  # FCM and APNs delegate methods
│   ├── Core/
│   │   ├── Network/
│   │   │   └── FirebaseFunctionsClient.swift  # Typed wrappers around europe-west1 callables
│   │   ├── Security/
│   │   │   └── AppCheckProviderFactory.swift  # Debug vs AppAttest provider
│   │   └── Theme/
│   │       ├── Colors.swift                   # Semantic color system (Dark/Light mode)
│   │       └── Typography.swift               # Dynamic Type typographic scale
│   ├── Data/
│   │   ├── Models/                            # Codable models matching backend schemas
│   │   │   ├── FinancialHomeDTO.swift
│   │   │   ├── InvoiceDTO.swift
│   │   │   └── OpportunityDTO.swift
│   │   └── Repositories/                      # Repositories matching Android architecture
│   │       ├── AuthRepository.swift
│   │       ├── GmailRepository.swift
│   │       ├── FinancialHomeRepository.swift
│   │       └── OpportunityRepository.swift
│   ├── UI/
│   │   ├── Navigation/
│   │   │   └── AppCoordinator.swift           # Tab and Push deep-link navigation
│   │   ├── Screens/
│   │   │   ├── Auth/LoginView.swift
│   │   │   ├── Home/FinancialHomeView.swift   # Main dashboard screen
│   │   │   ├── Gmail/GmailConnectView.swift   # OAuth consent & connection screen
│   │   │   ├── Invoices/InvoicesListView.swift
│   │   │   ├── Opportunities/OpportunityDetailView.swift
│   │   │   ├── Push/PushTargetView.swift      # Deep-link destination
│   │   │   └── Settings/SettingsView.swift
│   │   └── Components/                        # Reusable SwiftUI components
│   │       ├── FinancialCard.swift
│   │       ├── SavingsBadge.swift
│   │       └── RTLHStack.swift
│   ├── Resources/
│   │   ├── Assets.xcassets
│   │   ├── he.lproj/Localizable.strings       # Hebrew strings (canonical)
│   │   └── en.lproj/Localizable.strings       # English fallback
│   └── Support/
│       └── Info.plist
└── ClickAndSaveAITests/
    ├── Repositories/
    ├── Security/
    │   └── AppCheckSecurityGuardTest.swift    # Verifies debug provider not in release
    └── Mocks/
```

---

## 4. Dependencies via Swift Package Manager (SPM)

Use standard Swift Package Manager (`Package.swift` or embedded Xcode SPM). Recommended dependencies:

1. **`firebase-ios-sdk`** (v11.x or v10.x):
   - `FirebaseAuth`
   - `FirebaseFirestore`
   - `FirebaseFunctions`
   - `FirebaseMessaging`
   - `FirebaseAppCheck`
2. **`GoogleSignIn-Swift`** (v8.x or latest):
   - Used for Google Sign-In and requesting Gmail readonly offline serverAuthCode.

---

## 5. Backend Integration Guide

- **Region**: Always initialize Functions with `Functions.functions(region: "europe-west1")`.
- **Callable Manifest**: Consult `docs/ios-backend-contracts.json` for exact schemas, required parameters, and error codes.
- **Error Handling Pattern**:
  ```swift
  do {
      let result = try await functions.httpsCallable("getFinancialHome").call()
  } catch let error as NSError {
      switch FunctionsErrorCode(rawValue: error.code) {
      case .unauthenticated:
          // Prompt user to re-login
      case .permissionDenied:
          // Account disabled or deleted
      case .failedPrecondition:
          // Display sanitized error message
      default:
          // Display generic localized Hebrew error
      }
  }
  ```

---

## 6. App Check Integration Pattern

All backend callables strictly reject unattested requests. App Check must be configured before `FirebaseApp.configure()`:

```swift
import SwiftUI
import FirebaseCore
import FirebaseAppCheck

class ClickAndSaveAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        #if DEBUG
        return AppCheckDebugProvider(app: app)
        #else
        // For iOS 17+ deployment target, App Attest is the default production provider.
        return AppAttestProvider(app: app)
        // Note: DeviceCheckProvider is retained only as an optional contingency if needed.
        #endif
    }
}
```

---

## 7. Google Sign-In & Gmail OAuth Flow

The backend expects a server auth code exchanged with the Web Client ID.

1. In `Info.plist`, configure `GIDClientID` with your iOS OAuth Client ID and add the reversed client ID to `CFBundleURLSchemes`.
2. Configure `GIDSignIn.sharedInstance.configuration` with:
   - `clientID`: iOS Client ID
   - `serverClientID`: `716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com` (Web Client ID)
3. Request scope `https://www.googleapis.com/auth/gmail.readonly`.
4. Call `connectGmail` with `{ serverAuthCode, consentAccepted: true, consentVersion: "gmail-readonly-v1" }`.

---

## 8. Push Notifications & Sign-Out Revocation Gate

1. **Token Registration**:
   In `messaging(_:didReceiveRegistrationToken:)`:
   ```swift
   let parameters: [String: Any] = [
       "token": fcmToken,
       "platform": "ios"
   ]
   try await functions.httpsCallable("registerPushToken").call(parameters)
   ```
2. **Push Payloads & Multicast**:
   - The backend delivers high-priority notifications with default sound via APNs.
   - When tapped, inspect `userInfo`:
     - If `sourceMessageId` exists: Open `PushTargetView` for that specific invoice.
     - If `opportunityId` and `offerId` exist: Open `PushTargetView` for that specific opportunity.
     - If missing exact target identifiers: Silently ignore entity push (mirrors Android safety contract).
3. **Sign-Out Revocation Requirement (P0)**:
   ```swift
   func signOut() async throws {
       guard let token = currentFcmToken else {
           try Auth.auth().signOut()
           return
       }
       // Hard gate: unregister token on backend BEFORE signing out locally
       _ = try await functions.httpsCallable("unregisterPushToken").call(["token": token])
       try Auth.auth().signOut()
   }
   ```

---

## 9. UI/UX Parity Guide

1. **Primary Language & RTL**:
   - The primary audience is Israel (`he-IL`). All screens must support right-to-left layout natively.
   - Use standard SF Pro and Hebrew system typography.
   - Currency symbol must always be New Israeli Shekel (`₪`), formatted with `NumberFormatter` using `locale = Locale(identifier: "he_IL")`.
2. **Visual Palette & Feel**:
   - Clean, modern, trustworthy financial design.
   - Green accents for verified savings (`#10B981` / semantic success green).
   - High-contrast text meeting WCAG AA accessibility standards.
3. **Screens to Build**:
   - `LoginView` (Google Sign-In & Email/Password)
   - `FinancialHomeView` (Current monthly total, monthly savings potential, opportunity cards)
   - `InvoicesListView` (List of parsed recurring bills with company logos / badges)
   - `OpportunityDetailView` (Offer comparison, action button "בקש הצעה")
   - `PushTargetView` (Direct navigation target from notifications)
   - `SettingsView` (Disconnect Gmail, Test push, Delete account)

---

## 10. Step-by-Step Implementation Roadmap for Claude

| Phase | Milestone Description | Primary Deliverables | Antigravity Validation Gate |
|---|---|---|---|
| **Phase 1** | Project Setup & Foundation | Xcode project structure, SPM configuration, App Check factory, theme, Info.plist | Validates build settings, ensures zero tracked secrets. |
| **Phase 2** | Authentication & App Check | `AuthRepository`, `LoginView`, Debug/Attest verification | Verifies App Check attestation and session handling. |
| **Phase 3** | Gmail Integration | Google Sign-In with Web Client ID, `connectGmail`, `getGmailConnectionStatus` | Verifies serverAuthCode exchange and readonly scope. |
| **Phase 4** | Financial Home & Bills | `FinancialHomeRepository`, `FinancialHomeView`, `InvoicesListView` | Verifies data projection fidelity, zero client calculations. |
| **Phase 5** | Opportunities & Handoff | `OpportunityRepository`, `OpportunityDetailView`, `acceptSavingsOpportunity` | Verifies consent versioning and lead creation. |
| **Phase 6** | Push Notifications | APNs delegate, FCM token registration (`platform: "ios"`), `PushTargetView` | Verifies multiplatform push delivery and sign-out gate. |
| **Phase 7** | Privacy & Polish | `deleteAccount`, `deleteImportedFinancialData`, VoiceOver & RTL audit | Full regression and contract acceptance suite. |

---

## 11. How Antigravity Will Validate Your Work

As you implement each phase, I will execute automated tests and audits against:
1. **Contract Conformance**: Verifying every callable request/response against `docs/ios-backend-contracts.json`.
2. **Security Integrity**: Confirming no debug tokens or private keys are committed, and that App Check cannot be bypassed.
3. **Functional Parity**: Checking that behavior, error states, and flows exactly mirror Android's production release.

Let's build a flawless, rock-solid iOS experience together!
