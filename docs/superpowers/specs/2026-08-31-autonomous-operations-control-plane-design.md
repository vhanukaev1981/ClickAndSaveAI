# Autonomous Operations Control Plane Design

## Objective

Build a durable automation and authorization architecture for Click & Save AI so an agent can operate the project end-to-end across GitHub, Google Cloud, Firebase, Google Play Internal Testing, Vercel, Supabase, Make, and adjacent operational systems with minimal human intervention, while preserving explicit safety boundaries around irreversible or public-production actions.

## Design Principles

1. **No permanent broad credentials in repository code or chat.** Prefer OIDC/WIF, short-lived access tokens, provider-managed secret stores, and environment-scoped secrets.
2. **Autonomy by environment.** Staging and non-public test environments may run autonomously. Production-publication capabilities must remain separately gated.
3. **Least privilege by task, not least functionality.** Service identities receive every permission required for their bounded operational role, but not unrelated organization-wide ownership.
4. **Exact-SHA traceability.** Every release, deployment, artifact, and automated remediation must identify the exact source commit it operates on.
5. **Observable and reversible operations.** Every mutation should emit durable logs/evidence and, where technically possible, provide a rollback path.
6. **API-first, browser-fallback.** Prefer provider APIs/connectors. Use browser automation only where a required provider operation is not exposed by an available API surface.
7. **No Google Play Production track publication by default.** Google Play Internal Testing is the highest automatically authorized Play release target unless a future explicit policy change is approved.

## Trust and Identity Model

### GitHub

- GitHub remains the source-of-truth orchestrator for repository state, CI, release candidates, and exact-SHA release gates.
- The connected GitHub integration may perform repository reads/writes, PR operations, CI inspection, bounded retries, and merge operations when branch protection and required checks allow them.
- GitHub Actions uses OIDC identity tokens for cloud authentication rather than long-lived cloud keys wherever supported.

### Google Cloud

Create or retain dedicated service identities for distinct responsibilities rather than a single Owner identity:

- **CI verifier identity:** read-only access to required GCP/Firebase metadata and configuration validation surfaces.
- **Deployment identity:** permissions needed for Firebase/GCP deployment operations that are explicitly allowed by the release workflow.
- **Google Play publishing identity:** Play Developer API access restricted to the Click & Save AI application and Internal Testing publishing responsibilities.
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

Each capability must have an independent authorization input or policy gate. Authorizing one capability must not implicitly authorize another.

## Google Play Release Model

Routine autonomous mobile release target:

**Google Play Internal Testing only.**

The release workflow must:

1. Accept an exact 40-character source SHA.
2. Verify successful final CI for that exact SHA.
3. Rebuild/sign the candidate from that exact SHA.
4. Verify upload signing identity and expected Play app-signing metadata.
5. Publish only to the configured Internal Testing track when the dedicated authorization phrase is present.
6. Record package ID, version code, version name, source SHA, artifact hashes, workflow run ID, and publishing result.
7. Never infer authorization for Production/Open/Closed tracks from Internal Testing authorization.

Production-track publication remains outside the default autonomous policy.

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

GitHub Actions is the primary source-controlled orchestrator for build, CI, signed mobile candidates, release gates, and cloud deployment workflows.

### Secondary

- **Make:** cross-service event orchestration and scheduled business/operational workflows.
- **Vercel:** web deployment and environment management where applicable.
- **Supabase:** database/auth/storage/edge-function operations where applicable.
- **Connected ChatGPT plugins/connectors:** interactive autonomous operations when the provider exposes the required API surface.

### Browser Fallback

Browser automation may be used for required operations that are unavailable through the active connector/API surface, such as a provider UI-only dispatch or approval flow. Browser automation must obey the same release policy and may not broaden the authorized target.

## Autonomous Remediation Policy

The agent may autonomously diagnose and repair bounded technical blockers when all of the following are true:

- the change is reversible or reviewable through Git;
- scope is limited to the current project;
- it does not broaden cloud IAM beyond the approved architecture;
- it does not publish to the Google Play Production track;
- it does not destroy production data;
- CI/tests can validate the repair.

Examples include workflow syntax errors, dependency pinning, CI configuration, release contract validation, environment-variable wiring, bounded IAM binding corrections, and retrying transient jobs.

The agent must stop for genuinely user-only actions such as provider terms acceptance, billing/identity verification, MFA/device confirmation, legal declarations, or permissions that cannot be granted through available connected tools.

## Audit and Evidence

Every privileged workflow should preserve enough evidence to answer:

- who/what initiated the operation;
- exact source SHA;
- workflow/run ID;
- target project/environment/track;
- artifact hashes/version identifiers;
- final outcome;
- whether any deployment or publication occurred.

Release evidence should be retained as GitHub Actions artifacts where practical.

## Rollback Strategy

- Git changes: revert commit or PR.
- CI/workflows: revert workflow commit and rerun exact prior SHA if appropriate.
- Firebase/web: use provider-supported version/release rollback where available.
- Google Play Internal Testing: replace with a corrected higher versionCode release rather than attempting unsupported binary rollback semantics.
- IAM: keep bootstrap changes explicit and documented so bindings can be removed deterministically.

## Implementation Phases

1. Inventory current GitHub Actions, WIF, Firebase/GCP, Play, Vercel, Supabase, and Make integration state.
2. Normalize identity and permission boundaries; eliminate unnecessary long-lived cloud keys.
3. Make workflow dispatch and release invocation operable through an available API/automation path, with browser fallback where required.
4. Harden exact-SHA release gates and Internal Testing publication evidence.
5. Add autonomous blocker diagnosis/retry/repair flows.
6. Add observability, audit evidence, and rollback documentation.
7. Validate the full path from green main CI to installable Google Play Internal Testing build without enabling Google Play Production publication.

## Success Criteria

The architecture is complete when:

- green `main` can trigger an exact-SHA signed release candidate;
- Google Play Internal Testing can be authorized and published without user intervention except genuine provider-only actions;
- Firebase/GCP staging operations are autonomous;
- production capabilities remain independently scoped and auditable;
- no routine workflow depends on a long-lived Google Cloud service-account JSON key when WIF can replace it;
- CI failures can be inspected, retried, and bounded technical blockers repaired autonomously;
- all sensitive operations retain exact-SHA and run-level audit evidence;
- no workflow can publish to the Google Play Production track under the Internal Testing authorization path.
