# Secure Firebase deployment

This document describes the minimum configuration required before the Android app can use Google authentication, Gmail import, CRM leads or Gemini analysis.

## 1. Create and select the Firebase project

The staging project is already registered as `clickandsaveai-staging`.

1. Copy `.firebaserc.example` to `.firebaserc`; the example already points at `clickandsaveai-staging`.
2. Keep the registered Android application ID as `com.aistudio.clickandsaveai.app`.
3. Place the downloaded `google-services.json` in `app/`. The file is intentionally ignored by Git.
4. The staging debug SHA-1 and SHA-256 fingerprints are already registered. Add release fingerprints only when the release signing key exists.

Never commit `google-services.json`, Android signing keys or Firebase CLI login material.

## 2. Enable required services

Enable:

- Firebase Authentication with Google as a provider
- Cloud Firestore in the selected European location
- Cloud Functions, 2nd generation
- Gmail API
- Firebase App Check
- Play Integrity API for release builds
- Secret Manager

Do not enable App Check enforcement until debug and release requests are visible as valid in App Check metrics.

## 3. Configure OAuth clients

The staging Firebase project has already generated:

- an Android OAuth client for `com.aistudio.clickandsaveai.app` and the registered debug signing certificate
- a Web OAuth client for Firebase Authentication and server-side code exchange

The staging Web client ID is scoped to debug builds in `app/src/debug/res/values/strings.xml`. The main/release resource intentionally remains blank so a staging OAuth client cannot accidentally ship in a production build. A future production Firebase project must provide its own release Web OAuth client ID.

For Functions, copy the checked-in non-secret template:

```bash
cp functions/.env.example functions/.env.clickandsaveai-staging
```

The resulting file contains only the non-secret string parameter:

```dotenv
GOOGLE_OAUTH_CLIENT_ID=716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com
```

`functions/.env.clickandsaveai-staging` is ignored by Git. OAuth client secrets and API keys must not be added to this env file.

The requested Gmail scope is limited to:

```text
https://www.googleapis.com/auth/gmail.readonly
```

This is a Google restricted scope. Configure the OAuth consent screen, privacy policy, verified domains and the verification/security-review process required by Google before production use. Do not add broader Gmail scopes.

## 4. Configure secrets

Run from the repository root after authenticating Firebase CLI:

```bash
firebase functions:secrets:set GOOGLE_OAUTH_CLIENT_SECRET --project clickandsaveai-staging
firebase functions:secrets:set OAUTH_TOKEN_ENCRYPTION_KEY --project clickandsaveai-staging
firebase functions:secrets:set GEMINI_API_KEY --project clickandsaveai-staging
```

Generate the OAuth encryption key with:

```bash
openssl rand -base64 32
```

Never reuse the Firebase service-account key, Android signing key or Gemini key as the encryption key. Do not paste secret values into GitHub issues, PR comments or source files.

## 5. App Check

- Debug builds use the Firebase App Check debug provider. Register only developer/CI debug tokens.
- Release builds use Play Integrity.
- Register the release SHA-256 certificate in Firebase App Check when the release key exists.
- Confirm callable-function traffic is valid before enforcing App Check for Cloud Functions.

## 6. Firestore data model

Backend-owned collections are denied to direct Android clients:

- `gmailConnections/{uid}` — encrypted refresh token, scopes and consent metadata
- `providerLeads/{leadId}` — CRM intake queue
- `users/{uid}/gmailMessageImports/{messageId}` — Gmail deduplication audit

Cloud Functions access these collections with Admin SDK. Review data retention, deletion requests and administrator access before launch. Configure a Firestore TTL policy for CRM leads if a fixed retention period is adopted.

## 7. Deploy and verify

Before deployment, create the local Firebase alias file and Functions env file:

```bash
cp .firebaserc.example .firebaserc
cp functions/.env.example functions/.env.clickandsaveai-staging
```

Install dependencies and run backend tests:

```bash
cd functions
npm install
npm test
cd ..
```

Deploy rules, indexes and functions explicitly to staging:

```bash
firebase deploy --project clickandsaveai-staging --only firestore:rules,firestore:indexes,functions
```

Verify in staging:

1. Google/Firebase sign-in succeeds.
2. Gmail consent names only `gmail.readonly`.
3. A server authorization code is exchanged successfully.
4. The stored refresh token is encrypted and never appears in logs.
5. Repeated Gmail scans do not duplicate a message ID.
6. Imported invoices remain `UNVERIFIED_GMAIL_IMPORT` with zero calculated savings.
7. Repeated provider-lead submissions with the same idempotency key return the existing lead.
8. AI failures return an error and never a fabricated result.
9. Disconnect revokes Google access when possible and deletes the backend connection.

## 8. CRM handoff

`providerLeads` is the phase-one CRM intake queue. Before external launch, choose the operational CRM destination and add a server-side dispatcher or managed integration. Do not expose Firestore write access to the Android app and do not mark a lead as contacted or completed until the downstream CRM confirms it.
