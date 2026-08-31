#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
EXPECTED_BOOTSTRAP_SA="clickandsaveai-github-bootstra@click-save-ai-production.iam.gserviceaccount.com"
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

require_project_boundary() {
  [[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
  command -v gcloud >/dev/null 2>&1 || fail "gcloud is required."
  command -v python3 >/dev/null 2>&1 || fail "python3 is required."

  local actual_project_id actual_project_number active_account
  actual_project_id="$(gcloud projects describe "$PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
  actual_project_number="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
  active_account="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' 2>/dev/null | head -n1)"

  [[ "$actual_project_id" == "$EXPECTED_PROJECT_ID" ]] || fail "Resolved project ID mismatch."
  [[ "$actual_project_number" == "$EXPECTED_PROJECT_NUMBER" ]] || fail "Resolved project number mismatch."
  [[ "$active_account" == "$EXPECTED_BOOTSTRAP_SA" ]] || fail "Controller WIF bootstrap must run as $EXPECTED_BOOTSTRAP_SA"
}

verify_service_account_boundary() {
  local service_account="$1"
  local actual user_key_count
  actual="$(gcloud iam service-accounts describe "$service_account" --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true)"
  [[ "$actual" == "$service_account" ]] || fail "Missing or mismatched service account: $service_account"
  user_key_count="$(gcloud iam service-accounts keys list --iam-account="$service_account" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
  [[ "$user_key_count" == "0" ]] || fail "User-managed keys are forbidden for $service_account"
}

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

configure_boundary() {
  local pool_id="$1" provider_id="$2" workflow_ref="$3" service_account="$4"
  local expected_provider expected_condition expected_member pool_name provider_json provider_ids

  expected_provider="projects/${EXPECTED_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${pool_id}/providers/${provider_id}"
  expected_condition="attribute.repository_id=='${GITHUB_REPOSITORY_ID}' && attribute.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && attribute.environment=='${GITHUB_ENVIRONMENT}' && attribute.ref=='${GITHUB_REF}' && attribute.workflow_ref=='${workflow_ref}'"
  expected_member="principalSet://iam.googleapis.com/projects/${EXPECTED_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${pool_id}/attribute.repository_id/${GITHUB_REPOSITORY_ID}"

  pool_name="$(gcloud iam workload-identity-pools describe "$pool_id" --project="$PROJECT_ID" --location=global --format='value(name)' 2>/dev/null || true)"
  if [[ -z "$pool_name" ]]; then
    gcloud iam workload-identity-pools create "$pool_id" --project="$PROJECT_ID" --location=global --display-name="$pool_id" --description="ClickAndSaveAI exact-workflow GitHub OIDC trust boundary" --quiet >/dev/null
  fi

  mapfile -t provider_ids < <(gcloud iam workload-identity-pools providers list --project="$PROJECT_ID" --location=global --workload-identity-pool="$pool_id" --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sed 's#^.*/##' | sort -u)
  if [[ "${#provider_ids[@]}" -eq 0 ]]; then
    gcloud iam workload-identity-pools providers create-oidc "$provider_id" \
      --project="$PROJECT_ID" \
      --location=global \
      --workload-identity-pool="$pool_id" \
      --display-name="$provider_id" \
      --issuer-uri="$GITHUB_OIDC_ISSUER" \
      --attribute-mapping="$EXPECTED_MAPPING" \
      --attribute-condition="$expected_condition" \
      --quiet >/dev/null
  elif [[ "${#provider_ids[@]}" -ne 1 || "${provider_ids[0]}" != "$provider_id" ]]; then
    fail "Unexpected provider inventory in isolated pool $pool_id"
  fi

  provider_json="$(gcloud iam workload-identity-pools providers describe "$provider_id" --project="$PROJECT_ID" --location=global --workload-identity-pool="$pool_id" --format=json 2>/dev/null || true)"
  [[ -n "$provider_json" ]] || fail "Provider $provider_id is missing after bootstrap."
  verify_provider_json "$provider_json" "$expected_provider" "$expected_condition" || fail "Provider boundary mismatch for $provider_id"

  gcloud iam service-accounts add-iam-policy-binding "$service_account" \
    --project="$PROJECT_ID" \
    --role="roles/iam.workloadIdentityUser" \
    --member="$expected_member" \
    --quiet >/dev/null

  gcloud iam service-accounts get-iam-policy "$service_account" --project="$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=roles/iam.workloadIdentityUser AND bindings.members=${expected_member}" \
    --format='value(bindings.members)' 2>/dev/null | grep -Fqx "$expected_member" \
    || fail "roles/iam.workloadIdentityUser binding was not verified for $service_account"
}

require_project_boundary
verify_service_account_boundary "$EXPECTED_FIREBASE_SA"
verify_service_account_boundary "$EXPECTED_PLAY_SA"

configure_boundary \
  "github-actions-firebase-health" \
  "clickandsaveai-firebase-health" \
  "vhanukaev1981/ClickAndSaveAI/.github/workflows/firebase-production-health-controller.yml@refs/heads/main" \
  "$EXPECTED_FIREBASE_SA"

configure_boundary \
  "github-actions-play-production" \
  "clickandsaveai-play-production" \
  "vhanukaev1981/ClickAndSaveAI/.github/workflows/google-play-production-controller.yml@refs/heads/main" \
  "$EXPECTED_PLAY_SA"

printf 'Isolated Production controller WIF trust boundaries configured and verified.\n'
