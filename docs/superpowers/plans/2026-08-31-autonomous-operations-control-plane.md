# Autonomous Operations Control Plane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing autonomous release control plane from Internal Testing to guarded Firebase Production deployment and Google Play Production staged rollout.

**Architecture:** Keep `production-release.yml` as the exact-SHA source-controlled release gate. Firebase Production and Google Play Production receive distinct authorization inputs, WIF-authenticated jobs, version-controlled health policy, fail-closed telemetry checks, staged rollout state, and immutable audit evidence. The existing owner-only Internal Testing issue bridge remains isolated and cannot authorize Firebase Production or Play Production.

**Tech Stack:** GitHub Actions, Node.js 22, Bash, Google Cloud Workload Identity Federation, Firebase CLI/APIs, Google Play Android Publisher API, Android/Gradle.

**Spec:** `docs/superpowers/specs/2026-08-31-autonomous-operations-control-plane-design.md`

## Global Constraints

- Repository is `vhanukaev1981/ClickAndSaveAI`.
- Privileged releases use an exact lowercase 40-character source SHA.
- Routine Google auth uses short-lived OIDC/WIF, not service-account private keys.
- Internal Testing authorization cannot authorize Firebase Production or Google Play Production.
- Google Play Production rollout sequence is `5 -> 20 -> 50 -> 100` percent.
- Production promotion fails closed when required health telemetry is missing or unhealthy.
- Google Play rollback means halt promotion and, when replacement is required, publish a corrected higher `versionCode`; never decrement `versionCode`.
- No secret values may be committed or printed.
- Branch protection and required checks on `main` remain enabled.

---

## File Structure

- Modify `.github/workflows/production-release.yml` — add independent Firebase Production and Play Production authorization/jobs.
- Keep `.github/workflows/agent-internal-testing-dispatch.yml` fail-closed for Internal Testing only.
- Create `.github/workflows/agent-production-release-dispatch.yml` — owner-only exact-main production dispatch with explicit mode.
- Create `config/production-release-policy.json` — rollout percentages and health thresholds.
- Create `scripts/production-health-gate.mjs` — validate normalized health telemetry against policy.
- Create `scripts/production-release-evidence.mjs` — validate/append production deployment and rollout evidence.
- Modify `functions/test/googlePlayInternalTestingWorkflow.test.js` — preserve isolation contract.
- Create `functions/test/productionAutonomyWorkflow.test.js` — production workflow and dispatch contract.
- Create `functions/test/productionHealthGate.test.js` — health policy unit tests.
- Modify `docs/operations/autonomous-control-plane-runbook.md` — deployment, halt, rollback, and provider-only action runbook.

### Task 1: Preserve Internal Testing isolation while adding production authorization inputs

**Files:**
- Modify: `.github/workflows/production-release.yml`
- Modify: `functions/test/googlePlayInternalTestingWorkflow.test.js`
- Create: `functions/test/productionAutonomyWorkflow.test.js`

**Interfaces:**
- Consumes: existing `source_sha`, `confirm_environment`, Internal Testing and Firebase inputs.
- Produces: independent inputs `authorize_google_play_production` and `production_rollout_percent` without changing the Internal Testing bridge.

- [ ] **Step 1: Write failing production contract assertions**

Add a new test that requires:

```js
assert.match(workflow, /authorize_google_play_production:/);
assert.match(workflow, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
assert.match(workflow, /default:\s*NO_PRODUCTION_UPLOAD/);
assert.match(workflow, /production_rollout_percent:/);
```

Extend the existing Internal Testing bridge test with:

```js
assert.doesNotMatch(bridge, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
assert.doesNotMatch(bridge, /authorize_google_play_production/);
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd functions
node --test test/googlePlayInternalTestingWorkflow.test.js test/productionAutonomyWorkflow.test.js
```

Expected: production autonomy test fails because the new production inputs do not exist; the existing Internal Testing contract still passes.

- [ ] **Step 3: Add minimal independent workflow inputs**

Add to `workflow_dispatch.inputs`:

```yaml
authorize_google_play_production:
  description: Type PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED only for a staged Production rollout
  required: true
  default: NO_PRODUCTION_UPLOAD
  type: string
production_rollout_percent:
  description: Requested staged rollout percentage: 5, 20, 50, or 100
  required: true
  default: '5'
  type: string
```

Do not modify `.github/workflows/agent-internal-testing-dispatch.yml` to send either production input; GitHub applies workflow defaults.

