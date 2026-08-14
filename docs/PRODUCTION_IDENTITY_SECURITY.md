# Click & Save AI — Production Identity & Security

## Canonical checkpoint

Production Enablement starts from P0 SHA `6a3a3ac2dcbeef16260cb8d7456568a41c80fa01`. Production changes must remain descendants of that checkpoint until an explicitly approved integration decision. `main` is not a production-enablement target during Block 1.

## Environment matrix

| Area | Development | Staging | Production |
|---|---|---|---|
| Firebase / GCP project | No dedicated project proven by repository evidence; local/emulator use only unless separately configured | `clickandsaveai-staging` | Not proven. Must be a distinct project, never the staging project |
| Android application ID | `com.aistudio.clickandsaveai.app` | `com.aistudio.clickandsaveai.app` | `com.aistudio.clickandsaveai.app` unless owner approves a technically required pre-Play change |
| Android signing | Local/default debug | Stable staging debug identity when CI secrets are present | Google Play App Signing for delivered APKs + owner-controlled distinct upload key for AAB upload |
| Google OAuth | Local/test configuration only | Debug resource contains staging Web client | Production Android client + production Web/server client required |
| Gmail scope | `gmail.readonly` only | `gmail.readonly` only | `gmail.readonly` only; restricted-scope verification required |
| App Check | Debug provider | Debug provider for debug builds | Play Integrity provider in release; Firebase/Play registration and enforcement still external |
| Firestore | Backend-owned | Backend-owned | Backend-owned; direct client access denied by rules |
| GitHub environment | None proven | `staging` exists | `production` does not exist as of Block 1 audit |
| Deployment workflow | None | `deploy-staging.yml` | `production-release.yml`, manual dispatch only; must remain blocked until protected environment inputs exist |

## Android production identity

The canonical application ID remains `com.aistudio.clickandsaveai.app`. Block 1 does not rename it. Current version values are `versionCode=1`, `versionName=1.0`, `minSdk=24`, and `targetSdk=36`.

Release configuration is intentionally separate from debug/staging configuration. The production Web OAuth client ID is injected only into the release variant through `PRODUCTION_GOOGLE_WEB_CLIENT_ID`. A normal CI release audit remains unsigned; a production candidate requires `PRODUCTION_RELEASE_CANDIDATE=true` and all signing inputs.

## Google Play signing model

Use Google Play App Signing.

- **App signing key:** controlled by Google Play and used to sign APKs delivered to users. Register its SHA-1/SHA-256 fingerprints with Firebase, Google OAuth/API providers, App Check, and any other certificate-bound integration.
- **Upload key:** owner-controlled and distinct from the app signing key. CI uses this key only to sign the AAB/APK production candidate sent to Play. The keystore and passwords must exist only in the protected GitHub `production` environment or an equivalently protected secret store.
- Never commit either private key. Never put the keystore in a GitHub artifact. The production workflow uploads only signed candidate binaries plus a non-secret identity manifest.

Required protected inputs:

### GitHub `production` environment variables

- `PRODUCTION_FIREBASE_PROJECT_ID`
- `PRODUCTION_GOOGLE_WEB_CLIENT_ID`
- `PRODUCTION_APP_SIGNING_CERT_SHA256`
- `PRODUCTION_UPLOAD_KEY_ALIAS`
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_DEPLOY_SERVICE_ACCOUNT`

### GitHub `production` environment secrets

- `PRODUCTION_GOOGLE_SERVICES_JSON_B64`
- `PRODUCTION_UPLOAD_KEYSTORE_B64`
- `PRODUCTION_UPLOAD_STORE_PASSWORD`
- `PRODUCTION_UPLOAD_KEY_PASSWORD`

The Google OAuth client secret and Gmail token encryption key are runtime secrets and must stay in Google Secret Manager/Firebase Functions secrets, not GitHub unless a separately approved bootstrap process requires transient delivery.

## Production Firebase and google-services

A production Firebase project has not been proven by repository evidence. It must be logically and administratively separate from `clickandsaveai-staging`.

For the production project:

1. Register Android package `com.aistudio.clickandsaveai.app`.
2. Add the Google Play **app signing certificate** SHA-1 and SHA-256 fingerprints after the Play signing identity is established.
3. Enable Authentication providers actually used by the app.
4. Enable Firestore, Functions, FCM and App Check/Play Integrity only as required by the existing architecture.
5. Download the production Android `google-services.json`; store its base64 form as the protected `PRODUCTION_GOOGLE_SERVICES_JSON_B64` environment secret. Do not commit it.
6. The production workflow materializes it only at `app/src/release/google-services.json`, after deleting any root/staging config, validates project/package isolation, builds, and deletes the materialized file.

## Google OAuth architecture

Production requires two OAuth client types in the production Google Cloud project:

1. **Android OAuth client** — package `com.aistudio.clickandsaveai.app`, bound to the Google Play app-signing SHA-1 certificate fingerprint used on installed production APKs.
2. **Web application / server OAuth client** — used as the Android server client ID for Google sign-in/offline Gmail authorization and as `GOOGLE_OAUTH_CLIENT_ID` by Cloud Functions when exchanging the server authorization code. Its client secret is a server-side secret only.

The app and backend request exactly:

`https://www.googleapis.com/auth/gmail.readonly`

