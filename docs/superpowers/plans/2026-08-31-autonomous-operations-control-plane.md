# Autonomous Operations Control Plane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish an end-to-end autonomous operations control plane for Click & Save AI across GitHub, Google Cloud/Firebase, Google Play Internal Testing, Vercel, Supabase, Make, and related operational systems, while retaining explicit guardrails against unintended public-production actions.

**Architecture:** GitHub Actions remains the source-controlled orchestrator. Privileged cloud access uses GitHub OIDC to Google Cloud Workload Identity Federation with bounded service-account impersonation, provider-native secret stores, exact-SHA release traceability, API-first execution, and browser automation only as a fallback for UI-only operations. Google Play automation is authorized through Internal Testing only by default.

**Tech Stack:** GitHub Actions, GitHub OIDC, Google Cloud IAM/WIF, Firebase, Google Play Developer API, Node.js 22, Bash, Android/Gradle, Vercel, Supabase, Make.

**Spec:** `docs/superpowers/specs/2026-08-31-autonomous-operations-control-plane-design.md`

## Global Constraints

- Repository: `vhanukaev1981/ClickAndSaveAI`.
- Privileged release source must be an exact 40-character commit SHA.
- Routine cloud auth must use short-lived OIDC/WIF where supported.
- Never commit or log long-lived secrets.
- Google Play autonomous publishing target is Internal Testing only.
- Internal Testing authorization must never authorize Google Play Production/Open/Closed tracks.
- Production capabilities remain independently gated.
- Bounded technical blockers may be repaired autonomously only when reversible/reviewable and testable.

---

## File Structure

- Modify `.github/workflows/production-release.yml` — exact-SHA candidate, WIF, Play Internal Testing, evidence, and independent authorization gates.
- Modify `.github/workflows/android-ci.yml` — ensure final CI emits stable required checks for exact-SHA release gating.
- Create `.github/workflows/autonomous-ops-preflight.yml` — read-only control-plane readiness checks across configured providers.
- Create `.github/workflows/autonomous-release-dispatch.yml` — safe source-controlled dispatch shim for release invocation when direct connector dispatch is unavailable.
- Modify `functions/test/googlePlayInternalTestingWorkflow.test.js` — Play release contract tests.
- Create `functions/test/autonomousOpsPreflightWorkflow.test.js` — preflight workflow contract tests.
- Create `functions/test/autonomousReleaseDispatchWorkflow.test.js` — dispatch boundary contract tests.
- Create `scripts/autonomous-ops-preflight.mjs` — local/static validation for required workflow inputs, variables, and policy boundaries.
- Create `scripts/release-evidence-validate.mjs` — validate exact-SHA, track, artifact identity, and authorization evidence.
- Create `docs/operations/autonomous-control-plane-runbook.md` — operator/audit/rollback runbook.

### Task 1: Inventory and encode current control-plane assumptions

**Files:**
- Create: `scripts/autonomous-ops-preflight.mjs`
- Test: `functions/test/autonomousOpsPreflightWorkflow.test.js`

**Interfaces:**
- Consumes: repository workflow YAML and documented expected repository/project identifiers.
- Produces: deterministic exit status and concise diagnostics for missing or policy-violating configuration.

- [ ] **Step 1: Write the failing contract test**

Create `functions/test/autonomousOpsPreflightWorkflow.test.js` asserting that `.github/workflows/autonomous-ops-preflight.yml` exists and that it invokes `node scripts/autonomous-ops-preflight.mjs` without any deployment command.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd functions && node --test test/autonomousOpsPreflightWorkflow.test.js`

Expected: FAIL because the workflow/script do not yet exist.

- [ ] **Step 3: Implement the minimal preflight script**

Create `scripts/autonomous-ops-preflight.mjs` that:

```js
import fs from 'node:fs';

const requiredFiles = [
  '.github/workflows/android-ci.yml',
  '.github/workflows/production-release.yml',
];

const missing = requiredFiles.filter((path) => !fs.existsSync(path));
if (missing.length) {
  console.error(`Missing required control-plane files: ${missing.join(', ')}`);
  process.exit(1);
}

