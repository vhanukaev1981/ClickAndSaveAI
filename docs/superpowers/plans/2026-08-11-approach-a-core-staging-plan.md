# Approach A Core/Staging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove and, if necessary, repair the immutable deployment path for Stream A Core so `clickandsaveai-staging` is demonstrably running the exact Core SHA expected by Android before any new APK is accepted.

**Architecture:** Work from a new branch off `agent/ai-native-financial-core` rather than mutating Stream A directly. Keep deployment proof separate from Android UI. Gate all staging claims on immutable SHA validation plus authenticated smoke evidence for Gmail connection, invoice scan/backfill and Financial Home.

**Tech Stack:** GitHub Actions, Firebase Functions/Firestore, Node.js tests, Google Cloud/Firebase authentication, Kotlin Android client only as a downstream consumer.

## Global Constraints

- Core reference SHA at plan creation: `ac2105098d698df06159f929f41595f91505c855`.
- Staging project: `clickandsaveai-staging`.
- Gmail access remains `gmail.readonly` only.
- Never print secret values or OAuth refresh/access tokens in CI logs.
- Deployment must fail closed on wrong project, wrong SHA, missing credentials, missing Firebase configuration, or failed smoke verification.
- A successful compile/test is not evidence of a successful staging deployment.
- `unknown != 0`; smoke output must distinguish absent/unknown fields from known numeric zero.
- Do not modify Stream B or Stream C in this plan.

---

### Task 1: Create isolated Core recovery branch and capture deployment preconditions

**Files:**
- Modify: `.github/workflows/deploy-staging.yml`
- Test: add `functions/test/stagingDeploymentContract.test.js`

**Interfaces:**
- Consumes: immutable Core source SHA `ac2105098d698df06159f929f41595f91505c855`.
- Produces: a fail-closed deployment workflow contract with explicit staging project and credential-source validation.

- [ ] **Step 1: Create failing deployment-contract tests**

Test the workflow text for all of the following properties:

```js
const fs = require('node:fs');
const text = fs.readFileSync('../.github/workflows/deploy-staging.yml', 'utf8');

expect(text).toContain('clickandsaveai-staging');
expect(text).toContain('workflow_dispatch');
expect(text).toContain('SOURCE_SHA');
expect(text).toMatch(/GCP_WORKLOAD_IDENTITY_PROVIDER|GOOGLE_APPLICATION_CREDENTIALS|FIREBASE_SERVICE_ACCOUNT/);
expect(text).toContain('Verify immutable Core source');
expect(text).toContain('firebase deploy');
expect(text).toContain('firestore:rules,firestore:indexes,functions');
```

Also assert that the workflow does not continue from a missing credential preflight.

- [ ] **Step 2: Run the focused test and record RED**

Run from `functions/`:

```bash
npm test -- --runInBand test/stagingDeploymentContract.test.js
```

Expected: FAIL on one or more missing immutable/credential/deploy guards.

- [ ] **Step 3: Implement the minimum workflow contract**

The workflow must:
1. accept or pin one `SOURCE_SHA`;
2. checkout that exact SHA;
3. compare `git rev-parse HEAD` to `SOURCE_SHA`;
4. verify the SHA is the intended Core lineage before deployment;
5. validate that the target project is exactly `clickandsaveai-staging`;
6. select only an actually configured authentication mechanism;
7. fail before checkout/deploy if required credential configuration is absent;
8. deploy exactly `firestore:rules,firestore:indexes,functions` non-interactively.

Do not invent credential values. If no usable GitHub credential path is configured, the workflow must report that as the blocking condition rather than pretending to deploy.

- [ ] **Step 4: Run the focused contract test to GREEN**

```bash
npm test -- --runInBand test/stagingDeploymentContract.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/deploy-staging.yml functions/test/stagingDeploymentContract.test.js
git commit -m "ci(core): fail closed on immutable staging deployment prerequisites"
```

---

### Task 2: Add a staging smoke contract that reports evidence without secrets