- [ ] **Step 4: Re-run focused tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/production-release.yml functions/test/googlePlayInternalTestingWorkflow.test.js functions/test/productionAutonomyWorkflow.test.js
git commit -m "ci: add isolated production rollout authorization"
```

### Task 2: Add version-controlled health policy and fail-closed evaluator

**Files:**
- Create: `config/production-release-policy.json`
- Create: `scripts/production-health-gate.mjs`
- Create: `functions/test/productionHealthGate.test.js`

**Interfaces:**
- Consumes: JSON telemetry file with `crash_rate`, `anr_rate`, `backend_error_rate`, `smoke_ok`, and `telemetry_complete`.
- Produces: exit 0 only when every required signal satisfies policy for the requested promotion.

- [ ] **Step 1: Write failing unit tests**

Test these cases using temporary telemetry JSON:

```js
// healthy => exit 0
{ crash_rate: 0.005, anr_rate: 0.002, backend_error_rate: 0.005, smoke_ok: true, telemetry_complete: true }
// missing telemetry => non-zero
{ crash_rate: 0.005, anr_rate: 0.002, backend_error_rate: 0.005, smoke_ok: true, telemetry_complete: false }
// crash threshold breach => non-zero
{ crash_rate: 0.03, anr_rate: 0.002, backend_error_rate: 0.005, smoke_ok: true, telemetry_complete: true }
```

- [ ] **Step 2: Run tests and verify RED**

Run: `cd functions && node --test test/productionHealthGate.test.js`

Expected: FAIL because policy/evaluator files do not exist.

- [ ] **Step 3: Create policy**

Create:

```json
{
  "rollout_percentages": [5, 20, 50, 100],
  "max_crash_rate": 0.02,
  "max_anr_rate": 0.01,
  "max_backend_error_rate": 0.02,
  "require_smoke_ok": true,
  "require_complete_telemetry": true
}
```

- [ ] **Step 4: Implement evaluator**

`scripts/production-health-gate.mjs` reads policy path and telemetry path from argv, validates every numeric field is finite and non-negative, requires the rollout percentage to be one of the configured percentages, and exits non-zero on missing/unhealthy signals.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `cd functions && node --test test/productionHealthGate.test.js`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add config/production-release-policy.json scripts/production-health-gate.mjs functions/test/productionHealthGate.test.js
git commit -m "ci: add fail closed production health policy"
```

### Task 3: Harden Firebase Production deployment with exact-SHA post-deploy health evidence

**Files:**
- Modify: `.github/workflows/production-release.yml`
- Modify: `functions/test/productionAutonomyWorkflow.test.js`
- Create: `scripts/production-release-evidence.mjs`

**Interfaces:**
- Consumes: `authorize_firebase_deploy=DEPLOY_FIREBASE_PRODUCTION`, exact source SHA, deploy WIF identity, production project ID.
- Produces: Firebase deployment evidence and a post-deploy health gate result; no Play authorization is implied.

- [ ] **Step 1: Add failing assertions**

Require the Firebase deploy job to contain:

```js
assert.match(firebaseJob, /DEPLOY_FIREBASE_PRODUCTION/);
assert.match(firebaseJob, /google-github-actions\/auth@v3/);
assert.match(firebaseJob, /production-health-gate\.mjs/);
assert.match(firebaseJob, /firebase_deployed=true/);
assert.doesNotMatch(firebaseJob, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
```

- [ ] **Step 2: Verify RED**

Run: `cd functions && node --test test/productionAutonomyWorkflow.test.js`

- [ ] **Step 3: Add post-deploy health collection contract**

After Firebase deploy, write normalized telemetry to `$RUNNER_TEMP/firebase-production-health.json`. The initial implementation may use deterministic smoke/service checks already available in the repository; if crash/ANR Play vitals are not yet available for a Firebase-only deployment, mark those signals not applicable in the evidence but require all Firebase/backend signals needed by the Firebase-specific policy path.

- [ ] **Step 4: Add evidence validator/appender**

`production-release-evidence.mjs` accepts an existing `identity.txt`, action name, target, result, and optional rollout percentage; it rejects invalid SHA/package/version metadata and appends only normalized non-secret key/value evidence.

- [ ] **Step 5: Run focused and backend tests**

```bash
cd functions
node --test test/productionAutonomyWorkflow.test.js
npm test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/production-release.yml functions/test/productionAutonomyWorkflow.test.js scripts/production-release-evidence.mjs
git commit -m "ci: gate firebase production deploy on health evidence"
```

### Task 4: Implement Google Play Production staged rollout job

**Files:**
- Modify: `.github/workflows/production-release.yml`
- Modify: `functions/test/productionAutonomyWorkflow.test.js`

**Interfaces:**
- Consumes: successful `production-candidate`, explicit `PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED`, rollout percentage in policy, Internal Testing lineage evidence, dedicated Play publisher WIF identity.
- Produces: Android Publisher edit committed to `tracks/production` with `userFraction = percentage / 100` until 100%, plus rollout evidence.

- [ ] **Step 1: Add failing Play Production assertions**

Require a `google-play-production-staged` job with:

```js
assert.match(playJob, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
assert.match(playJob, /tracks\/production/);
assert.match(playJob, /userFraction/);
assert.match(playJob, /production-health-gate\.mjs/);
assert.match(playJob, /clickandsaveai-play-publisher@click-save-ai-production\.iam\.gserviceaccount\.com/);
```

Also require it to reject rollout percentages outside `5|20|50|100` and require evidence that the candidate lineage reached Internal Testing before the first Production stage.

- [ ] **Step 2: Run focused test and verify RED**

Run: `cd functions && node --test test/productionAutonomyWorkflow.test.js`

