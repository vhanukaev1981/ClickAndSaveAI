#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-clickandsaveai-staging}"
POOL_ID="${POOL_ID:-github-actions}"
PROVIDER_ID="${PROVIDER_ID:-clickandsaveai}"
DEPLOY_SA_ID="${DEPLOY_SA_ID:-clickandsaveai-github-deployer}"
GITHUB_REPO="${GITHUB_REPO:-vhanukaev1981/ClickAndSaveAI}"
GITHUB_ENVIRONMENT="${GITHUB_ENVIRONMENT:-staging}"
STAGING_SMOKE_USER_UID="${STAGING_SMOKE_USER_UID:-}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v gcloud >/dev/null 2>&1 || fail "gcloud is required to resolve the numeric project number."
command -v gh >/dev/null 2>&1 || fail "GitHub CLI (gh) is required. Install/authenticate gh or set the variables in the GitHub UI."

gh auth status >/dev/null 2>&1 || fail "GitHub CLI is not authenticated. Run: gh auth login"
[[ -n "$STAGING_SMOKE_USER_UID" ]] || fail "Set STAGING_SMOKE_USER_UID to the Firebase Auth UID of the Gmail-connected staging test account before running this helper."

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
[[ "$PROJECT_NUMBER" =~ ^[0-9]+$ ]] || fail "Could not resolve project number for $PROJECT_ID."

DEPLOY_SA_EMAIL="${DEPLOY_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
WIF_PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"

printf 'Setting keyless staging deployment variables on %s / environment %s...\n' "$GITHUB_REPO" "$GITHUB_ENVIRONMENT"

gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER \
  --repo "$GITHUB_REPO" \
  --env "$GITHUB_ENVIRONMENT" \
  --body "$WIF_PROVIDER_RESOURCE"

gh variable set GCP_DEPLOY_SERVICE_ACCOUNT \
  --repo "$GITHUB_REPO" \
  --env "$GITHUB_ENVIRONMENT" \
  --body "$DEPLOY_SA_EMAIL"

gh variable set STAGING_SMOKE_USER_UID \
  --repo "$GITHUB_REPO" \
  --env "$GITHUB_ENVIRONMENT" \
  --body "$STAGING_SMOKE_USER_UID"

ACTUAL_PROVIDER="$(gh variable get GCP_WORKLOAD_IDENTITY_PROVIDER --repo "$GITHUB_REPO" --env "$GITHUB_ENVIRONMENT" 2>/dev/null || true)"
ACTUAL_SA="$(gh variable get GCP_DEPLOY_SERVICE_ACCOUNT --repo "$GITHUB_REPO" --env "$GITHUB_ENVIRONMENT" 2>/dev/null || true)"
ACTUAL_SMOKE_UID="$(gh variable get STAGING_SMOKE_USER_UID --repo "$GITHUB_REPO" --env "$GITHUB_ENVIRONMENT" 2>/dev/null || true)"

[[ "$ACTUAL_PROVIDER" == "$WIF_PROVIDER_RESOURCE" ]] || fail "GitHub provider variable verification failed."
[[ "$ACTUAL_SA" == "$DEPLOY_SA_EMAIL" ]] || fail "GitHub deploy service-account variable verification failed."
[[ "$ACTUAL_SMOKE_UID" == "$STAGING_SMOKE_USER_UID" ]] || fail "GitHub staging smoke UID verification failed."

cat <<EOF
PASS: GitHub staging variables configured and verified.
GCP_WORKLOAD_IDENTITY_PROVIDER=${WIF_PROVIDER_RESOURCE}
GCP_DEPLOY_SERVICE_ACCOUNT=${DEPLOY_SA_EMAIL}
STAGING_SMOKE_USER_UID is configured and verified (value intentionally not printed).
EOF