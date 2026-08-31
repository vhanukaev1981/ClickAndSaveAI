# Guarded Production Autonomy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace self-modifying production automation with independent, immutable Firebase health, Google Play Production staged-rollout, and owner-only dispatch controllers while preserving exact-SHA and track-isolation guarantees.

**Architecture:** `production-release.yml` remains the existing exact-SHA gate. New controllers live in separate workflow files and communicate through explicit evidence and dispatch contracts rather than editing one another. Every controller independently validates repository identity, owner identity, `main`, exact SHA, authorization phrase, and target boundary.

**Tech Stack:** GitHub Actions YAML, Node.js 22 contract tests, GitHub OIDC, Google Cloud Workload Identity Federation, Firebase CLI, Android Publisher API, GitHub REST API.

**Spec:** `docs/superpowers/specs/2026-08-31-guarded-production-autonomy-design.md`

## Global Constraints

- Do not weaken `main` required checks or branch protection.
- Do not commit or print long-lived credentials.
- Internal Testing authorization must never authorize Google Play Production.
- Firebase production authorization must never authorize Google Play Production.
- Google Play Production staged rollout must use distinct phrase `PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED`.
- Exact production rollout sequence is `5 -> 20 -> 50 -> 100`.
- Missing required health/evidence must fail closed.
- No workflow may self-modify files under `.github/workflows/` at runtime.

---

### Task 1: Clean branch and preserve exact existing contracts

**Files:**
- Modify: `.github/workflows/production-release.yml`
- Test: `functions/test/productionAutonomyWorkflow.test.js`

**Interfaces:**
- Consumes: existing `production-release.yml` Firebase and Internal Testing jobs.
- Produces: valid runtime GitHub expressions for Firebase evidence artifact names/paths and no temporary helper workflows.

- [ ] Verify branch is based on commit `538d5fb06ab85625efe31657dbb234141430d432` or later with no helper workflow files.
- [ ] Update only malformed Firebase artifact expressions in `production-release.yml`.
- [ ] Remove Firebase-job text that references Play publication state if it exists solely as cross-capability evidence.
- [ ] Run `node --test test/productionAutonomyWorkflow.test.js test/productionEnablementBlock3fWorkflow.test.js` from `functions/`.
- [ ] Commit only the surgical production-release correction.

### Task 2: Add Firebase post-deploy health controller

**Files:**
- Create: `.github/workflows/firebase-production-health-controller.yml`
- Create: `functions/test/firebaseProductionHealthController.test.js`

**Interfaces:**
- Consumes: exact `source_sha`, Firebase deployment evidence artifact/run metadata, production environment variables, WIF identity.
- Produces: fail-closed `healthy|blocked|rollback-required` evidence artifact tied to the exact SHA.

- [ ] Write a failing contract test asserting exact-SHA validation, owner/repository/ref pinning, WIF auth, Firebase-only scope, health-policy invocation, and absence of Google Play Production authorization/track calls.
- [ ] Run the new test and confirm RED because the controller does not yet exist.
- [ ] Implement `firebase-production-health-controller.yml` with `workflow_dispatch`, exact authorization phrase `VERIFY_FIREBASE_PRODUCTION_HEALTH`, and default `NO_FIREBASE_HEALTH_CHECK`.
- [ ] Validate `source_sha` is lowercase 40-char hex and equals workflow `main` SHA.
- [ ] Authenticate through the existing production WIF variables using read-only/runtime-appropriate Google scopes.
- [ ] Consume or reconstruct Firebase deployment evidence for the exact SHA without exposing secrets.
- [ ] Run the version-controlled health policy and fail closed on missing required evidence.
- [ ] Upload durable health/rollback-intent evidence.
- [ ] Run the controller test and existing autonomy/security tests until GREEN.

### Task 3: Add Google Play Production staged-rollout controller

**Files:**
- Create: `.github/workflows/google-play-production-controller.yml`
- Create: `functions/test/googlePlayProductionController.test.js`
- Create: `scripts/google-play-production-policy.mjs`

**Interfaces:**
- Consumes: exact `source_sha`, prior Internal Testing/release evidence, production rollout percentage, production health evidence, WIF Play publisher identity.
- Produces: one bounded transition among `5`, `20`, `50`, `100`, or a halt/no-op evidence result.

- [ ] Write failing tests requiring distinct authorization, exact-main validation, dedicated Play publisher identity, production-track-only endpoint, prior Internal Testing lineage, staged sequence enforcement, fail-closed telemetry, and halt semantics.
- [ ] Run tests and confirm RED.
- [ ] Implement a single-source policy module that accepts current rollout state and health decision and returns only an allowed next action.
- [ ] Implement controller workflow with `workflow_dispatch` inputs `source_sha`, `authorize_google_play_production`, and `production_rollout_percent`.
- [ ] Require exact phrase `PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED` and default `NO_PRODUCTION_UPLOAD`.
- [ ] Authenticate via WIF using the dedicated Play publisher service account.
- [ ] Verify package ID, exact SHA lineage, release evidence, and permitted rollout transition before any Android Publisher mutation.
- [ ] Perform only production-track staged edit/commit operations; never reuse Internal Testing authorization.
- [ ] On unhealthy/missing health evidence, do not promote; halt active rollout when API support is available and record evidence.
- [ ] Upload rollout transition evidence containing SHA, version, percentage, health decision, and result.
- [ ] Run new and existing tests until GREEN.

### Task 4: Add owner-only autonomous production dispatcher

**Files:**
- Create: `.github/workflows/agent-production-dispatch.yml`
- Create: `functions/test/agentProductionDispatch.test.js`

**Interfaces:**
- Consumes: owner-created GitHub issue labels/body containing exact `source_sha` and one bounded requested operation.
- Produces: dispatch to exactly one downstream controller with all unrelated authorizations set to NO_* defaults.

- [ ] Write failing tests requiring owner login/ID check, exact current `main` SHA validation, bounded labels, and strict operation isolation.
- [ ] Run tests and confirm RED.
- [ ] Implement issue-label trigger for Firebase health verification and Play staged-production actions as separate labels.
- [ ] Require issue creator to equal repository owner and verify repository/owner IDs.
- [ ] Parse exact `source_sha`, compare it to `refs/heads/main`, and reject stale/malformed requests.
- [ ] Dispatch only the selected downstream workflow; do not include Internal Testing credentials or cross-authorizations.
- [ ] Run all dispatcher/controller tests until GREEN.

### Task 5: Full regression, PR cleanup, and merge readiness

**Files:**
- Delete if present: `.github/workflows/task3-firebase-expression-fix.yml`
- Verify all changed production/autonomy files.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: merge-ready PR #136 with immutable controllers and no temporary helpers.

- [ ] Run `node --test test/productionAutonomyWorkflow.test.js test/firebaseProductionHealthController.test.js test/googlePlayProductionController.test.js test/agentProductionDispatch.test.js test/productionEnablementBlock3fWorkflow.test.js test/googlePlayInternalTestingWorkflow.test.js` from `functions/`.
- [ ] Verify repository required CI workflows are GREEN for the exact PR head SHA.
- [ ] Inspect PR diff for accidental secret exposure, branch-protection weakening, production-track bypass, or helper workflow remnants.
- [ ] Update PR #136 description to reflect the final controller architecture.
- [ ] Enable auto-merge if GitHub permits; otherwise leave PR merge-ready and report the exact blocker.
