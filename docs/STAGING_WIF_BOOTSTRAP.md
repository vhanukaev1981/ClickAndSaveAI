# One-time staging WIF bootstrap

This runbook removes the only current blocker preventing the locked Click&SaveAI Stream A backend from deploying to `clickandsaveai-staging`.

The setup is intentionally keyless. It uses GitHub Actions OIDC -> Google Cloud Workload Identity Federation -> a dedicated staging deploy service account. Do not create or download a JSON service-account key.

## Security boundary

The provider created by this runbook accepts GitHub tokens only when all three immutable/context claims match:

- GitHub repository ID: `1314210715`
- GitHub repository owner ID: `64756523`
- GitHub environment: `staging`

The deploy identity is `clickandsaveai-github-deployer@clickandsaveai-staging.iam.gserviceaccount.com` unless overridden deliberately.

## 1. Use a trusted Google Cloud administrator shell

Google Cloud Shell is a good fit because `gcloud` is already installed. The active account must be allowed to create service accounts, Workload Identity Federation resources and IAM bindings in `clickandsaveai-staging`.

Clone the repository and switch to the isolated ops branch:

```bash
git clone https://github.com/vhanukaev1981/ClickAndSaveAI.git
cd ClickAndSaveAI
git checkout ops/bootstrap-staging-wif
```

## 2. Create/converge WIF and deploy IAM

```bash
bash scripts/bootstrap-staging-wif.sh
```

The script is idempotent. Re-running it converges the same pool/provider/service account instead of creating a new identity.

It dynamically resolves:

- the Google Cloud numeric project number;
- the default Cloud Run functions runtime service account;
- the project's actual Cloud Build default service account.

It grants the deploy service account only the product roles needed by the current Firebase deployment path, plus `Service Account User` on the runtime/Cloud Build identities. It also ensures the Cloud Build identity has `roles/cloudbuild.builds.builder`.

## 3. Verify before touching GitHub variables

```bash
bash scripts/verify-staging-wif.sh
```

Do not continue unless the script ends with:

```text
WIF verification PASSED. No service-account key is required.
```

## 4. Set the two GitHub `staging` environment variables

If GitHub CLI is installed and authenticated with repository-admin permission:

```bash
bash scripts/configure-github-staging-vars.sh
```

It sets and reads back:

- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_DEPLOY_SERVICE_ACCOUNT`

If `gh` is unavailable, the bootstrap/verify scripts print the exact two non-secret values. Set those values in:

`GitHub repository -> Settings -> Environments -> staging -> Environment variables`

Do not store a Google service-account key as a GitHub secret.

## 5. Retry only the locked staging deployment

Current immutable E2E correction baseline:

```text
ac2105098d698df06159f929f41595f91505c855
```

The one-time deployment probe is intentionally pinned to this SHA and to project `clickandsaveai-staging`. The first probe run (`31293189685`) failed before checkout/auth/deploy because the two WIF variables were empty. No partial Firebase deployment occurred.

After the two variables are configured, rerun that locked deployment workflow. Do not move the Stream A branch or install a different APK in between.

## 6. Device E2E artifact paired with the locked backend

Only after the locked backend deploy succeeds, install the signed staging artifact produced by CI run `31292701315`:

- GitHub artifact ID: `9032000365`
- artifact digest: `sha256:cfc918d75b1a10a61867e48fad1646541972a7eb4442797912eebf67fa77628e`
- extracted APK SHA-256: `cbd841ed22f51f7fc1216ee1dd23148fb468965659e9ff3d5aa1831736adfd0a`

Then run E2E correction cycle #2 against the same backend/code tree.

## IAM notes

The bootstrap currently grants the deploy service account:

- `roles/firebase.viewer`
- `roles/cloudfunctions.admin`
- `roles/firebaserules.admin`
- `roles/datastore.indexAdmin`
- `roles/cloudscheduler.admin`
- `roles/secretmanager.viewer`

It grants `roles/iam.serviceAccountUser` only on the function runtime and Cloud Build service accounts, rather than at project scope.

If a future authenticated deployment reports a specific additional missing permission, add the narrow role required by that resource. Do not solve a missing permission by granting `Owner` or `Editor`.

## Queued immediately after E2E #2

The next Stream A item is the authoritative realtime Bills/Room synchronization design documented in `docs/POST_E2E_REALTIME_BILLS_SYNC.md`. It must not move the locked E2E baseline before device validation.
