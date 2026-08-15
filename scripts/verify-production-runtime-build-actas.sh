#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
EXPECTED_DEPLOY_SA="clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_POOL_ID="github-actions"
EXPECTED_PROVIDER_ID="clickandsaveai-production"
EXPECTED_WIF_PROVIDER="projects/991489557172/locations/global/workloadIdentityPools/github-actions/providers/clickandsaveai-production"
EXPECTED_RUNTIME_REGION="europe-west1"
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
ALLOW_MISSING_ACTAS="${ALLOW_MISSING_ACTAS:-0}"
DISCOVERY_OUTPUT="${DISCOVERY_OUTPUT:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

INTENDED_DEPLOY_ROLES=(
  roles/cloudfunctions.developer
  roles/firebaserules.admin
  roles/datastore.indexAdmin
  roles/serviceusage.serviceUsageConsumer
)
DANGEROUS_IDENTITY_ROLES=(
  roles/owner
  roles/editor
  roles/firebase.admin
  roles/firebase.developAdmin
  roles/cloudfunctions.admin
  roles/run.admin
  roles/resourcemanager.projectIamAdmin
  roles/iam.serviceAccountAdmin
  roles/iam.serviceAccountTokenCreator
  roles/iam.serviceAccountUser
  roles/secretmanager.admin
  roles/storage.admin
  roles/artifactregistry.admin
)

FAILED=0
pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*" >&2; FAILED=1; }
fatal() { printf 'FAIL  %s\n' "$*" >&2; exit 1; }
contains() {
  local needle="$1"; shift
  local item
  for item in "$@"; do [[ "$item" == "$needle" ]] && return 0; done
  return 1
}
normalize_sa() {
  local value="$1"
  value="${value//$'\r'/}"
  value="${value//$'\n'/}"
  value="${value#projects/${PROJECT_ID}/serviceAccounts/}"
  printf '%s' "$value"
}
validate_production_sa_identity() {
  local label="$1" sa="$2"
  [[ -n "$sa" ]] || fatal "$label identity is empty."
  [[ "$sa" != *"clickandsaveai-staging"* && "$sa" != *"@clickandsaveai."* ]] || fatal "$label identity references staging/legacy project: $sa"
  case "$sa" in
    "${EXPECTED_PROJECT_NUMBER}-compute@developer.gserviceaccount.com") ;;
    "${EXPECTED_PROJECT_NUMBER}@cloudbuild.gserviceaccount.com") ;;
    *"@${EXPECTED_PROJECT_ID}.iam.gserviceaccount.com") ;;
    *) fatal "$label identity is not provably owned by Production project $EXPECTED_PROJECT_ID/$EXPECTED_PROJECT_NUMBER: $sa" ;;
  esac
}
project_roles_for() {
  local sa="$1"
  gcloud projects get-iam-policy "$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.members=serviceAccount:${sa}" \
    --format='value(bindings.role)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sort -u
}
sa_has_deployer_actas() {
  local sa="$1"
  gcloud iam service-accounts get-iam-policy "$sa" --project="$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=roles/iam.serviceAccountUser AND bindings.members=serviceAccount:${EXPECTED_DEPLOY_SA}" \
    --format='value(bindings.members)' 2>/dev/null | sed '/^[[:space:]]*$/d' | grep -Fqx "serviceAccount:${EXPECTED_DEPLOY_SA}"
}

case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging) fatal "forbidden non-Production project: $PROJECT_ID" ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fatal "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
[[ "$ALLOW_MISSING_ACTAS" == "0" || "$ALLOW_MISSING_ACTAS" == "1" ]] || fatal "ALLOW_MISSING_ACTAS must be 0 or 1"
command -v gcloud >/dev/null 2>&1 || fatal "gcloud is required"
command -v python3 >/dev/null 2>&1 || fatal "python3 is required"
command -v curl >/dev/null 2>&1 || fatal "curl is required"

ACTIVE_ACCOUNT="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' 2>/dev/null | head -n 1)"
[[ -n "$ACTIVE_ACCOUNT" ]] || fatal "no active gcloud account"

