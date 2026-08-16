#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
EXPECTED_DEPLOY_SA="clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_POOL_ID="github-actions"
EXPECTED_PROVIDER_ID="clickandsaveai-production"
EXPECTED_WIF_PROVIDER="projects/991489557172/locations/global/workloadIdentityPools/github-actions/providers/clickandsaveai-production"
GITHUB_REPOSITORY_ID="1314210715"
GITHUB_REPOSITORY_OWNER_ID="64756523"
GITHUB_ENVIRONMENT="production"
GITHUB_REF="refs/heads/main"
GITHUB_WORKFLOW_REF="vhanukaev1981/ClickAndSaveAI/.github/workflows/production-release.yml@refs/heads/main"
GITHUB_OIDC_ISSUER="https://token.actions.githubusercontent.com"
EXPECTED_MAPPING="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.environment=assertion.environment,attribute.ref=assertion.ref,attribute.workflow_ref=assertion.workflow_ref"
EXPECTED_CONDITION="attribute.repository_id=='${GITHUB_REPOSITORY_ID}' && attribute.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && attribute.environment=='${GITHUB_ENVIRONMENT}' && attribute.ref=='${GITHUB_REF}' && attribute.workflow_ref=='${GITHUB_WORKFLOW_REF}'"
EXPECTED_WIF_MEMBER="principalSet://iam.googleapis.com/projects/${EXPECTED_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${EXPECTED_POOL_ID}/attribute.repository_id/${GITHUB_REPOSITORY_ID}"
GITHUB_REPOSITORY="vhanukaev1981/ClickAndSaveAI"
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"

INTENDED_ROLES=(
  roles/cloudfunctions.developer
  roles/firebaserules.admin
  roles/datastore.indexAdmin
  roles/serviceusage.serviceUsageConsumer
)
FORBIDDEN_ROLES=(
  roles/owner
  roles/editor
  roles/firebase.admin
  roles/firebase.developAdmin
  roles/cloudfunctions.admin
  roles/run.admin
  roles/resourcemanager.projectIamAdmin
  roles/iam.serviceAccountAdmin
  roles/iam.serviceAccountTokenCreator
  roles/secretmanager.admin
  roles/storage.admin
  roles/artifactregistry.admin
  roles/iam.serviceAccountUser
)

FAILED=0
pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*" >&2; FAILED=1; }
contains() {
  local needle="$1"; shift
  local item
  for item in "$@"; do [[ "$item" == "$needle" ]] && return 0; done
  return 1
}

case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging) printf 'FAIL  forbidden non-Production project: %s\n' "$PROJECT_ID" >&2; exit 1 ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || { printf 'FAIL  PROJECT_ID must be exactly %s\n' "$EXPECTED_PROJECT_ID" >&2; exit 1; }
command -v gcloud >/dev/null 2>&1 || { echo 'FAIL  gcloud is required' >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo 'FAIL  python3 is required' >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo 'FAIL  curl is required' >&2; exit 1; }
ACTIVE_ACCOUNT="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' 2>/dev/null | head -n 1)"
[[ -n "$ACTIVE_ACCOUNT" ]] || { echo 'FAIL  no active gcloud account' >&2; exit 1; }

ACTUAL_PROJECT_ID="$(gcloud projects describe "$PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
ACTUAL_PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$ACTUAL_PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] && pass "Project ID exact" || fail "Project ID mismatch"
[[ "$ACTUAL_PROJECT_NUMBER" == "$EXPECTED_PROJECT_NUMBER" ]] && pass "Project Number exact" || fail "Project Number mismatch"
[[ "$ACTUAL_PROJECT_ID" == "$EXPECTED_PROJECT_ID" && "$ACTUAL_PROJECT_NUMBER" == "$EXPECTED_PROJECT_NUMBER" ]] || { printf '\nProduction deploy IAM verification FAILED.\n' >&2; exit 1; }

ACTUAL_DEPLOY_SA="$(gcloud iam service-accounts describe "$EXPECTED_DEPLOY_SA" --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true)"
[[ "$ACTUAL_DEPLOY_SA" == "$EXPECTED_DEPLOY_SA" ]] && pass "deploy SA exact" || fail "deploy SA mismatch or missing"
USER_KEY_COUNT="$(gcloud iam service-accounts keys list --iam-account="$EXPECTED_DEPLOY_SA" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
[[ "$USER_KEY_COUNT" == "0" ]] && pass "user-managed deploy-SA key count = 0" || fail "user-managed deploy-SA key count = $USER_KEY_COUNT"

