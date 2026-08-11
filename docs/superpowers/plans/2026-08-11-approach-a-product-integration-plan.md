# Approach A Product Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the standalone Product Preview shell with the real Click&SaveAI product experience driven by the synchronized authoritative financial session proven in Gates A and B.

**Architecture:** Keep the approved Light Premium visual language, but route Home/Bills/Savings/Activity/Me through one synchronized state model instead of separate preview/load triggers. Product copy and actions are projections of backend evidence; no screen may manufacture values to fill empty space.

**Tech Stack:** Jetpack Compose, Kotlin StateFlow, existing Core financial models, Material 3.

## Global Constraints

- Gate A and Gate B must be proven before final product acceptance.
- Product authority: Issue #29 + `docs/PRODUCT_UX_SOURCE_OF_TRUTH.md` + approved Approach A design.
- Brand: canonical `Click&SaveAI`, LTR token inside Hebrew RTL.
- Light Premium only; no dark primary screens.
- Verified savings green; primary actions blue.
- No fake offers, savings, progress, delivery, switching or payment processing.
- No consumer-facing `lead` / `ליד`, CRM, raw backend enums or IDs.
- Stream C remains isolated; provider-specific external fulfillment is not invented here.

---

### Task 1: Introduce integrated product shell and Activity destination

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Modify: `app/src/main/java/com/example/ui/components/BottomNavBar.kt`
- Create: `app/src/main/java/com/example/ui/screens/ActivityScreen.kt`
- Test: `app/src/test/java/com/example/IntegratedNavigationContractTest.kt`

**Interfaces:**
- Primary destinations: Home, Bills, Savings, Activity, Me.
- All screens consume `FinancialSyncState` or derived immutable screen state from `MainViewModel`.

- [ ] Write failing navigation tests requiring five destinations and forbidding a separate standalone Product Preview architecture as the final shell.
- [ ] Add Activity destination and keep RTL/system safe-area correctness.
- [ ] Run focused tests to GREEN.
- [ ] Commit `feat(android): integrate Click&SaveAI product shell`.

---

### Task 2: Rebuild Home around synchronized real value

**Files:**
- Modify/split: `app/src/main/java/com/example/ui/screens/ProductPreviewScreens.kt` or replace with focused `HomeScreen.kt` and shared product components.
- Test: `app/src/test/java/com/example/HomeTruthStateTest.kt`

**Interfaces:**
- Recovering -> truthful active synchronization state, no numeric zero cards.
- Ready -> recognized services, observed recurring spend, verified opportunities, attention items.
- Partial -> last verified values + clear incomplete-coverage message.

- [ ] Write failing tests for Recovering/Ready/Partial/Disconnected states.
- [ ] Implement Home hierarchy: compact greeting/status, money snapshot when known, verified savings hero when present, live real activity, opportunities, attention.
- [ ] Ensure cumulative realized savings is hidden unless reliable evidence exists.
- [ ] Run tests and commit `feat(home): render synchronized savings intelligence`.

---

### Task 3: Make Bills a recovered active utility

**Files:**
- Create/modify: `app/src/main/java/com/example/ui/screens/BillsScreen.kt`
- Test: `app/src/test/java/com/example/BillsTruthStateTest.kt`

**Interfaces:**
- Bills list uses recovered Gmail projection from Gate B.
- Empty after Ready means authoritative scan found no recognized bills; empty during Recovering/Partial is not rendered as a known empty archive.

- [ ] Write failing tests distinguishing cache-empty Recovering from known-empty Ready.
- [ ] Implement provider/category/amount/date hierarchy from recognized records.
- [ ] Preserve document viewing where supported.
- [ ] Show official provider payment handoff only when Core provides a trusted verified destination; otherwise omit CTA.
- [ ] Run tests and commit `feat(bills): render server-recoverable bill center`.

---

### Task 4: Make Savings reflect only current verified Core opportunities

