# Click & Save AI

Android application and Firebase backend for importing household invoices, creating provider-change leads and producing cautious AI-assisted analysis.

## Current branch status

The remediation branch implements the approved architecture but is **not deployed production infrastructure**:

- Firebase Authentication is used for identity.
- Gmail authorization requests only `gmail.readonly` with explicit consent.
- Google server authorization codes are exchanged by Cloud Functions.
- Gmail refresh tokens are encrypted with AES-256-GCM before Firestore storage.
- Gmail scanning runs on the backend and deduplicates by Gmail message ID.
- Imported invoices remain unverified and do not generate automatic savings.
- Provider-change requests create idempotent leads in a backend-owned Firestore queue.
- Gemini is called only from an authenticated, App-Check-protected Cloud Function.
- Android contains no Gmail refresh token, OAuth client secret or Gemini API key.
- Fake invoices, fake authentication, fake savings and fake provider-switch success paths have been removed.

The CRM queue still requires an operational dispatcher/integration before external launch. Image receipt scanning, background price monitoring and real provider fulfillment are not implemented.

## Repository layout

- `app/` — Android Kotlin/Compose client
- `functions/` — Firebase Functions 2nd generation, Node.js 22
- `firestore.rules` — direct-client access restrictions
- `docs/DEPLOYMENT.md` — Firebase, OAuth, App Check and secret setup
- `.github/workflows/android-ci.yml` — Android build/lint/tests and backend tests

## Security boundaries

Backend-owned collections containing OAuth credentials, Gmail-import audit records and CRM leads are denied to direct Android clients. Callable functions require Firebase Authentication and App Check. Provider credentials and model keys must remain in Secret Manager.

Android backup and cleartext traffic are disabled. Room uses an explicit migration and imported Gmail invoices have a unique source-message index.

## Local validation

Backend unit tests:

```bash
cd functions
npm install
npm test
```

Android validation currently uses Gradle 9.3.1 in CI because a committed Gradle Wrapper is still pending:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

## Deployment

Follow [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md). Do not ship or enable App Check enforcement until OAuth verification, Firebase secrets, signing fingerprints, retention policy, CRM operations and end-to-end staging tests are complete.
