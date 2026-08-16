# Secure Firebase staging deployment

This document describes the configuration and validation required for the Click&SaveAI proactive financial agent and commerce pipeline in `clickandsaveai-staging`.

## 1. Staging project and Android app

The staging project is `clickandsaveai-staging`.

1. Copy `.firebaserc.example` to `.firebaserc` for local CLI use.
2. Keep the registered Android application ID as `com.aistudio.clickandsaveai.app`.
3. Place the staging `google-services.json` in `app/` when building locally. It is intentionally ignored by Git.
4. Keep staging and production OAuth/signing identities separate.

Never commit `google-services.json`, signing keys, Firebase CLI login material, provider credentials or Google Cloud service-account keys.

## 2. Required Google/Firebase services

Enable:

- Firebase Authentication with Google provider
- Cloud Firestore in the selected European location
- Cloud Functions, 2nd generation
- Cloud Scheduler
- Eventarc / required 2nd-gen trigger services
- Gmail API
- Firebase Cloud Messaging
- Firebase App Check
- Play Integrity API for release builds
- Secret Manager

Do not enforce App Check until valid staging traffic is visible in App Check metrics.

## 3. Gmail OAuth

The Android client requests only:

```text
https://www.googleapis.com/auth/gmail.readonly
```

The staging Web OAuth client ID is supplied to debug builds only. Functions use the non-secret parameter in:

```bash
cp functions/.env.example functions/.env.clickandsaveai-staging
```

The Gmail scope is restricted by Google. Complete the required consent-screen, verified-domain, privacy-policy and verification/security-review work before production launch. Do not add broader Gmail scopes merely to simplify implementation.

## 4. Runtime secrets

Configure staging secrets with Firebase CLI:

```bash
firebase functions:secrets:set GOOGLE_OAUTH_CLIENT_SECRET --project clickandsaveai-staging
firebase functions:secrets:set OAUTH_TOKEN_ENCRYPTION_KEY --project clickandsaveai-staging
firebase functions:secrets:set GEMINI_API_KEY --project clickandsaveai-staging
```

Generate the OAuth token encryption key with:

```bash
openssl rand -base64 32
```

Never reuse a Firebase/GCP service-account key, Android signing key or Gemini key as the encryption key.

## 5. Android staging build identity

GitHub CI restores staging configuration only from encrypted repository secrets:

- `STAGING_GOOGLE_SERVICES_JSON_B64`
- `STAGING_DEBUG_KEYSTORE_B64`
- `STAGING_DEBUG_KEYSTORE_PASSWORD`

The staging key alias is `clickandsaveai-staging`. CI verifies the built debug APK against the SHA-256 certificate registered in Firebase before publishing the OAuth E2E-ready APK artifact.

## 6. Protected GitHub staging deployment with OIDC/WIF

`.github/workflows/deploy-staging.yml` is manual (`workflow_dispatch`) and targets the GitHub `staging` environment. It does not contain a long-lived Google Cloud key.

Configure the `staging` GitHub environment with these **environment variables**:

- `GCP_WORKLOAD_IDENTITY_PROVIDER` — full Workload Identity Provider resource name.
- `GCP_DEPLOY_SERVICE_ACCOUNT` — deploy service-account email.

The workflow requests `id-token: write`, exchanges the GitHub OIDC token through Google Workload Identity Federation, re-runs backend tests, then deploys explicitly to `clickandsaveai-staging`.

Protect the `staging` GitHub environment with required reviewers where available. Configure the Google Workload Identity Provider with a condition that trusts only this repository/owner and the intended deployment context. Do not use an unrestricted GitHub OIDC trust relationship.

The deploy service account must have only the Google Cloud/Firebase permissions actually needed for Functions 2nd gen, Firestore rules/index deployment, scheduler/event trigger creation and service-account usage. Avoid broad Owner/Editor roles.

## 7. App Check

- Debug builds use the Firebase App Check debug provider.
- Release builds use Play Integrity.
- Register only controlled developer/CI debug tokens.
- Add production release SHA-256 only when the production signing key exists.
- Confirm callable traffic is valid before enforcing App Check.

## 8. Backend-owned data boundaries

Direct Android access remains denied by Firestore rules. Important backend-owned data includes:

