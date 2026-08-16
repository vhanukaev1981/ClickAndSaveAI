# Production Enablement Block 3C — Cloud Build Identity Initialization & Build actAs Closure

## Status

Design approved by Master Control under the owner's standing delegation to make project sequencing and scope decisions, escalating only actions that require owner-only external authentication or approval.

## Canonical starting point

- Repository: `vhanukaev1981/ClickAndSaveAI`
- Parent branch: `agent/production-enablement-block3b3c-v2-runtime-identity`
- Exact parent SHA: `5d3b3e413805bcf1e301259c1da70099884fb2a2`
- Parent PR: `#69`
- Parent live checkpoint: Block 3B.3C repository-complete and live-verified for dedicated v1/v2 runtime identities.
- Production project: `click-save-ai-production`
- Project number: `991489557172`
- Region: `europe-west1`

The accepted Block 3B.3C verifier blob is `1a60a70dba55eff3423b2599c8a30810aecb79a8` for `scripts/verify-production-runtime-build-actas.sh`.

## Current live truth entering Block 3C

The owner executed the exact Block 3B.3C bootstrap from SHA `5d3b3e413805bcf1e301259c1da70099884fb2a2` in authenticated Google Cloud Shell. The immediate verifier established:

- `productionRuntimeIdentityStatus=READY`
- v1 runtime identity exists: `clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com`
- v1 runtime project role is exactly `roles/datastore.user`
- v2 runtime identity exists: `clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com`
- v2 has zero project-level application roles
- deployer `roles/iam.serviceAccountUser` is present only on the intended runtime identities
- `productionRuntimeActAsConfigured=true`
- `productionBuildIdentityStatus=DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION`
- `productionCloudBuildServiceEnabled=false`
- `productionBuildIdentityDiscoveryAttempted=false`
- `productionBuildIdentityConfigured=false`
- `productionBuildActAsConfigured=false`
- `productionDeployed=false`

## External control-plane state observed before Block 3C

Master Control independently observed:

- `main` is protected.
- Required status contexts are `android-build`, `backend-test`, `production-security`, and `repository-operations-readiness`.
- GitHub Environment `production` exists.
- The `production` Environment has a required-reviewer rule for the owner with self-review allowed.
- The `production` Environment has a custom deployment-branch policy allowing `main` only.
- Current `main` does not yet contain `.github/workflows/production-release.yml` from the stacked Production Enablement work.

These facts mean the repository trust boundary is materially stronger than the historical Block 3A pre-state, but WIF end-to-end execution is still intentionally not part of Block 3C.

## Why WIF end-to-end is excluded from this block

The Production WIF provider is intentionally constrained to:

- `environment=production`
- `ref=refs/heads/main`
- `workflow_ref=vhanukaev1981/ClickAndSaveAI/.github/workflows/production-release.yml@refs/heads/main`

A GitHub Actions job that satisfies `environment=production` creates a GitHub Environment deployment record even if the job performs only an identity probe. The accepted Block 3B.3C verifier still requires the Production deployment inventory to remain empty before the first authorized Production release.

Therefore Block 3C MUST NOT create a fake or control-plane-only Production deployment record merely to prove WIF. WIF end-to-end proof is deferred to the first genuinely authorized release-path execution, where a Production Environment deployment record is expected and can be classified truthfully.

Block 3C must not weaken the WIF provider condition, allow `agent/*`, create a temporary alternate provider, bypass the `production` Environment, or reinterpret a GitHub deployment record as a Firebase deployment.

## Goal

Initialize the real Cloud Build service state, discover the actual Google-selected default build service account, grant the deployer `roles/iam.serviceAccountUser` only on that exact discovered build identity, and independently verify the runtime/build identity boundary without deploying any workload.

The block closes when:

- `cloudbuild.googleapis.com` is enabled in `click-save-ai-production`;
- Cloud Build default build identity discovery is attempted only after exact enabled-service verification;
- the discovered identity is non-empty, Production-owned, exists, and passes the existing forbidden-role checks;
- deployer `actAs` exists on that exact build identity and no unintended Production service account receives deployer `actAs`;
- runtime v1/v2 identity guarantees remain unchanged;
- Production deployment inventory remains empty;
- no Firebase/Functions deployment occurs.

## Google Cloud behavior the design must respect

Current Google Cloud documentation states that the default Cloud Build service account is no longer universally the legacy `PROJECT_NUMBER@cloudbuild.gserviceaccount.com`. Depending on project history and organization policy, Cloud Build may select either:

- the Compute Engine default service account: `PROJECT_NUMBER-compute@developer.gserviceaccount.com`; or
- the legacy Cloud Build service account: `PROJECT_NUMBER@cloudbuild.gserviceaccount.com`.

