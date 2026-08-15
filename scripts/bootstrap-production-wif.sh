#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"
POOL_ID="${POOL_ID:-github-actions}"
PROVIDER_ID="${PROVIDER_ID:-clickandsaveai-production}"
DEPLOY_SA_ID="${DEPLOY_SA_ID:-clickandsaveai-github-deployer}"
GITHUB_REPOSITORY_ID="1314210715"
GITHUB_REPOSITORY_OWNER_ID="64756523"
GITHUB_ENVIRONMENT="production"
GITHUB_REF="refs/heads/main"
GITHUB_WORKFLOW_REF="vhanukaev1981/ClickAndSaveAI/.github/workflows/production-release.yml@refs/heads/main"
GITHUB_OIDC_ISSUER="https://token.actions.githubusercontent.com"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging)
    fail "Refusing forbidden non-Production project: $PROJECT_ID"
    ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"

command -v gcloud >/dev/null 2>&1 || fail "gcloud is required in an authenticated administrator shell."
ACTIVE_ACCOUNT="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' 2>/dev/null | head -n 1)"
[[ -n "$ACTIVE_ACCOUNT" ]] || fail "No active gcloud account is available."

ACTUAL_PROJECT_ID="$(gcloud projects describe "$PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
[[ "$ACTUAL_PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "Resolved Project ID differs from $EXPECTED_PROJECT_ID"
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$PROJECT_NUMBER" == "$EXPECTED_PROJECT_NUMBER" ]] || fail "Project Number must be exactly $EXPECTED_PROJECT_NUMBER"

DEPLOY_SA_EMAIL="${DEPLOY_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
WIF_PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"
ATTRIBUTE_MAPPING="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.environment=assertion.environment,attribute.ref=assertion.ref,attribute.workflow_ref=assertion.workflow_ref"
ATTRIBUTE_CONDITION="attribute.repository_id=='${GITHUB_REPOSITORY_ID}' && attribute.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && attribute.environment=='${GITHUB_ENVIRONMENT}' && attribute.ref=='${GITHUB_REF}' && attribute.workflow_ref=='${GITHUB_WORKFLOW_REF}'"
WIF_MEMBER="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository_id/${GITHUB_REPOSITORY_ID}"

# WIF/IAM administration and short-lived service-account impersonation only.
gcloud services enable \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  --project="$PROJECT_ID" \
  --quiet >/dev/null

if ! gcloud iam service-accounts describe "$DEPLOY_SA_EMAIL" --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$DEPLOY_SA_ID" \
    --project="$PROJECT_ID" \
    --display-name="Click & Save AI GitHub Production deployer" \
    --description="Keyless GitHub Actions Production deploy identity; product deployment roles intentionally deferred" \
    --quiet >/dev/null
fi

USER_KEY_COUNT="$(gcloud iam service-accounts keys list \
  --iam-account="$DEPLOY_SA_EMAIL" \
  --managed-by=user \
  --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
[[ "$USER_KEY_COUNT" == "0" ]] || fail "Deploy service account has user-managed keys; refusing keyless bootstrap."

if ! gcloud iam workload-identity-pools describe "$POOL_ID" \
  --project="$PROJECT_ID" --location=global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "$POOL_ID" \
    --project="$PROJECT_ID" \
    --location=global \
    --display-name="GitHub Actions Production" \
    --description="Production GitHub Actions keyless federation for Click & Save AI" \
    --quiet >/dev/null
fi

if gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL_ID" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers update-oidc "$PROVIDER_ID" \
    --project="$PROJECT_ID" \
    --location=global \
    --workload-identity-pool="$POOL_ID" \
    --issuer-uri="$GITHUB_OIDC_ISSUER" \
    --attribute-mapping="$ATTRIBUTE_MAPPING" \
    --attribute-condition="$ATTRIBUTE_CONDITION" \
    --display-name="Click Save AI Prod GitHub" \
    --quiet >/dev/null
else
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
    --project="$PROJECT_ID" \
    --location=global \
    --workload-identity-pool="$POOL_ID" \
    --issuer-uri="$GITHUB_OIDC_ISSUER" \
    --attribute-mapping="$ATTRIBUTE_MAPPING" \
    --attribute-condition="$ATTRIBUTE_CONDITION" \
    --display-name="Click Save AI Prod GitHub" \
    --description="Trust only the protected main Production release workflow in the production Environment" \
    --quiet >/dev/null
fi

mapfile -t PROVIDER_IDS < <(gcloud iam workload-identity-pools providers list \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL_ID" \
  --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sed 's#^.*/##')
[[ "${#PROVIDER_IDS[@]}" -eq 1 && "${PROVIDER_IDS[0]}" == "$PROVIDER_ID" ]] || fail "Pool $POOL_ID contains an unexpected provider; refusing cross-provider trust."

PROVIDER_DISABLED="$(gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL_ID" \
  --format='value(disabled)' 2>/dev/null || true)"
[[ "$PROVIDER_DISABLED" != "True" && "$PROVIDER_DISABLED" != "true" ]] || fail "Production provider is disabled; refusing to claim convergence."

gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA_EMAIL" \
  --project="$PROJECT_ID" \
  --member="$WIF_MEMBER" \
  --role="roles/iam.workloadIdentityUser" \
  --condition=None \
  --quiet >/dev/null

mapfile -t WIF_MEMBERS < <(gcloud iam service-accounts get-iam-policy "$DEPLOY_SA_EMAIL" \
  --project="$PROJECT_ID" \
  --flatten='bindings[].members' \
  --filter='bindings.role=roles/iam.workloadIdentityUser' \
  --format='value(bindings.members)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sort -u)
[[ "${#WIF_MEMBERS[@]}" -eq 1 && "${WIF_MEMBERS[0]}" == "$WIF_MEMBER" ]] || fail "Deploy service account has an unexpected Workload Identity User principal."

if gcloud projects get-iam-policy "$PROJECT_ID" \
  --flatten='bindings[].members' \
  --filter="bindings.role=roles/iam.serviceAccountUser AND bindings.members=serviceAccount:${DEPLOY_SA_EMAIL}" \
  --format='value(bindings.role)' 2>/dev/null | grep -Fqx 'roles/iam.serviceAccountUser'; then
  fail "Project-wide roles/iam.serviceAccountUser exists for the Production deploy service account."
fi

printf 'GCP_WORKLOAD_IDENTITY_PROVIDER=%s\n' "$WIF_PROVIDER_RESOURCE"
printf 'GCP_DEPLOY_SERVICE_ACCOUNT=%s\n' "$DEPLOY_SA_EMAIL"
