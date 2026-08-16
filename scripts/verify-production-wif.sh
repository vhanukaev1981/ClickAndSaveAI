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
GITHUB_REPOSITORY="vhanukaev1981/ClickAndSaveAI"

FAILED=0
pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*" >&2; FAILED=1; }

case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging)
    printf 'FAIL  forbidden non-Production project: %s\n' "$PROJECT_ID" >&2
    exit 1
    ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || { printf 'FAIL  PROJECT_ID must be exactly %s\n' "$EXPECTED_PROJECT_ID" >&2; exit 1; }
command -v gcloud >/dev/null 2>&1 || { echo 'FAIL  gcloud is required' >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo 'FAIL  python3 is required' >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo 'FAIL  curl is required' >&2; exit 1; }
ACTIVE_ACCOUNT="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' 2>/dev/null | head -n 1)"
[[ -n "$ACTIVE_ACCOUNT" ]] || { echo 'FAIL  no active gcloud account' >&2; exit 1; }

ACTUAL_PROJECT_ID="$(gcloud projects describe "$PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$ACTUAL_PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] && pass "Project ID exact" || fail "Project ID mismatch"
[[ "$PROJECT_NUMBER" == "$EXPECTED_PROJECT_NUMBER" ]] && pass "Project Number exact" || fail "Project Number mismatch"
[[ "$PROJECT_NUMBER" == "$EXPECTED_PROJECT_NUMBER" ]] || { printf '\nProduction WIF verification FAILED.\n' >&2; exit 1; }

DEPLOY_SA_EMAIL="${DEPLOY_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
WIF_PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"
WIF_MEMBER="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository_id/${GITHUB_REPOSITORY_ID}"
EXPECTED_CONDITION="attribute.repository_id=='${GITHUB_REPOSITORY_ID}' && attribute.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && attribute.environment=='${GITHUB_ENVIRONMENT}' && attribute.ref=='${GITHUB_REF}' && attribute.workflow_ref=='${GITHUB_WORKFLOW_REF}'"

if gcloud iam service-accounts describe "$DEPLOY_SA_EMAIL" --project="$PROJECT_ID" >/dev/null 2>&1; then
  pass "Production deploy service account exists"
else
  fail "Production deploy service account missing"
fi

USER_KEY_COUNT="$(gcloud iam service-accounts keys list --iam-account="$DEPLOY_SA_EMAIL" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
[[ "$USER_KEY_COUNT" == "0" ]] && pass "user-managed service-account key count = 0" || fail "user-managed service-account key count = $USER_KEY_COUNT"

if gcloud iam workload-identity-pools describe "$POOL_ID" --project="$PROJECT_ID" --location=global >/dev/null 2>&1; then
  pass "Workload Identity Pool exists"
else
  fail "Workload Identity Pool missing"
fi

mapfile -t PROVIDER_IDS < <(gcloud iam workload-identity-pools providers list \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL_ID" \
  --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sed 's#^.*/##')
if [[ "${#PROVIDER_IDS[@]}" -eq 1 && "${PROVIDER_IDS[0]}" == "$PROVIDER_ID" ]]; then
  pass "Production pool contains only the intended provider"
else
  fail "Production pool provider inventory is not exactly [$PROVIDER_ID]"
fi

PROVIDER_JSON="$(gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL_ID" --format=json 2>/dev/null || true)"
if [[ -n "$PROVIDER_JSON" ]]; then
  pass "Production OIDC provider exists"
  ACTUAL_ISSUER="$(printf '%s' "$PROVIDER_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("oidc",{}).get("issuerUri",""))')"
  ACTUAL_CONDITION="$(printf '%s' "$PROVIDER_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("attributeCondition",""))')"
  PROVIDER_DISABLED="$(printf '%s' "$PROVIDER_JSON" | python3 -c 'import json,sys; print(str(json.load(sys.stdin).get("disabled",False)).lower())')"
  [[ "$PROVIDER_DISABLED" == "false" ]] && pass "OIDC provider enabled" || fail "OIDC provider disabled"
  [[ "$ACTUAL_ISSUER" == "$GITHUB_OIDC_ISSUER" ]] && pass "OIDC issuer exact" || fail "OIDC issuer mismatch"
  [[ "$ACTUAL_CONDITION" == "$EXPECTED_CONDITION" ]] && pass "attribute condition exact" || fail "attribute condition mismatch"

  if printf '%s' "$PROVIDER_JSON" | python3 -c '