The authoritative discovery mechanism is `gcloud builds get-default-service-account`, and the REST API can return an empty `serviceAccountEmail` if no default identity is currently selected.

Block 3C therefore MUST NOT hard-code which account will be selected and MUST NOT create a guessed build service account.

## Architecture

### 1. Preserve the accepted Block 3B.3C verifier

`scripts/verify-production-runtime-build-actas.sh` remains byte-for-byte unchanged in Block 3C.

The new Block 3C bootstrap must verify the accepted Git blob before any live mutation. This prevents the build-identity initialization block from silently changing the already accepted runtime/build verifier contract.

### 2. Add a dedicated Block 3C initializer

Add `scripts/bootstrap-production-build-identity.sh`.

Responsibilities:

1. Fail closed unless `PROJECT_ID` is exactly `click-save-ai-production`.
2. Require the accepted Block 3B.3C verifier and runtime/build bootstrap to be present and executable.
3. Run the accepted verifier before mutation with runtime gaps forbidden and current runtime `actAs` required.
4. Require the pre-state to be either:
   - Cloud Build disabled + build identity deferred; or
   - Cloud Build already enabled + build identity ready, for idempotent reruns.
5. Enable exactly `cloudbuild.googleapis.com` when disabled.
6. Never call `gcloud services enable` for any other API.
7. Poll exact enabled-service state with a bounded timeout.
8. Run `gcloud builds get-default-service-account` only after the exact service is enabled.
9. If a default identity is immediately returned, validate it through the accepted verifier path before granting any build `actAs`.
10. If the API returns an empty default identity after bounded polling, perform at most one explicit no-source, no-artifact, no-deployment initialization build, then retry discovery with a bounded timeout.
11. The initialization build, if needed, is a single-step `busybox`/equivalent `true` operation with no source, no image output, no Artifact Registry write, no deployment command, and no application code.
12. Never loop build submission. At most one initialization build may be submitted per invocation.
13. After a non-empty identity is discoverable, invoke the existing `scripts/bootstrap-production-runtime-build-actas.sh`. Its existing logic validates the discovered build identity and applies deployer `roles/iam.serviceAccountUser` only on intended identities.
14. Run the new Block 3C verifier after mutation.
15. Be idempotent: a second successful invocation performs no API enablement, no initialization build, and no duplicate IAM grant.

### 3. Add an independent Block 3C closure verifier

Add `scripts/verify-production-build-identity.sh`.

The verifier is read-only. It must:

- verify exact project ID and number;
- verify the accepted Block 3B.3C verifier blob is unchanged;
- invoke the accepted verifier with no missing runtime or `actAs` allowances;
- require `CLOUD_BUILD_SERVICE_ENABLED=true`;
- require `BUILD_IDENTITY_DISCOVERY_ATTEMPTED=true`;
- require `PRODUCTION_BUILD_IDENTITY_STATUS=READY`;
- require a non-empty exact `BUILD_SA`;
- rely on the accepted verifier's Production ownership, forbidden-role, inventory, and per-SA `actAs` checks;
- independently confirm there is no project-wide `roles/iam.serviceAccountUser` grant;
- confirm Production GitHub deployment inventory remains empty;
- emit `productionBuildIdentityReady=true` and `productionRuntimeBuildActAsConfigured=true` only when all checks pass;
- continue to emit `productionWifEndToEndVerified=false`, `productionDeployEndToEndReady=false`, `productionIdentityReady=false`, and `productionDeployed=false` because WIF/release execution has not yet occurred.

`productionIdentityReady=false` is not a Block 3C failure. It is a deliberate truth boundary: build identity configuration is ready, while release-path identity has not yet been exercised from `main` through the protected `production` Environment.

## Initialization build safety

The no-source initialization build is a fallback only when Cloud Build is enabled but `get-default-service-account` remains empty.

Before submitting it, the initializer must:

- prove exactly one Cloud Build API service row is enabled;
- prove there is still no discovered default build identity;
- write the minimal build config to a temporary file with restrictive permissions;
- use `--no-source`;
- contain no `images`, `artifacts`, `secrets`, substitutions, repository source, deployment commands, or network credentials;
- submit only one build;
- remove the temporary config on exit;
- record only sanitized build ID/status evidence, never tokens or credentials.

If the build command fails, the script may perform one final read-only identity discovery. It may proceed only if the default identity is now non-empty and passes all normal verifier checks; otherwise it fails closed.

## IAM boundary

No Block 3C code may:

- grant Owner or Editor;
- grant project-wide `roles/iam.serviceAccountUser`;
- grant `roles/iam.serviceAccountTokenCreator`;
- grant service-agent roles;
- create or delete service-account keys;
- create or delete a build service account;
- change organization policy;
- alter the WIF provider condition;
- grant new project roles to the v1 or v2 runtime identities.