Do not add `gmail.send`, `gmail.modify`, `https://mail.google.com/`, or any broader Gmail scope.

Runtime production Functions must have:

- string parameter `GOOGLE_OAUTH_CLIENT_ID` = production Web/server client ID
- secret `GOOGLE_OAUTH_CLIENT_SECRET` = matching production Web/server client secret
- secret `OAUTH_TOKEN_ENCRYPTION_KEY` = independently generated production encryption key

No production OAuth ID or secret may reuse staging values.

## OAuth consent and verification

Because `gmail.readonly` is a restricted Gmail scope and the backend exchanges/stores refresh-token access for server-side Gmail processing, public production requires Google OAuth verification and the applicable restricted-scope security assessment. Do not represent either as approved until Google has approved it.

Owner must configure the production OAuth project with accurate app name, user support email, developer contact email, public homepage, privacy policy, terms link when used, and authorized domains. Domains used by the homepage/privacy/terms/redirect origins must be owned and verified. Declare only the identity scopes actually used plus `gmail.readonly` and submit the production project through Google's verification flow when the public pages and production client configuration are final.

## App Check / Play Integrity

Repository-side state:

- release source uses `PlayIntegrityAppCheckProviderFactory`
- debug source uses `DebugAppCheckProviderFactory`
- Gmail connection callable explicitly sets `enforceAppCheck: true`
- production dependency `firebase-appcheck-playintegrity` is present

Production enforcement remains **UNPROVEN** until the production Firebase project and Play app are linked, the production Android app is registered in Firebase App Check using the Play app-signing SHA-256 fingerprint, metrics are reviewed, and enforcement is enabled/verified for every protected Firebase backend used by the production app.

## Firestore security

`firestore.rules` remains deny-by-default for all direct clients:

`allow read, write: if false;`

Admin SDK Functions remain the authoritative data path. Production Enablement must not weaken this rule without a separately reviewed architecture change.

## Production IAM design

Production identities must be distinct from staging:

1. **GitHub production deploy identity** — a production-only Google service account impersonated through GitHub OIDC/WIF. No downloaded service-account key.
2. **Production Functions runtime identity** — a dedicated production runtime service account with only the service permissions required by the deployed Functions. Do not rely on staging identity or grant project Owner/Editor.
3. **Staging deploy identity** — remains staging-only and must not be granted production access.
4. **Cloud Build service identity** — platform build identity, with only the build permissions required by Functions deployment.

The WIF provider condition must constrain at least repository ID `1314210715`, repository owner ID `64756523`, GitHub environment `production`, ref `refs/heads/main`, and workflow identity `vhanukaev1981/ClickAndSaveAI/.github/workflows/production-release.yml@refs/heads/main` before production deployment is allowed.

Start from the already validated staging role model, then grant only production-equivalent service-specific deployment roles proven necessary by a production dry-run. Do not grant `roles/owner` or `roles/editor`. The deploy identity may need service-specific deployment roles and `roles/iam.serviceAccountUser` only on the selected production runtime/build identities. Runtime data/API roles must be separately minimized from actual Function requirements.

## GitHub production environment

Create exactly one environment named `production` before the production workflow is enabled from the default branch.

Required controls:

- required reviewer/approval gate appropriate to the owner team
- selected deployment branch/tag policy restricted to `main` (and later explicitly approved release tags only)
- production-only variables/secrets listed above
- no staging secret reuse
- no automatic production deployment from `push` or `pull_request`
- do not weaken main branch protection

The repository currently contains only the `staging` environment; therefore this remains an owner control-plane action.

## Production CI/CD control plane

`.github/workflows/production-release.yml` is manual-dispatch only and uses the protected `production` environment. It fails closed unless:

1. exact lowercase 40-character source SHA is supplied
2. explicit environment confirmation phrase matches
3. source is a descendant of frozen P0 SHA
4. exact-source final CI has succeeded
5. current-tree and full-history secret audits pass
6. production environment isolation guard passes
7. production Firebase/OAuth/signing inputs are present and distinct from staging
8. backend/Android regression and lint pass
9. signed release APK/AAB are produced and verified
10. exact source/build/artifact/signing identity is recorded
11. the protected environment approval gate has allowed the job
12. Firebase deployment occurs only when the separate `DEPLOY_FIREBASE_PRODUCTION` authorization phrase is supplied

