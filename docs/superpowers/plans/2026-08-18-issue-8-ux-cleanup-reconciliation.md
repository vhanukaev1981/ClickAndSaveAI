# Issue #8 UX Cleanup Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconcile the current five-destination Android V2 UI with Issue #8 by removing customer-visible implementation diagnostics, preserving internal contracts, proving every visible CTA is real or honestly unavailable, and locking Gmail first-connection behavior.

**Architecture:** Keep the existing `selectedTab`/Compose structure and authoritative financial state model unchanged. Changes are limited to customer-facing copy and presentation of backend-originated enums/errors in the active V2 screens plus Gmail authorization copy in `MainActivity`; internal repository/API/lead identifiers remain compatible. Add a source-level acceptance guard test alongside the existing architecture guards.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, Android Gradle build; canonical CI from `.github/workflows/android-ci.yml`.

**Spec:** GitHub Issue #8 and the Master Control execution task authorized against `main` SHA `fdd6935840504d7d30982d9480a5aaca8d28145f`.

## Global Constraints

- Exact authorized base: `fdd6935840504d7d30982d9480a5aaca8d28145f`.
- Working branch: `agent/issue-8-ux-cleanup-reconciliation`.
- Preserve internal API/domain identifiers when they are not customer-visible.
- Preserve fail-closed Gmail/OAuth behavior and potential-vs-realized savings truth semantics.
- No Production deployment, Play publication, IAM/WIF mutation, App Check enforcement mutation, signing mutation, or secret disclosure.
- No unrelated refactor and no direct commit to `main`.

---

### Task 1: Lock Issue #8 acceptance in Android unit guards

**Files:**
- Create: `app/src/test/java/com/example/Issue8UxAcceptanceGuardTest.kt`

**Interfaces:**
- Consumes: current active V2 source files and `MainActivity.kt`.
- Produces: regression guards for technical copy, lead terminology, CTA wiring, and Gmail onboarding visibility.

- [ ] **Step 1: Add a failing guard for known customer-visible technical diagnostics**

Assert the baseline anti-patterns (`Firebase/OAuth`, raw Gmail scope/server-code language, raw activity ledger/status/destination fields, raw offer verification enums, server/session/Push lifecycle prose, and raw source coverage) are absent from active customer UI.

- [ ] **Step 2: Confirm the guard fails against the authorized baseline for the known anti-patterns**

Canonical command when an executable checkout is available: `gradle --no-daemon testDebugUnitTest --stacktrace`.

- [ ] **Step 3: Add passing structural guards for current customer-visible lead terminology, CTA wiring, and Gmail onboarding state**

Require no `lead`/`ליד` text in active V2 screens, no empty/TODO `onClick`, real navigation/retry/provider/privacy handlers, and Gmail onboarding only in unauthenticated/disconnected branches while connected profile state hides the connect action.

### Task 2: Replace technical diagnostics with consumer language

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Modify: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/InvoicesScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/ProvidersScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/ActivityScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/ProfileScreen.kt`

**Interfaces:**
- Consumes: existing `FinancialSyncState`, `FinancialOpportunity`, activity/invoice DTOs and existing ViewModel/repository callbacks.
- Produces: consumer-language presentation without changing backend contracts, action semantics, navigation, or security state.

- [ ] **Step 1: Make Gmail authorization errors consumer-safe**

Keep authorization behavior unchanged while replacing configuration/scope/server-code details with normal retry/reconnect guidance.

- [ ] **Step 2: Hide raw source/status enums from financial screens**

Translate verification and lifecycle summaries into human-readable states; unknown values fail closed to neutral consumer wording rather than rendering raw enum/server values.

- [ ] **Step 3: Remove infrastructure prose from Profile and Activity**

Retain distinct privacy operations and truthful states while removing `server`, `session`, `Push`, `watch`, `ledger`, raw destination/status, and similar implementation detail from customer-visible copy.

- [ ] **Step 4: Preserve financial hierarchy**

Keep the existing verified/fresh/eligible checks and the explicit separation between potential and realized savings unchanged.

### Task 3: Verify and publish review evidence

**Files:**
- No production file additions beyond Tasks 1-2.

**Interfaces:**
- Consumes: final branch tip and GitHub pull-request CI.
- Produces: green canonical verification, Issue #8 evidence comment, and Draft PR targeting `main`.

- [ ] **Step 1: Static acceptance audit**

Search active customer UI for forbidden technical wording, `lead` terminology, unresolved active CTAs, and raw internal status interpolation.

- [ ] **Step 2: Run canonical Android gates**

Use CI authority: `gradle --no-daemon testDebugUnitTest --stacktrace`, `gradle --no-daemon lintDebug --stacktrace`, `gradle --no-daemon assembleDebug --stacktrace`, then `gradle --no-daemon assembleRelease bundleRelease --stacktrace`.

- [ ] **Step 3: Run security/diff audit**

Verify no private keys, client secrets, tokens, Production identity changes, workflow changes, or secret values were introduced.

- [ ] **Step 4: Record Issue #8 evidence and open Draft PR**

Record exact base/tip, changed files, CTA/Gmail reconciliation, test/lint/build results and safety statement; keep Issue #8 open and do not merge.