ACTUAL_PROJECT_ID="$(gcloud projects describe "$PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
ACTUAL_PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$ACTUAL_PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] && pass "Project ID exact" || fail "Project ID mismatch: ${ACTUAL_PROJECT_ID:-missing}"
[[ "$ACTUAL_PROJECT_NUMBER" == "$EXPECTED_PROJECT_NUMBER" ]] && pass "Project Number exact" || fail "Project Number mismatch: ${ACTUAL_PROJECT_NUMBER:-missing}"
[[ "$ACTUAL_PROJECT_ID" == "$EXPECTED_PROJECT_ID" && "$ACTUAL_PROJECT_NUMBER" == "$EXPECTED_PROJECT_NUMBER" ]] || fatal "Production project lock failed before identity discovery."

ACTUAL_DEPLOY_SA="$(gcloud iam service-accounts describe "$EXPECTED_DEPLOY_SA" --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true)"
[[ "$ACTUAL_DEPLOY_SA" == "$EXPECTED_DEPLOY_SA" ]] && pass "deploy SA exact" || fatal "deploy SA mismatch or missing"
USER_KEY_COUNT="$(gcloud iam service-accounts keys list --iam-account="$EXPECTED_DEPLOY_SA" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
[[ "$USER_KEY_COUNT" == "0" ]] && pass "user-managed deploy-SA key count = 0" || fatal "deploy SA has user-managed keys: $USER_KEY_COUNT"