The workflow does not upload to Google Play and is not executed in Production Enablement Block 1.

## Artifact traceability

A future signed candidate records:

- source SHA
- final CI run number
- production gate run ID/run number
- application ID
- versionCode/versionName
- Firebase project ID
- APK SHA-256
- AAB SHA-256
- upload signing certificate SHA-256
- Google Play app signing certificate SHA-256
- `release_approved=false`
- `firebase_deployed=false`
- `google_play_published=false`

Debug, staging, OAuth E2E, unsigned release audit, and signed production candidate artifacts must never share an ambiguous identity label.

## Owner action checklist

1. **Google Play Console — create/confirm the app identity without publishing.** Create/select Click & Save AI with permanent package `com.aistudio.clickandsaveai.app`. Configure Google Play App Signing. Let Google manage the app-signing key unless an established legitimate alternative exists. Create a distinct owner-controlled upload key; register its upload certificate. Do not create a production release yet.
2. **Google Play Console — record certificate fingerprints.** From App integrity / Play app signing, record the app-signing certificate SHA-1 and SHA-256. These are the fingerprints for installed Play-distributed production APK identity; do not substitute the upload-key fingerprint.
3. **Firebase Console — verify or create a separate production project.** It must not be `clickandsaveai-staging`. Record its exact Firebase/GCP project ID. Register Android app `com.aistudio.clickandsaveai.app` and add the Play app-signing SHA-1/SHA-256. Enable the existing required services only.
4. **Firebase production config — obtain `google-services.json`.** Download it from the production Android app registration, verify its package/project, base64-encode it locally without logging, and store it only as GitHub `production` environment secret `PRODUCTION_GOOGLE_SERVICES_JSON_B64`.
5. **Google Cloud Console — create production OAuth clients.** In the production project create (a) Android OAuth client for package `com.aistudio.clickandsaveai.app` + Play app-signing SHA-1 and (b) Web application/server OAuth client. Record the Web client ID as `PRODUCTION_GOOGLE_WEB_CLIENT_ID`. Store the matching Web client secret only as production server secret `GOOGLE_OAUTH_CLIENT_SECRET`.
6. **Firebase Functions production secrets/params.** Configure `GOOGLE_OAUTH_CLIENT_ID` to the production Web client ID. Create production-only `GOOGLE_OAUTH_CLIENT_SECRET` and `OAUTH_TOKEN_ENCRYPTION_KEY` in Secret Manager/Firebase Functions secrets. Do not copy staging secret values.
7. **OAuth Branding / Verification Center.** Configure accurate Click & Save AI app name, support email, developer contact, public homepage, privacy policy, terms link if used, and authorized domains. Verify domain ownership. Declare only required identity scopes plus `https://www.googleapis.com/auth/gmail.readonly`. Keep testing/staging separate. When the public production configuration is final, submit brand/data-access verification and complete the restricted-scope security assessment required for server-side restricted Gmail data. Do not claim approval before Google grants it.
8. **Google Play + Firebase App Check.** Link the same production Cloud/Firebase project to Play Integrity. In Firebase Console > App Check register the production Android app with the Play app-signing SHA-256 fingerprint. Review App Check metrics and enable/verify enforcement for the protected production services. Record the actual status; until then classification remains UNPROVEN.
9. **Google Cloud IAM — create production-only WIF/deploy/runtime identities.** Create a production deploy service account and production Functions runtime service account. Configure keyless GitHub OIDC/WIF constrained to repository ID `1314210715`, owner ID `64756523`, environment `production`, ref `refs/heads/main`, and `production-release.yml@refs/heads/main`. Grant only demonstrated service-specific roles; never Owner/Editor and never a downloadable deploy key.
10. **GitHub Settings > Environments — create `production`.** Add an approval gate/required reviewer, restrict deployment branches to `main`, and add the production variables/secrets listed in this document. Do not reuse staging values. The current connector can verify that `production` does not exist but cannot read Actions secret/variable metadata, so owner-side verification is mandatory.
11. **GitHub production variables.** Set `PRODUCTION_FIREBASE_PROJECT_ID`, `PRODUCTION_GOOGLE_WEB_CLIENT_ID`, `PRODUCTION_APP_SIGNING_CERT_SHA256`, `PRODUCTION_UPLOAD_KEY_ALIAS`, `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOY_SERVICE_ACCOUNT` on the protected `production` environment.
12. **GitHub production signing secrets.** Set `PRODUCTION_UPLOAD_KEYSTORE_B64`, `PRODUCTION_UPLOAD_STORE_PASSWORD`, and `PRODUCTION_UPLOAD_KEY_PASSWORD` on the protected `production` environment. Never commit or attach the keystore.
13. **Do not dispatch production deployment yet.** Block 1 prepares the control plane only. No Firebase production deployment, Google Play release, OAuth verification submission, merge, or publication is authorized by this document.
