# Production Enablement — Block 2/4 — Real User #1 Identity & Authentication

Status: candidate evidence record only. This document does not authorize production deployment, public OAuth launch, Google Play publication, provider submission, or merge to `main`.

## 1. Approved lineage

- P0 Block 6: `6a3a3ac2dcbeef16260cb8d7456568a41c80fa01`
- Approved Production Enablement Block 1: `4f674d27dfec148e10108274de23013ae73613df`
- Block 2 continuation branch: `agent/production-enablement-block2-identity-auth`
- Block 2 starts directly from the exact approved Block 1 commit.
- `main` is not the Block 2 base and must not be modified by this work.
- PRs #56–#60 remain outside this continuation and must remain unmerged unless separately authorized.

## 2. Canonical production authority

The canonical production system remains:

Android application (`com.aistudio.clickandsaveai.app`)
→ Firebase Authentication
→ Firebase App Check
→ callable Firebase backend
→ canonical Firestore state
→ Google OAuth/Gmail READ-ONLY authorization owned by Click & Save AI production identity.

The Lovable project is not a second production authority. Its current Supabase/Lovable state is non-canonical and must not be presented as a Click & Save AI production account or production financial state.

## 3. Production control-plane classification

| Resource | Classification | Evidence / boundary |
|---|---|---|
| Separate production Firebase/GCP project | INACCESSIBLE / OWNER ACTION REQUIRED | No GCP/Firebase control-plane connector is available; repository does not prove a production project exists. Staging must not be reused. |
| Production Firebase Android app | INACCESSIBLE / OWNER ACTION REQUIRED | Must be registered for exactly `com.aistudio.clickandsaveai.app` in the separate production project. |
| Production Android OAuth client | OWNER ACTION REQUIRED | Release guard now requires an Android OAuth client in production `google-services.json` bound to the canonical package and exact Play app-signing SHA-1. |
| Production Web/server OAuth client | OWNER ACTION REQUIRED | Release guard now requires the configured production Web client ID to exist in production `google-services.json`; server/offline Gmail code exchange uses this identity. |
| Production `google-services.json` | OWNER ACTION REQUIRED | Must be supplied only through protected production secret material and must pass project/package/OAuth isolation checks. |
| GitHub production environment | MISSING / OWNER ACTION REQUIRED | Repository environment inspection exposed `staging` only; no `production` environment was present during Block 2 discovery. |
| Production WIF provider | INACCESSIBLE / OWNER ACTION REQUIRED | Not provable from repository state; production workflow expects `GCP_WORKLOAD_IDENTITY_PROVIDER`. |
| Production deploy service account | INACCESSIBLE / OWNER ACTION REQUIRED | Not provable from repository state; production workflow expects `GCP_DEPLOY_SERVICE_ACCOUNT`. |
| Production runtime service account | INACCESSIBLE / OWNER ACTION REQUIRED | Must be separately provisioned/least-privileged; repository cannot prove external IAM state. |
| Production App Check registration | INACCESSIBLE / OWNER ACTION REQUIRED | Release source uses Play Integrity; external Firebase registration/enforcement is not proved by source. |
| Play Integrity linkage | INACCESSIBLE / OWNER ACTION REQUIRED | External Play/Firebase linkage cannot be inferred from source integration. |
| Production Secret Manager entries | INACCESSIBLE / OWNER ACTION REQUIRED | Values must never be committed or printed. Required runtime OAuth/token-encryption secrets must be provisioned externally. |
| Upload signing identity | INACCESSIBLE / OWNER ACTION REQUIRED | Workflow accepts only protected keystore material; actual owner-controlled key is not proved by repository state. |
| Play App Signing identity/fingerprints | INACCESSIBLE / OWNER ACTION REQUIRED | SHA-1 is required for Android OAuth binding; SHA-256 is required for release/App Check identity evidence. Both must come from the real Play signing identity. |

