# Production Enablement Block 3C Build Identity Hardening Implementation Plan

> **For agentic workers:** implement with TDD and preserve the locked accepted files byte-for-byte.

**Goal:** Correct PR #70 for the live-discovered Compute default Cloud Build identity that still holds legacy `roles/editor`, without any live Production mutation by the agent.

**Architecture:** Add a dedicated fail-closed hardener with read-only preflight and add-before-remove apply semantics. Extend the existing Block 3C wrapper so only an explicitly proven Editor remediation state can recover from the accepted verifier failure; rerun the unchanged accepted verifier before any runtime/build actAs bootstrap.

**Locked files:**
- `scripts/verify-production-runtime-build-actas.sh` blob `1a60a70dba55eff3423b2599c8a30810aecb79a8`
- `scripts/bootstrap-production-runtime-build-actas.sh` blob `53ecc26c2842df891699c4b3e2446dc5bd406354`

## Tasks

- [x] Add deterministic RED tests for Editor-only, Editor+builder, builder-only, missing roles, unknown extra roles, custom/legacy identities, wrong project, project-wide Service Account User, build-SA keys, add-before-remove visibility, removal failure, post-remove Editor visibility, arbitrary verifier failure, apply failure, forbidden-operation scans, and `bash -n`.
- [x] Implement `scripts/harden-production-build-identity.sh` with exact Production/project-number/discovery/runtime/deployment/IAM preconditions and `--preflight`.
- [x] Implement exact allowed state machine and add-before-remove role transition to `roles/cloudbuild.builds.builder` only.
- [x] Update `scripts/bootstrap-production-build-identity.sh` so arbitrary accepted-verifier failures remain fatal unless explicit hardening preflight returns `EDITOR_ONLY` or `EDITOR_BUILDER`.
- [x] Require the unchanged accepted verifier to PASS after hardening before invoking the unchanged runtime/build actAs bootstrap.
- [x] Preserve the existing Cloud Build service-enable / bounded discovery / one-shot no-source initialization path without adding any new API enablement.
- [x] Record the live partial state additively without erasing prior evidence.
- [ ] Run focused new hardening tests on repository exact HEAD.
- [ ] Run complete Block 3C tests and complete backend Node suite.
- [ ] Run `bash -n`, Production security/operations repository guards, and secret/key/deployment scans.
- [ ] Verify both locked blobs remain exact on final HEAD.
- [ ] Push only to `agent/production-enablement-block3c-build-identity` / PR #70 and require exact-head Android and Backend CI, Production Enablement Security CI, and Production Operations CI to reach terminal SUCCESS.

## Live-operation boundary

Repository implementation and CI validation only. The agent must not execute the hardener, bootstrap, verifier, IAM writes, builds, or deployments against Production. Owner execution of the final exact SHA is the next Master Control action after repository and CI closure.
