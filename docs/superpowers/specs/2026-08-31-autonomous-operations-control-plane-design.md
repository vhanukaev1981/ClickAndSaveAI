# Autonomous Operations Control Plane Design

## Objective

Build a durable automation and authorization architecture for Click & Save AI so an agent can operate the project end-to-end across GitHub, Google Cloud, Firebase, Google Play, Vercel, Supabase, Make, and adjacent operational systems with minimal human intervention, while preserving explicit safety boundaries around irreversible, destructive, and public-production actions.

## Design Principles

1. **No permanent broad credentials in repository code or chat.** Prefer OIDC/WIF, short-lived access tokens, provider-managed secret stores, and environment-scoped secrets.
2. **Autonomy by environment.** Staging is fully autonomous. Production is autonomous only through explicit release policies, health gates, staged rollout, and rollback/stop controls.
3. **Least privilege by task, not least functionality.** Service identities receive every permission required for their bounded operational role, but not unrelated organization-wide ownership.
4. **Exact-SHA traceability.** Every release, deployment, artifact, and automated remediation must identify the exact source commit it operates on.
5. **Observable and reversible operations.** Every mutation should emit durable logs/evidence and, where technically possible, provide a rollback or stop path.
6. **API-first, browser-fallback.** Prefer provider APIs/connectors. Use browser automation only where a required provider operation is not exposed by an available API surface.
7. **Public production is staged, never implicit.** No ordinary push, merge, or Internal Testing authorization may directly imply a full public rollout.

## Trust and Identity Model

### GitHub

- GitHub remains the source-of-truth orchestrator for repository state, CI, release candidates, exact-SHA release gates, and release evidence.
- The connected GitHub integration may perform repository reads/writes, PR operations, CI inspection, bounded retries, merge operations, release dispatch, and release-state inspection when branch protection and required checks allow them.
- GitHub Actions uses OIDC identity tokens for cloud authentication rather than long-lived cloud keys wherever supported.

### Google Cloud

Create or retain dedicated service identities for distinct responsibilities rather than a single Owner identity:

- **CI verifier identity:** read-only access to required GCP/Firebase metadata and configuration validation surfaces.
- **Deployment identity:** permissions needed for Firebase/GCP deployment operations that are explicitly allowed by the release workflow.
- **Google Play publishing identity:** Play Developer API access restricted to the Click & Save AI application and only the release-track operations required by the approved policy.
- **Bootstrap/admin identity:** only for one-time infrastructure/IAM bootstrap operations; not part of routine autonomous execution.

GitHub OIDC claims must be constrained by repository identity, repository ID, owner ID, branch/ref, workflow identity, and environment where supported.

## Workload Identity Federation

The default Google Cloud authentication path is:

`GitHub Actions OIDC -> Google Cloud Workload Identity Pool/Provider -> bounded service account impersonation -> Google/Firebase API`

Required controls:

- Repository: `vhanukaev1981/ClickAndSaveAI`
- Repository ID pinning
- Repository owner ID pinning
- Branch/ref pinning for privileged operations (`refs/heads/main` unless a narrower workflow requires otherwise)
- Workflow path/ref pinning for production release operations
- No checked-in JSON service-account keys
- Short-lived tokens only for routine automation

## Firebase and Google Cloud Authorization

### Staging

Staging should be fully autonomous for supported deployment, verification, smoke-test, rollback, and operational remediation actions.

### Production

Production access is separated into independent capabilities:

- read-only health/metadata verification
- release candidate creation
- Firebase production deployment
- IAM/bootstrap administration
- Google Play Internal Testing publication
- Google Play Closed/Open Testing publication, if enabled later
- Google Play Production staged rollout

Each capability must have an independent authorization input or policy gate. Authorizing one capability must not implicitly authorize another.

### Firebase Production Deployment Policy

Firebase production deploys may become autonomous when all of the following are true:

1. `main` required checks are green for the exact source SHA.
2. The release workflow verifies the expected production project and WIF identity.
3. The deployment is tied to the exact source SHA and workflow run ID.
4. Post-deploy smoke/health checks execute immediately.
5. If provider-supported rollback is available and the health gate fails, the workflow performs or prepares deterministic rollback according to the component's rollback semantics.
6. Deployment logs and evidence identify what changed, where, and from which SHA.

A Firebase production deploy authorization must not authorize a Google Play release and vice versa.

## Google Play Release Model

### Internal Testing

Internal Testing remains the first autonomous mobile release target.

The release workflow must:

1. Accept an exact 40-character source SHA.
2. Verify successful final CI for that exact SHA.
3. Rebuild/sign the candidate from that exact SHA.
4. Verify upload signing identity and expected Play app-signing metadata.
5. Publish only to the configured Internal Testing track when the dedicated Internal Testing policy permits it.
6. Record package ID, version code, version name, source SHA, artifact hashes, workflow run ID, and publishing result.

### Production Staged Rollout

Google Play Production publication may be autonomous only through a staged-release policy. It must never be inferred from Internal Testing authorization or from a normal `main` push.

The production release path must enforce:

1. The exact SHA has completed required CI and the release-candidate checks.
2. The same build lineage has successfully passed Internal Testing or an explicitly approved equivalent pre-production gate.
3. Signing identity, package ID, version code, version name, and artifact hashes match the release evidence.
4. Production release uses a dedicated authorization/policy gate distinct from all testing-track gates.
5. Initial rollout starts at a bounded percentage, defaulting to **5%**.
6. Automated health evaluation runs before each promotion step using available signals such as Play vitals, crash/ANR data, backend/service health, deployment smoke checks, and release-specific error telemetry.
7. Promotion follows a controlled sequence, initially **5% -> 20% -> 50% -> 100%**, with every step producing audit evidence.
8. If a health threshold is breached, rollout promotion stops automatically. Where the provider permits halting an active staged rollout, the workflow halts it; otherwise it prevents further promotion and surfaces the corrective path.
9. A failed or halted production rollout must never automatically retry to a higher percentage without the health gate returning to an acceptable state.
10. Full 100% rollout is reached only after all prior stages satisfy health criteria.

