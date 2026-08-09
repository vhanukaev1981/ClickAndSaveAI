#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-clickandsaveai-staging}"
POOL_ID="${POOL_ID:-github-actions}"
PROVIDER_ID="${PROVIDER_ID:-clickandsaveai}"
DEPLOY_SA_ID="${DEPLOY_SA_ID:-clickandsaveai-github-deployer}"
GITHUB_REPOSITORY_ID="${GITHUB_REPOSITORY_ID:-1314210715}"
GITHUB_REPOSITORY_OWNER_ID="${GITHUB_REPOSITORY_OWNER_ID:-64756523}"
GITHUB_ENVIRONMENT="${GITHUB_ENVIRONMENT:-staging}"
GITHUB_REF="${GITHUB_REF:-refs/heads/main}"
GITHUB_JOB_WORKFLOW_REF="${GITHUB_JOB_WORKFLOW_REF:-vhanukaev1981/ClickAndSaveAI/.github/workflows/deploy-staging.yml@refs/heads/main}"

pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*" >&2; FAILED=1; }

command -v gcloud >/dev/null 2>&1 || { echo "gcloud is required" >&2; exit 1; }
FAILED=0

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
if [[ "$PROJECT_NUMBER" =~ ^[0-9]+$ ]]; then
  pass "project $PROJECT_ID -> $PROJECT_NUMBER"
else
  fail "cannot resolve project number for $PROJECT_ID"
  exit 1
fi

DEPLOY_SA_EMAIL="${DEPLOY_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
RUNTIME_SA_EMAIL="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
WIF_PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"
WIF_MEMBER="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository_id/${GITHUB_REPOSITORY_ID}"
EXPECTED_CONDITION="assertion.repository_id=='${GITHUB_REPOSITORY_ID}' && assertion.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && assertion.environment=='${GITHUB_ENVIRONMENT}' && assertion.ref=='${GITHUB_REF}' && assertion.job_workflow_ref=='${GITHUB_JOB_WORKFLOW_REF}'"

if gcloud iam service-accounts describe "$DEPLOY_SA_EMAIL" --project="$PROJECT_ID" >/dev/null 2>&1; then
  pass "deploy service account exists"
else
  fail "deploy service account missing: $DEPLOY_SA_EMAIL"
fi

if gcloud iam workload-identity-pools describe "$POOL_ID" \
  --project="$PROJECT_ID" --location=global >/dev/null 2>&1; then
  pass "workload identity pool exists"
else
  fail "workload identity pool missing: $POOL_ID"
fi

if gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL_ID" >/dev/null 2>&1; then
  pass "OIDC provider exists"
  ACTUAL_CONDITION="$(gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
    --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL_ID" \
    --format='value(attributeCondition)')"
  if [[ "$ACTUAL_CONDITION" == "$EXPECTED_CONDITION" ]]; then
    pass "provider condition locks repo ID, owner ID, staging environment, main ref and deploy workflow"
  else
    fail "provider condition differs from expected secure condition"
    printf '      expected: %s\n      actual:   %s\n' "$EXPECTED_CONDITION" "$ACTUAL_CONDITION" >&2
  fi
else
  fail "OIDC provider missing: $PROVIDER_ID"
fi

if gcloud iam service-accounts get-iam-policy "$DEPLOY_SA_EMAIL" \
  --project="$PROJECT_ID" \
  --flatten='bindings[].members' \
  --filter="bindings.role=roles/iam.workloadIdentityUser AND bindings.members=${WIF_MEMBER}" \
  --format='value(bindings.role)' | grep -Fxq 'roles/iam.workloadIdentityUser'; then
  pass "federated repository principal can impersonate deploy service account"
else
  fail "roles/iam.workloadIdentityUser binding missing on deploy service account"
fi

PROJECT_ROLES=(
  roles/firebase.viewer
  roles/cloudfunctions.admin
  roles/firebaserules.admin
  roles/datastore.indexAdmin
  roles/cloudscheduler.admin
  roles/secretmanager.viewer
)

for role in "${PROJECT_ROLES[@]}"; do
  if gcloud projects get-iam-policy "$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=${role} AND bindings.members=serviceAccount:${DEPLOY_SA_EMAIL}" \
    --format='value(bindings.role)' | grep -Fxq "$role"; then
    pass "$DEPLOY_SA_EMAIL has $role"
  else
    fail "$DEPLOY_SA_EMAIL is missing $role"
  fi
done

if gcloud iam service-accounts get-iam-policy "$RUNTIME_SA_EMAIL" \
  --project="$PROJECT_ID" \
  --flatten='bindings[].members' \
  --filter="bindings.role=roles/iam.serviceAccountUser AND bindings.members=serviceAccount:${DEPLOY_SA_EMAIL}" \
  --format='value(bindings.role)' | grep -Fxq 'roles/iam.serviceAccountUser'; then
  pass "deployer can act as Cloud Run functions runtime service account"
else
  fail "deployer cannot act as runtime service account $RUNTIME_SA_EMAIL"
fi

CLOUD_BUILD_SA_EMAIL="$(gcloud builds get-default-service-account --project="$PROJECT_ID" --format='value(serviceAccountEmail)' 2>/dev/null || true)"
if [[ -z "$CLOUD_BUILD_SA_EMAIL" ]]; then
  CLOUD_BUILD_SA_EMAIL="$(gcloud builds get-default-service-account --project="$PROJECT_ID" 2>/dev/null | tail -n 1 | tr -d '[:space:]')"
fi

if [[ "$CLOUD_BUILD_SA_EMAIL" == *@*.gserviceaccount.com ]]; then
  pass "Cloud Build default service account resolved: $CLOUD_BUILD_SA_EMAIL"

  if gcloud iam service-accounts get-iam-policy "$CLOUD_BUILD_SA_EMAIL" \
    --project="$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=roles/iam.serviceAccountUser AND bindings.members=serviceAccount:${DEPLOY_SA_EMAIL}" \
    --format='value(bindings.role)' | grep -Fxq 'roles/iam.serviceAccountUser'; then
    pass "deployer can act as Cloud Build service account"
  else
    fail "deployer cannot act as Cloud Build service account"
  fi

  if gcloud projects get-iam-policy "$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=roles/cloudbuild.builds.builder AND bindings.members=serviceAccount:${CLOUD_BUILD_SA_EMAIL}" \
    --format='value(bindings.role)' | grep -Fxq 'roles/cloudbuild.builds.builder'; then
    pass "Cloud Build service account has roles/cloudbuild.builds.builder"
  else
    fail "Cloud Build service account is missing roles/cloudbuild.builds.builder"
  fi
else
  fail "could not resolve Cloud Build default service account"
fi

printf '\nGitHub staging environment variables should be:\n'
printf 'GCP_WORKLOAD_IDENTITY_PROVIDER=%s\n' "$WIF_PROVIDER_RESOURCE"
printf 'GCP_DEPLOY_SERVICE_ACCOUNT=%s\n' "$DEPLOY_SA_EMAIL"

if (( FAILED != 0 )); then
  printf '\nWIF verification FAILED. Fix the items above before retrying staging deploy.\n' >&2
  exit 1
fi

printf '\nWIF verification PASSED. No service-account key is required.\n'
