# Secure Firebase deployment

This document describes the minimum configuration required before the Android app can use Google authentication, Gmail import, CRM leads or Gemini analysis.

## 1. Create and select the Firebase project

1. Create a Firebase project in the Google Cloud organization that will own production data.
2. Copy `.firebaserc.example` to `.firebaserc` and replace `YOUR_FIREBASE_PROJECT_ID`.
3. Register the Android application ID `com.aistudio.clickandsaveai.app`.
4. Download `google-services.json` into `app/`. The file is intentionally ignored by Git.
5. Register debug and release SHA-1/SHA-256 signing certificate fingerprints.

## 2. Enable required services

Enable:

- Firebase Authentication with Google as a provider
- Cloud Firestore in a European location selected for the project
- Cloud Functions, 2nd generation
- Gmail API
- Firebase App Check
- Play Integrity API for release builds
- Secret Manager

Do not enable App Check enforcement until debug and release requests are visible as valid in App Check metrics.

## 3. Configure OAuth clients

Create or identify:

- an Android OAuth client for the application ID and signing fingerprints
- a Web OAuth client used by Firebase Authentication and server-side code exchange

Set the same Web client ID in both places:

1. `app/src/main/res/values/strings.xml` as `google_web_client_id`
2. `functions/.env.<PROJECT_ID>`:

```dotenv
GOOGLE_OAUTH_CLIENT_ID=000000000000-example.apps.googleusercontent.com
```

The requested Gmail scope is limited to:

```text
https://www.googleapis.com/auth/gmail.readonly
```

This is a Google restricted scope. Configure the OAuth consent screen, privacy policy, verified domains and the verification/security-review process required by Google before production use. Do not add broader Gmail scopes.

## 4. Configure secrets

Run from the repository root after authenticating Firebase CLI:

```bash
firebase functions:secrets:set GOOGLE_OAUTH_CLIENT_SECRET
firebase functions:secrets:set OAUTH_TOKEN_ENCRYPTION_KEY
firebase functions:secrets:set GEMINI_API_KEY
```

Generate the OAuth encryption key with:

```bash
openssl rand -base64 32
```

Never reuse the Firebase service-account key, Android signing key or Gemini key as the encryption key.

## 5. App Check

- Debug builds use the Firebase App Check debug provider. Register only developer/CI debug tokens.
- Release builds use Play Integrity.
- Register the release SHA-256 certificate in Firebase App Check.
- Confirm callable-function traffic is valid before enforcing App Check for Cloud Functions.

## 6. Firestore data model

Backend-owned collections are denied to direct Android clients:

- `gmailConnections/{uid}` — encrypted refresh token, scopes and consent metadata
- `providerLeads/{leadId}` — CRM intake queue
- `users/{uid}/gmailMessageImports/{messageId}` — Gmail deduplication audit

Cloud Functions access these collections with Admin SDK. Review data retention, deletion requests and administrator access before launch. Configure a Firestore TTL policy for CRM leads if a fixed retention period is adopted.

## 7. Deploy and verify

Install dependencies and run backend tests:

```bash
cd functions
npm install
npm test
cd ..
```

Deploy rules and functions:

```bash
firebase deploy --only firestore:rules,firestore:indexes,functions
```

Verify in a non-production Firebase project first:

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
