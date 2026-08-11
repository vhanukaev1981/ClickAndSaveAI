# Click&SaveAI Approach A — Integrated Recovery Design

## Status
Approved product direction: replace the current visual-only Product Preview correction loop with an end-to-end recovery of the real Click&SaveAI product path.

Current reference points:
- Stream A/Core: `agent/ai-native-financial-core` at `ac2105098d698df06159f929f41595f91505c855`
- Stream B/Android UI: `agent/ui-myfinanda-polish` at `dfa2b66dcf3f6b0af9f9f8574fce0118099125cd`
- Product authority: Issue #29 and `docs/PRODUCT_UX_SOURCE_OF_TRUTH.md`
- Real-device rejection/correction tracking: Issue #48

## Goal
A user with an already-connected read-only Gmail account and existing billing emails must open a freshly installed Click&SaveAI APK and receive a truthful, recoverable financial picture built from the server-side source of truth. The app must not present an empty local database as evidence that no bills, recurring services, or savings opportunities exist.

## Non-negotiable product rules
- Gmail remains read-only.
- `unknown != 0` for all financial presentation.
- No fabricated savings, offers, scan progress, provider status, payment status, or household-spend completeness.
- No provider switching by Click&SaveAI.
- Provider contact requires explicit user consent and exact-offer revalidation.
- No consumer-facing `lead` / `ליד` terminology.
- No in-app card storage or payment processing.
- Click&SaveAI remains a proactive savings intelligence product, not an expense tracker or a sparse Product Preview.
- A new APK is not considered valid until the backend/data chain used by that APK has been proven on staging.

## Root-cause summary
The rejected real-device APK exposes three architectural gaps:

1. **Fresh-install recovery gap.** `MainActivity` refreshes Gmail connection status for an authenticated user, but an already-connected Gmail account does not automatically trigger invoice rehydration/backfill. Bills are read from local Room, so a fresh APK can show an empty bill set even when Gmail/server state contains billing evidence.

2. **Financial refresh gap.** Gmail scanning and Financial Context loading are not one coordinated state machine. A successful scan can update server state while Home/Savings continue rendering a previously loaded empty `getFinancialHome()` result.

3. **Integration/deployment gap.** Stream B can produce a valid Android artifact without proving that the exact Stream A/Core backend it expects is actually deployed to staging. A green visual build is therefore insufficient evidence of a working product.

## Architecture decision
Use the server as the recovery/source-of-truth boundary and make Android a synchronized projection of verified server state.

The end-to-end flow becomes:

`Firebase auth -> Gmail connection status -> server recovery/backfill/rehydration -> Financial Agent refresh -> authoritative financial snapshot -> Android local cache projection -> Home/Bills/Savings/Activity/Me render from one synchronized session state`

Room remains a local cache for recognized bills, not the authority for whether the account has server-side billing data.

## Workstream 1 — Core/Staging proof

### Objective
Prove that the exact Core code used by Android is deployed to `clickandsaveai-staging` before any new APK is accepted.

### Required behavior
- The staging deployment must be tied to an immutable Core SHA.
- Deployment must include the Firebase Functions, Firestore rules, and indexes required by that Core SHA.
- The deployment path must fail closed if credentials/project/branch/SHA do not match expected staging values.
- Post-deploy smoke verification must call authenticated staging contracts rather than infer success from CI compilation.

### Required staging evidence
For the target test account, collect evidence for:
- `getGmailConnectionStatus`: connected state and read-only consent version.
- `scanGmailInvoices`: candidate count, returned invoice count, imported/recovered count, parser version, and Financial Agent refresh result.
- `getFinancialHome`: recurring-service count, observed recurring monthly spend, source coverage, insights, and opportunities.

No user-facing APK acceptance proceeds if these calls are not proven against the same staging backend.

## Workstream 2 — Gmail recovery and synchronized Android data

### Objective
A fresh install or cleared local database must recover the user's recognized billing data without requiring Gmail reconnection.

### Session state
Introduce one explicit synchronization state owned by the ViewModel/repository boundary:
- `Unauthenticated`
- `CheckingConnection`
- `Disconnected`
- `Recovering`
- `Ready`
- `Partial`
- `Failed`

`Ready` means the connection check, invoice recovery/backfill result, Financial Context refresh, and local-cache projection have completed for the current session.

`Partial` means usable verified data exists but one non-critical stage failed; the UI must explain the incomplete coverage and never convert the missing portion to zero.

### Recovery rules
When an authenticated session becomes active:
1. Refresh Gmail connection status.
2. If disconnected, stop and render the truthful disconnected state.
3. If connected, invoke server-side invoice recovery/backfill without requesting Google consent again.
4. Replace/upsert the local Gmail invoice projection by stable `sourceMessageId` so reinstall/recovery is idempotent and does not duplicate invoices.
5. Refresh `getFinancialHome()` only after the scan/recovery callable returns.
6. Publish one synchronized `Ready` or `Partial` state to all primary screens.

After a manual scan, Gmail push-triggered refresh, or successful initial Gmail connection, the same refresh pipeline runs again. Home, Bills, and Savings are never refreshed independently from stale triggers.

