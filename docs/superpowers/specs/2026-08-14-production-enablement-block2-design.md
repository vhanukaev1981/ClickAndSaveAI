# Production Enablement Block 2 Design

## Canonical Starting Point

- Repository: `vhanukaev1981/ClickAndSaveAI`
- Exact approved Block 1 SHA: `4f674d27dfec148e10108274de23013ae73613df`
- Block 2 branch starts from that exact SHA.
- PR #60 remains Draft/Open/Unmerged.
- P0 PRs #56-#59 remain Draft/Open/Unmerged.
- `main` is not a deployment target for this block.

## Mission

Close repository-side and architecture-side production operations blockers without product development, UI redesign, production deployment, Google Play publication, or fabrication of owner-controlled production resources.

Block 2 covers:

1. production rollback architecture and known-good release identity;
2. Firebase Functions, Firestore Rules, configuration, and Android recovery strategy;
3. data/schema compatibility safeguards;
4. production observability and structured operational logging;
5. repository-ready metric/alert specifications for Gmail, OAuth, push, provider handoff, privacy/deletion, and deployment;
6. incident/runbook readiness;
7. canonical retention policy with DELETE / ANONYMIZE / RETAIN classifications;
8. repository-verifiable recovery/failure-mode contracts.

## Truth Model

Repository evidence SHALL distinguish capability from activation and verification.

- `LOG_EXISTS` SHALL NOT be reported as `MONITORING_READY`.
- `METRIC_SPECIFIED` SHALL NOT be reported as `ALERT_ACTIVE`.
- `ROLLBACK_PLAN_EXISTS` SHALL NOT be reported as `ROLLBACK_VERIFIED`.
- `RETENTION_POLICY_EXISTS` SHALL NOT be reported as `LEGAL_APPROVAL`.
- Staging evidence SHALL NOT be reported as production evidence.
- A repository test of a recovery planner SHALL NOT be reported as a production recovery execution.

Every operational control SHALL expose one of these evidence states where applicable:

- `REPOSITORY_READY`
- `SPECIFIED_NOT_ACTIVE`
- `OWNER_ACTION_REQUIRED`
- `PRODUCTION_VERIFICATION_REQUIRED`
- `VERIFIED`

Only the last state may be used after real environment verification.

## Architecture

### 1. Known-good release identity

A machine-readable release manifest SHALL identify an exact immutable source SHA and the production surfaces that must remain mutually compatible:

- Firebase Functions source
- Firestore Rules source
- Firestore indexes source
- production configuration version identifier
- Android `versionCode` / `versionName`
- data schema compatibility epoch

The repository SHALL validate the manifest shape and SHA format. A manifest is repository evidence only until a real production release binds it to deployed artifacts.

### 2. Recovery model

The canonical server recovery mechanism is **forward recovery from a known-good immutable SHA**.

For Firebase Functions and Firestore Rules, recovery means checking out the exact known-good source and redeploying only the authorized target surface. This avoids claiming platform-native rollback semantics that are not guaranteed by the repository.

Configuration recovery SHALL restore only declared non-secret configuration identifiers and secret-version references. Secret values SHALL never be stored in git.

Android recovery SHALL use Google Play controls to halt/pause a rollout where available, then publish a forward-fix build with a strictly higher `versionCode`. The repository SHALL NOT claim that an already installed Android binary can be downgraded in place.

### 3. Data/schema compatibility

Production changes SHALL remain compatible with at least the currently released Android client and the immediately previous supported release unless an explicit migration plan proves otherwise.

Repository safeguards SHALL enforce:

- additive schema changes by default;
- no semantic reuse of an existing field;
- destructive field removal only after a documented deprecation window;
- migration/backfill operations separated from destructive cleanup;
- rollback/forward-recovery instructions that account for data written by the newer release.

### 4. Operational telemetry

A shared backend telemetry helper SHALL emit structured, privacy-aware operational events through `firebase-functions/logger`.

Required canonical fields:

- `schemaVersion`
- `event`
- `subsystem`
- `outcome`
- `severity`
- `code`
- `correlationId` when available
- irreversible pseudonymous actor reference when a UID is operationally useful
- bounded sanitized details

Operational telemetry SHALL never log OAuth tokens, authorization codes, refresh tokens, access tokens, encryption keys, raw email addresses, request bodies, or secrets.

### 5. Monitoring specification

The repository SHALL contain a machine-readable monitoring specification mapping canonical events to intended log-based metrics and alert policies.

The specification SHALL cover at minimum:

- Gmail/OAuth connection and provider-cleanup failures
- Gmail watch/reconciliation failures
- push delivery failures
- provider handoff/dispatch failures
- privacy/deletion failures and retry-required states
- deployment and recovery workflow failures

All alert definitions remain `SPECIFIED_NOT_ACTIVE` until created and tested in the production GCP/Firebase environment.

### 6. Retention policy

The repository SHALL contain a canonical, machine-readable retention classification for known application data families.

Each entry SHALL declare exactly one primary disposition:

- `DELETE`
- `ANONYMIZE`
- `RETAIN`

The policy SHALL distinguish user-request deletion behavior from operational/security/audit retention and SHALL mark legal/regulatory approval as external and not yet proven.

### 7. CI and recovery verification

A dedicated Block 2 CI workflow SHALL run repository-only verification:

- backend regression tests;
- telemetry contract tests;
- retention-policy validation;
- monitoring-spec validation;
- release-manifest validation;
- recovery-plan dry-run validation;
- schema compatibility guard;
- production-readiness/secret-audit regressions inherited from Block 1 where practical.

CI success proves repository readiness only. It SHALL NOT be described as production rollback verification, active monitoring, or legal approval.

## Failure Modes

Repository tests SHALL verify fail-closed behavior for:

- malformed/unknown release SHA;
- missing known-good manifest fields;
- recovery target not explicitly selected;
- attempt to include secrets in recovery metadata;
- invalid monitoring event references;
- retention entries without a valid disposition;
- telemetry attempts to emit forbidden sensitive keys;
- destructive schema change without a compatibility declaration.

## External Blockers Preserved

The following remain owner/environment controlled and are not fabricated by Block 2:

- Play App Signing / upload signing identity
- signed production artifact
- separate production Firebase project/config
- production OAuth clients and runtime secrets
- Google restricted-scope verification/security assessment
- production App Check / Play Integrity enforcement
- production GCP IAM/WIF identities
- GitHub production environment
- real production secrets/variables
- active GCP log-based metrics, alert policies, notification channels, dashboards
- legal/privacy counsel approval of retention periods

## Completion Criteria

Block 2 may report `PASS — REPOSITORY OPERATIONS READINESS` only when:

1. all repository-side Block 2 tests and CI pass on an exact Block 2 SHA;
2. all recovery/retention/monitoring artifacts are present and validated;
3. structured telemetry contracts are integrated at the required backend boundaries;
4. no product/UI feature changes are introduced;
5. `main` remains unchanged and no production deployment/publication occurred;
6. the final report explicitly separates repository-ready controls from owner actions and production verification still outstanding.
