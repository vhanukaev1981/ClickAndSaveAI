# Approach A Gmail/Android Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a fresh Android install recover recognized Gmail billing data and the Financial Home snapshot from the proven staging backend without reconnecting Gmail or mistaking an empty Room cache for known zero.

**Architecture:** Introduce one ViewModel-owned synchronization pipeline and explicit session state. Server callables remain authoritative; Room becomes an idempotent projection keyed by stable Gmail source identity. All primary screens consume the same synchronized state and refresh after scan/recovery completes.

**Tech Stack:** Kotlin, coroutines/StateFlow, Room, Firebase callable functions, Android unit tests.

## Global Constraints

- Gate A must be complete before this plan is accepted as working against staging.
- Gmail remains read-only.
- No Gmail reconnect prompt for an already-connected account unless authorization is actually invalid.
- `unknown != 0`; recovering/partial/failed states cannot emit known-zero UI semantics.
- Server invoice identity uses stable `sourceMessageId`.
- Do not modify Stream C.
- Do not weaken Trust/Truthfulness/Privacy tests.

---

### Task 1: Define synchronized financial session state

**Files:**
- Create: `app/src/main/java/com/example/data/repository/FinancialSyncState.kt`
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`
- Test: `app/src/test/java/com/example/FinancialSyncStateTest.kt`

**Interfaces:**
- Produces `sealed interface FinancialSyncState` with `Unauthenticated`, `CheckingConnection`, `Disconnected`, `Recovering`, `Ready`, `Partial`, `Failed`.
- `Ready` carries the latest `GmailScanResult` metadata and `FinancialHomeResult`.
- `Partial` carries the last usable verified snapshot plus a human-safe reason.

- [ ] Write failing state tests proving Recovering/Partial do not expose financial values as known zero.
- [ ] Run `gradle testDebugUnitTest --tests com.example.FinancialSyncStateTest` and record RED.
- [ ] Implement the sealed state with explicit nullable/known fields rather than synthetic zeros.
- [ ] Run the focused test to GREEN.
- [ ] Commit `feat(android): add synchronized financial session state`.

---

### Task 2: Make Gmail recovery idempotent on fresh install

**Files:**
- Modify: `app/src/main/java/com/example/data/repository/GmailRepository.kt`
- Modify: `app/src/main/java/com/example/data/repository/ShoppingRepository.kt`
- Modify if required: `app/src/main/java/com/example/data/local/AppDatabase.kt`
- Test: `app/src/test/java/com/example/GmailInvoiceMergeTest.kt`
- Create: `app/src/test/java/com/example/GmailFreshInstallRecoveryTest.kt`

**Interfaces:**
- Add a repository method equivalent to `recoverInvoicesForConnectedAccount(): Result<GmailScanResult>` that calls the server scan/backfill and replaces/upserts the Gmail projection by `sourceMessageId`.
- Preserve non-Gmail/manual records unless explicitly scoped.

- [ ] Write failing test: empty Room + connected account + server returns three invoices -> local projection contains exactly three.
- [ ] Write failing idempotency test: run the same recovery twice -> still three, no duplicates.
- [ ] Write failing replacement test: same `sourceMessageId` with updated amount -> one updated record.
- [ ] Implement stable upsert/replace behavior.
- [ ] Run focused tests and then all Android unit tests.
- [ ] Commit `fix(android): recover Gmail invoice projection idempotently`.

---

### Task 3: Coordinate connection -> recovery -> Financial Home in one pipeline

**Files:**
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/data/repository/BackendRepository.kt` only if status metadata from Gate A requires model expansion.
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Test: `app/src/test/java/com/example/FinancialRecoveryPipelineTest.kt`

**Interfaces:**
- Add `refreshFinancialSession(reason: FinancialRefreshReason)`.
- Required call order for an authenticated connected account: `getGmailConnectionStatus -> scanGmailInvoices/recovery -> getFinancialHome -> Ready/Partial`.
- Manual scan and post-OAuth connection reuse the same method.

- [ ] Write a failing order test using fakes that records callable invocation order.
- [ ] Write a failing fresh-start test proving an authenticated connected session enters Recovering and reaches Ready without user action.
- [ ] Write failure tests: Gmail auth invalid -> action-required state; scan unavailable -> Partial/Failed without zero; Financial Home failure after successful bills -> Partial retaining bills.
- [ ] Implement the single refresh pipeline.
- [ ] Replace startup `refreshConnectionStatus()`-only behavior in `MainActivity` with the synchronized ViewModel entry point.
- [ ] Replace `completeGmailAuthorization` and `triggerGmailSync` follow-up logic with the same pipeline.
- [ ] Run focused and full Android unit tests.
- [ ] Commit `fix(android): synchronize Gmail recovery and Financial Home refresh`.

---

### Task 4: Stop local cache totals from masquerading as authoritative financial totals

**Files:**
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`
- Modify: product screen models as required.
- Test: `app/src/test/java/com/example/FinancialAuthorityContractTest.kt`

**Interfaces:**
- Authoritative recurring spend/service count comes from `FinancialHomeResult` in Ready/Partial state.
- Room invoice totals remain bill-list/cache utilities only.

- [ ] Write failing contract tests showing empty Room during Recovering is not rendered as zero recurring spend/service count.
- [ ] Remove or isolate any `map { it ?: 0.0 }` paths used by primary financial truth surfaces.
- [ ] Keep legacy/local totals only where explicitly labelled local/cache data.
- [ ] Run tests to GREEN and commit `fix(android): preserve unknown financial state until authoritative sync`.

---

### Task 5: Gate B exact-SHA verification

**Files:** no new production files expected.

- [ ] Re-check Stream A/B/C and confirm Gate A evidence still points to the backend used by this Android candidate.
- [ ] Run fresh Android unit tests and lint on the exact candidate SHA.
- [ ] Build an internal candidate only for recovery verification; do not call it product-approved.
- [ ] Install with app data cleared/fresh install.
- [ ] Confirm Gmail remains connected without re-consent.
- [ ] Confirm recognized bills are reconstructed from server/Gmail evidence.
- [ ] Confirm a second refresh does not duplicate bills.
- [ ] Confirm Home/Savings state updates after recovery without app restart.
- [ ] Record Ready/Partial/failure evidence in Issue #48.

## Gate B Definition of Done

A fresh install against the Gate A backend recovers the Gmail invoice projection, refreshes Financial Home afterward, reaches one explicit synchronized state, and never reports an empty local cache as a known financial zero.