mapfile -t PROVIDER_IDS < <(gcloud iam workload-identity-pools providers list \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$EXPECTED_POOL_ID" \
  --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sed 's#^.*/##')
[[ "${#PROVIDER_IDS[@]}" -eq 1 && "${PROVIDER_IDS[0]}" == "$EXPECTED_PROVIDER_ID" ]] || fatal "Production WIF provider inventory mismatch"
PROVIDER_JSON="$(gcloud iam workload-identity-pools providers describe "$EXPECTED_PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$EXPECTED_POOL_ID" --format=json 2>/dev/null || true)"
[[ -n "$PROVIDER_JSON" ]] || fatal "Production WIF provider missing"
PROVIDER_JSON_INPUT="$PROVIDER_JSON" python3 - "$EXPECTED_WIF_PROVIDER" "$GITHUB_OIDC_ISSUER" "$EXPECTED_MAPPING" "$EXPECTED_CONDITION" <<'PY' || fatal "Production WIF provider boundary mismatch"
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
pass "Production WIF provider exact and enabled"
mapfile -t WIF_MEMBERS < <(gcloud iam service-accounts get-iam-policy "$EXPECTED_DEPLOY_SA" \
  --project="$PROJECT_ID" --flatten='bindings[].members' \
  --filter='bindings.role=roles/iam.workloadIdentityUser' --format='value(bindings.members)' 2>/dev/null \
  | sed '/^[[:space:]]*$/d' | sort -u)
[[ "${#WIF_MEMBERS[@]}" -eq 1 && "${WIF_MEMBERS[0]}" == "$EXPECTED_WIF_MEMBER" ]] || fatal "Production WIF impersonation boundary mismatch"
pass "Production WIF impersonation boundary exact"

mapfile -t DEPLOY_PROJECT_ROLES < <(project_roles_for "$EXPECTED_DEPLOY_SA")
mapfile -t EXPECTED_SORTED < <(printf '%s\n' "${INTENDED_DEPLOY_ROLES[@]}" | sort -u)
[[ "$(printf '%s\n' "${DEPLOY_PROJECT_ROLES[@]}")" == "$(printf '%s\n' "${EXPECTED_SORTED[@]}")" ]] || fatal "deploy-SA project roles do not exactly match Block 3B.2B"
pass "deploy-SA project roles exactly match Block 3B.2B"

PROJECT_POLICY_JSON="$(gcloud projects get-iam-policy "$PROJECT_ID" --format=json 2>/dev/null || true)"
[[ -n "$PROJECT_POLICY_JSON" ]] || fatal "project IAM policy could not be read"
PROJECT_POLICY_JSON_INPUT="$PROJECT_POLICY_JSON" python3 - <<'PY' || fatal "project-wide roles/iam.serviceAccountUser exists"
import json, os
p=json.loads(os.environ['PROJECT_POLICY_JSON_INPUT'])
assert not any(b.get('role') == 'roles/iam.serviceAccountUser' and b.get('members') for b in p.get('bindings', []))
PY
pass "no project-wide roles/iam.serviceAccountUser binding"
if [[ "$PROJECT_POLICY_JSON" == *"clickandsaveai-staging"* || "$PROJECT_POLICY_JSON" == *"serviceAccount:clickandsaveai-github-deployer@clickandsaveai.iam.gserviceaccount.com"* ]]; then
  fatal "staging/legacy principal reference present in Production project IAM"
fi
pass "staging/legacy deploy principal references absent"

python3 - "$ROOT" <<'PY' || fatal "repository runtime/build identity configuration is not the proven Block 3B.3 shape"
from pathlib import Path
import json,re,sys
root=Path(sys.argv[1])
fb=json.loads((root/'firebase.json').read_text())
fn=fb.get('functions',{})
assert fn.get('source')=='functions'
assert fn.get('codebase')=='default'
assert fn.get('runtime')=='nodejs22'
blob='\n'.join(p.read_text(errors='replace') for p in sorted((root/'functions'/'src').glob('*.js')))
assert 'firebase-functions/v2' in blob
for bad in ('firebase-functions/v1', 'serviceAccountEmail'):
    assert bad not in blob, bad
assert not re.search(r'\bserviceAccount\s*:', blob), 'custom runtime serviceAccount option present'
# Reject old generation-specific module imports while allowing v2, params, logger.
for m in re.findall(r'firebase-functions/([^"\'\s)]+)', blob):
    if not (m.startswith('v2') or m.startswith('params') or m.startswith('logger')):
        raise AssertionError(f'unproven firebase-functions import: {m}')
wf=(root/'.github/workflows/production-release.yml').read_text()
assert '--only firestore:rules,firestore:indexes,functions' in wf.replace('\\\n',' ').replace('  ',' '), 'production deployment surface mismatch'
assert '--service-account' not in wf
assert '--build-service-account' not in wf
fb_text=(root/'firebase.json').read_text()
assert 'serviceAccount' not in fb_text
assert 'buildServiceAccount' not in fb_text
PY
pass "repository proves Firebase Functions v2 default runtime identity path and no custom build identity"

RUNTIME_SA="${EXPECTED_PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
validate_production_sa_identity "runtime" "$RUNTIME_SA"
ACTUAL_RUNTIME_EMAIL="$(gcloud iam service-accounts describe "$RUNTIME_SA" --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true)"
[[ "$ACTUAL_RUNTIME_EMAIL" == "$RUNTIME_SA" ]] || fatal "runtime identity mismatch or absent: expected $RUNTIME_SA, observed ${ACTUAL_RUNTIME_EMAIL:-missing}"
pass "runtime identity exists and matches repository-derived 2nd-gen default: $RUNTIME_SA"

BUILD_ERR="$(mktemp)"
trap 'rm -f "$BUILD_ERR"' EXIT
set +e
BUILD_RAW="$(gcloud builds get-default-service-account --project="$PROJECT_ID" --region="$EXPECTED_RUNTIME_REGION" --format='value(serviceAccountEmail)' 2>"$BUILD_ERR")"
BUILD_STATUS=$?
set -e
if [[ "$BUILD_STATUS" -ne 0 ]]; then
  BUILD_MESSAGE="$(tr '\n' ' ' < "$BUILD_ERR" | sed 's/[[:space:]]\+/ /g')"
  fatal "Cloud Build default service-account discovery failed; no API was enabled. gcloud: ${BUILD_MESSAGE:-unknown error}"
fi
BUILD_SA="$(normalize_sa "$BUILD_RAW")"
[[ -n "$BUILD_SA" ]] || fatal "Cloud Build default service-account discovery returned an empty identity; no API was enabled or substituted"
validate_production_sa_identity "build" "$BUILD_SA"
ACTUAL_BUILD_EMAIL="$(gcloud iam service-accounts describe "$BUILD_SA" --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true)"
[[ "$ACTUAL_BUILD_EMAIL" == "$BUILD_SA" ]] || fatal "build identity mismatch or absent: discovered $BUILD_SA, describe observed ${ACTUAL_BUILD_EMAIL:-missing}"
pass "Cloud Build default identity live-discovered with gcloud builds get-default-service-account: $BUILD_SA"

for identity in "$RUNTIME_SA" "$BUILD_SA"; do
  mapfile -t IDENTITY_ROLES < <(project_roles_for "$identity")
  for role in "${IDENTITY_ROLES[@]}"; do
    if contains "$role" "${DANGEROUS_IDENTITY_ROLES[@]}" || [[ "$role" =~ ^roles/.+\.serviceAgent$ ]]; then
      fatal "discovered identity $identity holds unexpectedly dangerous project role: $role"
    fi
  done
  printf 'INFO  identity project roles %s: %s\n' "$identity" "${IDENTITY_ROLES[*]:-(none)}"
  POLICY="$(gcloud iam service-accounts get-iam-policy "$identity" --project="$PROJECT_ID" --format=json 2>/dev/null || true)"
  [[ -n "$POLICY" ]] || fatal "unable to inspect service-account IAM policy for $identity"
done
pass "runtime/build identity risk audit found no forbidden broad project roles"

mapfile -t PROJECT_SERVICE_ACCOUNTS < <(gcloud iam service-accounts list --project="$PROJECT_ID" --format='value(email)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sort -u)
contains "$RUNTIME_SA" "${PROJECT_SERVICE_ACCOUNTS[@]}" || fatal "runtime service account absent from Production service-account inventory"
contains "$BUILD_SA" "${PROJECT_SERVICE_ACCOUNTS[@]}" || fatal "build service account absent from Production service-account inventory"

INTENDED_SAS=("$RUNTIME_SA")
[[ "$BUILD_SA" == "$RUNTIME_SA" ]] || INTENDED_SAS+=("$BUILD_SA")
for sa in "${PROJECT_SERVICE_ACCOUNTS[@]}"; do
  if sa_has_deployer_actas "$sa"; then
    if contains "$sa" "${INTENDED_SAS[@]}"; then
      pass "deploy SA actAs present only on intended identity: $sa"
    else
      fatal "deploy SA has accidental roles/iam.serviceAccountUser on unintended Production service account: $sa"
    fi
  fi
done

if [[ "$ALLOW_MISSING_ACTAS" == "1" ]]; then
  pass "pre-mutation mode allows intended actAs bindings to be absent"
else
  for sa in "${INTENDED_SAS[@]}"; do
    sa_has_deployer_actas "$sa" || fatal "missing deploy-SA roles/iam.serviceAccountUser on intended identity: $sa"
  done
  pass "deploy SA has roles/iam.serviceAccountUser on every and only intended runtime/build identity"
fi

DEPLOYMENTS_URL="https://api.github.com/repos/${GITHUB_REPOSITORY}/deployments?environment=production&per_page=1"
CURL_HEADERS=(-H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28')
if [[ -n "${GH_TOKEN:-}" ]]; then CURL_HEADERS+=( -H "Authorization: Bearer ${GH_TOKEN}" );
elif [[ -n "${GITHUB_TOKEN:-}" ]]; then CURL_HEADERS+=( -H "Authorization: Bearer ${GITHUB_TOKEN}" ); fi
DEPLOYMENTS_JSON="$(curl -fsSL "${CURL_HEADERS[@]}" "$DEPLOYMENTS_URL" 2>/dev/null || true)"
if [[ -n "$DEPLOYMENTS_JSON" ]] && printf '%s' "$DEPLOYMENTS_JSON" | python3 -c 'import json,sys; x=json.load(sys.stdin); sys.exit(0 if isinstance(x,list) and len(x)==0 else 1)'; then
  pass "Production deployments = 0"
else
  fatal "unable to verify Production deployments = 0"
fi

if [[ -n "$DISCOVERY_OUTPUT" ]]; then
  umask 077
  printf 'RUNTIME_SA=%q\nBUILD_SA=%q\n' "$RUNTIME_SA" "$BUILD_SA" > "$DISCOVERY_OUTPUT"
fi

if (( FAILED != 0 )); then
  printf '\nProduction runtime/build actAs verification FAILED.\n' >&2
  exit 1
fi
printf '\nProduction runtime/build actAs verification PASSED.\n'
printf 'runtimeServiceAccount=%s\n' "$RUNTIME_SA"
printf 'buildServiceAccount=%s\n' "$BUILD_SA"
printf 'productionDeployIamConfigured=true\n'
if [[ "$ALLOW_MISSING_ACTAS" == "1" ]]; then
  printf 'productionRuntimeBuildActAsConfigured=false\n'
else
  printf 'productionRuntimeBuildActAsConfigured=true\n'
fi
printf 'productionWifConfigured=true\n'
printf 'productionWifEndToEndVerified=false\n'
printf 'productionDeployEndToEndReady=false\n'
printf 'productionIdentityReady=false\n'
printf 'productionDeployed=false\n'
