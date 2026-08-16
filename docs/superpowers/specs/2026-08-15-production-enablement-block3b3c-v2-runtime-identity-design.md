# Production Enablement Block 3B.3C — Dedicated v2 Runtime Identity Design

## Goal

Replace the absent default 2nd-generation runtime identity dependency with one dedicated Production user-managed v2 runtime service account, while preserving the dedicated v1 auth-cleanup identity and allowing Cloud Build identity discovery to defer safely when the build service is not enabled or has not yet produced a default build identity.

## Locked execution target

- Environment: `PRODUCTION`
- GCP/Firebase project: `click-save-ai-production`
- Project number: `991489557172`
- Repository: `vhanukaev1981/ClickAndSaveAI`
- Repository ID: `1314210715`
- Approved parent SHA: `21a3ab694a8e9218152e13fa7e6e9bf1808ec608`
- Approved parent branch: `agent/production-enablement-block3b3b-v1-runtime-identity`
- Parent PR: `#68`, Draft/Open/Unmerged
- Block 3B.3C branch: `agent/production-enablement-block3b3c-v2-runtime-identity`

The implementation must fail closed on any project/repository/identity mismatch. It must not use `clickandsaveai`, `clickandsaveai-staging`, legacy identities, staging identities, or alternate repositories.

## Identity boundaries

### v1 runtime

Preserve exactly:

`clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com`

This identity remains dedicated to `onPushAccountDeleted` and retains exactly the project role `roles/datastore.user`. The existing `projectID.equals("click-save-ai-production").thenElse(..., "default")` and v1 `runWith({ serviceAccount: ... })` semantics remain unchanged. App Engine must not be initialized and the App Engine default service account must not be reintroduced.

### v2 runtime

Create/use exactly:

`clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com`

Service account ID: `clicksave-v2-runtime`.

It is a runtime-only identity for the exported Firebase Functions v2 surface. It must not be reused as the GitHub deploy identity, WIF identity, Cloud Build identity, v1 cleanup identity, or any staging/development identity.

The v2 service account starts with zero project-level application roles. This block establishes the identity boundary only. If runtime application privileges beyond identity bootstrap are needed, they are documented in the permission matrix and deferred for explicit approval rather than granted here.

### deploy identity

Preserve exactly:

`clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com`

The deploy service account may receive `roles/iam.serviceAccountUser` only on individual intended runtime/build service accounts. Project-wide `roles/iam.serviceAccountUser` remains forbidden.

## Application configuration

`functions/package.json` configures `src/entry.js` as the Functions entry module. `entry.js` requires `./index` before the other exported v2 modules. `index.js` calls `setGlobalOptions` before exported v2 function modules are subsequently required from `entry.js`. Therefore the smallest safe change is to keep the common v2 configuration in `index.js` and add a Production-safe parameterized service-account expression there.

The common configuration must use `firebase-functions/params` `projectID`:

```js
const PRODUCTION_V2_SERVICE_ACCOUNT =
  "clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com";
const productionV2ServiceAccount = projectID
  .equals("click-save-ai-production")
  .thenElse(PRODUCTION_V2_SERVICE_ACCOUNT, "default");

setGlobalOptions({
  ...existingOptions,
  serviceAccount: productionV2ServiceAccount,
});
```

Production resolves to the dedicated v2 runtime service account. Every non-Production project resolves to `"default"`. No staging/dev path may resolve to the Production service account.

Deterministic source tests must prove:

1. the package entry module is `src/entry.js`;
2. `entry.js` loads `index.js` before all other exported v2 modules;
3. `setGlobalOptions` is executed in `index.js` before those modules are loaded;
4. the Production project resolves to the exact dedicated v2 runtime service account;
5. non-Production resolves to `"default"`;
6. the v1 cleanup module and semantics are unchanged.

No trigger generation, function name, route, Firestore path, Gmail behavior, commerce behavior, or notification behavior may change.

## Production bootstrap behavior

The existing runtime/build bootstrap remains fail-closed and is extended to establish both dedicated runtime identities.

For v1:

- create/reuse only `clicksave-auth-cleanup`;
- require the exact email and project;
- require zero user-managed keys;
- allow exactly `roles/datastore.user` as its project role;
- grant the deploy service account `roles/iam.serviceAccountUser` only on that service account.

For v2:

- create/reuse only `clicksave-v2-runtime`;
- require the exact email and project;
- require zero user-managed keys;
- require zero project-level application roles in this block;
- reject unexpected project roles;
- grant the deploy service account `roles/iam.serviceAccountUser` only on that service account.

The bootstrap must not create service-account keys, enable APIs, initialize App Engine, deploy Functions/Firebase resources, or grant broad runtime roles.

## Cloud Build identity sequencing

Cloud Build service state must be established before any default build-service-account discovery. The verifier must first run exactly the Production-scoped enabled-service query:

```bash
gcloud services list \
  --project=click-save-ai-production \
  --enabled \
  --filter='config.name:cloudbuild.googleapis.com' \
  --format='value(config.name)'
```

Only when `cloudbuild.googleapis.com` is confirmed enabled may the verifier invoke the authoritative default build identity discovery command:

```bash
gcloud builds get-default-service-account \
  --project=click-save-ai-production \
  --region=europe-west1 \
  --format='value(serviceAccountEmail)'
```

The state machine is deterministic and independent of gcloud error prose:

1. **Service-state query fails** → hard FAIL. No API is enabled.
2. **`cloudbuild.googleapis.com` is not enabled** → `productionCloudBuildServiceEnabled=false`, `productionBuildIdentityDiscoveryAttempted=false`, `BUILD_SA` remains empty, and `productionBuildIdentityStatus=DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION`. The default build identity discovery command must not be invoked and build `actAs` verification/mutation is skipped.
3. **Cloud Build service is enabled** → set `productionCloudBuildServiceEnabled=true`, set `productionBuildIdentityDiscoveryAttempted=true`, and only then run `gcloud builds get-default-service-account`.
4. **Enabled-service identity discovery command fails** → hard FAIL independent of the command's stderr wording.
5. **Enabled-service identity discovery succeeds with an empty identity** → `productionBuildIdentityStatus=DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION`; no substitute identity is created or inferred and build `actAs` is skipped.
6. **Enabled-service identity discovery returns a non-empty Production-owned identity** → `productionBuildIdentityStatus=READY`; normal build identity validation and per-service-account deployer `actAs` checks apply.

The retired prose-based build-service classifier must not be present. Disabled-service deferral is determined solely by the explicit enabled-service query, not by matching gcloud error text.

The verifier must export and print the truth flags `CLOUD_BUILD_SERVICE_ENABLED`, `BUILD_IDENTITY_DISCOVERY_ATTEMPTED`, `PRODUCTION_BUILD_IDENTITY_STATUS`, `BUILD_SA`, `productionCloudBuildServiceEnabled`, `productionBuildIdentityDiscoveryAttempted`, and `productionBuildIdentityStatus`. Runtime identity readiness must remain independent from build identity readiness.

## Runtime privilege audit

This block produces a source-backed permission matrix for the actual exported v2 surface covering at minimum:

- Firestore data access;
- Firebase Cloud Messaging sends;
- bound Secret Manager secrets;
- Pub/Sub interactions;
- scheduled functions;
- any other Google Cloud API invoked through Application Default Credentials.

Each row must include the exact code/module, exact permission and candidate predefined role, recommended scope, and whether it is required for identity bootstrap now or deferred to the later Production runtime/configuration block.

Secret Manager access, when later approved, must use `roles/secretmanager.secretAccessor` only on individual secrets; project-wide Secret Manager Secret Accessor is forbidden.

No application-runtime IAM role is granted in Block 3B.3C. If the audit reveals a privilege that is necessary for identity bootstrap itself, execution must stop and report the exact proposed role set for Master approval instead of granting it.

## Verification and safety

Static and executable tests must prove:

- exact lineage and target constants;
- dedicated v2 Production parameter expression and non-Production default;
- common `setGlobalOptions` ordering across the actual exported v2 surface;
- exact preservation of the v1 service account and `roles/datastore.user` contract;
- v2 creation/reuse, zero user-managed keys, and zero project roles;
- per-service-account deployer `actAs` only;
- no project-wide Service Account User;
- Cloud Build service-state-before-discovery ordering;
- disabled-service deferral without invoking build identity discovery;
- enabled-service discovery failure as a hard failure independent of error prose;
- enabled-service empty identity versus `READY` behavior;
- explicit service/discovery/build truth flags;
- no Compute/App Engine/API initialization commands;
- no deployment commands;
- no service-account key creation;
- no embedded secrets/private keys.

Block 3C is out of scope. No Production deployment, merge, API enablement, or live IAM mutation is performed by repository preparation itself.