- `gmailConnections/{uid}` — encrypted Gmail refresh token, scopes and consent metadata
- `users/{uid}/gmailMessageImports/{messageId}` — Gmail import/deduplication audit
- `users/{uid}/gmailInvoices/*` — normalized Gmail billing evidence
- `users/{uid}/financialContext/current` — observed recurring financial context
- `users/{uid}/financialInsights/*` — proactive signals
- `users/{uid}/opportunities/*` — savings opportunity lifecycle
- `providerOffers/*` — operator-managed verified offer catalog
- `commerceMatches/*` — internal attribution and commercial terms
- `providerLeads/*` — user-consented attributed provider requests
- `providerDispatchQueue/*` — minimum-data dispatch boundary for real provider adapters
- `commerceEvents/*` — append-only privacy-safe funnel events

Provider-facing payloads must not contain raw Gmail content, the user's current spending context or Click&SaveAI commission terms.

Review retention/deletion policy and administrator access before production. Add Firestore TTL only after the required retention period for leads/dispatch/audit data is formally selected.

## 9. Local validation before deployment

```bash
cd functions
npm install --ignore-scripts
npm test
cd ..
```

Android validation:

```bash
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The PR CI must be green before staging deployment.

## 10. Manual local deployment alternative

After authenticating Firebase CLI and creating the local env/alias files:

```bash
cp .firebaserc.example .firebaserc
cp functions/.env.example functions/.env.clickandsaveai-staging
firebase deploy \
  --project clickandsaveai-staging \
  --only firestore:rules,firestore:indexes,functions \
  --non-interactive
```

Do not deploy this branch to a production Firebase project.

## 11. Staging E2E acceptance checklist

Validate the complete chain, not isolated screens:

1. Google/Firebase sign-in succeeds on the staging-signed APK.
2. Gmail consent names only `gmail.readonly`.
3. Server authorization-code exchange succeeds and the refresh token is encrypted server-side.
4. Initial Gmail backfill imports billing candidates without duplicate message IDs.
5. Backfill batching does not wake the Financial Agent once per imported message.
6. A real-time Gmail billing message triggers the Financial Agent without user action.
7. Financial Context clearly reports Gmail as partial observed coverage, not total household spending.
8. A recurring service creates a stable proactive optimization opportunity.
9. Promotional service wording is not mistaken for the user's current service profile.
10. A provider offer cannot generate a savings claim unless country/category/service, validity, consumer pricing and availability gates pass.
11. VAT, mandatory recurring fees and one-time fees are reflected in first-year savings economics.
12. The best verified user-value offer ranks first regardless of commission.
13. A non-partner best offer is displayed as `VIEW_ONLY` and cannot send user contact data to a provider.
14. A trackable partner offer exposes `IN_APP_PROVIDER_REQUEST` only with an active positive commission model.
15. Newly verified savings generates one deduplicated push notification.
16. Before user acceptance, the exact offer is revalidated for availability and full economics.
17. Explicit consent creates one idempotent attributed lead linked to Opportunity + Offer.
18. A trackable AI lead creates one `providerDispatchQueue` record with the minimum provider payload.
19. No Gmail content, current-spend context or commission value appears in the provider dispatch payload.
20. Commerce funnel events represent match → lead → dispatch → contacted → quote → activation → commission confirmation without contact PII.
21. Operator/admin can move the provider lead only through allowed lifecycle transitions.
22. Activation updates Opportunity/Commerce attribution without later agent sweeps rewriting the accepted offer.
23. `COMMISSION_CONFIRMED` requires and records a positive actual commission amount internally.
24. Disconnect revokes Google access when possible and removes the backend Gmail connection.

## 12. Real provider integration boundary

The repository now creates `providerDispatchQueue` only for user-consented, offer-specific, commercially attributable leads. It intentionally does **not** pretend to contact Partner, Cellcom, Bezeq, insurers or other providers without a real integration contract.

For each provider integration, add a server-side adapter only after these are known:

- authenticated endpoint / CRM handoff method
- exact accepted payload contract
- provider-side idempotency/reference field
- timeout/retry policy
- success and rejection semantics
- activation confirmation method
- commission/reconciliation source of truth

Do not mark a dispatch as sent, a lead as contacted, a service as activated or a commission as confirmed until the corresponding downstream system provides evidence.