### Local cache rules
- Gmail invoices are keyed/upserted by stable source identity.
- A server recovery can replace stale local Gmail-derived records without deleting manual/non-Gmail data unless explicitly scoped.
- A new install with empty Room is a cache miss, not a financial conclusion.
- Local totals must not be used as authoritative household/recurring-spend figures when Financial Context is available.

### Truthful zero/unknown contract
The synchronized model must distinguish:
- known zero after a completed authoritative scan;
- unknown because recovery has not completed;
- partial because only some sources/stages succeeded;
- known positive values.

The UI may render numeric `0` only for a field whose backend contract establishes that the value is a completed, known zero.

## Workstream 3 — Integrated Click&SaveAI product UI

### Objective
Remove the current standalone Product Preview feeling and render the already-defined Click&SaveAI product from synchronized real data.

### Primary navigation
Target five product areas once Activity is integrated:
- `בית`
- `חשבונות`
- `חיסכון`
- `פעילות`
- `אני`

If Activity is not implemented in the first integration increment, the existing four tabs may remain temporarily, but no separate Product Preview architecture may be treated as the final product shell.

### Home
Home must answer from synchronized data:
- what Click&SaveAI has recognized;
- observed recurring monthly spend when known;
- what is currently being checked;
- verified savings opportunities available now;
- what needs user action;
- cumulative realized savings only when reliable evidence exists.

During `Recovering`, Home shows truthful synchronization stages rather than `0` cards.

### Bills
Bills is a server-recoverable recognized-bill utility, not a manual expense ledger. Each recognized bill should support provider, category/service, amount, date/status where reliable, bill/document access where supported, price/history insight, and verified provider-payment handoff only when Core supplies a trusted official destination.

### Savings
Savings renders only verified/current opportunities from Core. Empty-state copy means no verified opportunity after a completed evaluation, not failure to load or absence of local cache.

### Activity
Activity records meaningful customer-facing Click&SaveAI events: connected source, scan/recovery completion, bill recognition, price-change detection, verified opportunity, user-approved provider handoff, and provable provider lifecycle events. It must not expose raw backend enums, CRM terms, or fabricated progress.

### Me
Me remains the trust/privacy/account surface. Gmail read-only status and connection controls stay explicit. Connection status must reflect the same synchronized session state used elsewhere.

## Error handling
- Authentication failure -> do not scan; return to authenticated/disconnected flow.
- Gmail token/authorization failure -> mark source action-required; do not delete previously verified financial history automatically.
- Scan timeout/unavailable -> retain last known verified snapshot, mark it stale/partial, expose retry.
- Financial Agent refresh failure after successful invoice import -> preserve imported bills, mark financial insights/opportunities as pending/partial, retry through the same refresh pipeline.
- Local-cache write failure -> continue to render authoritative server snapshot where possible; do not turn it into a zero state.
- Staging deployment mismatch -> block artifact acceptance.

## Testing strategy

### Core
- Unit tests for recovery/backfill idempotency and returned metadata.
- Contract tests for known-zero vs unknown/partial semantics.
- Staging authenticated smoke test for Gmail status -> scan -> Financial Home.

### Android data layer
- Fresh-install test: connected account + empty Room -> recovered invoices -> refreshed Financial Home -> `Ready`.
- Idempotency test: running recovery twice does not duplicate invoices.
- Refresh-order test: Financial Home is fetched after scan/recovery completion.
- Failure tests for token failure, scan failure, Financial Home failure, and Room write failure.
- State tests proving `Recovering`/`Partial` do not expose financial zero as known truth.

### UI
- Compose/state tests for Home/Bills/Savings/Me and Activity when implemented.
- Guard tests for no `lead`/`ליד`, no fake savings, no fake scan percentage, no provider-switch claim, and no payment-processing claim.
- Real-device test on the user's Gmail-backed account after exact-SHA staging proof.

## Delivery gates

### Gate A — Backend truth
Exact Core SHA is deployed to staging and authenticated status/scan/Financial Home smoke tests pass.

### Gate B — Recovery truth
A fresh Android install with empty Room recovers the user's server/Gmail bill projection without Gmail reconnection and reaches `Ready` or an explicitly explained `Partial` state.

### Gate C — Product truth
Home/Bills/Savings/Activity/Me render the synchronized model and preserve all trust/product invariants.

### Gate D — Artifact truth
Only after Gates A-C: run fresh Android unit tests, lint, `assembleDebug`, staging certificate verification, exact-SHA artifact generation, real-device install, and screenshot review.

## Explicitly out of scope for this recovery
- Provider-specific external CRM/API adapters.
- Automated provider switching/cancellation.
- Card storage or in-app payment processing.
- Fabricated demo data to make screens appear populated.
- Broad unrelated refactors.

## Definition of done
Approach A is complete only when a fresh real-device installation against the proven staging backend recognizes/reconstructs the user's billing evidence, produces a synchronized Financial Context, renders the real Click&SaveAI product experience from that data, and no screen mistakes incomplete synchronization for a known zero.