**Files:**
- Create: `scripts/staging-core-smoke.mjs`
- Create: `functions/test/stagingCoreSmokeContract.test.js`
- Modify: `.github/workflows/deploy-staging.yml`

**Interfaces:**
- Consumes: authenticated staging test identity supplied through existing secure CI configuration.
- Produces: one machine-readable smoke summary containing connection/scan/Financial Home evidence, with no tokens or raw Gmail content.

- [ ] **Step 1: Write failing smoke-contract unit tests**

Define pure helpers in `scripts/staging-core-smoke.mjs` that sanitize callable responses into this shape:

```js
{
  projectId: 'clickandsaveai-staging',
  sourceSha: '<40-char sha>',
  gmail: {
    connected: true,
    consentVersion: 'gmail-readonly-v1'
  },
  scan: {
    scannedMessages: 0,
    returnedInvoices: 0,
    importedCount: 0,
    parserVersion: 0,
    agentRefreshed: false
  },
  financialHome: {
    recurringServiceCount: null,
    observedRecurringMonthlySpend: null,
    sourceCoverage: [],
    insightCount: 0,
    opportunityCount: 0
  }
}
```

Use `null`, not `0`, when the backend response omitted a financial field. Unit tests must prove that tokens, email bodies, snippets, message subjects and raw IDs are never serialized into the artifact.

- [ ] **Step 2: Run smoke-contract tests and record RED**

```bash
node --test functions/test/stagingCoreSmokeContract.test.js
```

Expected: FAIL because the helper does not yet exist.

- [ ] **Step 3: Implement authenticated callable execution**

The smoke script must call, in order:
1. `getGmailConnectionStatus`;
2. if connected, `scanGmailInvoices`;
3. after scan returns, `getFinancialHome`.

It must exit non-zero if authentication cannot be established, if a callable fails unexpectedly, or if the response cannot be parsed. A disconnected Gmail account is a truthful smoke result, not a transport failure, but it is insufficient for Gate A on the designated connected test account.

- [ ] **Step 4: Add the smoke step after Firebase deploy**

The workflow must run the smoke only after deploy succeeds and upload the sanitized JSON summary as an artifact. It must never echo bearer tokens, refresh tokens, service-account JSON or raw Gmail data.

- [ ] **Step 5: Run unit tests to GREEN and commit**

```bash
node --test functions/test/stagingCoreSmokeContract.test.js
npm test
```

Then commit:

```bash
git add scripts/staging-core-smoke.mjs functions/test/stagingCoreSmokeContract.test.js .github/workflows/deploy-staging.yml
git commit -m "test(core): add authenticated staging truth smoke"
```

---

### Task 3: Preserve Gmail backfill metadata needed for recovery proof

**Files:**
- Modify if required: `functions/src/gmailScanV5Functions.js`
- Test: `functions/test/gmailScanV5Functions.test.js`

**Interfaces:**
- Consumes: existing `scanGmailInvoices` callable and parser-v5 backfill.
- Produces: stable metadata sufficient to prove recovery: `scannedMessages`, `invoices`, `importedCount`, `lookback`, `parserVersion`, `upgradedMessages`, `agentRefreshed`.

- [ ] **Step 1: Add/confirm failing tests for the complete metadata response**

The test must explicitly assert the response contains:

```js
assert.equal(typeof result.scannedMessages, 'number');
assert.ok(Array.isArray(result.invoices));
assert.equal(typeof result.importedCount, 'number');
assert.equal(result.lookback, '6m');
assert.equal(typeof result.parserVersion, 'number');
assert.equal(typeof result.upgradedMessages, 'number');
assert.equal(typeof result.agentRefreshed, 'boolean');
```

- [ ] **Step 2: Run the focused test**

```bash
node --test functions/test/gmailScanV5Functions.test.js
```

If already GREEN, record that no production change is necessary. Do not change working code just to create a commit.

- [ ] **Step 3: If RED, implement only missing metadata and rerun**

Keep invoice body/subject/snippet out of the callable response.

- [ ] **Step 4: Commit only if production/test code changed**