import json,sys
p=json.load(sys.stdin)
actual=p.get("attributeMapping",{})
expected={
 "google.subject":"assertion.sub",
 "attribute.repository_id":"assertion.repository_id",
 "attribute.repository_owner_id":"assertion.repository_owner_id",
 "attribute.environment":"assertion.environment",
 "attribute.ref":"assertion.ref",
 "attribute.workflow_ref":"assertion.workflow_ref",
}
sys.exit(0 if actual == expected else 1)
'; then
    pass "attribute mapping exact"
  else
    fail "attribute mapping mismatch"
  fi

  if [[ "$PROVIDER_JSON" == *"clickandsaveai-staging"* ]]; then
    fail "staging reference present in Production provider"
  else
    pass "staging references absent from Production provider"
  fi
  if [[ "$ACTUAL_CONDITION" == *"clickandsaveai'"* || "$ACTUAL_CONDITION" == *'clickandsaveai"'* ]]; then
    fail "legacy project reference present in Production condition"
  else
    pass "legacy project reference absent from Production condition"
  fi
else
  fail "Production OIDC provider missing"
fi

mapfile -t WIF_MEMBERS < <(gcloud iam service-accounts get-iam-policy "$DEPLOY_SA_EMAIL" \
  --project="$PROJECT_ID" --flatten='bindings[].members' \
  --filter='bindings.role=roles/iam.workloadIdentityUser' \
  --format='value(bindings.members)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sort -u)
if [[ "${#WIF_MEMBERS[@]}" -eq 1 && "${WIF_MEMBERS[0]}" == "$WIF_MEMBER" ]]; then
  pass "federated Production principal is the sole Workload Identity User"
else
  fail "Workload Identity User binding is missing or broader than intended"
fi

for forbidden_role in roles/iam.serviceAccountUser roles/owner roles/editor; do
  if gcloud projects get-iam-policy "$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=${forbidden_role} AND bindings.members=serviceAccount:${DEPLOY_SA_EMAIL}" \
    --format='value(bindings.role)' 2>/dev/null | grep -Fqx "$forbidden_role"; then
    fail "forbidden project-wide grant exists: $forbidden_role"
  else
    pass "forbidden project-wide grant absent: $forbidden_role"
  fi
done

DEPLOYMENTS_URL="https://api.github.com/repos/${GITHUB_REPOSITORY}/deployments?environment=production&per_page=1"
CURL_HEADERS=(-H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28')
if [[ -n "${GH_TOKEN:-}" ]]; then
  CURL_HEADERS+=( -H "Authorization: Bearer ${GH_TOKEN}" )
elif [[ -n "${GITHUB_TOKEN:-}" ]]; then
  CURL_HEADERS+=( -H "Authorization: Bearer ${GITHUB_TOKEN}" )
fi
DEPLOYMENTS_JSON="$(curl -fsSL "${CURL_HEADERS[@]}" "$DEPLOYMENTS_URL" 2>/dev/null || true)"
if [[ -n "$DEPLOYMENTS_JSON" ]] && printf '%s' "$DEPLOYMENTS_JSON" | python3 -c 'import json,sys; x=json.load(sys.stdin); sys.exit(0 if isinstance(x,list) and len(x)==0 else 1)'; then
  pass "Production deployments = 0"
else
  fail "unable to verify that Production deployments remain zero"
fi

printf '\nExpected GitHub production Environment variables:\n'
printf 'GCP_WORKLOAD_IDENTITY_PROVIDER=%s\n' "$WIF_PROVIDER_RESOURCE"
printf 'GCP_DEPLOY_SERVICE_ACCOUNT=%s\n' "$DEPLOY_SA_EMAIL"

if (( FAILED != 0 )); then
  printf '\nProduction WIF verification FAILED.\n' >&2
  exit 1
fi
printf '\nProduction WIF verification PASSED. Static configuration is verified; no OIDC token exchange is claimed.\n'
