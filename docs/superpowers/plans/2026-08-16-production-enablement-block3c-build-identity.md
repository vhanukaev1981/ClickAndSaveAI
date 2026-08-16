# Production Enablement Block 3C Build Identity Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Safely enable only Cloud Build in `click-save-ai-production`, live-discover Google's actual default build service account, add deployer `actAs` only on that exact identity, and independently prove build-identity closure without deploying Production.

**Architecture:** Keep the accepted Block 3B.3C runtime/build verifier and bootstrap byte-for-byte unchanged. Add a Block 3C wrapper initializer that enables exactly `cloudbuild.googleapis.com`, performs bounded default-identity discovery with at most one no-source initialization build fallback, then delegates validation/IAM mutation to the accepted runtime/build bootstrap. Add a read-only Block 3C closure verifier that requires Cloud Build and build `actAs` readiness while preserving WIF/release/deployment truth as false.

**Tech Stack:** Bash (`set -euo pipefail`), Google Cloud CLI (`gcloud`), Node.js 22 built-in test runner, deterministic fake-`gcloud` harness, GitHub Actions existing CI.

## Global Constraints

- Exact parent SHA: `5d3b3e413805bcf1e301259c1da70099884fb2a2`.
- Production project must be exactly `click-save-ai-production`; reject `clickandsaveai`, `clickandsaveai-staging`, and all arbitrary project IDs.
- Production project number is exactly `991489557172`.
- Cloud Build region is exactly `europe-west1`.
- Accepted Block 3B.3C verifier Git blob must remain exactly `1a60a70dba55eff3423b2599c8a30810aecb79a8`.
- `scripts/verify-production-runtime-build-actas.sh` remains byte-for-byte unchanged.
- `scripts/bootstrap-production-runtime-build-actas.sh` remains unchanged unless a separately proven correctness defect makes that impossible.
- The only API Block 3C may enable is `cloudbuild.googleapis.com`.
- No Firebase, Functions, Firestore, Cloud Run, Artifact Registry, Compute Engine, Secret Manager, App Engine, or other API enablement.
- No workload deployment and no GitHub `production` Environment identity-probe run.
- No project-wide `roles/iam.serviceAccountUser`.
- No Owner, Editor, Token Creator, service-agent role, service-account key, org-policy, WIF-provider, OAuth, signing, App Check, or Google Play mutation.
- The only new IAM mutation is deployer `roles/iam.serviceAccountUser` on the exact live-discovered build identity, through the accepted bootstrap.
- At most one no-source initialization build per invocation and only if enabled Cloud Build still returns no default identity after bounded discovery.
- WIF/release truth remains false: `productionWifEndToEndVerified=false`, `productionDeployEndToEndReady=false`, `productionIdentityReady=false`, `productionDeployed=false`.

---

## File Structure

- Create `scripts/bootstrap-production-build-identity.sh` — Block 3C live initializer/orchestrator; the only file allowed to enable Cloud Build and optionally submit one no-source initialization build.
- Create `scripts/verify-production-build-identity.sh` — read-only closure verifier layered on the accepted Block 3B.3C verifier.
- Create `functions/test/productionBuildIdentityBlock3C.test.js` — deterministic static + fake-`gcloud` behavioral test suite.
- Preserve `scripts/verify-production-runtime-build-actas.sh` unchanged.
- Preserve `scripts/bootstrap-production-runtime-build-actas.sh` unchanged.
- Existing CI workflows are reused; no workflow changes are required for repository validation.

---

### Task 1: Add failing Block 3C contract and behavioral tests

**Files:**
- Create: `functions/test/productionBuildIdentityBlock3C.test.js`
- Read-only dependency: `scripts/verify-production-runtime-build-actas.sh`
- Read-only dependency: `scripts/bootstrap-production-runtime-build-actas.sh`

**Interfaces:**
- Consumes accepted verifier blob `1a60a70dba55eff3423b2599c8a30810aecb79a8`.
- Expects future scripts `scripts/bootstrap-production-build-identity.sh` and `scripts/verify-production-build-identity.sh`.
- Produces a deterministic fake-`gcloud` state model used to prove mutation order and idempotence.

- [ ] **Step 1: Write test helpers and static contract tests**

The test file must load the future Block 3C scripts if present, load the accepted verifier/bootstrap, and calculate Git blob SHA using `sha1("blob <len>\\0<content>")`.

Required static assertions:

```js
assert.equal(gitBlobSha(acceptedVerifier), "1a60a70dba55eff3423b2599c8a30810aecb79a8");
assert.match(block3cBootstrap, /EXPECTED_PROJECT_ID="click-save-ai-production"/);
assert.match(block3cBootstrap, /CLOUD_BUILD_SERVICE="cloudbuild\.googleapis\.com"/);
assert.match(block3cBootstrap, /gcloud services enable "\$CLOUD_BUILD_SERVICE"/);
assert.doesNotMatch(block3cBootstrap, /gcloud services enable (?!"\$CLOUD_BUILD_SERVICE")/);
assert.match(block3cBootstrap, /gcloud builds get-default-service-account/);
assert.match(block3cBootstrap, /--region="\$REGION"/);
assert.match(block3cBootstrap, /--no-source/);
assert.match(block3cBootstrap, /MAX_INITIALIZATION_BUILDS=1/);
assert.doesNotMatch(block3cBootstrap, /firebase deploy|gcloud functions deploy|gcloud run deploy|gcloud app create/);
assert.doesNotMatch(block3cBootstrap, /service-accounts keys create|service-accounts keys delete/);
assert.doesNotMatch(block3cBootstrap, /roles\/owner|roles\/editor|roles\/iam\.serviceAccountTokenCreator/);
```

The closure verifier must statically require:

```js
for (const token of [
  "productionBuildIdentityReady=true",
  "productionWifEndToEndVerified=false",
  "productionDeployEndToEndReady=false",
  "productionIdentityReady=false",
  "productionDeployed=false",
]) assert.match(block3cVerifier, new RegExp(token));
```

- [ ] **Step 2: Build fake-`gcloud` scenarios**

Fake state must track:

```js
{
  projectId: "click-save-ai-production",
  projectNumber: "991489557172",
  cloudBuildEnabled: false,
  discoveryReads: [],
  initializationBuildExit: 0,
  initializationBuildCount: 0,
  serviceEnableCount: 0,
  runtimeBootstrapCount: 0,
  buildSa: "991489557172-compute@developer.gserviceaccount.com",
  events: []
}
```

The fake `gcloud` command dispatcher must support only the commands needed by Block 3C itself; the accepted verifier/runtime bootstrap can be replaced in the scenario directory by deterministic test stubs that emit the required discovery file and record invocation order. This keeps Task 1 focused on Block 3C orchestration rather than re-testing the entire accepted Block 3B.3C implementation.

- [ ] **Step 3: Add behavioral tests**

Create tests proving:

```text
1 exact Production target accepted
2 staging target rejected before mutation
3 legacy target rejected before mutation
4 arbitrary target rejected before mutation
5 disabled service -> exactly one cloudbuild enable
6 already enabled -> zero enable calls
7 enabled + immediate identity -> zero initialization builds
8 empty identity -> bounded polling before build fallback
9 still empty -> exactly one no-source initialization build
10 build success + later identity -> accepted runtime bootstrap invoked once
11 build failure + still empty -> hard fail
12 build failure + later identity -> may continue only through accepted bootstrap validation
13 second configured rerun -> zero API enable + zero init build + no duplicate orchestration
14 no source/artifact/deploy semantics in initialization config
15 accepted verifier blob mismatch -> fail before mutation
16 runtime bootstrap must occur only after non-empty identity discovery
17 Block 3C closure verifier rejects deferred/empty build identity
18 Block 3C closure verifier accepts enabled + READY + non-empty build identity
19 closure truth keeps WIF/deploy/overall identity false
20 bash -n on both future scripts
```

- [ ] **Step 4: Run the focused test and verify RED**

Run:

```bash
cd functions
node --test test/productionBuildIdentityBlock3C.test.js
```

Expected: FAIL because the two Block 3C scripts do not exist.

- [ ] **Step 5: Commit the red tests**

```bash
git add functions/test/productionBuildIdentityBlock3C.test.js
git commit -m "test: define Block 3C build identity contract"
```

---

### Task 2: Implement the read-only Block 3C closure verifier

**Files:**
- Create: `scripts/verify-production-build-identity.sh`
- Test: `functions/test/productionBuildIdentityBlock3C.test.js`

**Interfaces:**
- Consumes `DISCOVERY_OUTPUT` support from `scripts/verify-production-runtime-build-actas.sh`.
- Produces sanitized truth lines only; no secret-bearing output.

- [ ] **Step 1: Add the verifier shell skeleton**

Required constants:

```bash
EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
ACCEPTED_VERIFIER_BLOB="1a60a70dba55eff3423b2599c8a30810aecb79a8"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_VERIFIER="$ROOT/scripts/verify-production-runtime-build-actas.sh"
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"
```

Require `gcloud`, `git`, `curl`, `python3`, and `timeout`, reject all non-exact project IDs, and verify the project ID/number live.

