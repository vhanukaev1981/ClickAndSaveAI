# Production Enablement Block 3B.3C v2 Runtime Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure the exported Firebase Functions v2 surface to use a dedicated Production runtime service account, preserve the v1 cleanup identity, and make Cloud Build identity safely deferrable when the build service is not initialized.

**Architecture:** Keep one common v2 `setGlobalOptions` call in `functions/src/index.js`, which is loaded first by the configured `src/entry.js`, and add a `projectID` parameter expression so only Production resolves to the dedicated v2 service account. Extend the existing fail-closed runtime/build verifier and bootstrap so v1 and v2 runtime readiness are independent of Cloud Build readiness, with the v2 identity starting at zero project roles. Produce a source-backed runtime permission matrix but grant no application runtime roles in this block.

**Tech Stack:** Node.js 22, Firebase Functions 7.3.0, Firebase Admin 14.2.0, Bash, gcloud CLI, Node `node:test`, GitHub Actions/GitHub.

## Global Constraints

- Environment is exactly `PRODUCTION`; project ID `click-save-ai-production`; project number `991489557172`.
- Repository is exactly `vhanukaev1981/ClickAndSaveAI`; repository ID `1314210715`.
- Parent SHA is exactly `21a3ab694a8e9218152e13fa7e6e9bf1808ec608`; parent branch `agent/production-enablement-block3b3b-v1-runtime-identity`; PR #68 remains Draft/Open/Unmerged.
- Work only on `agent/production-enablement-block3b3c-v2-runtime-identity` stacked on the PR #68 branch.
- Dedicated v2 runtime SA: `clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com`; SA ID `clicksave-v2-runtime`.
- Preserve v1 runtime SA `clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com` and exact project role `roles/datastore.user` for `onPushAccountDeleted`.
- Preserve deploy SA `clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com`.
- Never grant project-wide `roles/iam.serviceAccountUser`.
- Never enable Compute Engine, Cloud Build, or another API in this block; never initialize App Engine.
- Never create service-account keys, deploy Firebase/Functions, merge #67/#68, rebase the chain, or start Block 3C.
- v2 runtime SA receives zero broad/project application roles in this block.
- Secret Manager access, if later approved, is per individual secret only.

---

### Task 1: Add Block 3B.3C contract tests first

**Files:**
- Create: `functions/test/productionRuntimeIdentityBlock3B3C.test.js`
- Modify: `functions/test/productionRuntimeIdentityBlock3B3B.test.js`

**Interfaces:**
- Consumes: repository source files and shell scripts as text.
- Produces: deterministic guards for the dedicated v2 identity, common configuration ordering, v1 preservation, zero-role bootstrap, build deferral, and forbidden operations.

- [ ] **Step 1: Write failing tests for the new v2 contract**

The new test file must assert at minimum:

```js
const prod = "click-save-ai-production";
const v1 = "clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com";
const v2 = "clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com";
const buildDeferred = "DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION";
```

Tests must prove:

- `functions/package.json` main is `src/entry.js`;
- `entry.js` requires `./index` before every other exported v2 module;
- `index.js` imports `projectID`, compares exactly with Production, uses the exact v2 SA and `"default"` fallback, and adds `serviceAccount` to `setGlobalOptions`;
- `pushAccountCleanup.js` still contains the exact v1 SA, Production/default expression, and v1 `runWith` binding;
- bootstrap has exact IDs for v1 and v2, can create both exact service accounts, verifies user-managed key count, and enforces v2 zero project roles;
- verifier identifies the exact v2 SA, requires zero v2 project roles, and emits a distinct build status;
- the exact `gcloud builds get-default-service-account` discovery command remains present;
- Cloud Build API-not-initialized/disabled and empty-result paths map to `DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION` and skip build `actAs`;
- unrelated Cloud Build discovery failures remain hard failures;
- no `gcloud services enable`, `gcloud app create`, default Compute runtime SA dependency, Firebase deployment, Functions deployment, or service-account key creation is introduced;
- no private key, Google API key, or GitHub token pattern appears in changed security files.

Update the historical Block 3B.3B test only where its v2 assertion still names the absent Compute default identity; retain all v1 historical guards.

- [ ] **Step 2: Run focused tests and verify RED**

Run from `functions/`:

```bash
node --test test/productionRuntimeIdentityBlock3B3B.test.js test/productionRuntimeIdentityBlock3B3C.test.js
```

Expected: the new Block 3B.3C assertions fail specifically because the dedicated v2 configuration/bootstrap/build-deferral behavior is not implemented yet.