- [ ] **Step 3: Implement staged Play API transaction**

Use the existing Android Publisher edit/upload/commit pattern. For Production, update `tracks/production`; for 5/20/50 use staged rollout status and `userFraction`; for 100 use completed/full rollout semantics supported by the API. Never reuse the Internal Testing authorization input.

- [ ] **Step 4: Wire health gate before every promotion**

Before changing an existing Production rollout from one configured percentage to the next, collect normalized telemetry, run:

```bash
node scripts/production-health-gate.mjs config/production-release-policy.json "$RUNNER_TEMP/production-health.json" "$ROLLOUT_PERCENT"
```

and fail before the Play edit if unhealthy or incomplete.

- [ ] **Step 5: Record audit evidence**

Append at least:

```text
google_play_track=production
production_rollout_percent=<5|20|50|100>
production_health_gate=passed
google_play_published=true
```

- [ ] **Step 6: Run focused tests and full backend suite**

```bash
cd functions
node --test test/googlePlayInternalTestingWorkflow.test.js test/productionAutonomyWorkflow.test.js test/productionHealthGate.test.js
npm test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/production-release.yml functions/test/productionAutonomyWorkflow.test.js
git commit -m "ci: add guarded play production staged rollout"
```

### Task 5: Add owner-only autonomous production dispatch without weakening Internal Testing

**Files:**
- Create: `.github/workflows/agent-production-release-dispatch.yml`
- Modify: `functions/test/productionAutonomyWorkflow.test.js`

**Interfaces:**
- Consumes: owner-created issue titled `Agent Production Release` with exact `source_sha`, `mode`, and optional `rollout_percent`.
- Produces: one bounded `production-release.yml` workflow dispatch for either `firebase` or `play-production`; it never enables bootstrap/probes and never combines both modes in one issue.

- [ ] **Step 1: Add failing dispatch assertions**

Require owner identity check, exact current `main` SHA check, strict mode parsing, and these mappings:

```text
mode=firebase -> authorize_firebase_deploy=DEPLOY_FIREBASE_PRODUCTION; Play Internal/Production disabled
mode=play-production -> authorize_google_play_production=PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED; Firebase/Internal disabled
```

Require `rollout_percent` to be one of `5|20|50|100` only for `play-production`.

- [ ] **Step 2: Run focused test and verify RED**

Run: `cd functions && node --test test/productionAutonomyWorkflow.test.js`

- [ ] **Step 3: Implement fail-closed issue dispatch workflow**

Trigger on `issues: [opened]`; require `github.event.issue.user.login == github.repository_owner`, exact title, exact-main SHA, and no unrecognized body keys used for authorization. Dispatch with WIF proof, bootstrap, and 3F probes fixed to their `NO_*` values.

- [ ] **Step 4: Re-run focused tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/agent-production-release-dispatch.yml functions/test/productionAutonomyWorkflow.test.js
git commit -m "ci: add owner only autonomous production dispatch"
```

### Task 6: Document halt, rollback, and user-only boundaries; verify complete contract

**Files:**
- Modify/Create: `docs/operations/autonomous-control-plane-runbook.md`
- Modify: `functions/test/productionAutonomyWorkflow.test.js`

**Interfaces:**
- Consumes: workflow evidence, Play staged rollout state, Firebase deployment history.
- Produces: deterministic recovery instructions and final regression contract.

- [ ] **Step 1: Document Play unhealthy-release handling**

Record: stop promotion immediately; halt staged rollout through Android Publisher when supported; otherwise leave current fraction unchanged; prepare corrected build with higher `versionCode`; never decrement or silently replace version code.

- [ ] **Step 2: Document Firebase unhealthy-deploy handling**

Record: restore provider-supported previous release/version where available; otherwise redeploy the last-known-good exact SHA/configuration; record bad SHA, rollback SHA, reason, and result.

- [ ] **Step 3: Document user-only blockers**

Limit human intervention to provider terms, billing/identity verification, MFA/device confirmation, legal declarations, and Play Console permission grants unavailable through connected APIs.

- [ ] **Step 4: Run complete repository verification**

```bash
cd functions
node --test test/googlePlayInternalTestingWorkflow.test.js test/productionAutonomyWorkflow.test.js test/productionHealthGate.test.js
npm test
cd ..
node scripts/repository-secret-audit.mjs current
node scripts/production-readiness-guard.mjs repository
```

Expected: all tests/guards exit 0 and secret audit reports no blocking findings.

- [ ] **Step 5: Commit**

```bash
git add docs/operations/autonomous-control-plane-runbook.md functions/test/productionAutonomyWorkflow.test.js
git commit -m "docs: define autonomous production recovery policy"
```

## Self-Review

- Spec coverage: Firebase Production autonomy, Play Production staged rollout, health gates, automatic halt behavior, exact-SHA evidence, WIF, authorization isolation, and rollback semantics are each mapped to a task.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation step remains.
- Interface consistency: the production authorization input is `authorize_google_play_production`; the authorization phrase is `PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED`; rollout values are exactly `5|20|50|100`; health policy lives at `config/production-release-policy.json`.
