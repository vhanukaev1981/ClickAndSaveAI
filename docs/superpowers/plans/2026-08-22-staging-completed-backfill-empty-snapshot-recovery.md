# Staging Completed-Backfill Empty Snapshot Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover a completed Gmail baseline whose authoritative `gmailInvoices` snapshot is empty/stale without turning normal refresh or parser upgrades into repeated six-month Gmail rescans, while making Android report scan truth accurately.

**Architecture:** Execute in two governed phases. Phase 1 adds only a self-scoped, read-only sanitized recovery-state diagnostic so the existing connected Staging user can prove connection/backfill/parser/import/snapshot state without exposing message, attachment, credential, email, or invoice content. Phase 2 uses that evidence to prefer authoritative reconstruction from stored `gmailMessageImports`; only when stored evidence is insufficient, an explicit version-gated Staging-only controlled Gmail recovery may execute. Normal `scanGmailInvoices` remains incremental after the initial backfill and never invokes the controlled Gmail backfill path.

**Tech Stack:** Firebase Functions v2 / Node.js 22, Firestore Admin SDK, Gmail readonly API, Kotlin/Jetpack Compose Android, Firebase callable Functions, Node `node:test`, Gradle/JUnit.

**Spec:** Owner-provided `CLICK & SAVE AI — STAGING COMPLETED-BACKFILL EMPTY SNAPSHOT RECOVERY` mission, 2026-08-22.

## Global Constraints

- Repository: `vhanukaev1981/ClickAndSaveAI`.
- Backend branch/PR: `agent/gmail-recurring-bills-filter` / PR #99; authorized starting head `b0ec78749acf1ec0b3d264a7cb964512c03ac9b7`.
- Android branch/PR: `agent/v3-gmail-diagnostic-surface` / PR #96; authorized starting head `d10060c4a4b26063b9753d92c6b0665860e3e270`.
- Gmail scope remains exactly `https://www.googleapis.com/auth/gmail.readonly`.
- Never delete `gmailConnections`, encrypted credentials, or `gmailMessageImports` for recovery.
- Never clear `initialBackfillCompleted` to force the ordinary backfill path.
- Normal refresh and parser upgrades must never trigger the six-month Gmail candidate query after initial completion.
- No Production deployment/data mutation, merge, Google Play action, Gmail disconnect/reconnect, or Gmail scope expansion.
- Financial Agent refresh runs exactly once after a successful authoritative recovery.
- Controlled Gmail recovery, if required by measured Staging evidence, is explicit, version-gated, recorded, idempotent, and unavailable from ordinary refresh.

---

### Task 1: Read-only Staging recovery-state diagnostic

**Files:**
- Modify: `functions/src/gmailSyncStatusFunctions.js`
- Test: `functions/test/gmailSyncStatusFunctions.test.js`

**Interfaces:**
- Consumes: authenticated caller UID, `gmailConnections/{uid}`, `users/{uid}/gmailInvoices`, `users/{uid}/gmailMessageImports`, `ACTIVE_GMAIL_PARSER_VERSION`.
- Produces: self-scoped sanitized `recoveryState` containing `initialBackfillCompleted`, `initialBackfillCompletedAt`, `storedParserVersion`, `activeParserVersion`, `authoritativeInvoiceCount`, `gmailMessageImportCount`, bounded parser-version distribution, stored-candidate count, and a truncation flag. It never returns email/message/attachment/token/invoice content.

- [ ] **Step 1: Write failing backend tests**

Add tests that require a sanitizer/collector contract with count-only fields and explicitly forbid content-bearing fields (`email`, `subject`, `body`, `attachment`, `token`, `refreshToken`, provider/amount details).

- [ ] **Step 2: Run backend test to verify RED**

Run: `cd functions && npm test`
Expected: FAIL only on the new diagnostic contract because the recovery-state collector/result does not exist yet.

- [ ] **Step 3: Implement minimal read-only collector**

Use Firestore reads only. Read the caller's connection document, count authoritative invoices, inspect a bounded set of import docs to calculate parser-version distribution and stored candidate count, and return sanitized metadata. Do not mutate Firestore or call Gmail.

- [ ] **Step 4: Run backend tests to verify GREEN**

Run: `cd functions && npm test`
Expected: all tests PASS.

- [ ] **Step 5: Commit diagnostic-only backend phase**