- [ ] **Step 3: Commit the failing tests**

```bash
git add functions/test/productionRuntimeIdentityBlock3B3B.test.js functions/test/productionRuntimeIdentityBlock3B3C.test.js
git commit -m "test: define Block 3B.3C runtime identity contract"
```

### Task 2: Configure one Production-safe v2 runtime identity

**Files:**
- Modify: `functions/src/index.js`
- Test: `functions/test/productionRuntimeIdentityBlock3B3C.test.js`

**Interfaces:**
- Consumes: `projectID` from `firebase-functions/params`.
- Produces: `productionV2ServiceAccount`, passed as `serviceAccount` in the existing common `setGlobalOptions` call.

- [ ] **Step 1: Implement the minimum common v2 configuration**

Use the existing `index.js` `setGlobalOptions` location and add:

```js
const { defineSecret, defineString, projectID } = require("firebase-functions/params");

const PRODUCTION_V2_SERVICE_ACCOUNT =
  "clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com";
const productionV2ServiceAccount = projectID
  .equals("click-save-ai-production")
  .thenElse(PRODUCTION_V2_SERVICE_ACCOUNT, "default");
```

Then extend the existing options only:

```js
setGlobalOptions({
  region: "europe-west1",
  maxInstances: 10,
  memory: "256MiB",
  timeoutSeconds: 60,
  serviceAccount: productionV2ServiceAccount,
});
```

Do not change any function definition or v1 module.

- [ ] **Step 2: Run focused tests and verify GREEN for application configuration**

```bash
node --test test/productionRuntimeIdentityBlock3B3B.test.js test/productionRuntimeIdentityBlock3B3C.test.js
```

Expected: application-configuration assertions pass; bootstrap/verifier assertions may remain red until Task 3.

- [ ] **Step 3: Commit the application change**

```bash
git add functions/src/index.js functions/test/productionRuntimeIdentityBlock3B3C.test.js
git commit -m "feat: bind Production v2 functions to dedicated runtime identity"
```

### Task 3: Separate runtime readiness from build identity readiness

**Files:**
- Modify: `scripts/bootstrap-production-runtime-build-actas.sh`
- Modify: `scripts/verify-production-runtime-build-actas.sh`
- Test: `functions/test/productionRuntimeIdentityBlock3B3C.test.js`

**Interfaces:**
- Consumes: exact v1/v2/deploy identities and live `gcloud` state.
- Produces: runtime identity verification, optional `BUILD_SA`, and `productionBuildIdentityStatus` equal to `READY` or `DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION`.

- [ ] **Step 1: Replace the v2 default Compute identity with the dedicated v2 identity**

Set verifier/bootstrap constants to:

```bash
V2_RUNTIME_SA="clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_V2_RUNTIME_SA_ID="clicksave-v2-runtime"
EXPECTED_V2_RUNTIME_SA="clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com"
```

- [ ] **Step 2: Add exact v2 creation and zero-role validation to bootstrap**

Bootstrap must:

1. describe the exact v2 SA;
2. create `clicksave-v2-runtime` only if absent;
3. verify exact email;
4. verify `--managed-by=user` key count is zero;
5. read project roles for the v2 SA and fail if any exist;
6. never add an application project role;
7. add deployer `roles/iam.serviceAccountUser` only on the individual v2 SA.

Keep the existing v1 `roles/datastore.user` logic unchanged.

- [ ] **Step 3: Add v2 zero-role verification**

The verifier must require the v2 service account to exist outside bootstrap-gap mode and require its project-role set to be empty. Bootstrap-gap mode may permit only the exact v1/v2 dedicated runtime accounts to be absent before creation.

- [ ] **Step 4: Classify Cloud Build discovery safely**

Run the exact discovery command and implement three outcomes:

```text
READY
DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION
HARD FAILURE
```

A successful empty result is deferred. A nonzero result is deferred only when stderr clearly indicates Cloud Build API/service not initialized/enabled, including known `SERVICE_DISABLED`, `API ... not enabled`, or `has not been used in project` forms. Authentication, authorization, malformed target, network, and unclassified errors are hard failures.

When deferred:

- leave `BUILD_SA` empty;
- do not add build identity to the intended service-account set;
- do not mutate build `actAs`;
- continue runtime identity inventory and runtime `actAs` verification;
- emit `productionBuildIdentityStatus=DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION`.

When ready, preserve normal build identity validation and per-SA `actAs` behavior.

- [ ] **Step 5: Run focused tests and Bash syntax**