const release = fs.readFileSync('.github/workflows/production-release.yml', 'utf8');
const requiredTokens = [
  'authorize_google_play_internal_testing',
  'PUBLISH_GOOGLE_PLAY_INTERNAL_TESTING',
  'NO_DEPLOY',
  'source_sha',
];

for (const token of requiredTokens) {
  if (!release.includes(token)) {
    console.error(`Production release policy token missing: ${token}`);
    process.exit(1);
  }
}

if (/track:\s*production/i.test(release)) {
  console.error('Google Play production track must not be present in autonomous release workflow.');
  process.exit(1);
}

console.log('Autonomous operations control-plane static preflight passed.');
```

- [ ] **Step 4: Add the read-only preflight workflow**

Create `.github/workflows/autonomous-ops-preflight.yml` with `workflow_dispatch` and `pull_request` triggers, `contents: read`, Node 22 setup, checkout, and `node scripts/autonomous-ops-preflight.mjs`. Do not request `id-token: write` in this first task.

- [ ] **Step 5: Run focused and backend tests**

Run:

```bash
node scripts/autonomous-ops-preflight.mjs
cd functions
node --test test/autonomousOpsPreflightWorkflow.test.js
npm test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add scripts/autonomous-ops-preflight.mjs .github/workflows/autonomous-ops-preflight.yml functions/test/autonomousOpsPreflightWorkflow.test.js
git commit -m "ci: add autonomous operations preflight"
```

### Task 2: Harden the exact-SHA release contract

**Files:**
- Modify: `.github/workflows/production-release.yml`
- Modify: `functions/test/googlePlayInternalTestingWorkflow.test.js`

**Interfaces:**
- Consumes: `source_sha`, explicit release authorization inputs, successful final CI on exact SHA.
- Produces: signed candidate and release evidence tied to exact SHA.

- [ ] **Step 1: Extend the failing test first**

Add assertions that `production-release.yml`:

```js
assert.match(workflow, /source_sha/);
assert.match(workflow, /\^\[0-9a-f\]\{40\}\$/);
assert.match(workflow, /head_sha=\$SOURCE_SHA/);
assert.match(workflow, /PUBLISH_GOOGLE_PLAY_INTERNAL_TESTING/);
assert.doesNotMatch(workflow, /track:\s*production/i);
```

Also assert the Internal Testing job does not declare `environment: production` if that would cause a protected-environment approval unrelated to Internal Testing.

- [ ] **Step 2: Run the focused test**

Run: `cd functions && node --test test/googlePlayInternalTestingWorkflow.test.js`

Expected: RED for any unmet contract.

- [ ] **Step 3: Make the minimal workflow changes**

Update `.github/workflows/production-release.yml` so exact-SHA validation, final-CI lookup, candidate build/signing, and Internal Testing publication all operate on `inputs.source_sha`; keep Firebase deploy, WIF proof, IAM bootstrap, and probes independent.

- [ ] **Step 4: Verify no production-track path exists**

Run:

```bash
node scripts/autonomous-ops-preflight.mjs
cd functions
node --test test/googlePlayInternalTestingWorkflow.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/production-release.yml functions/test/googlePlayInternalTestingWorkflow.test.js
git commit -m "ci: harden exact sha internal testing release gate"
```

### Task 3: Add an autonomous release dispatch shim

**Files:**
- Create: `.github/workflows/autonomous-release-dispatch.yml`
- Test: `functions/test/autonomousReleaseDispatchWorkflow.test.js`

**Interfaces:**
- Consumes: exact `source_sha` and a fixed Internal Testing authorization mode.
- Produces: a repository-controlled invocation of `production-release.yml` without enabling production-track publication.

- [ ] **Step 1: Write the failing dispatch contract test**

The test must assert that the new workflow:

```js
assert.match(workflow, /workflow_dispatch/);
assert.match(workflow, /source_sha/);
assert.match(workflow, /PUBLISH_GOOGLE_PLAY_INTERNAL_TESTING/);
assert.match(workflow, /NO_DEPLOY/);
assert.doesNotMatch(workflow, /DEPLOY_FIREBASE_PRODUCTION/);
assert.doesNotMatch(workflow, /track:\s*production/i);
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd functions && node --test test/autonomousReleaseDispatchWorkflow.test.js`

Expected: FAIL because the workflow does not exist.

- [ ] **Step 3: Implement the shim with least privilege**

Create `.github/workflows/autonomous-release-dispatch.yml` that validates `source_sha`, verifies it equals current `main` when policy requires current-main-only release, and invokes the Production Release Gate through a supported GitHub mechanism. Preferred implementation order:

1. reusable workflow call if `production-release.yml` is refactored to `workflow_call` safely;
2. GitHub API dispatch using `actions: write` and `gh api` if repository token permissions permit;
3. keep browser automation as external fallback if GitHub rejects both repository-native paths.

The selected implementation must hard-code these effective inputs:

```text
confirm_environment=CLICKANDSAVEAI_PRODUCTION
authorize_firebase_deploy=NO_DEPLOY
authorize_google_play_internal_testing=PUBLISH_GOOGLE_PLAY_INTERNAL_TESTING
authorize_wif_auth_proof=NO_WIF_PROOF
authorize_production_bootstrap=NO_BOOTSTRAP
authorize_3f_metadata_probe=NO_3F_PROBE
authorize_3f_external_authority_probe=NO_3F_EXTERNAL_PROBE
authorize_3f_service_state_probe=NO_3F_SERVICE_STATE_PROBE
authorize_3f_firebase_iam_permission_probe=NO_3F_FIREBASE_IAM_PERMISSION_PROBE
```

- [ ] **Step 4: Run focused tests and YAML/static checks**

Run:

```bash
cd functions
node --test test/autonomousReleaseDispatchWorkflow.test.js
node --test test/googlePlayInternalTestingWorkflow.test.js
cd ..
node scripts/autonomous-ops-preflight.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/autonomous-release-dispatch.yml functions/test/autonomousReleaseDispatchWorkflow.test.js
git commit -m "ci: add bounded autonomous internal release dispatch"
```

### Task 4: Normalize Google Cloud WIF and service-account boundaries

**Files:**
- Modify: `.github/workflows/production-release.yml`
- Modify/Create: `docs/operations/autonomous-control-plane-runbook.md`
- Test: `functions/test/googlePlayInternalTestingWorkflow.test.js`

**Interfaces:**
- Consumes: repository/environment variables for WIF provider and bounded service-account identities.
- Produces: short-lived Google credentials for only the selected job/capability.

- [ ] **Step 1: Add failing contract assertions**

Assert that privileged Google jobs use `google-github-actions/auth` with Workload Identity Federation variables and do not require a service-account JSON key for routine execution.

- [ ] **Step 2: Run focused test and verify RED where applicable**

Run: `cd functions && node --test test/googlePlayInternalTestingWorkflow.test.js`

- [ ] **Step 3: Update workflow auth boundaries**

For each privileged Google capability, configure only the minimum required job permissions, including `id-token: write` only on WIF-authenticated jobs. Pin repository/ref/workflow expectations in the existing guard steps before requesting credentials.

- [ ] **Step 4: Document exact external IAM bindings**

In `docs/operations/autonomous-control-plane-runbook.md`, record the required identities and the exact purpose of each binding. Do not place secret values in this document.

- [ ] **Step 5: Validate**

Run backend tests plus the static preflight. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/production-release.yml functions/test/googlePlayInternalTestingWorkflow.test.js docs/operations/autonomous-control-plane-runbook.md
git commit -m "ci: normalize workload identity release boundaries"
```

