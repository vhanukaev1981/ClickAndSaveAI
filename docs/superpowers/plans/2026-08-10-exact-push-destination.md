# Exact Savings Push Destination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a verified-savings push open the exact currently valid `Opportunity -> Offer` pair instead of only opening the Savings tab.

**Architecture:** Reuse the backend payload that already sends `type`, `opportunityId`, and `offerId`. Android converts only allowlisted payload fields into a typed navigation target, carries them through foreground notification PendingIntents and activity intents, stores a one-shot savings target in `MainViewModel`, and lets `ProvidersScreen` validate the pair against fresh `FinancialHomeResult` before focusing it. A stale/mismatched pair never auto-selects a replacement offer.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, Firebase Cloud Messaging, Android PendingIntent, existing Android/JVM unit and source-guard tests.

## Global Constraints

- Keep Stream A locked; this work stays stacked/queued and must not merge to Core before staging E2E correction cycle #2.
- No backend ranking, savings arithmetic, commerce lifecycle, Gmail, Firestore rules or provider payload changes.
- The backend remains authoritative for `opportunityId` and `offerId`; Android must never infer a replacement offer.
- Only allowlisted push types can navigate.
- A verified-savings push requires both nonblank `opportunityId` and `offerId` for exact focus.
- If the exact pair is stale or mismatched, show current Savings state and a truthful stale-message; do not claim the old offer is still valid.
- Foreground notification PendingIntents must not overwrite the routing extras of another savings opportunity.
- Do not copy arbitrary FCM data into an Activity intent; copy only known routing keys.
- Preserve private notification visibility and existing `NEW_INVOICE` observed-bills refresh behavior.

---

### Task 1: Typed Push Navigation Contract

**Files:**
- Modify: `app/src/main/java/com/example/PushNavigationPolicy.kt`
- Modify: `app/src/test/java/com/example/PushNavigationPolicyTest.kt`

**Interfaces:**
- Produces: `PushNavigationTarget(tab: Int, opportunityId: String? = null, offerId: String? = null)`.
- Produces: `navigationTargetForPush(type: String?, opportunityId: String?, offerId: String?): PushNavigationTarget?`.
- Produces allowlisted extras `PUSH_OPPORTUNITY_ID_EXTRA` and `PUSH_OFFER_ID_EXTRA`.

- [ ] **Step 1: Write failing pure-function tests**

Add tests proving:
```kotlin
assertEquals(PushNavigationTarget(tab = 1), navigationTargetForPush("NEW_INVOICE", null, null))
assertEquals(PushNavigationTarget(tab = 0), navigationTargetForPush("PUSH_TEST", null, null))
assertEquals(
    PushNavigationTarget(tab = 2, opportunityId = "opp-1", offerId = "offer-1"),
    navigationTargetForPush("VERIFIED_SAVINGS_OPPORTUNITY", "opp-1", "offer-1")
)
assertNull(navigationTargetForPush("VERIFIED_SAVINGS_OPPORTUNITY", "", "offer-1"))
assertNull(navigationTargetForPush("VERIFIED_SAVINGS_OPPORTUNITY", "opp-1", ""))
assertNull(navigationTargetForPush("UNKNOWN", "opp-1", "offer-1"))
```

- [ ] **Step 2: Run Android unit tests and verify RED**

Run through CI on the tests-only commit. Expected: compilation/test failure because `PushNavigationTarget` / `navigationTargetForPush` do not exist.

- [ ] **Step 3: Implement the minimal typed policy**

Keep existing tab mapping semantics for invoice/test. For verified savings, return a target only when both exact IDs are nonblank after trimming.

- [ ] **Step 4: Run unit tests and verify GREEN**

Expected: all navigation policy tests pass.

- [ ] **Step 5: Commit**

Commit only the policy and its tests.

---

### Task 2: Preserve Exact IDs Through Foreground Notification PendingIntent

**Files:**
- Modify: `app/src/main/java/com/example/ClickAndSaveMessagingService.kt`
- Create or modify: `app/src/test/java/com/example/PushNavigationSourceGuardTest.kt`

**Interfaces:**
- Consumes: `navigationTargetForPush(...)` and the two allowlisted ID extras from Task 1.
- Produces: an `Intent` containing only the recognized `type`, `opportunityId`, and `offerId` routing values.

- [ ] **Step 1: Add failing source guards**

Require the service to:
```text
read message.data["opportunityId"]
read message.data["offerId"]
pass them to navigationTargetForPush
put only PUSH_TYPE_EXTRA / PUSH_OPPORTUNITY_ID_EXTRA / PUSH_OFFER_ID_EXTRA
```
Also require savings PendingIntent request codes to depend on the exact pair rather than a single constant `102`.