**Files:**
- Create/modify: `app/src/main/java/com/example/ui/screens/SavingsScreen.kt`
- Modify: opportunity action components/repositories only where needed for existing exact-offer consent flow.
- Test: `app/src/test/java/com/example/SavingsTruthStateTest.kt`

**Interfaces:**
- Unknown/Recovering -> no `₪0` savings conclusion.
- Ready with no verified opportunity -> truthful evaluated empty state.
- Ready with verified opportunity -> Current -> New, verified monthly/annual economics, conditions, action mode.

- [ ] Write failing state tests.
- [ ] Implement current/new comparison and verified-value presentation.
- [ ] Preserve explicit consent and exact-offer revalidation before provider handoff.
- [ ] Preserve VIEW_ONLY when commercial action is unavailable.
- [ ] Run tests and commit `feat(savings): integrate verified opportunity experience`.

---

### Task 5: Implement truthful Activity

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/ActivityScreen.kt`
- Add model/mapping file if useful: `app/src/main/java/com/example/ui/model/CustomerActivityItem.kt`
- Test: `app/src/test/java/com/example/CustomerActivityContractTest.kt`

**Interfaces:**
- Customer-facing events may include connected source, completed recovery/scan, bill recognition, price-change detection, verified opportunity, explicit handoff intent, and provider lifecycle only when externally evidenced.

- [ ] Write failing sanitization tests forbidding `lead`, raw CRM/backend statuses, raw IDs and unproven activation/delivery claims.
- [ ] Map only provable events into concise customer language.
- [ ] If no authoritative event feed exists for a category, omit it rather than fabricate history.
- [ ] Run tests and commit `feat(activity): show provable Click&SaveAI activity`.

---

### Task 6: Align Me with synchronized connection/trust state

**Files:**
- Create/modify: `app/src/main/java/com/example/ui/screens/MeScreen.kt`
- Test: `app/src/test/java/com/example/MeConnectionStateTest.kt`

**Interfaces:**
- Gmail connection status comes from the same synchronized session used by Home/Bills/Savings.
- Read-only permission and retry/action-required states remain explicit.

- [ ] Write failing tests for connected, disconnected, recovering, and action-required source states.
- [ ] Implement compact account/connection/privacy rows.
- [ ] Ensure sign-out does not dominate the product surface.
- [ ] Run tests and commit `feat(me): align account and source trust state`.

---

### Task 7: Remove legacy Product Preview truth leaks

**Files:**
- Modify/delete obsolete preview routes/components only after replacements are live.
- Update guard tests: `ProductPreviewSourceOfTruthGuardTest.kt`, `StreamBFinancialSurfaceContractTest.kt`, and related tests.

- [ ] Add a failing guard that primary navigation no longer depends on standalone preview placeholder states.
- [ ] Remove dead preview-only code and stale strings such as synthetic local-finance framing.
- [ ] Preserve reusable approved brand/theme components.
- [ ] Run full Android tests and lint.
- [ ] Commit `refactor(android): retire standalone Product Preview shell`.

---

### Task 8: Gates C and D exact-SHA acceptance

- [ ] Re-check Stream A/B/C before candidate freeze.
- [ ] Confirm Gate A backend SHA/deploy evidence is still the backend target.
- [ ] Confirm Gate B fresh-install recovery evidence still passes.
- [ ] Freeze one final Android SHA.
- [ ] Run fresh unit tests, Android lint, `assembleDebug`, staging certificate verification and exact-SHA artifact upload.
- [ ] Install exact APK on real Android device.
- [ ] Capture Home/Bills/Savings/Activity/Me.
- [ ] Verify the user's known Gmail billing evidence is actually represented; do not approve on appearance alone.
- [ ] Compare visual/product result against Issue #29 and approved references.
- [ ] Record explicit approval or defects in Issue #48/next P0 issue.

## Gates C/D Definition of Done

The app is accepted only when synchronized real data drives the integrated Click&SaveAI experience on the real device, all Truthfulness/Privacy boundaries hold, exact-SHA Android verification is green, and the user explicitly approves the resulting product experience.