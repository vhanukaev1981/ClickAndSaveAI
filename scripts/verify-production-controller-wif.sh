#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
EXPECTED_FIREBASE_SA="clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_PLAY_SA="clickandsaveai-play-publisher@click-save-ai-production.iam.gserviceaccount.com"
GITHUB_REPOSITORY_ID="1314210715"
GITHUB_REPOSITORY_OWNER_ID="64756523"
GITHUB_ENVIRONMENT="production"
GITHUB_REF="refs/heads/main"
GITHUB_OIDC_ISSUER="https://token.actions.githubusercontent.com"
EXPECTED_MAPPING="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.environment=assertion.environment,attribute.ref=assertion.ref,attribute.workflow_ref=assertion.workflow_ref"
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

verify_provider_json() {
  local provider_json="$1" expected_name="$2" expected_condition="$3"
  PROVIDER_JSON_INPUT="$provider_json" python3 - "$expected_name" "$EXPECTED_MAPPING" "$expected_condition" "$GITHUB_OIDC_ISSUER" <<'PY'
import json, os, sys
expected_name, mapping_csv, expected_condition, issuer = sys.argv[1:]
p = json.loads(os.environ['PROVIDER_JSON_INPUT'])
expected_mapping = dict(item.split('=', 1) for item in mapping_csv.split(','))
assert p.get('name') == expected_name
assert str(p.get('disabled', False)).lower() == 'false'
assert p.get('oidc', {}).get('issuerUri') == issuer
assert p.get('attributeMapping', {}) == expected_mapping
assert p.get('attributeCondition', '') == expected_condition
PY
}

verify_boundary() {
  local pool_id="$1" provider_id="$2" workflow_ref="$3" service_account="$4"
  local expected_provider expected_condition expected_member provider_json provider_ids

  expected_provider="projects/${EXPECTED_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${pool_id}/providers/${provider_id}"
  expected_condition="attribute.repository_id=='${GITHUB_REPOSITORY_ID}' && attribute.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && attribute.environment=='${GITHUB_ENVIRONMENT}' && attribute.ref=='${GITHUB_REF}' && attribute.workflow_ref=='${workflow_ref}'"
  expected_member="principalSet://iam.googleapis.com/projects/${EXPECTED_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${pool_id}/attribute.repository_id/${GITHUB_REPOSITORY_ID}"

  mapfile -t provider_ids < <(gcloud iam workload-identity-pools providers list --project="$PROJECT_ID" --location=global --workload-identity-pool="$pool_id" --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sed 's#^.*/##' | sort -u)
  [[ "${#provider_ids[@]}" -eq 1 && "${provider_ids[0]}" == "$provider_id" ]] || fail "Isolated pool $pool_id does not contain exactly provider $provider_id"

  provider_json="$(gcloud iam workload-identity-pools providers describe "$provider_id" --project="$PROJECT_ID" --location=global --workload-identity-pool="$pool_id" --format=json 2>/dev/null || true)"
  [[ -n "$provider_json" ]] || fail "Missing provider $provider_id"
  verify_provider_json "$provider_json" "$expected_provider" "$expected_condition" || fail "Provider boundary mismatch for $provider_id"

  gcloud iam service-accounts get-iam-policy "$service_account" --project="$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=roles/iam.workloadIdentityUser AND bindings.members=${expected_member}" \
    --format='value(bindings.members)' 2>/dev/null | grep -Fqx "$expected_member" \
    || fail "roles/iam.workloadIdentityUser binding missing for $service_account"
}

[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
command -v gcloud >/dev/null 2>&1 || fail "gcloud is required."
command -v python3 >/dev/null 2>&1 || fail "python3 is required."
[[ "$(gcloud projects describe "$PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)" == "$EXPECTED_PROJECT_ID" ]] || fail "Resolved project ID mismatch."
[[ "$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)" == "$EXPECTED_PROJECT_NUMBER" ]] || fail "Resolved project number mismatch."

verify_boundary \
  "github-actions-firebase-health" \
  "clickandsaveai-firebase-health" \
  "vhanukaev1981/ClickAndSaveAI/.github/workflows/firebase-production-health-controller.yml@refs/heads/main" \
  "$EXPECTED_FIREBASE_SA"

verify_boundary \
  "github-actions-play-production" \
  "clickandsaveai-play-production" \
  "vhanukaev1981/ClickAndSaveAI/.github/workflows/google-play-production-controller.yml@refs/heads/main" \
  "$EXPECTED_PLAY_SA"

printf 'Isolated Production controller WIF trust boundaries verified.\n'