mapfile -t PROVIDER_IDS < <(gcloud iam workload-identity-pools providers list \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$EXPECTED_POOL_ID" \
  --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sed 's#^.*/##')
[[ "${#PROVIDER_IDS[@]}" -eq 1 && "${PROVIDER_IDS[0]}" == "$EXPECTED_PROVIDER_ID" ]] && pass "Production WIF provider inventory exact" || fail "Production WIF provider inventory mismatch"

PROVIDER_JSON="$(gcloud iam workload-identity-pools providers describe "$EXPECTED_PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$EXPECTED_POOL_ID" --format=json 2>/dev/null || true)"
if [[ -n "$PROVIDER_JSON" ]] && PROVIDER_JSON_INPUT="$PROVIDER_JSON" python3 - "$EXPECTED_WIF_PROVIDER" "$GITHUB_OIDC_ISSUER" "$EXPECTED_MAPPING" "$EXPECTED_CONDITION" <<'PY'
import json, os, sys
expected_name, expected_issuer, mapping_csv, expected_condition = sys.argv[1:]
p = json.loads(os.environ['PROVIDER_JSON_INPUT'])
expected_mapping = dict(x.split('=',1) for x in mapping_csv.split(','))
assert p.get('name') == expected_name
assert str(p.get('disabled', False)).lower() == 'false'
assert p.get('oidc', {}).get('issuerUri') == expected_issuer
assert p.get('attributeMapping', {}) == expected_mapping
assert p.get('attributeCondition', '') == expected_condition
PY
then
  pass "Production WIF provider exact and enabled"
else
  fail "Production WIF provider boundary mismatch"
fi

mapfile -t WIF_MEMBERS < <(gcloud iam service-accounts get-iam-policy "$EXPECTED_DEPLOY_SA" \
  --project="$PROJECT_ID" --flatten='bindings[].members' \
  --filter='bindings.role=roles/iam.workloadIdentityUser' --format='value(bindings.members)' 2>/dev/null \
  | sed '/^[[:space:]]*$/d' | sort -u)
[[ "${#WIF_MEMBERS[@]}" -eq 1 && "${WIF_MEMBERS[0]}" == "$EXPECTED_WIF_MEMBER" ]] && pass "Production WIF impersonation boundary exact" || fail "Production WIF impersonation boundary mismatch"

mapfile -t PROJECT_ROLES < <(gcloud projects get-iam-policy "$PROJECT_ID" \
  --flatten='bindings[].members' --filter="bindings.members=serviceAccount:${EXPECTED_DEPLOY_SA}" \
  --format='value(bindings.role)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sort -u)
mapfile -t EXPECTED_SORTED < <(printf '%s\n' "${INTENDED_ROLES[@]}" | sort -u)
if [[ "$(printf '%s\n' "${PROJECT_ROLES[@]}")" == "$(printf '%s\n' "${EXPECTED_SORTED[@]}")" ]]; then
  pass "deploy-SA project roles exactly match Block 3B.2B"
else
  fail "deploy-SA project roles do not exactly match Block 3B.2B"
fi

for role in "${FORBIDDEN_ROLES[@]}"; do
  if contains "$role" "${PROJECT_ROLES[@]}"; then fail "forbidden deploy-SA project role present: $role"; else pass "forbidden deploy-SA project role absent: $role"; fi
done
for role in "${PROJECT_ROLES[@]}"; do
  [[ "$role" =~ ^roles/.+\.serviceAgent$ ]] && fail "service-agent role must not be granted to GitHub deploy SA: $role"
done
if ! printf '%s\n' "${PROJECT_ROLES[@]}" | grep -Eq '^roles/.+\.serviceAgent$'; then pass "service-agent roles absent from GitHub deploy SA"; fi

POLICY_TEXT="$(gcloud projects get-iam-policy "$PROJECT_ID" --format=json 2>/dev/null || true)"
if [[ "$POLICY_TEXT" == *"serviceAccount:clickandsaveai-github-deployer@clickandsaveai-staging.iam.gserviceaccount.com"* || "$POLICY_TEXT" == *"serviceAccount:clickandsaveai-github-deployer@clickandsaveai.iam.gserviceaccount.com"* ]]; then
  fail "legacy/staging deploy principal reference present in project IAM policy"
else
  pass "legacy/staging deploy principal references absent from project IAM policy"
fi

DEPLOYMENTS_URL="https://api.github.com/repos/${GITHUB_REPOSITORY}/deployments?environment=production&per_page=1"
CURL_HEADERS=(-H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28')
if [[ -n "${GH_TOKEN:-}" ]]; then CURL_HEADERS+=( -H "Authorization: Bearer ${GH_TOKEN}" );
elif [[ -n "${GITHUB_TOKEN:-}" ]]; then CURL_HEADERS+=( -H "Authorization: Bearer ${GITHUB_TOKEN}" ); fi
DEPLOYMENTS_JSON="$(curl -fsSL "${CURL_HEADERS[@]}" "$DEPLOYMENTS_URL" 2>/dev/null || true)"
if [[ -n "$DEPLOYMENTS_JSON" ]] && printf '%s' "$DEPLOYMENTS_JSON" | python3 -c 'import json,sys; x=json.load(sys.stdin); sys.exit(0 if isinstance(x,list) and len(x)==0 else 1)'; then
  pass "Production deployments = 0"
else
  fail "unable to verify Production deployments = 0"
fi

if (( FAILED != 0 )); then
  printf '\nProduction deploy IAM verification FAILED.\n' >&2
  exit 1
fi
printf '\nProduction deploy IAM verification PASSED.\n'
printf 'productionDeployIamConfigured=true\n'
printf 'productionDeployEndToEndReady=false\n'
printf 'Block 3B.3 runtime/build service-account actAs relationships remain intentionally NOT configured.\n'