### Task 5: Validate Google Play Internal Testing evidence

**Files:**
- Create: `scripts/release-evidence-validate.mjs`
- Modify: `.github/workflows/production-release.yml`
- Test: `functions/test/googlePlayInternalTestingWorkflow.test.js`

**Interfaces:**
- Consumes: `identity.txt`/publishing evidence generated by the release job.
- Produces: non-zero exit status when SHA, track, package, version, or publication boundary is inconsistent.

- [ ] **Step 1: Write failing tests for required evidence tokens**

Require release evidence to include:

```text
source_sha
application_id
version_code
version_name
aab_sha256
production_gate_run_id
google_play_track=internal
google_play_published=true|false
```

- [ ] **Step 2: Implement validator**

Create `scripts/release-evidence-validate.mjs` to parse `key=value` evidence, reject missing fields, reject any track other than `internal`, validate the SHA format, and require the expected application ID `com.aistudio.clickandsaveai.app`.

- [ ] **Step 3: Wire validation into the release job**

Run the validator before uploading final release evidence.

- [ ] **Step 4: Test**

Run focused test, backend test suite, and static preflight. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/release-evidence-validate.mjs .github/workflows/production-release.yml functions/test/googlePlayInternalTestingWorkflow.test.js
git commit -m "ci: validate internal testing release evidence"
```

### Task 6: Add autonomous diagnosis and bounded retry rules

**Files:**
- Modify: `docs/operations/autonomous-control-plane-runbook.md`
- Modify: `.github/workflows/autonomous-ops-preflight.yml`

**Interfaces:**
- Consumes: GitHub Actions job status/logs and known transient/fixable failure categories.
- Produces: deterministic guidance for retry vs code fix vs user-only action.

- [ ] **Step 1: Encode failure classes in the runbook**

Document three categories:

```text
TRANSIENT: runner/network/provider temporary failure -> retry bounded failed job
BOUNDED_TECHNICAL: workflow/code/config contract failure -> branch, test-first fix, PR, CI, merge
USER_ONLY: MFA, legal terms, billing verification, provider console ownership/identity confirmation -> stop and request user action
```

- [ ] **Step 2: Add non-mutating diagnostic output to preflight**

Have the preflight workflow emit the exact source SHA, configured target environment/track, and which external credential classes are present as boolean/redacted readiness signals only.

- [ ] **Step 3: Verify no secret values are printed**

Review the workflow and script for direct `echo`/printing of secret-bearing variables. Run repository secret audit if available.

- [ ] **Step 4: Commit**

```bash
git add docs/operations/autonomous-control-plane-runbook.md .github/workflows/autonomous-ops-preflight.yml scripts/autonomous-ops-preflight.mjs
git commit -m "docs: define autonomous remediation and retry policy"
```

### Task 7: Validate adjacent provider autonomy

**Files:**
- Modify: `docs/operations/autonomous-control-plane-runbook.md`

**Interfaces:**
- Consumes: connected provider capabilities for Vercel, Supabase, Make, Gmail/Drive as operational dependencies.
- Produces: explicit capability matrix and fallback route per provider.

- [ ] **Step 1: Record provider capability matrix**

For each provider, record whether ChatGPT connector/API access supports read, write, deployment/execute, secrets/config changes, and whether browser fallback is required.

- [ ] **Step 2: Record authorization boundary**

State that provider-specific Full Access permits only actions exposed by that connector and does not create missing API endpoints.

- [ ] **Step 3: Commit**

```bash
git add docs/operations/autonomous-control-plane-runbook.md
git commit -m "docs: map autonomous provider capabilities"
```

### Task 8: End-to-end Internal Testing release validation

**Files:**
- No new source files unless a bounded blocker requires a test-first fix.

**Interfaces:**
- Consumes: green `main`, exact current `main` SHA, autonomous dispatch path, WIF, Play publishing credentials.
- Produces: installable Google Play Internal Testing release and evidence.

- [ ] **Step 1: Verify current main and required CI**

Confirm all required branch checks are green on the exact current `main` SHA.

- [ ] **Step 2: Invoke the bounded Internal Testing release path**

Use the autonomous dispatch shim or supported connector/API path with only Google Play Internal Testing authorization enabled.

- [ ] **Step 3: Monitor every release job**

Inspect status, job steps, and logs. Retry transient failures only. For bounded technical blockers, create a test-first fix branch/PR and merge only after required CI is green.

- [ ] **Step 4: Verify release evidence**

Confirm exact SHA, versionCode/versionName, application ID, AAB hash, `google_play_track=internal`, and successful publication status.

- [ ] **Step 5: Confirm install/test readiness**

Verify the release reached Google Play Internal Testing and report readiness only after the publication job and evidence are successful.

- [ ] **Step 6: Confirm production remained untouched**

Verify no Google Play Production track publication and no Firebase Production deployment occurred during this run.

## Self-Review

- Spec coverage: all design sections map to tasks 1-8.
- Placeholder scan: no TBD/TODO/future-fill instructions remain.
- Type/interface consistency: `source_sha`, Internal Testing authorization phrase, and evidence keys are consistent across tasks.
- Safety boundary: Google Play Production remains excluded from the autonomous path.