Production rollout thresholds and observation windows should be stored as repository-controlled policy/configuration rather than hard-coded across multiple workflow steps.

## Health Gates

The autonomous production controller should aggregate the strongest available signals rather than rely on a single metric. Where available, the gate should include:

- Android crash rate / crash-free users or sessions
- Android ANR rate
- backend HTTP/server error rate
- Firebase/GCP service health and deployment smoke tests
- authentication/payment/critical-path synthetic checks where applicable
- release-specific alerts or regression indicators

Health thresholds must be explicit, version-controlled, auditable, and fail closed when required telemetry is unavailable for a production promotion decision.

## Stop and Rollback Policy

### Google Play

- A staged rollout may be halted when health gates fail.
- Binary rollback semantics on Google Play are limited; when replacing a bad build is required, publish a corrected build with a higher `versionCode`.
- The system must never attempt to reduce `versionCode` or silently republish an older binary as if it were a native rollback.

### Firebase / Web / Backend

- Use provider-supported rollback/version restoration where available.
- If rollback is not supported for a component, redeploy the last known-good exact SHA/configuration.
- Rollback actions must preserve evidence of the bad release, rollback target, reason, and outcome.

## Secrets Architecture

Secrets may live only in appropriate secret stores:

- GitHub Actions Secrets / Environment Secrets
- Google Secret Manager
- Vercel encrypted environment variables
- Supabase encrypted project secrets
- provider-native encrypted credential stores

Rules:

- Never commit secrets to Git.
- Never echo secrets into CI logs.
- Never paste long-lived credentials into chat.
- Use separate credentials by environment and function.
- Rotate/revoke credentials when a long-lived credential becomes unnecessary after WIF migration.

## Orchestration Layer

### Primary

GitHub Actions is the primary source-controlled orchestrator for build, CI, signed mobile candidates, release gates, cloud deployment workflows, staged rollout state transitions, and release evidence.

### Secondary

- **Make:** cross-service event orchestration and scheduled business/operational workflows.
- **Vercel:** web deployment and environment management where applicable.
- **Supabase:** database/auth/storage/edge-function operations where applicable.
- **Connected ChatGPT plugins/connectors:** interactive autonomous operations when the provider exposes the required API surface.

### Browser Fallback

Browser automation may be used for required operations that are unavailable through the active connector/API surface, such as provider UI-only approval, policy, or release configuration. Browser automation must obey the same target, rollout, and safety policy and may not broaden the authorized scope.

## Autonomous Remediation Policy

The agent may autonomously diagnose and repair bounded technical blockers when all of the following are true:

- the change is reversible or reviewable through Git;
- scope is limited to the current project;
- it does not broaden cloud IAM beyond the approved architecture;
- it does not destroy production data;
- CI/tests can validate the repair;
- a production remediation does not bypass release health gates or staged-rollout policy.

Examples include workflow syntax errors, dependency pinning, CI configuration, release contract validation, environment-variable wiring, bounded IAM binding corrections, retrying transient jobs, stopping an unhealthy rollout, and preparing a corrected higher-version release.

The agent must stop for genuinely user-only actions such as provider terms acceptance, billing/identity verification, MFA/device confirmation, legal declarations, or permissions that cannot be granted through available connected tools.

## Audit and Evidence

Every privileged workflow should preserve enough evidence to answer:

- who/what initiated the operation;
- exact source SHA;
- workflow/run ID;
- target project/environment/track;
- artifact hashes/version identifiers;
- rollout percentage and transition history;
- health-gate inputs and decision;
- final outcome;
- whether any deployment, halt, rollback, or publication occurred.

Release evidence should be retained as GitHub Actions artifacts where practical.

## Implementation Phases

1. Inventory current GitHub Actions, WIF, Firebase/GCP, Play, Vercel, Supabase, and Make integration state.
2. Normalize identity and permission boundaries; eliminate unnecessary long-lived cloud keys.
3. Validate the autonomous issue-to-workflow dispatch path already introduced for Internal Testing.
4. Harden exact-SHA release gates and Internal Testing publication evidence.
5. Add Firebase production deployment automation with post-deploy health verification and rollback handling.
6. Add a distinct Google Play Production staged-rollout workflow/policy with fail-closed health gates.
7. Add autonomous rollout halt, remediation, and corrected-release flows.
8. Add observability, audit evidence, and rollback documentation.
9. Validate the complete path from green `main` to Internal Testing and then to guarded staged Production rollout without allowing one authorization path to impersonate another.

## Success Criteria

The architecture is complete when:

- green `main` can trigger an exact-SHA signed release candidate;
- Google Play Internal Testing can be authorized and published without user intervention except genuine provider-only actions;
- Firebase/GCP staging operations are autonomous;
- Firebase production deployment can execute autonomously behind exact-SHA, identity, and health gates;
- Google Play Production can execute only as an explicit staged rollout with independent authorization, health checks, automatic halt behavior, and audit evidence;
- production rollout cannot skip directly from 0% to 100% under the ordinary autonomous path;
- Internal Testing authorization cannot publish to Production;
- no routine workflow depends on a long-lived Google Cloud service-account JSON key when WIF can replace it;
- CI failures can be inspected, retried, and bounded technical blockers repaired autonomously;
- all sensitive operations retain exact-SHA, run-level, rollout-state, and health-decision evidence;
- no production rollout promotion occurs when required telemetry is missing or unhealthy.