No staging resource is reclassified as production.

## 4. Production OAuth architecture

### A. Android OAuth client

The production Android OAuth client must be bound to:

- package: `com.aistudio.clickandsaveai.app`
- certificate: the actual Google Play app-signing SHA-1 for the installed production application.

`PRODUCTION_APP_SIGNING_CERT_SHA1` is now a required protected production environment variable. The production materialization guard fails closed unless the production Firebase configuration contains a type-1 Android OAuth client matching both the canonical package and that exact SHA-1 fingerprint.

The upload certificate is not a substitute for the Play app-signing certificate used by the Play-installed application.

### B. Web/server OAuth client

`PRODUCTION_GOOGLE_WEB_CLIENT_ID` remains the Web/server client used by the installed application/server-side authorization architecture where offline Gmail authorization requires it. The materialization guard now also proves that the configured client ID is present as a type-3 Web OAuth client in the production Firebase configuration.

Staging OAuth credentials and Lovable-hosted OAuth credentials are not valid production substitutes.

## 5. Gmail authorization truth

Allowed Gmail scope remains exactly:

`https://www.googleapis.com/auth/gmail.readonly`

The canonical implementation keeps these transitions separate:

`SIGNED IN` → Firebase-authenticated Click & Save AI account

`GMAIL CONNECTED` → explicit Gmail consent accepted, real Google authorization completed, server authorization code validated, readonly scope verified, Gmail identity matched to the authenticated account, encrypted refresh credential stored

`GMAIL SYNCHRONIZED` → a real authoritative Gmail scan completed or reached a truthful recoverable partial/failure state

The canonical Android flow requests Gmail authorization only after a separate privacy explanation and explicit user acceptance. Google sign-in alone does not mark Gmail connected.

## 6. Real User #1 path

| Step | Canonical implementation status | Production runtime status |
|---|---|---|
| Install | Canonical application ID and release build path exist | BLOCKED pending real signing/Play identity and production release authorization |
| First open | Implemented | READY in source |
| Authenticate | Firebase Google sign-in implemented separately from Gmail | BLOCKED pending production Firebase/OAuth identity |
| Privacy / Gmail explanation | Explicit readonly explanation and consent UI implemented | READY in source |
| Explicit Gmail authorization | Exact readonly scope + offline server code path implemented | BLOCKED pending production OAuth configuration/verification |
| Real Google consent | Canonical Google authorization path exists | BLOCKED pending production OAuth identity and external approval |
| Server receives authorization | Callable backend validates auth, App Check, consent, scope, identity and encrypted refresh token | BLOCKED pending production runtime/secrets/deployment |
| Real Gmail connected state | Server-authoritative state implemented | BLOCKED pending production runtime |
| Initial real scan | Server-authoritative real Gmail scan implemented | BLOCKED pending production runtime |
| Authoritative processing | Firebase backend/Firestore processing implemented | BLOCKED pending production runtime |
| Home | Reads authoritative server state and preserves unknown values | BLOCKED until real production ingestion exists |

No UI advancement is accepted as proof that an external step completed.

## 7. Lovable integration map

The current Lovable project has Supabase enabled and contains no Firebase integration. Its public database contains `app_user_connections`, `invoices`, `profiles`, and `user_roles`. That system is operational for the Lovable experience but is **not authoritative production state for Click & Save AI**.