```bash
git add functions/src/gmailScanV5Functions.js functions/test/gmailScanV5Functions.test.js
git commit -m "fix(core): expose recovery-safe Gmail scan evidence"
```

---

### Task 4: Prove Financial Home unknown/zero semantics at the callable boundary

**Files:**
- Modify if required: `functions/src/financialAgentFunctions.js`
- Test: `functions/test/financialIntelligence.test.js` and/or a focused new callable test.

**Interfaces:**
- Consumes: server-side Gmail invoice projection and Financial Context.
- Produces: `getFinancialHome` whose completed context is distinguishable from absent/failed data by the caller.

- [ ] **Step 1: Add a failing contract test**

Test two separate situations:
1. completed authoritative evaluation with zero recurring services -> numeric zero is valid;
2. unavailable/not-built context -> callable must not silently synthesize the same zero-filled shape as situation 1.

If the existing callable cannot represent situation 2, introduce an explicit status field such as `contextStatus: 'READY' | 'PARTIAL' | 'UNAVAILABLE'` while preserving existing data fields.

- [ ] **Step 2: Run the focused test and record RED/GREEN**

```bash
npm test -- --runInBand test/financialIntelligence.test.js
```

- [ ] **Step 3: Implement the minimum truthful status contract if required**

Do not alter ranking, savings calculations, commission policy or provider matching.

- [ ] **Step 4: Run the full backend test suite**

```bash
npm test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add functions/src/financialAgentFunctions.js functions/test/financialIntelligence.test.js
git commit -m "fix(core): distinguish unavailable financial context from known zero"
```

---

### Task 5: Run immutable staging deploy and Gate A smoke

**Files:**
- No product code changes expected.
- Evidence: GitHub Actions run + sanitized smoke artifact.

**Interfaces:**
- Consumes: final Core recovery branch SHA.
- Produces: Gate A evidence tied to that exact SHA.

- [ ] **Step 1: Re-check PR #7 / Stream A head before deploy**

Verify Stream A still points to the intended base lineage. If it moved, compare changes before proceeding.

- [ ] **Step 2: Run fresh backend tests on the exact candidate SHA**

Expected: full backend suite PASS.

- [ ] **Step 3: Trigger immutable staging deployment**

The run must visibly verify the exact source SHA and project before deployment.

- [ ] **Step 4: Evaluate authenticated smoke evidence**

For the designated already-connected Gmail test account, Gate A requires:
- Gmail reports connected/read-only;
- `scanGmailInvoices` runs successfully and reports real candidate/returned counts;
- `getFinancialHome` runs after the scan;
- no secret/raw Gmail content appears in logs/artifact.

Do not require counts to be positive by assumption. If the mailbox is known to contain many bills but the scan result is zero, Gate A is **not** accepted; open a parser/search diagnosis task using the returned scan metadata.

- [ ] **Step 5: Record Gate A result in Issue #48 and the Core recovery PR**

State separately:
- deployed and verified;
- deployed but smoke failed;
- blocked before deployment;
- unknown.

Never label compile/test-only evidence as deployment proof.

---

### Task 6: Gate A completion review

**Files:**
- Update: `docs/superpowers/plans/2026-08-11-approach-a-core-staging-plan.md` only if actual evidence changes assumptions.

- [ ] **Step 1:** Confirm exact deployed Core SHA.
- [ ] **Step 2:** Confirm exact GitHub Actions run and deploy step success.
- [ ] **Step 3:** Confirm sanitized authenticated smoke result exists.
- [ ] **Step 4:** Confirm no Stream B/C changes were made by this plan.
- [ ] **Step 5:** Only then authorize the Gmail/Android synchronization plan.

## Gate A Definition of Done

Gate A is complete only when the exact candidate Core SHA is proven deployed to `clickandsaveai-staging` and authenticated staging calls prove the Gmail-status -> scan/backfill -> Financial Home chain. If credentials are missing or the user's known invoice-rich mailbox still returns no recognized billing evidence, the gate remains blocked and no new visual APK is accepted.