Commit message: `test+feat: add sanitized gmail recovery state diagnostic`

### Task 2: Expose diagnostic truth in the existing V3 diagnostic branch

**Files:**
- Modify: `app/src/main/java/com/example/data/repository/BackendRepository.kt`
- Modify only if needed for existing diagnostic presentation: `app/src/main/java/com/example/ui/screens/ProfileScreen.kt`
- Test: new or existing focused V3 diagnostic/repository contract test under `app/src/test/java/com/example/`

**Interfaces:**
- Consumes: backend `getGmailSyncStatus()` sanitized recovery-state fields.
- Produces: debug/diagnostic-only count metadata sufficient for Master Control to inspect Staging state; no customer financial content or credentials.

- [ ] **Step 1: Write failing Android contract test**

Require decoding of the sanitized recovery-state fields while preserving existing connection truth semantics.

- [ ] **Step 2: Run Android unit test to verify RED**

Run the focused Gradle unit test. Expected: FAIL because the fields are not decoded.

- [ ] **Step 3: Implement minimal decode/presentation**

Extend the result model. If presentation is needed, reuse the existing diagnostic surface and show only count/version metadata on debug builds; do not redesign V3 screens.

- [ ] **Step 4: Run unit tests/lint/assembleDebug**

Expected: PASS.

- [ ] **Step 5: Commit diagnostic-only Android phase**

Commit message: `test+feat: surface sanitized gmail recovery diagnostics`

### Task 3: Measure the real connected Staging account before recovery

**Files:** none.

**Interfaces:**
- Consumes: Phase 1 Staging backend and diagnostic APK.
- Produces: sanitized pre-recovery evidence required by the mission.

- [ ] **Step 1: Require exact-head CI GREEN for diagnostic commits**

Backend and Android heads must pass Android and Backend CI, Production Operations CI, Production Enablement Security CI, and Staging WIF Bootstrap where applicable.

- [ ] **Step 2: Deploy backend diagnostic to Staging only**

Use governed `Deploy Firebase Staging` with the exact backend diagnostic SHA. Do not touch Production.

- [ ] **Step 3: Install fresh diagnostic Staging APK and read the self-scoped state**

Capture only: initial-backfill completion/timestamp presence, stored/active parser versions, authoritative invoice count, import count, parser-version distribution, stored candidate count, truncation state.

- [ ] **Step 4: Decide recovery source from evidence**

If current stored candidates are sufficient under the active recurring policy, proceed with stored-import recovery. Otherwise add the explicit controlled Gmail recovery path.

### Task 4: Stored-import authoritative recovery on ordinary completed refresh

**Files:**
- Modify: `functions/src/gmailReliableScanFunctions.js`
- Modify or create narrow helper: `functions/src/gmailSnapshotRecovery.js`
- Modify only for shared persistence/backfill helpers if needed: `functions/src/gmailScanV5Functions.js`
- Test: `functions/test/block3ReliabilityContract.test.js`
- Test: new `functions/test/gmailCompletedBackfillRecovery.test.js`

**Interfaces:**
- Consumes: completed connection, authoritative snapshot, normalized candidates stored in `gmailMessageImports`, `selectRecurringBills`.
- Produces: `recoveryPerformed`, `recoverySource` (`STORED_IMPORTS` or `NONE`), `recoveredInvoiceCount`, `parserVersion`, `authoritativeInvoiceCount`, plus the existing scan fields.

- [ ] **Step 1: Write RED tests**

Cover:
1. completed + empty authoritative snapshot + stored recurring candidates => rebuild from stored evidence and never call Gmail six-month listing;
2. completed + populated snapshot => return snapshot only;
3. completed + no sufficient stored evidence => return empty snapshot and never call Gmail six-month listing.

- [ ] **Step 2: Verify RED**

Run focused tests; failures must be caused by the missing recovery behavior.

- [ ] **Step 3: Implement minimal stored-import recovery**

Collect normalized stored candidates, apply the current recurring policy, write the replacement snapshot before deleting stale authoritative rows, record recovery metadata, then run Financial Agent exactly once. If no accepted stored evidence exists, perform no Gmail query and return `recoverySource=NONE`.

- [ ] **Step 4: Verify GREEN and full backend suite**

Run `cd functions && npm test`.

- [ ] **Step 5: Commit**

Commit message: `fix: rebuild completed gmail snapshot from stored imports`