| Lovable screen/action | Current data source / authority | Canonical required data source | Status |
|---|---|---|---|
| `/auth` Google sign-in | Lovable OAuth / Supabase Auth | Firebase Auth using Click & Save AI production Google identity | NOT PRODUCTION REAL |
| `/auth` email/password fallback | Supabase Auth | No approved canonical email/password method exists | NON-CANONICAL; must be disabled/removed from production path |
| authenticated route gate | Supabase `auth.getUser()` | Firebase authenticated account | NOT PRODUCTION REAL |
| `/connect` Gmail connect | Lovable Connector Gateway `google_mail` + Supabase connection state | Canonical Firebase Gmail authorization backend | NOT PRODUCTION REAL |
| Gmail scan | Lovable connector + Lovable AI extraction + Supabase `invoices` | Canonical Firebase Gmail scan/processing | NOT PRODUCTION REAL |
| Home | Supabase profile/invoice state | Canonical Firebase financial home | NOT PRODUCTION REAL |
| Bills | Supabase invoices | Canonical authoritative Gmail/financial state | NOT PRODUCTION REAL |
| Savings | Supabase-derived bills; correctly shows no verified offer when none exists | Canonical verified offer/opportunity state | NOT PRODUCTION REAL |
| Activity | Supabase invoice rows | Canonical server-authoritative activity ledger | NOT PRODUCTION REAL |
| Sign out | Supabase sign-out | Canonical Firebase sign-out/push revocation | INTEGRATION GAP |
| Gmail disconnect | Lovable connector cleanup + Supabase connection-key deletion | Canonical fail-closed Gmail disconnect | INTEGRATION GAP; current provider cleanup may be unconfirmed |
| Delete imported data | Not implemented | Canonical `deleteImportedFinancialData` | INTEGRATION GAP |
| Delete account | Not implemented; UI directs user to contact support | Canonical `deleteAccount` | INTEGRATION GAP |

No new Supabase architecture is introduced. No Firebase data is migrated to Supabase. Cross-project integration must use the existing canonical Firebase authority rather than creating parallel deletion/auth/financial backends.

## 8. Authentication fallback audit

The Lovable email/password registration/login path is a real Supabase authentication method, but it is **not an approved canonical Click & Save AI production authentication method**. It must not be presented as a production fallback when Lovable-hosted Google OAuth fails. A successful Supabase session is not proof of a production Click & Save AI Firebase account.

Required cross-project action: disable/remove that fallback from the production user path or replace the Lovable authentication boundary with the canonical Firebase identity architecture. Do not whitelist around the observed `lovable.dev` Google OAuth `403 access_denied` failure.

## 9. Mock/demo/local state audit

- No mock/demo/sample financial rows were found in the Lovable production-facing Home/Bills/Savings/Activity paths inspected during Block 2.
- No financial application state was found using `localStorage`, `sessionStorage`, or IndexedDB as an authoritative source.
- Lovable uses `localStorage` only for Supabase Auth session persistence; that session is non-canonical production identity and must not be treated as Firebase authority.
- Canonical Android bills/savings/activity paths use server-authoritative Firebase results and preserve unknown values rather than inventing zero.
- Source contains debug-only simulation facilities outside the production financial truth path; they are not evidence of a production event.

## 10. Account/data lifecycle

The canonical Firebase backend already implements and tests distinct operations:

1. sign out
2. Gmail disconnect
3. delete imported financial data
4. delete account

Canonical account deletion fails closed if external Gmail provider cleanup cannot be confirmed. Lovable currently does not expose the canonical delete-imported-data or delete-account operations and therefore has an integration gap; no parallel Lovable deletion backend should be created.

## 11. Real bills, savings, activity and offers

Canonical real bills and activity can be produced only after real production Gmail authorization and server ingestion are operational.

The canonical provider-offer catalog enforces source URL, verification/freshness, eligibility, service compatibility and complete consumer pricing evidence. It does not by itself prove a live external comparison-provider API exists. No live external provider transport was verified in this Block. Until a real authorized provider source/transport exists, the product must continue to show no verified live comparison rather than sample offers or fabricated delivery/savings.

## 12. Protected production material

Production values must be provisioned outside the repository and must never be printed or committed.

GitHub production environment variables required by the production release path include:

- `PRODUCTION_FIREBASE_PROJECT_ID`
- `PRODUCTION_GOOGLE_WEB_CLIENT_ID`
- `PRODUCTION_APP_SIGNING_CERT_SHA1`
- `PRODUCTION_APP_SIGNING_CERT_SHA256`
- `PRODUCTION_UPLOAD_KEY_ALIAS`
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_DEPLOY_SERVICE_ACCOUNT`

Protected GitHub secrets include:

- `PRODUCTION_GOOGLE_SERVICES_JSON_B64`
- `PRODUCTION_UPLOAD_KEYSTORE_B64`
- `PRODUCTION_UPLOAD_STORE_PASSWORD`
- `PRODUCTION_UPLOAD_KEY_PASSWORD`

Canonical production runtime protected configuration includes:

- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `OAUTH_TOKEN_ENCRYPTION_KEY`

No value is recorded in this document.

## 13. Owner actions required before Real User #1 can run in production

1. Create or identify a separate production Firebase/GCP project that is not `clickandsaveai-staging`; register the Firebase Android application with package `com.aistudio.clickandsaveai.app` and enable only the required production services.
2. In Google Play Console, confirm/enroll the real application in Play App Signing and obtain the authoritative Play app-signing SHA-1 and SHA-256 fingerprints. Do not generate a replacement identity merely for this Block.
3. Create the production Android OAuth client for `com.aistudio.clickandsaveai.app` bound to the Play app-signing SHA-1.
4. Create the production Web/server OAuth client for the server/offline authorization architecture and configure the OAuth consent identity as Click & Save AI, not `lovable.dev`.
5. Download the production Firebase `google-services.json`; verify project/package/Android OAuth/Web OAuth bindings and store it only as `PRODUCTION_GOOGLE_SERVICES_JSON_B64` in the protected GitHub production environment.
6. Confirm an owner-controlled upload signing key exists; if one does not exist, create it under an owner-controlled key-management procedure, register the required upload identity with Play, and store only the keystore/password material in protected production secrets. Keep the upload key distinct from Play App Signing.
7. Create a protected GitHub `production` environment and populate the required non-secret variables and protected secrets listed above. Do not copy staging values into it.
8. Provision separate least-privileged production deploy/runtime service accounts and a production Workload Identity Federation provider constrained to this repository, intended workflow/environment/ref; configure `GCP_WORKLOAD_IDENTITY_PROVIDER` and `GCP_DEPLOY_SERVICE_ACCOUNT`.
9. Provision the canonical runtime OAuth/token-encryption protected configuration (`GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, `OAUTH_TOKEN_ENCRYPTION_KEY`) in the production Firebase/GCP secret/configuration plane without committing or logging values.
10. Register the production Android app with Firebase App Check using Play Integrity, register the real Play app-signing SHA-256, complete Play/Firebase linkage, validate real tokens, then enable enforcement for protected production services under a separately authorized production change.
11. In the external Lovable project, remove/disable the Supabase email/password fallback from any production path and prevent Lovable/Supabase authentication from masquerading as a production Click & Save AI account.
12. In the external Lovable project, replace/gate the non-canonical Lovable Gmail, invoice, Home, Bills, Savings, Activity and account/privacy actions so that production uses the canonical Firebase authority. Preserve sign-out, Gmail disconnect, delete imported data and delete account as separate operations; make Gmail cleanup fail closed; do not migrate canonical Firebase data into Supabase.
13. Establish and approve a real verified provider-offer source and an authorized external provider transport before displaying live provider offers or claiming provider delivery/deal completion/saving realization.

## 14. External approvals required

1. Complete Google OAuth verification for the Click & Save AI production consent screen and restricted `gmail.readonly` scope, including any Google-required restricted-scope security assessment/evidence before public OAuth launch.
2. Complete any required Google Play Console / Play App Signing / Play Integrity approval or linkage steps for the production application identity before a production release is authorized.

## 15. Prohibited actions preserved

This Block does not deploy Firebase production, mutate a production database, publish to Google Play, launch public OAuth, submit a provider request, merge PRs, or modify `main`.