- [ ] **Step 2: Lock the accepted verifier blob**

Use:

```bash
actual_blob="$(git -C "$ROOT" hash-object "scripts/verify-production-runtime-build-actas.sh")"
[[ "$actual_blob" == "$ACCEPTED_VERIFIER_BLOB" ]] || fatal "accepted Block 3B.3C verifier blob mismatch: $actual_blob"
```

- [ ] **Step 3: Invoke the accepted verifier read-only and parse discovery**

Use a restrictive temporary file:

```bash
DISCOVERY_FILE="$(mktemp)"
trap 'rm -f "$DISCOVERY_FILE"' EXIT
PROJECT_ID="$PROJECT_ID" \
ALLOW_MISSING_ACTAS=0 \
ALLOW_RUNTIME_BOOTSTRAP_GAP=0 \
DISCOVERY_OUTPUT="$DISCOVERY_FILE" \
  bash "$BASE_VERIFIER"
source "$DISCOVERY_FILE"
```

Require exactly:

```bash
[[ "$CLOUD_BUILD_SERVICE_ENABLED" == true ]]
[[ "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED" == true ]]
[[ "$PRODUCTION_BUILD_IDENTITY_STATUS" == READY ]]
[[ -n "$BUILD_SA" ]]
```

The accepted verifier already proves the build SA is Production-owned, exists, has no forbidden broad/service-agent roles, is in the intended inventory, has exact per-SA deployer `actAs`, and that no unintended Production SA has deployer `actAs`.

- [ ] **Step 4: Add independent no-project-wide-SA-User check**

Read the project IAM policy as JSON and fail if any non-empty binding uses `roles/iam.serviceAccountUser` at project scope.

- [ ] **Step 5: Emit closure truth**

Output exactly:

```text
Production Block 3C build identity verification PASSED.
productionBuildIdentityReady=true
productionBuildIdentityStatus=READY
productionBuildIdentityConfigured=true
productionBuildActAsConfigured=true
productionRuntimeBuildActAsConfigured=true
productionWifEndToEndVerified=false
productionDeployEndToEndReady=false
productionIdentityReady=false
productionDeployed=false
```

Also print `buildServiceAccount=<exact discovered email>` and `productionCloudBuildServiceEnabled=true`.

- [ ] **Step 6: Run focused tests**

```bash
cd functions
node --test test/productionBuildIdentityBlock3C.test.js
```

Expected: verifier-related tests PASS; bootstrap-related tests remain RED.

- [ ] **Step 7: Commit**

```bash
git add scripts/verify-production-build-identity.sh functions/test/productionBuildIdentityBlock3C.test.js
git commit -m "feat: verify Block 3C build identity closure"
```

---

### Task 3: Implement the Block 3C Cloud Build initializer

**Files:**
- Create: `scripts/bootstrap-production-build-identity.sh`
- Test: `functions/test/productionBuildIdentityBlock3C.test.js`

**Interfaces:**
- Consumes accepted base verifier and accepted runtime/build bootstrap.
- Produces a fully configured build identity or fails closed.

- [ ] **Step 1: Add constants and fail-closed prerequisites**

Use:

```bash
EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
CLOUD_BUILD_SERVICE="cloudbuild.googleapis.com"
REGION="europe-west1"
ACCEPTED_VERIFIER_BLOB="1a60a70dba55eff3423b2599c8a30810aecb79a8"
SERVICE_ENABLE_TIMEOUT_SECONDS=90
DISCOVERY_TIMEOUT_SECONDS=60
DISCOVERY_POLL_SECONDS=2
MAX_INITIALIZATION_BUILDS=1
```

Reject non-exact projects before any write and verify accepted verifier blob before any write.

- [ ] **Step 2: Run pre-mutation accepted verification**

Invoke the accepted verifier with:

```bash
ALLOW_MISSING_ACTAS=0
ALLOW_RUNTIME_BOOTSTRAP_GAP=0
DISCOVERY_OUTPUT=<temp-file>
```

Permit only these pre-states:

```text
A. CLOUD_BUILD_SERVICE_ENABLED=false + DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION
B. CLOUD_BUILD_SERVICE_ENABLED=true + READY
C. CLOUD_BUILD_SERVICE_ENABLED=true + DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION
```

State C is the permitted partially initialized state where the service exists but Google has not selected a default identity yet.

- [ ] **Step 3: Enable exactly Cloud Build when disabled**

Use exactly:

```bash
gcloud services enable "$CLOUD_BUILD_SERVICE" --project="$PROJECT_ID" --quiet
```

Then poll:

```bash
gcloud services list \
  --project="$PROJECT_ID" \
  --enabled \
  --filter="config.name=$CLOUD_BUILD_SERVICE" \
  --format='value(config.name)'
```

until the single exact row is visible or 90 seconds elapse. Any non-empty unexpected row fails.

- [ ] **Step 4: Implement bounded default identity discovery**

Helper:

```bash
discover_build_sa() {
  timeout 30s gcloud builds get-default-service-account \
    --project="$PROJECT_ID" \
    --region="$REGION" \
    --format='value(serviceAccountEmail)' 2>/dev/null || return $?
}
```

Normalize `projects/$PROJECT_ID/serviceAccounts/` prefix and CR/LF. Poll for up to 60 seconds. Exit 124 is a hard timeout error. Other nonzero discovery exit is a hard error because the service has already been proven enabled.

- [ ] **Step 5: Add one-shot no-source initialization fallback**

Only if bounded discovery ends with an empty identity, create a temp YAML with `umask 077`:

```yaml
steps:
  - name: gcr.io/cloud-builders/gcloud
    entrypoint: bash
    args: ["-ceu", "true"]
```

Submit exactly once:

```bash
gcloud builds submit \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --no-source \
  --config="$INIT_CONFIG" \
  --quiet
```

Do not include `images`, `artifacts`, source, substitutions, secrets, service-account override, deploy commands, or application code.

If submission exits nonzero, perform one final discovery. Continue only if a non-empty identity now exists; otherwise fail.

- [ ] **Step 6: Delegate identity validation and per-SA actAs mutation**

After non-empty discovery, call exactly:

```bash
PROJECT_ID="$PROJECT_ID" bash "$ROOT/scripts/bootstrap-production-runtime-build-actas.sh"
```

Do not implement a second IAM-grant path in Block 3C. The accepted bootstrap remains the sole writer for build `actAs`.

- [ ] **Step 7: Run independent Block 3C verifier**

```bash
PROJECT_ID="$PROJECT_ID" bash "$ROOT/scripts/verify-production-build-identity.sh"
```

Print sanitized summary:

```text
Production Block 3C Cloud Build identity configured and verified.
initializationBuildSubmitted=<true|false>
```

- [ ] **Step 8: Run focused tests until GREEN**

```bash
cd functions
node --test test/productionBuildIdentityBlock3C.test.js
```

Expected: all Block 3C tests PASS.

- [ ] **Step 9: Run shell syntax checks**

```bash
bash -n scripts/bootstrap-production-build-identity.sh
bash -n scripts/verify-production-build-identity.sh
```

Expected: both exit 0.

- [ ] **Step 10: Commit**

```bash
git add scripts/bootstrap-production-build-identity.sh scripts/verify-production-build-identity.sh functions/test/productionBuildIdentityBlock3C.test.js
git commit -m "feat: initialize Production Cloud Build identity"
```

---

### Task 4: Full regression and static safety audit

**Files:**
- No production code changes expected.
- May modify: `functions/test/productionBuildIdentityBlock3C.test.js` only to correct a test defect, never to weaken a required assertion.

**Interfaces:**
- Validates full repository compatibility before PR creation/live execution.

- [ ] **Step 1: Run full backend test suite**

```bash
cd functions
npm install --ignore-scripts
npm test
```

Expected: 0 failed.

- [ ] **Step 2: Run production dependency severity gate**

```bash
npm audit --omit=dev --audit-level=high
```

Expected: no High/Critical blocking finding.

- [ ] **Step 3: Run exact forbidden-operation scans**

From repository root:

```bash
! grep -nE 'firebase deploy|gcloud functions deploy|gcloud run deploy|gcloud app create' scripts/bootstrap-production-build-identity.sh scripts/verify-production-build-identity.sh
! grep -nE 'service-accounts keys (create|delete)' scripts/bootstrap-production-build-identity.sh scripts/verify-production-build-identity.sh
! grep -nE 'roles/(owner|editor)|roles/iam\.serviceAccountTokenCreator' scripts/bootstrap-production-build-identity.sh scripts/verify-production-build-identity.sh
```

Then verify the only service-enable target is the symbolic `cloudbuild.googleapis.com` constant.

- [ ] **Step 4: Verify accepted file blobs unchanged**

```bash
git hash-object scripts/verify-production-runtime-build-actas.sh
```

Expected exactly `1a60a70dba55eff3423b2599c8a30810aecb79a8`.

Compare `scripts/bootstrap-production-runtime-build-actas.sh` to parent SHA and require zero diff.

- [ ] **Step 5: Run repository security guards used by CI**