```bash
node --test test/productionRuntimeIdentityBlock3B3B.test.js test/productionRuntimeIdentityBlock3B3C.test.js
bash -n ../scripts/bootstrap-production-runtime-build-actas.sh
bash -n ../scripts/verify-production-runtime-build-actas.sh
```

Expected: all focused tests pass and both scripts parse successfully.

- [ ] **Step 6: Commit runtime/build sequencing changes**

```bash
git add scripts/bootstrap-production-runtime-build-actas.sh scripts/verify-production-runtime-build-actas.sh functions/test/productionRuntimeIdentityBlock3B3C.test.js
git commit -m "feat: bootstrap dedicated v2 runtime identity safely"
```

### Task 4: Audit the actual v2 runtime permission surface

**Files:**
- Create: `docs/PRODUCTION_V2_RUNTIME_PERMISSION_MATRIX.md`
- Test: `functions/test/productionRuntimeIdentityBlock3B3C.test.js`

**Interfaces:**
- Consumes: actual modules exported by `functions/src/entry.js` and their Firebase Admin/Google Cloud API calls.
- Produces: source-backed IAM permission matrix with no IAM grant side effects.

- [ ] **Step 1: Trace actual exported v2 modules and Google Cloud/ADC calls**

Inspect every module spread into `entry.js`, excluding the v1-only `pushAccountCleanup.js`, and record Firestore, FCM, Secret Manager-bound secrets, Pub/Sub triggers/publishes, scheduler triggers, and any other ADC-backed Google Cloud API calls.

- [ ] **Step 2: Write the matrix**

Each capability row must include:

```text
Capability | Exact module/code | Exact permission | Candidate predefined role | Scope | Block 3B.3C action
```

Rules:

- Firestore role candidates must be documented, not granted to v2 in this block.
- FCM send permission/role candidates must be documented, not granted.
- `roles/secretmanager.secretAccessor` must be documented only at individual-secret scope for each bound secret.
- Pub/Sub/scheduler invocation/service-agent behavior must distinguish trigger infrastructure from runtime ADC access.
- External Gmail OAuth and API-key-backed Gemini calls must not be misclassified as ADC privileges.
- Every application runtime IAM grant is `DEFERRED` unless it is proven necessary for identity bootstrap itself; if such a bootstrap requirement appears, stop for Master approval.

- [ ] **Step 3: Add/extend deterministic matrix guard**

The test must assert the matrix names the dedicated v2 SA, states zero project application roles in Block 3B.3C, forbids project-wide Secret Manager access, and has no `GRANT NOW` application privilege.

- [ ] **Step 4: Run focused tests**

```bash
node --test test/productionRuntimeIdentityBlock3B3B.test.js test/productionRuntimeIdentityBlock3B3C.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit the audit**

```bash
git add docs/PRODUCTION_V2_RUNTIME_PERMISSION_MATRIX.md functions/test/productionRuntimeIdentityBlock3B3C.test.js
git commit -m "docs: audit Production v2 runtime permissions"
```

### Task 5: Full verification and stacked Draft PR

**Files:**
- Verify all Block 3B.3C changed files.

**Interfaces:**
- Consumes: completed branch.
- Produces: exact final SHA and a Draft PR based on `agent/production-enablement-block3b3b-v1-runtime-identity`.

- [ ] **Step 1: Run the complete Functions test suite**

```bash
cd functions
npm test
```

Expected: all tests pass.

- [ ] **Step 2: Run syntax and forbidden-operation scans**

```bash
bash -n ../scripts/bootstrap-production-runtime-build-actas.sh
bash -n ../scripts/verify-production-runtime-build-actas.sh
```

Search changed Block 3B.3C files and fail on:

```text
gcloud services enable
gcloud app create
service-accounts keys create
firebase deploy
gcloud functions deploy
-----BEGIN ... PRIVATE KEY-----
```

- [ ] **Step 3: Compare lineage**

Verify the branch is a strict descendant of `21a3ab694a8e9218152e13fa7e6e9bf1808ec608`, PR #68 is still Draft/Open/Unmerged, and the new PR base is exactly `agent/production-enablement-block3b3b-v1-runtime-identity`.

- [ ] **Step 4: Open a new Draft PR**

Title:

```text
Block 3B.3C: dedicate v2 runtime identity and defer build identity safely
```

The PR body must state that repository implementation was performed without Production IAM mutation, API enablement, deployment, merge, or Block 3C work.

- [ ] **Step 5: Report exact evidence**

Report branch, exact final SHA, Draft PR number/URL, test counts, application/runtime/build identity status semantics, the permission-matrix conclusion, and explicitly state that no live Production IAM mutation was performed by this repository execution.