# Click & Save AI

Click&SaveAI is an Android + Firebase **AI-native proactive household savings system**. The user connects Gmail once; the backend continuously builds a minimal financial context from billing evidence, detects recurring services, looks for verified savings opportunities and surfaces them without requiring the user to start a chat or manual analysis.

## Current branch status

`agent/ai-native-financial-core` implements the proactive financial core and commerce attribution pipeline. It is **staging-ready code, not production deployment**.

### Financial intelligence

- Firebase Authentication is used for identity.
- Gmail authorization is limited to `gmail.readonly` with explicit consent.
- Refresh tokens are encrypted server-side with AES-256-GCM.
- Gmail backfill and real-time imports run on Cloud Functions and deduplicate by Gmail message ID.
- Parser v5 extracts only the billing/service fields needed by the financial system and propagates canonical service profiles.
- The Financial Agent builds recurring-spend context, insights and stable service-level savings opportunities.
- Real-time Gmail changes wake the agent automatically; a scheduled sweep also re-evaluates users every four hours.
- Provider-catalog changes trigger a fresh agent sweep, so an existing user expense can become actionable when a new offer appears.

### Truthfulness and offer matching

- No savings amount is shown until a compatible current offer is verified.
- Matching validates country, category, canonical service type, availability and offer validity.
- Consumer price evidence must be VAT-inclusive.
- Mandatory recurring fees and one-time fees are included in first-year economics.
- Availability-sensitive offers require explicit eligibility evidence before they can produce an automatic savings claim.
- Insurance and electricity do not reuse the fixed-monthly comparison model until category-specific pricing engines exist.
- Recommendation ranking is based on user value; **commission is deliberately excluded from ranking**.

### Business / commerce model

A verified offer can be shown even when Click&SaveAI has no commercial agreement with that provider. Commercial action is a separate decision:

- `VIEW_ONLY` — the recommendation remains visible, but no user contact data is sent to the provider.
- `IN_APP_PROVIDER_REQUEST` — enabled only when the exact offer has an active attributable commercial agreement and a positive commission model.

The implemented commercial chain is:

`Financial need → Opportunity → Verified offer → User consent → Lead → Dispatch queue → Provider lifecycle → Activation → Commission confirmation`

- Opportunity acceptance re-validates the exact offer, service compatibility, availability, full first-year economics and savings.
- Accepted opportunities are lifecycle-locked so later agent sweeps cannot rewrite an in-flight transaction.
- Provider lead lifecycle: `NEW → CONTACTED → QUOTED → ACTIVATED → COMMISSION_CONFIRMED` (or `REJECTED`).
- Actual confirmed commission is stored internally.
- `providerDispatchQueue` contains only attributable partner leads and a minimum-data provider payload.
- Gmail content, current-spend context and commission terms are not included in the provider-facing payload.
- An append-only privacy-safe commerce event ledger records the measurable funnel from verified match through commission confirmation.

External provider fulfillment still requires real provider-specific API/CRM adapters and credentials. The repository intentionally does not invent those integrations.

## Android experience

AI is ambient across the product rather than a standalone chat tab:

- Home shows observed recurring spend and proactive findings.
- Savings shows verified opportunities and full consumer economics rather than headline price alone.
- Non-partner offers can still be the best recommendation and remain visible as view-only.
- Trackable partner offers can open an explicit consent flow for a provider request.
- Push notifications are deduplicated and sent only for newly verified savings opportunities.

## Repository layout

- `app/` — Android Kotlin / Jetpack Compose client
- `functions/` — Firebase Functions 2nd generation, Node.js 22
- `firestore.rules` — direct-client access restrictions
- `docs/DEPLOYMENT.md` — staging, OAuth, App Check, WIF and E2E deployment checklist
- `.github/workflows/android-ci.yml` — backend tests + Android tests/lint/APK validation
- `.github/workflows/deploy-staging.yml` — protected manual Firebase staging deployment through GitHub OIDC/WIF

## Security boundaries

Backend-owned collections containing OAuth credentials, Gmail-import audit records, financial state, provider leads, commerce attribution and dispatch records are denied to direct Android clients. Callable functions require Firebase Authentication and App Check where applicable. Operator-only catalog and commerce mutations require `operator` or `admin` claims.

Android contains no Gmail refresh token, OAuth client secret, provider credential or Gemini API key. Android backup and cleartext traffic are disabled.

## Validation

Backend:

```bash
cd functions
npm install --ignore-scripts
npm test
```

Android CI runs:

```bash
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The CI also verifies the staging debug APK signing certificate before publishing an OAuth E2E-ready artifact.

## Deployment

Staging project: `clickandsaveai-staging`.

Use `docs/DEPLOYMENT.md`. A protected manual GitHub deployment workflow is included and uses Google Cloud Workload Identity Federation rather than a long-lived service-account key.

Do not call the branch production-ready until staging E2E validates Gmail import → Agent → Opportunity → Offer → Push/UI → consent → Lead → Dispatch Queue → commerce lifecycle.