```bash
SECRET_AUDIT_OUTPUT=/tmp/block3c-current.json node scripts/repository-secret-audit.mjs current
node scripts/production-readiness-guard.mjs repository
node scripts/production-operations-guard.mjs
```

Expected: PASS / no secret findings.

- [ ] **Step 6: Commit only if a legitimate test correction was required**

```bash
git add functions/test/productionBuildIdentityBlock3C.test.js
git commit -m "test: harden Block 3C build identity regression"
```

Skip this commit when no correction is needed.

---

### Task 5: Open stacked Draft PR and gate on exact-head CI

**Files:**
- No code changes.

**Interfaces:**
- Parent/base branch: `agent/production-enablement-block3b3c-v2-runtime-identity`.
- Head branch: `agent/production-enablement-block3c-build-identity`.

- [ ] **Step 1: Confirm exact ancestry**

Require Block 3C head to be a strict fast-forward descendant of `5d3b3e413805bcf1e301259c1da70099884fb2a2` with zero behind.

- [ ] **Step 2: Open Draft PR**

Title:

```text
Production Enablement Block 3C — initialize Cloud Build identity safely
```

Body must state:

- exact parent SHA;
- allowed live mutation: Cloud Build API only + exact build-SA `actAs` only;
- possible one-shot no-source initialization build;
- accepted Block 3B.3C verifier unchanged;
- no Firebase/Functions deployment;
- WIF/release/overall identity truth stays false;
- no live Block 3C execution performed by repository work.

- [ ] **Step 3: Wait for exact-head workflows**

Required terminal SUCCESS:

```text
Android and Backend CI
Production Enablement Security CI
Production Operations CI
```

Do not execute Production Block 3C while any exact-head workflow is pending, failed, cancelled, or missing.

- [ ] **Step 4: Record final exact HEAD and CI run numbers**

Update PR body/evidence only; do not add a code commit solely to record CI metadata.

---

### Task 6: Live owner-only execution and closure evidence

**Files:**
- No repository changes before execution.
- PR body may be updated after verified live evidence.

**Interfaces:**
- Consumes exact final CI-green Block 3C SHA.
- Requires owner's authenticated Google Cloud Shell because Master Control does not have an authenticated GCP administration channel.

- [ ] **Step 1: Give the owner one paste-safe command block**

The command must fetch and detach-checkout the exact final SHA, assert `HEAD`, then execute only:

```bash
PROJECT_ID=click-save-ai-production bash scripts/bootstrap-production-build-identity.sh
```

No manual role selection or IAM editing instructions.

- [ ] **Step 2: Inspect live output**

Require:

```text
Production runtime/build actAs verification PASSED.
Production Block 3C build identity verification PASSED.
productionBuildIdentityStatus=READY
productionBuildIdentityConfigured=true
productionBuildActAsConfigured=true
productionRuntimeBuildActAsConfigured=true
productionBuildIdentityReady=true
productionWifEndToEndVerified=false
productionDeployEndToEndReady=false
productionIdentityReady=false
productionDeployed=false
```

If any line is absent or false unexpectedly, stop and diagnose; do not proceed to release work.

- [ ] **Step 3: Independently verify final live truth**

Have the owner run only the read-only verifier if needed:

```bash
PROJECT_ID=click-save-ai-production bash scripts/verify-production-build-identity.sh
```

- [ ] **Step 4: Update the Draft PR closure record**

Record sanitized evidence: execution date, exact SHA, discovered build SA, whether the initialization build was needed, verifier PASS, and exact truth states. Keep PR Draft/Open/Unmerged.

- [ ] **Step 5: Declare Block 3C closed**

Closure wording:

```text
Block 3C is repository-complete and live-verified for Cloud Build default identity initialization and exact build actAs. WIF end-to-end, Production release execution, Firebase deployment, and overall productionIdentityReady remain intentionally deferred.
```

---

## Plan Self-Review

- Spec coverage: all API, IAM, no-deploy, idempotence, one-shot initialization, accepted-verifier lock, CI, and live-evidence requirements map to Tasks 1–6.
- Placeholder scan: no TBD/TODO/implement-later placeholders.
- Interface consistency: `BUILD_SA`, `CLOUD_BUILD_SERVICE_ENABLED`, `BUILD_IDENTITY_DISCOVERY_ATTEMPTED`, and `PRODUCTION_BUILD_IDENTITY_STATUS` are consumed exactly from the accepted verifier discovery contract; Block 3C output names are fixed consistently across design, tests, verifier, and live gate.
- Scope remains one subsystem: Cloud Build identity initialization and build `actAs` closure. WIF end-to-end is explicitly excluded rather than hidden.