The only new IAM mutation allowed is the existing deployer `roles/iam.serviceAccountUser` binding on the exact live-discovered build identity when that binding is absent.

## API boundary

The only API enablement allowed is:

`cloudbuild.googleapis.com`

No Firebase, Cloud Functions, Cloud Run, Artifact Registry, Compute Engine, Secret Manager, App Engine, or other API may be enabled by Block 3C.

If build initialization reveals that another API would be required, Block 3C stops and reports the dependency rather than broadening scope automatically.

## Repository changes

Expected new files:

- `scripts/bootstrap-production-build-identity.sh`
- `scripts/verify-production-build-identity.sh`
- `functions/test/productionBuildIdentityBlock3C.test.js`
- `docs/superpowers/specs/2026-08-16-production-enablement-block3c-build-identity-design.md`
- `docs/superpowers/plans/2026-08-16-production-enablement-block3c-build-identity.md`

The accepted Block 3B.3C verifier and runtime/build bootstrap should remain unchanged unless a test proves a correctness defect that cannot be solved by the Block 3C wrapper. Any such defect requires a separate explicit correction before live execution.

## Deterministic test matrix

Tests must cover at least:

1. Exact Production target accepted.
2. Staging, legacy, and arbitrary projects rejected before mutation.
3. Accepted Block 3B.3C verifier blob required.
4. Cloud Build disabled -> exactly one `services enable cloudbuild.googleapis.com`.
5. No other API enable command exists or executes.
6. Cloud Build already enabled -> zero service-enable calls.
7. Enabled + immediate default identity -> no initialization build.
8. Enabled + temporarily empty identity -> bounded polling.
9. Still empty -> at most one no-source initialization build.
10. Initialization build success -> identity discovery retries and can become ready.
11. Initialization build failure + identity still empty -> hard fail.
12. Initialization build failure + identity becomes discoverable -> normal validation still required.
13. Discovered Compute default SA accepted only if existing verifier role checks pass.
14. Discovered legacy Cloud Build SA accepted only if existing verifier role checks pass.
15. Foreign/staging/arbitrary identity rejected.
16. Build identity with forbidden broad project role rejected before `actAs` write.
17. Exact build identity gets at most one deployer `actAs` grant.
18. Existing build `actAs` -> zero duplicate grant.
19. No project-wide Service Account User mutation.
20. No key creation/deletion.
21. No Firebase/Functions/Run deploy command.
22. No App Engine initialization.
23. Second fully configured rerun is read-only/idempotent except verification queries.
24. Closure verifier requires build service enabled + discovery attempted + status READY + non-empty build SA.
25. Closure verifier keeps WIF/release/deployment truth false.
26. Bash syntax checks pass.
27. Full backend test suite remains green.

## Live execution gate

Repository work is completed and exact-head CI must be green before any Production Block 3C execution.

Only then may the owner be asked for one authenticated Cloud Shell action. Master Control should supply a single-line or paste-safe command from the exact final SHA.

The owner should not be asked to make IAM choices, select a build service account, edit organization policy, or manually grant roles. The scripts make the deterministic decision or stop fail-closed.

## Success evidence

A successful live Block 3C run must capture sanitized terminal truth including:

- exact execution SHA;
- Cloud Build API enabled state;
- whether an initialization build was required;
- discovered build service account email;
- accepted verifier PASS;
- Block 3C verifier PASS;
- `productionBuildIdentityStatus=READY`;
- `productionBuildIdentityConfigured=true`;
- `productionBuildActAsConfigured=true`;
- `productionRuntimeBuildActAsConfigured=true`;
- `productionBuildIdentityReady=true`;
- `productionWifEndToEndVerified=false`;
- `productionDeployEndToEndReady=false`;
- `productionIdentityReady=false`;
- `productionDeployed=false`.

## Explicit non-actions

Block 3C does not authorize:

- Firebase deployment;
- Functions deployment;
- Firestore deployment;
- Cloud Run deployment;
- Google Play publication;
- Android signing materialization;
- OAuth configuration;
- App Check enforcement;
- WIF provider weakening or replacement;
- a GitHub Actions run using the `production` Environment solely for identity testing;
- merge of the stacked Production Enablement PRs;
- first real Production release.

## Next boundary after Block 3C

After Block 3C closes live, the identity configuration layer will be complete through Cloud Build, but end-to-end GitHub OIDC/WIF/release-path execution remains intentionally unproven.

That proof belongs to the later first authorized Production release path, after the canonical stack is integrated into protected `main` and the full `production-release.yml` exists on `main`. At that point the resulting GitHub Production Environment deployment record is real release evidence rather than a synthetic identity-test deployment.