- [ ] **Step 2: Verify RED in CI**

Expected: source-guard failure because the current foreground path passes only `pushType`.

- [ ] **Step 3: Implement minimal foreground routing**

Change `showNotification` to receive the relevant data map or typed target. Copy only known values. Derive a stable positive request code for savings from `opportunityId|offerId`; keep fixed codes for Home/Bills.

- [ ] **Step 4: Verify tests GREEN**

Ensure `NEW_INVOICE` still triggers observed-bills refresh and notification privacy/channel behavior remains unchanged.

- [ ] **Step 5: Commit**

Commit service + source guards.

---

### Task 3: One-Shot Savings Push Target in MainViewModel / MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Modify: `app/src/test/java/com/example/PushNavigationSourceGuardTest.kt`

**Interfaces:**
- Produces: `SavingsPushTarget(opportunityId: String, offerId: String)` in ViewModel state.
- Produces: `setSavingsPushTarget(...)` and `clearSavingsPushTarget()`.
- MainActivity consumes typed intent extras, requires authenticated Firebase user, routes to tab 2 and stores the exact pair.

- [ ] **Step 1: Add failing tests/guards**

Require Activity to:
```text
parse type + opportunityId + offerId through navigationTargetForPush
set the exact savings target before/with tab navigation
remove all consumed routing extras
ignore malformed/unknown targets
```
Require ViewModel to expose a typed one-shot target, not raw arbitrary Intent data.

- [ ] **Step 2: Verify RED**

Expected: tests fail because only `type` is currently consumed.

- [ ] **Step 3: Implement minimal state + Activity bridge**

Do not persist the target to disk. It is navigation intent only. Keep the authentication gate already present in `applyPushDestination`.

- [ ] **Step 4: Verify GREEN**

Run unit/source tests.

- [ ] **Step 5: Commit**

Commit Activity/ViewModel bridge.

---

### Task 4: Validate and Focus Exact Opportunity/Offer in Savings

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/ProvidersScreen.kt`
- Create: `app/src/main/java/com/example/ui/SavingsPushTargetPolicy.kt` if a pure matcher keeps the screen simple.
- Create: `app/src/test/java/com/example/SavingsPushTargetPolicyTest.kt`
- Modify: `app/src/test/java/com/example/CustomerUiSourceGuardTest.kt` only if an existing source guard needs the new contract.

**Interfaces:**
- Consumes: pending `SavingsPushTarget` and current `FinancialHomeResult.opportunities`.
- Produces pure matching result: exact current pair, stale/missing pair, or no push target.

- [ ] **Step 1: Write failing pure matching tests**

Cover:
```text
exact opportunity ID + exact matched offer ID -> FOCUSED
same opportunity but different current offer ID -> STALE
missing opportunity -> STALE
no push target -> NONE
```

- [ ] **Step 2: Verify RED**

Expected: missing matcher/state.

- [ ] **Step 3: Implement matcher and presentation behavior**

When exact:
- put the matched opportunity first in the rendered list;
- show a small truthful marker such as `ההצעה מההתראה` on that card;
- clear the ViewModel one-shot target after the screen captures it locally.

When stale:
- clear the one-shot target;
- do not focus another offer;
- show a customer-safe message: `ההצעה מההתראה השתנתה. מציגים את ההצעות העדכניות.`

Do not modify savings numbers, `ACTION_STARTED`, consent, revalidation, or `acceptSavingsOpportunity`.

- [ ] **Step 4: Verify GREEN**

Run Android unit tests and source guards.

- [ ] **Step 5: Commit**

Commit matcher + Savings presentation only.

---

### Task 5: Full Same-SHA Verification and PR Evidence

**Files:**
- Update PR body / Issue #9 only after CI evidence exists.

**Interfaces:**
- Consumes all earlier tasks.
- Produces a queued, same-SHA green checkpoint; no merge to Core.

- [ ] **Step 1: Run full CI on exact HEAD**

Require:
```text
backend tests: success
Android unit tests: success
lint: success
debug APK: success
staging signing verification: success
artifact upload: success
release APK: success
```

- [ ] **Step 2: Inspect the run result and artifact metadata**

Do not claim completion while any step is queued/in-progress.

- [ ] **Step 3: Re-read Issue #9 acceptance criteria**

Confirm:
- backend continues to supply exact IDs;
- foreground path preserves exact IDs;
- activity consumes only allowlisted typed routing;
- Savings validates exact current Opportunity/Offer pair;
- stale pair never silently selects a replacement;
- existing invoice/test destinations still work;
- App Check/Auth and action/consent contracts are untouched.

- [ ] **Step 4: Update PR/Issue with RED->GREEN evidence**

Keep PR Draft/queued until Stream A E2E correction cycle #2 passes.