### Task 5: Explicit one-time controlled Gmail recovery only if measured evidence requires it

**Files:**
- Modify: `functions/src/gmailScanV5Functions.js`
- Modify/create: `functions/src/gmailSnapshotRecovery.js`
- Modify: `functions/src/entry.js` only if a new callable export is required
- Test: `functions/test/gmailCompletedBackfillRecovery.test.js`

**Interfaces:**
- Consumes: authenticated caller, completed connection, exact recovery version `completed-empty-snapshot-v1`, existing readonly credentials.
- Produces: explicit recovery result with `recoveryPerformed`, `recoverySource=CONTROLLED_GMAIL_BACKFILL`, `recoveredInvoiceCount`, `parserVersion`, `authoritativeInvoiceCount`.

- [ ] **Step 1: Write RED idempotency/gating tests**

Require that ordinary scan cannot reach the controlled Gmail path, recovery requires the exact version, the same version cannot execute twice, and completion metadata is recorded without clearing `initialBackfillCompleted`.

- [ ] **Step 2: Verify RED**

Focused tests fail for missing controlled-recovery mechanism.

- [ ] **Step 3: Implement explicit Staging-only recovery**

Reuse the same bounded six-month candidate query and current parser, but only from the explicit recovery callable/version gate. Keep `initialBackfillCompleted=true`; write `recoveryBackfillVersion`, `recoveryBackfillCompletedAt`, `recoveryReason`, and recovery source after a successful replacement. Preserve readonly scope exactly.

- [ ] **Step 4: Verify GREEN/full backend suite**

Run `cd functions && npm test`.

- [ ] **Step 5: Commit**

Commit message: `fix: add one-time controlled staging gmail recovery`

### Task 6: Android truthful scan result/status

**Files:**
- Modify: `app/src/main/java/com/example/data/repository/BackendRepository.kt`
- Modify: `app/src/main/java/com/example/data/repository/GmailRepository.kt`
- Test: new focused Android Gmail scan truth test/contract under `app/src/test/java/com/example/`

**Interfaces:**
- Consumes: `alreadyCompleted`, `lookback`, `parserVersion`, `scannedPages`, `agentRefreshed`, recovery result fields.
- Produces: truthful `GmailSyncState.Success.message`.

- [ ] **Step 1: Write RED tests**

Require:
- `alreadyCompleted=true && scannedMessages=0 && invoices>0` => saved-baseline message;
- `alreadyCompleted=true && scannedMessages=0 && invoices=0` => empty saved-baseline message;
- only an actual Gmail candidate query that executed and returned zero candidates may show the zero-candidate Gmail message.

- [ ] **Step 2: Verify RED**

Focused Android tests fail on existing `scannedMessages == 0` inference.

- [ ] **Step 3: Implement decode and message projection**

Extend `GmailScanResult` with required truth fields and make message selection use `alreadyCompleted`/`lookback`, not `scannedMessages` alone.

- [ ] **Step 4: Verify Android suite**

Run unit tests, lint, and staging/debug APK assembly.

- [ ] **Step 5: Commit**

Commit message: `fix: make gmail refresh status truthful`

### Task 7: Exact-head validation, Staging recovery, and physical acceptance

**Files:** none unless a defect is found.

- [ ] **Step 1: Require exact-head GREEN**

For final backend and Android SHAs: Android and Backend CI, Production Operations CI, Production Enablement Security CI, Validate Staging WIF Bootstrap if applicable.

- [ ] **Step 2: Deploy final backend SHA to Staging only**

Use the governed Staging deploy workflow. Record exact run ID and success evidence.

- [ ] **Step 3: Execute recovery for the existing connected test user**

First let ordinary refresh attempt stored-import recovery. Invoke the explicit controlled recovery version only if Phase 3 proved stored evidence insufficient. Never disconnect/reconnect Gmail.

- [ ] **Step 4: Build/install fresh Staging APK**

Record artifact name and exact Android SHA.

- [ ] **Step 5: Physical E2E**

Verify Gmail remains connected, recovered invoices appear, Home/Pay reflect authoritative invoices, navigation/restart preserves them, and another refresh does not execute the six-month Gmail query.

- [ ] **Step 6: Final governance verification**

Confirm PRs remain open/draft/unmerged and no Production/Play/scope/disconnect action occurred.
