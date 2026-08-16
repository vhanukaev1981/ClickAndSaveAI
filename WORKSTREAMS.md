# Click&SaveAI Workstreams

This file is the coordination source of truth for parallel ChatGPT work on Click&SaveAI.

## Global rules

1. One active chat = one active workstream = one branch.
2. Exactly two chats are active at this stage.
3. Never make commits to another active workstream's branch.
4. Do not weaken truthfulness, security, privacy, ranking, attribution, or consent guardrails to make a test pass.
5. If a change crosses ownership boundaries, record the dependency instead of editing the other owner's files unless the primary chat explicitly promotes an E2E-blocking fix into the validated baseline.
6. Every active workstream must keep CI green before integration.
7. Commission never determines recommendation ranking.
8. Provider actions require an attributable active commercial path; non-partner best offers may remain visible as VIEW_ONLY.

## Stream A — Core / Staging / E2E

**Owner:** primary Click&SaveAI chat / project manager  
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
- Android/backend contracts
- CI, staging deployment and E2E acceptance
- integration decisions and cross-stream coordination

### Current priority
1. Treat the real-device screenshots from 2026-08-08 as E2E findings.
2. Fix E2E blockers before expanding scope.
3. Keep staging/backend and the tested Android baseline aligned.
4. Re-run device E2E after every promoted blocker fix.

### Protected from Stream B unless coordinated
- `functions/**`
- `app/src/main/java/com/example/data/**`
- `app/src/main/java/com/example/MainActivity.kt`
- financial calculations/ranking/attribution/lifecycle logic
- Firestore/Firebase config and CI workflows

## Stream B — UI/UX / MyFinanda-style Product Experience

**Owner:** secondary active chat  
**Branch:** `agent/ui-myfinanda-polish`  
**PR:** #24  
**Status:** ACTIVE

### Owns
- Dashboard visual hierarchy and information architecture
- Savings/opportunity presentation
- Bills/activity presentation
- Profile/settings presentation
- navigation polish
- design system, typography, spacing, icons and states
- empty/loading/error/success states
- RTL/Hebrew usability
- removal of internal/technical terminology from user-facing UI

### Current E2E findings assigned to Stream B
- remove permanent Gmail technical card from Profile; keep revocation under Privacy & Connections
- remove legacy manual provider-lead flow from Bills
- never show `₪0` as if it were a verified saving; show an “under review” state
- hide raw internal values such as `NOT_FOUND`, `GMAIL_READONLY`, verification codes and internal IDs
- simplify customer-facing wording and visual density seen in the device screenshots

### Must not change without coordination
- backend Functions
- Firestore schema/rules
- Gmail/Auth mechanics
- financial calculations
- offer ranking
- commission/attribution logic
- `BackendRepository.kt` response semantics

## Stream C — Provider / Commerce Integrations

**Branch:** `agent/provider-commerce-integrations`  
**PR:** #23  
**Status:** FROZEN / DRAFT — NO ACTIVE CHAT ASSIGNED

Work already completed is preserved. Do not add new scope until Stream A completes the current E2E correction cycle and project management explicitly reassigns one of the two active chats.

Preserve existing guardrails: commission-independent ranking, minimum-data provider payloads, downstream evidence for lifecycle success, server-side credentials, no fabricated provider connectivity.

## Stream D — AI Orchestrator / MCP / Open Banking

**Status:** QUEUED — DO NOT START YET

Start only after the current E2E correction cycle and after one of the two active chats is explicitly reassigned.

## Current integration order

1. Stream A fixes and validates E2E blockers.
2. Stream B continues UI/UX polish in parallel.
3. Rebase/integrate Stream B onto the validated Stream A baseline and run regression/device E2E.
4. Only then choose the next active stream: normally Provider/Commerce (C), then AI/MCP/Open Banking (D).

## Status reporting

When the user asks for **"סטטוס"**, report:
- Active Chat 1 / Stream A — DONE / IN PROGRESS / BLOCKED / NEEDS USER, branch, CI and next action
- Active Chat 2 / Stream B — DONE / IN PROGRESS / BLOCKED / NEEDS USER, branch, CI and next action
- Stream C — frozen state and preserved work
- Stream D — queued state
