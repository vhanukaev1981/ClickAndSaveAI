# Production Enablement Block 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add repository-verifiable production recovery, observability, retention, and operational readiness controls without deploying production, publishing Google Play, or adding product features.

**Architecture:** Keep product behavior unchanged while adding a small shared backend telemetry contract, machine-readable operational policy/specification files, fail-closed repository validators, and a dedicated CI workflow. Recovery is forward recovery from an immutable known-good SHA; active monitoring, legal approval, and production rollback remain external until real environment verification exists.

**Tech Stack:** Node.js 22, `node:test`, Firebase Functions v2, `firebase-functions/logger`, Firestore, GitHub Actions, JSON/YAML-compatible repository specifications.

## Global Constraints

- Exact starting SHA: `4f674d27dfec148e10108274de23013ae73613df`.
- Do not merge historical PRs.
- Do not change `main`.
- Do not deploy production.
- Do not publish Google Play.
- Do not fabricate production Firebase/OAuth/IAM/WIF/GitHub-environment/secrets/signing resources.
- `LOG_EXISTS` is not `MONITORING_READY`.
- `METRIC_SPECIFIED` is not `ALERT_ACTIVE`.
- `ROLLBACK_PLAN_EXISTS` is not `ROLLBACK_VERIFIED`.
- `RETENTION_POLICY_EXISTS` is not `LEGAL_APPROVAL`.
- Staging evidence is not production evidence.

---

### Task 1: Operational telemetry contract

**Files:**
- Create: `functions/src/operationalTelemetry.js`
- Create: `functions/test/productionOperationsTelemetry.test.js`

**Interfaces:**
- Produces: `buildOperationalPayload(input)`, `emitOperationalEvent(input)`, `actorRef(uid)`, canonical event validation.
- Consumes: `firebase-functions/logger`, Node `crypto`.

- [ ] Write tests first for required fields, actor pseudonymization, forbidden-key redaction, bounded detail serialization, and invalid event rejection.
- [ ] Run `cd functions && npm test -- productionOperationsTelemetry.test.js` and confirm failure because the telemetry module does not exist.
- [ ] Implement the minimal telemetry helper.
- [ ] Re-run the targeted test and then full `npm test`.
- [ ] Commit the telemetry contract.

### Task 2: Critical subsystem instrumentation

**Files:**
- Modify: `functions/src/gmailConnectFunctions.js`
- Modify: `functions/src/gmailDisconnectFunctions.js`
- Modify: `functions/src/pushFunctions.js`
- Modify: `functions/src/providerDispatchFunctions.js`
- Modify: `functions/src/privacyLifecycleFunctions.js`
- Create: `functions/test/productionOperationsInstrumentation.test.js`

**Interfaces:**
- Consumes: `emitOperationalEvent()` from Task 1.
- Produces canonical events for Gmail/OAuth, push, provider dispatch, and privacy/deletion boundaries.

- [ ] Write a source-contract test that requires the canonical event calls at each boundary and prohibits raw `{ uid }` operational logger calls in the modified files.
- [ ] Run targeted test and confirm failure.
- [ ] Add telemetry calls without changing business return values, authorization rules, persistence semantics, or provider behavior.
- [ ] Re-run targeted and full backend tests.
- [ ] Commit instrumentation.

### Task 3: Known-good release identity and recovery planner

**Files:**
- Create: `operations/release/known-good-manifest.schema.json`
- Create: `operations/release/known-good-manifest.example.json`
- Create: `scripts/production-recovery-plan.mjs`
- Create: `scripts/production-operations-guard.mjs`
- Create: `functions/test/productionRecoveryContract.test.js`

**Interfaces:**
- `production-recovery-plan.mjs --manifest <path> --target <functions|firestore-rules|configuration|android> --mode plan`
- Output is a deterministic plan only; it never deploys.

- [ ] Write tests for exact 40-char SHA, required surface identities, explicit recovery target, Android forward-fix semantics, and refusal of secret-bearing keys.
- [ ] Confirm tests fail with missing scripts/specs.
- [ ] Implement manifest and planner.
- [ ] Re-run tests.
- [ ] Commit release/recovery contract.

