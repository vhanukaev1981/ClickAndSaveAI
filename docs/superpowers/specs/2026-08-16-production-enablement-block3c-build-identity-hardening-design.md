# Production Enablement Block 3C — Live-Discovered Build Identity Hardening Design

## Status and canonical live evidence

This document is an additive correction to the earlier Block 3C design. Prior repository and live evidence is preserved.

Canonical live partial state at the start of this correction:

- Project: `click-save-ai-production`
- Project number: `991489557172`
- Billing: enabled
- `cloudbuild.googleapis.com`: enabled
- Google-selected default Cloud Build identity: `991489557172-compute@developer.gserviceaccount.com`
- Accepted verifier failure: the discovered build identity holds forbidden `roles/editor`
- No build `actAs` mutation occurred after that failure
- No Production deployment occurred
- `productionBuildActAsConfigured=false`
- `productionRuntimeBuildActAsConfigured=false`
- `productionWifEndToEndVerified=false`
- `productionDeployEndToEndReady=false`
- `productionIdentityReady=false`
- `productionDeployed=false`

## Security boundary

The accepted verifier remains unchanged and must continue rejecting `roles/editor`. The accepted runtime/bootstrap script also remains unchanged. The only approved legacy-role remediation is for the exact Compute Engine default build identity above, and only by replacing Editor with `roles/cloudbuild.builds.builder` using add-before-remove ordering.

A dedicated `scripts/harden-production-build-identity.sh` owns this remediation. It has a read-only `--preflight` mode and a default apply mode. It fails closed unless project, project number, enabled Cloud Build service, dynamically discovered identity, runtime v1/v2 identities, runtime actAs, zero user-managed build-SA keys, no project-wide `roles/iam.serviceAccountUser`, zero Production deployments, no staging/legacy principal leakage, and locked accepted blobs are all exact.

Legacy Cloud Build or custom build identities are never automatically remediated by this path.

## Allowed role states and transitions

Allowed pre-states for the exact Compute default build identity are:

1. `roles/editor` only: add builder, verify builder visibility, remove Editor, verify exact builder-only state.
2. `roles/editor` + `roles/cloudbuild.builds.builder`: do not add builder, remove Editor, verify exact builder-only state.
3. builder only: no role mutation; pass idempotently.
4. neither role: fail with zero mutation.
5. any unknown additional project role: fail before mutation.

The hardener never grants Owner, Editor, service-agent roles, Token Creator, project-wide Service Account User, or any other project role. It never creates or deletes service-account keys, enables APIs, initializes App Engine, or deploys workloads.

## Block 3C orchestration

`scripts/bootstrap-production-build-identity.sh` continues to run the unchanged accepted verifier first. Arbitrary verifier failure remains fatal. If it fails, the wrapper may invoke only the dedicated hardener `--preflight`; only `EDITOR_ONLY` or `EDITOR_BUILDER` is treated as remediable. The hardener is then applied, and the unchanged accepted verifier must pass on a second run before the existing runtime/bootstrap actAs script may execute. Closure then remains the responsibility of `scripts/verify-production-build-identity.sh`.

The existing Cloud Build service-enable/default-identity initialization path is retained for its previously approved scope. No additional API enablement is introduced by this correction.

## Validation

Deterministic tests prove the exact live Editor-only path, Editor+builder, builder-only idempotence, missing-role drift, unknown roles, legacy/custom identities, wrong project, project-wide Service Account User, user-managed keys, add-before-remove ordering, remove failures, post-remove visibility, arbitrary verifier failure rejection, and the absence of forbidden operations. Existing Block 3C tests remain in force.
