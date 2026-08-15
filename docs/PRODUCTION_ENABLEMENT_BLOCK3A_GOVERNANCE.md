# Production Enablement Block 3A — Production Governance & Control Plane

## Scope and authority

This document records the repository-level governance contract for Production Enablement Block 3A. Block 3A is governance-only. It does not authorize a production deployment, Google Play publication, release creation, production credential materialization, monitoring activation, recovery execution, or Block 3B work.

Canonical input:

- Repository: `vhanukaev1981/ClickAndSaveAI`
- Canonical Block 2 branch: `agent/production-enablement-block2-canonical`
- Canonical Block 2 SHA: `2961a9cc701e919e955775c894bc4789d22c8872`
- Canonical Block 2 PR: `#63`
- Canonical Block 2 base: `agent/production-enablement-block1`
- Exact Block 1 SHA: `4f674d27dfec148e10108274de23013ae73613df`

## Live governance audit before Block 3A

The live GitHub state independently observed before this branch was created was:

- Repository visibility: `PUBLIC — UNCHANGED BY BLOCK 3A`
- `main`: unprotected
- GitHub Environments: `staging` only
- `production` Environment: absent
- `staging` deployment branch policy: none
- No repository visibility change is authorized by this block.

These external GitHub settings are not represented as operational merely because this document exists. They must be verified from GitHub after any external administration change.

## Main protection target

The intended non-deadlocking policy for `main` is:

- Require pull-request-based integration.
- Do not require a separate approving reviewer while the repository remains single-owner/single-user; GitHub self-approval restrictions must not create a governance deadlock.
- Require the existing, observed PR check contexts below only after confirming that they run for PRs targeting `main`:
  - `backend-test` — workflow `Android and Backend CI`
  - `android-build` — workflow `Android and Backend CI`
  - `production-security` — workflow `Production Enablement Security CI`
  - `repository-operations-readiness` — workflow `Production Operations CI`
- Disable force pushes.
- Disable branch deletion.
- Apply the protection to administrators/owner for ordinary code changes where GitHub supports this without creating unrecoverable lockout.
- Do not invent or require a status context that is not emitted by the repository.

The three relevant workflows currently contain an unfiltered `pull_request` trigger, so the four observed check contexts can run for PRs targeting `main`.

## Production GitHub Environment target

The Environment name is exactly `production` and must remain separate from `staging`.

Target policy:

- No staging secret or variable may be copied into `production`.
- No fake production secret or variable value may be created.
- Production deployment sources must be restricted to protected production source only; arbitrary feature branches, including `agent/*`, must not be eligible.
- Preferred GitHub deployment branch policy: protected branches only, with `main` protected before this policy is relied upon.
- If a required-reviewer Environment protection rule is available, use the repository owner as the explicit manual gate with self-review allowed; do not enable prevent-self-review for a sole owner.
- If the plan or repository mode does not safely support that rule, truth state is `MANUAL_ENVIRONMENT_APPROVAL_GATE = NOT_AVAILABLE / NOT_SAFE FOR SINGLE OWNER`.
- Creating the Environment alone is not production readiness and must not permit the release workflow to succeed.

## Production release path already present

`.github/workflows/production-release.yml` is manual-only (`workflow_dispatch`) and references `environment: production`.

Its first production-candidate guard fails closed unless all of the following are non-empty and the explicit confirmation phrase is supplied:

### Environment variables

- `PRODUCTION_FIREBASE_PROJECT_ID`
- `PRODUCTION_GOOGLE_WEB_CLIENT_ID`
- `PRODUCTION_APP_SIGNING_CERT_SHA1`
- `PRODUCTION_APP_SIGNING_CERT_SHA256`
- `PRODUCTION_UPLOAD_KEY_ALIAS`

### Environment secrets

- `PRODUCTION_GOOGLE_SERVICES_JSON_B64`
- `PRODUCTION_UPLOAD_KEYSTORE_B64`
- `PRODUCTION_UPLOAD_STORE_PASSWORD`
- `PRODUCTION_UPLOAD_KEY_PASSWORD`

The workflow also rejects `clickandsaveai-staging` as the production Firebase project.

The separately authorized Firebase deployment job additionally requires:

- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_DEPLOY_SERVICE_ACCOUNT`

and can execute only when the explicit workflow input equals `DEPLOY_FIREBASE_PRODUCTION`.

## Authoritative production input inventory

Statuses below describe operational production material, not repository specifications.

| Area | Input | Status | Owner/source | Next action |
|---|---|---|---|---|
| Android signing | Play App Signing certificate identity (SHA-1/SHA-256) | EXTERNAL OWNER ACTION REQUIRED | Google Play Console / owner | Establish Play App Signing and record verified certificate identities in protected production variables. |
| Android signing | Upload signing keystore material | EXTERNAL OWNER ACTION REQUIRED | Owner-controlled secure signing source | Create/retain outside the repository and add only through the protected production secret channel. |
| Android signing | Upload signing key alias | EXTERNAL OWNER ACTION REQUIRED | Owner-controlled signing source | Add verified alias as protected production variable. |
| Android signing | Upload signing store password | EXTERNAL OWNER ACTION REQUIRED | Owner-controlled secure secret source | Add only as protected production secret. |
| Android signing | Upload signing key password | EXTERNAL OWNER ACTION REQUIRED | Owner-controlled secure secret source | Add only as protected production secret. |
| Firebase/GCP | Production Firebase project ID | EXTERNAL OWNER ACTION REQUIRED | Firebase/GCP owner | Create/select a dedicated production project distinct from staging and add its verified ID. |
| Firebase/GCP | Production Android app configuration (`google-services.json`) | EXTERNAL OWNER ACTION REQUIRED | Firebase production Android app | Register the canonical Android app and add the encoded config only as the protected production secret. |
| Firebase/GCP | Deploy identity / Workload Identity Federation provider | EXTERNAL OWNER ACTION REQUIRED | GCP IAM owner | Create least-privilege production WIF and add provider identifier as protected variable. |
| Firebase/GCP | Production deploy service account | EXTERNAL OWNER ACTION REQUIRED | GCP IAM owner | Create least-privilege deploy service account and bind WIF. |
| Firebase/GCP | Production runtime service account | EXTERNAL OWNER ACTION REQUIRED | GCP IAM owner | Create runtime identity with least privilege; do not reuse staging identity. |
| Firebase/GCP | App Check / Play Integrity production configuration | EXTERNAL OWNER ACTION REQUIRED | Firebase / Google Play | Register, verify, and later enforce only after production identity exists. |
| OAuth | Production Android OAuth client | EXTERNAL OWNER ACTION REQUIRED | Google Cloud OAuth / Firebase | Bind canonical package to actual Play App Signing certificate identity. |
| OAuth | Production Web/server OAuth client | EXTERNAL OWNER ACTION REQUIRED | Google Cloud OAuth / Firebase | Create verified production web/server client and expose only the required client ID through protected production configuration. |
| OAuth | Production runtime OAuth secret/material | EXTERNAL OWNER ACTION REQUIRED | Google Cloud OAuth / owner | Store only in the protected production secret/runtime secret channel; never commit it. |
| Monitoring | Production log-based metrics | EXTERNAL OWNER ACTION REQUIRED | Production GCP project | Materialize metrics from the repository monitoring specification after production exists. |
| Monitoring | Production alert policies | EXTERNAL OWNER ACTION REQUIRED | Production GCP project | Create and validate alert policies after metrics exist. |
| Monitoring | Production notification channels | EXTERNAL OWNER ACTION REQUIRED | Owner / operations | Configure real notification targets outside the repository and validate delivery. |
| Monitoring | Production dashboards | EXTERNAL OWNER ACTION REQUIRED | Production GCP project | Materialize dashboards from repository specification and verify live data. |

## Fail-closed contract

The production control plane must not weaken the existing release gate.

While any mandatory production value is absent:

1. A production release candidate must fail before signing materialization/build completion.
2. Staging Firebase configuration is rejected as a production target.
3. Normal CI release artifacts remain unsigned audit artifacts and are not production candidates.
4. A Firebase production deployment additionally requires explicit `DEPLOY_FIREBASE_PRODUCTION` authorization plus valid WIF/deploy identity.
5. Missing external production identity, Firebase, OAuth, signing, monitoring, legal, or recovery evidence must remain visibly missing and must not be represented by placeholders.

## Truth-state boundary for Block 3A

Repository documentation or CI success cannot by itself set external readiness true.

Until live GitHub administration and real external material are independently verified:

- `repositoryReady=true`
- `productionControlPlaneReady=false`
- `productionIdentityReady=false`
- `productionFirebaseReady=false`
- `productionOAuthReady=false`
- `productionSigningReady=false`
- `productionRecoveryVerified=false`
- `monitoringActive=false`
- `alertsActive=false`
- `legalApproval=false`
- `productionDeployed=false`
- `googlePlayPublished=false`

`productionControlPlaneReady` may become true only after live verification that `main` is protected and the `production` Environment exists with the intended source restrictions and any safely available manual gate, while all production credentials remain absent unless separately supplied by their external owner.