#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-clickandsaveai-staging}"
POOL_ID="${POOL_ID:-github-actions}"
PROVIDER_ID="${PROVIDER_ID:-clickandsaveai}"
GITHUB_REPOSITORY_ID="${GITHUB_REPOSITORY_ID:-1314210715}"
GITHUB_REPOSITORY_OWNER_ID="${GITHUB_REPOSITORY_OWNER_ID:-64756523}"
GITHUB_ENVIRONMENT="${GITHUB_ENVIRONMENT:-staging}"
GITHUB_REF="${GITHUB_REF:-refs/heads/main}"
GITHUB_JOB_WORKFLOW_REF="${GITHUB_JOB_WORKFLOW_REF:-vhanukaev1981/ClickAndSaveAI/.github/workflows/deploy-staging.yml@refs/heads/main}"
GITHUB_OIDC_ISSUER="https://token.actions.githubusercontent.com"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v gcloud >/dev/null 2>&1 || fail "gcloud is required. Use Google Cloud Shell or another trusted administrator shell."

ACTIVE_ACCOUNT="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' | head -n 1)"
[[ -n "$ACTIVE_ACCOUNT" ]] || fail "No active administrator account is authenticated in gcloud."

echo "Validating project $PROJECT_ID as $ACTIVE_ACCOUNT"
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
[[ "$PROJECT_NUMBER" =~ ^[0-9]+$ ]] || fail "Could not resolve the staging project number."

PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"
ATTRIBUTE_MAPPING="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.environment=assertion.environment"
ATTRIBUTE_CONDITION="assertion.repository_id=='${GITHUB_REPOSITORY_ID}' && assertion.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && assertion.environment=='${GITHUB_ENVIRONMENT}' && assertion.ref=='${GITHUB_REF}' && assertion.job_workflow_ref=='${GITHUB_JOB_WORKFLOW_REF}'"

echo
echo "Current provider policy:"
gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" \
  --format='yaml(name,state,oidc.issuerUri,attributeMapping,attributeCondition)'

echo
echo "Applying the verified ClickAndSaveAI staging trust boundary only..."
gcloud iam workload-identity-pools providers update-oidc "$PROVIDER_ID" \
  --project="$PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" \
  --issuer-uri="$GITHUB_OIDC_ISSUER" \
  --attribute-mapping="$ATTRIBUTE_MAPPING" \
  --attribute-condition="$ATTRIBUTE_CONDITION" \
  --display-name="Click&SaveAI GitHub"

echo
echo "Verifying provider policy after repair:"
ACTUAL_CONDITION="$(gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" \
  --format='value(attributeCondition)')"

[[ "$ACTUAL_CONDITION" == "$ATTRIBUTE_CONDITION" ]] || fail "Provider condition did not converge to the verified policy."

gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" \
  --format='yaml(name,state,oidc.issuerUri,attributeMapping,attributeCondition)'

echo
echo "PASS: WIF provider condition repaired without changing IAM roles or service-account bindings."
echo "Provider: $PROVIDER_RESOURCE"
echo "Expected GitHub claims:"
echo "  repository_id       = $GITHUB_REPOSITORY_ID"
echo "  repository_owner_id = $GITHUB_REPOSITORY_OWNER_ID"
echo "  environment         = $GITHUB_ENVIRONMENT"
echo "  ref                 = $GITHUB_REF"
echo "  job_workflow_ref    = $GITHUB_JOB_WORKFLOW_REF"
