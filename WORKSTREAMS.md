# Click&SaveAI Workstreams

This file is the coordination source of truth for parallel ChatGPT work on Click&SaveAI.

## Global rules

1. One chat = one workstream = one branch.
2. Never make commits to another workstream's branch.
3. Read this file and the target branch/PR before changing code.
4. Do not weaken truthfulness, security, privacy, ranking, attribution, or consent guardrails to make a test pass.
5. If a change crosses ownership boundaries, stop and record the dependency instead of editing the other owner's files.
6. Every workstream must keep CI green before its PR is merged.
7. The user-first ranking rule is immutable: commission never determines which offer is ranked best for the user.
8. Provider actions require an attributable active commercial path; non-partner best offers may remain visible as VIEW_ONLY.

## Stream A — Core / Staging / E2E

**Owner:** primary Click&SaveAI chat
**Branch:** `agent/ai-native-financial-core`
**PR:** #7
**Status:** ACTIVE / CRITICAL PATH

### Owns
- Firebase/Auth/App Check/security boundaries
- Gmail OAuth, backfill, watch, parsing and deduplication
- Financial Context
- Financial Agent and scheduled sweeps
- Opportunity Engine
- Savings calculation and truthfulness gates
- Offer matching/ranking policy
- Commercial action policy
- Attribution and lifecycle core
- Push notifications
- Android/backend contract models
- CI, staging deployment and E2E acceptance

### Current priority
1. Keep current HEAD CI green.
2. Deploy AI-native core to staging.
3. Run full staging E2E on a staging-signed APK.
4. Fix E2E defects before expanding scope.

### Protected from other streams unless coordinated
- `functions/src/financial*`
- `functions/src/commerce*`
- `functions/src/opportunity*`
- `functions/src/providerOffer*`
- `functions/src/gmail*`
- `functions/src/entry.js`
- `functions/test/*` tests covering core policies
- `app/src/main/java/com/example/data/repository/BackendRepository.kt`
- `firestore.rules`
- `firebase.json`
- `.github/workflows/android-ci.yml`
- `.github/workflows/deploy-staging.yml`

## Stream B — UI/UX / MyFinanda-style Product Experience

**Owner:** secondary UI chat
**Branch:** `agent/ui-myfinanda-polish`
**Status:** READY

### Goal
Make the app feel like a polished AI-native financial product while preserving the existing backend contracts and truthfulness rules.

### Owns
- Dashboard visual hierarchy and information architecture
- Savings/opportunity cards
- Bills/activities visual experience
- Profile/settings presentation
- Navigation polish
- Design system, typography, spacing, icons and states
- Empty/loading/error/success states
- RTL/Hebrew usability
- MyFinanda-style clarity without copying proprietary assets

### Allowed files
Prefer changes under:
- `app/src/main/java/com/example/ui/**`
- app theme/design-system files
- user-facing strings/resources

### Must not change without coordination
- backend Functions
- Firestore schema/rules
- Gmail/auth flows
- financial calculations
- offer ranking
- commission/attribution logic
- `BackendRepository.kt` response semantics

### Deliverable
A separate PR targeting `agent/ai-native-financial-core`, with screenshots/description of all changed states and green Android CI.

## Stream C — Provider / Commerce Integrations

**Owner:** secondary provider/business-integration chat
**Branch:** `agent/provider-commerce-integrations`
**Status:** READY

### Goal
Turn the internal dispatch/attribution pipeline into an integration-ready commercial distribution layer without inventing provider connectivity.

### Owns
- provider adapter interface/framework
- dispatch worker architecture
- retry/idempotency/dead-letter behavior
- provider acknowledgement/reference handling
- operator-side integration status
- activation evidence model
- commission reconciliation adapters
- provider contract/config schema
- commercial analytics derived from privacy-safe funnel events

### Must preserve
- recommendation ranking is independent of commission
- no provider is marked contacted/activated without downstream evidence
- no raw Gmail content/current-spend context in provider payloads
- no provider API/CRM endpoint is fabricated
- provider-specific credentials stay server-side and outside Git

### Must not change without coordination
- user ranking algorithm
- Gmail parsing
- Financial Context
- Savings calculations
- core opportunity lifecycle semantics
- Android financial model contracts

### Deliverable
A separate PR targeting `agent/ai-native-financial-core`. Framework code may ship before a real provider is connected, but must clearly distinguish READY_FOR_ADAPTER from ACTUALLY_CONNECTED.

## Stream D — AI Orchestrator / MCP / Open Banking

**Status:** QUEUED — DO NOT START YET

Start only after Stream A completes the first staging E2E cycle. This stream will own tool registry, orchestration, conversational follow-up, MCP exposure and later data-source adapters such as Open Banking. Starting it before E2E would increase merge risk and obscure defects in the core path.

## Integration order

1. Stream A reaches green CI and staging E2E baseline.
2. Merge Stream B UI/UX after rebasing on the validated core.
3. Merge Stream C integration framework after rebasing on the validated core.
4. Re-run full regression/E2E.
5. Start Stream D.

## Status reporting

When the user asks for **"סטטוס"**, report all active streams in this order:
- Stream A Core/Staging/E2E
- Stream B UI/UX
- Stream C Provider/Commerce
- Stream D AI/MCP/Open Banking (queued until activated)

For each stream report: DONE / IN PROGRESS / BLOCKED / NEEDS USER, current branch, current CI state, and next concrete action.