### Task 4: Retention and privacy disposition policy

**Files:**
- Create: `operations/retention/retention-policy.json`
- Create: `docs/PRODUCTION_RETENTION_PRIVACY.md`
- Extend: `scripts/production-operations-guard.mjs`
- Create: `functions/test/productionRetentionPolicy.test.js`

**Interfaces:**
- Every data family declares `DELETE`, `ANONYMIZE`, or `RETAIN` plus trigger, rationale, and approval state.

- [ ] Write validation tests for disposition enum, coverage of known user/account/Gmail/provider/commerce/lifecycle data families, and explicit `LEGAL_APPROVAL_REQUIRED` status.
- [ ] Confirm failure.
- [ ] Add the policy and validator.
- [ ] Re-run tests.
- [ ] Commit retention policy.

### Task 5: Monitoring and alerting specification

**Files:**
- Create: `operations/monitoring/monitoring-spec.json`
- Create: `docs/PRODUCTION_OBSERVABILITY.md`
- Extend: `scripts/production-operations-guard.mjs`
- Create: `functions/test/productionMonitoringSpec.test.js`

**Interfaces:**
- Each metric references a canonical telemetry event and declares intended metric type, aggregation, threshold/window, severity, and state `SPECIFIED_NOT_ACTIVE`.

- [ ] Write tests requiring Gmail/OAuth, Gmail watch/reconciliation, push, provider handoff, privacy/deletion, deployment, and recovery coverage.
- [ ] Confirm failure.
- [ ] Add spec/documentation and validation.
- [ ] Re-run tests.
- [ ] Commit monitoring specification.

### Task 6: Data/schema compatibility safeguards

**Files:**
- Create: `operations/schema/schema-compatibility.json`
- Create: `docs/PRODUCTION_SCHEMA_COMPATIBILITY.md`
- Extend: `scripts/production-operations-guard.mjs`
- Create: `functions/test/productionSchemaCompatibility.test.js`

**Interfaces:**
- Defines compatibility epoch, minimum supported release window, additive-first policy, deprecation/removal rules, migration/backfill ordering, and rollback data-compatibility rules.

- [ ] Write tests for mandatory policy fields and destructive-change fail-closed requirements.
- [ ] Confirm failure.
- [ ] Add policy/docs and validator.
- [ ] Re-run tests.
- [ ] Commit compatibility safeguards.

### Task 7: Incident and recovery runbooks

**Files:**
- Create: `docs/PRODUCTION_OPERATIONS_RUNBOOK.md`
- Create: `docs/PRODUCTION_RECOVERY_RUNBOOK.md`
- Extend: `scripts/production-operations-guard.mjs`
- Create: `functions/test/productionRunbookContract.test.js`

**Interfaces:**
- Runbooks define incident severity, evidence capture, containment, forward recovery per surface, data-integrity checks, stop conditions, and truth-state reporting.

- [ ] Write source-contract tests for required sections and forbidden production-verification claims.
- [ ] Confirm failure.
- [ ] Add runbooks.
- [ ] Re-run tests.
- [ ] Commit runbooks.

### Task 8: Block 2 CI and final verification

**Files:**
- Create: `.github/workflows/production-operations-ci.yml`
- Update only if required: `docs/DEPLOYMENT.md`

**Interfaces:**
- CI proves repository readiness only.

- [ ] Add a workflow that checks exact candidate SHA lineage, runs secret/readiness guards, backend regression, Block 2 operational guard, and Android regression/lint/build audit without deployment.
- [ ] Open a Draft stacked PR from `agent/production-enablement-block2` to `agent/production-enablement-block1`.
- [ ] Verify workflow runs on the exact final Block 2 SHA.
- [ ] Record backend test count, P0 regression result, Block 2 CI conclusion, exact SHA, and remaining external blockers.
- [ ] Do not merge, deploy, or publish.
