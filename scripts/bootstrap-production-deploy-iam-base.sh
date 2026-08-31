#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
EXPECTED_BOOTSTRAP_SA="clickandsaveai-github-bootstra@click-save-ai-production.iam.gserviceaccount.com"
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
CUSTOM_DEPLOY_ROLE_ID="clickandsaveaiFirebaseDeployIamPolicy"
CUSTOM_DEPLOY_ROLE_TITLE="ClickAndSaveAI Firebase Deploy IAM Policy"
CUSTOM_DEPLOY_ROLE_PERMISSIONS="run.services.setIamPolicy,cloudscheduler.jobs.update"
EXPECTED_ARTIFACT_CLEANUP_DAYS="7"
ARTIFACT_REPOSITORY_ID="gcf-artifacts"
ARTIFACT_CLEANUP_POLICY_ID="firebase-functions-cleanup"
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"
CUSTOM_DEPLOY_ROLE_NAME="projects/${EXPECTED_PROJECT_ID}/roles/${CUSTOM_DEPLOY_ROLE_ID}"

INTENDED_ROLES=(
  roles/cloudfunctions.developer
  roles/firebaserules.admin
  roles/datastore.indexAdmin
  roles/serviceusage.serviceUsageConsumer
  "$CUSTOM_DEPLOY_ROLE_NAME"
)
APPROVED_PREEXISTING_ROLES=(
  "projects/click-save-ai-production/roles/clickandsaveFirebaseMetadataReader"
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
  roles/cloudscheduler.admin
  roles/iam.serviceAccountUser
)

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
contains() {
  local needle="$1"; shift
  local item
  for item in "$@"; do [[ "$item" == "$needle" ]] && return 0; done
  return 1
}
verify_custom_deploy_role() {
  local role_json="$1"
  CUSTOM_ROLE_JSON_INPUT="$role_json" python3 - "$CUSTOM_DEPLOY_ROLE_NAME" "$CUSTOM_DEPLOY_ROLE_PERMISSIONS" <<'PY'
import json, os, sys
expected_name, expected_permissions_csv = sys.argv[1:]
expected_permissions = sorted(expected_permissions_csv.split(','))
role = json.loads(os.environ['CUSTOM_ROLE_JSON_INPUT'])
assert role.get('name') == expected_name
assert role.get('deleted', False) is not True
assert sorted(role.get('includedPermissions', [])) == expected_permissions
PY
}
configure_firebase_adc() {
  if [[ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
    [[ -f "$GOOGLE_APPLICATION_CREDENTIALS" ]] || fail "GOOGLE_APPLICATION_CREDENTIALS points to a missing file."
  else
    local workspace="${GITHUB_WORKSPACE:-$PWD}"
    local adc_files=()
    mapfile -t adc_files < <(find "$workspace" -maxdepth 1 -type f -name 'gha-creds-*.json' -print | sort)
    [[ "${#adc_files[@]}" -eq 1 ]] || fail "Expected exactly one GitHub Actions WIF credentials file for Firebase ADC; found ${#adc_files[@]}."
    export GOOGLE_APPLICATION_CREDENTIALS="${adc_files[0]}"
  fi

  ADC_JSON_INPUT="$(cat "$GOOGLE_APPLICATION_CREDENTIALS")" python3 - "$EXPECTED_WIF_PROVIDER" "$EXPECTED_BOOTSTRAP_SA" <<'PY' || fail "Firebase ADC credential boundary mismatch."
import json, os, sys
expected_provider, expected_sa = sys.argv[1:]
cred = json.loads(os.environ['ADC_JSON_INPUT'])
assert cred.get('type') == 'external_account'
assert 'private_key' not in cred and 'private_key_id' not in cred
assert cred.get('audience') == f'//iam.googleapis.com/{expected_provider}'
url = cred.get('service_account_impersonation_url', '')
encoded_sa = expected_sa.replace('@', '%40')
assert f'/serviceAccounts/{expected_sa}:generateAccessToken' in url or f'/serviceAccounts/{encoded_sa}:generateAccessToken' in url
PY
}
configure_artifact_cleanup() {
  local location="$1"
  local repo_json policy_json

  repo_json="$(gcloud artifacts repositories describe "$ARTIFACT_REPOSITORY_ID" \
    --project="$PROJECT_ID" --location="$location" --format=json 2>/dev/null || true)"
  if [[ -z "$repo_json" ]]; then
    printf 'Artifact Registry repository %s is not present in %s; cleanup policy not required there yet.\n' \
      "$ARTIFACT_REPOSITORY_ID" "$location"
    return 0
  fi

  printf 'Configuring Artifact Registry cleanup policy in %s (%s days).\n' \
    "$location" "$EXPECTED_ARTIFACT_CLEANUP_DAYS"
  gcloud artifacts repositories set-cleanup-policies "$ARTIFACT_REPOSITORY_ID" \
    --project="$PROJECT_ID" \
    --location="$location" \
    --policy="$ARTIFACT_CLEANUP_POLICY_FILE" \
    --no-dry-run \
    --quiet >/dev/null

  policy_json="$(gcloud artifacts repositories describe "$ARTIFACT_REPOSITORY_ID" \
    --project="$PROJECT_ID" --location="$location" --format=json)"
  ARTIFACT_POLICY_JSON_INPUT="$policy_json" python3 - "$ARTIFACT_CLEANUP_POLICY_ID" <<'PY' || fail "Artifact cleanup policy verification failed in $location."
import json, os, sys
policy_id = sys.argv[1]
repo = json.loads(os.environ['ARTIFACT_POLICY_JSON_INPUT'])
policy = (repo.get('cleanupPolicies') or {}).get(policy_id)
assert policy is not None
assert policy.get('id') == policy_id
assert policy.get('action') == 'DELETE'
condition = policy.get('condition') or {}
assert condition.get('tagState') == 'ANY'
assert condition.get('olderThan') == '604800s'
assert repo.get('cleanupPolicyDryRun', False) is False
PY
}

case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging) fail "Refusing forbidden non-Production project: $PROJECT_ID" ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
command -v gcloud >/dev/null 2>&1 || fail "gcloud is required in an authenticated Production administrator shell."
command -v python3 >/dev/null 2>&1 || fail "python3 is required."
command -v firebase >/dev/null 2>&1 || fail "Firebase CLI >= 14 is required to configure Functions artifact cleanup policies."
FIREBASE_CLI_VERSION="$(firebase --version 2>/dev/null || true)"
FIREBASE_CLI_MAJOR="${FIREBASE_CLI_VERSION%%.*}"
[[ "$FIREBASE_CLI_MAJOR" =~ ^[0-9]+$ && "$FIREBASE_CLI_MAJOR" -ge 14 ]] || fail "Firebase CLI >= 14 is required; found '${FIREBASE_CLI_VERSION:-unknown}'."
ACTIVE_ACCOUNT="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' 2>/dev/null | head -n 1)"
[[ -n "$ACTIVE_ACCOUNT" ]] || fail "No active gcloud account is available."

ACTUAL_PROJECT_ID="$(gcloud projects describe "$PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
ACTUAL_PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$ACTUAL_PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "Resolved Project ID differs from $EXPECTED_PROJECT_ID"
[[ "$ACTUAL_PROJECT_NUMBER" == "$EXPECTED_PROJECT_NUMBER" ]] || fail "Project Number must be exactly $EXPECTED_PROJECT_NUMBER"

ACTUAL_DEPLOY_SA="$(gcloud iam service-accounts describe "$EXPECTED_DEPLOY_SA" --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true)"
[[ "$ACTUAL_DEPLOY_SA" == "$EXPECTED_DEPLOY_SA" ]] || fail "Production deploy service account identity mismatch or missing."
USER_KEY_COUNT="$(gcloud iam service-accounts keys list --iam-account="$EXPECTED_DEPLOY_SA" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
[[ "$USER_KEY_COUNT" == "0" ]] || fail "Deploy service account has user-managed keys; refusing IAM mutation."

mapfile -t PROVIDER_IDS < <(gcloud iam workload-identity-pools providers list \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$EXPECTED_POOL_ID" \
  --format='value(name)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sed 's#^.*/##')
[[ "${#PROVIDER_IDS[@]}" -eq 1 && "${PROVIDER_IDS[0]}" == "$EXPECTED_PROVIDER_ID" ]] || fail "Production WIF pool provider inventory is not exact."

PROVIDER_JSON="$(gcloud iam workload-identity-pools providers describe "$EXPECTED_PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$EXPECTED_POOL_ID" --format=json 2>/dev/null || true)"
[[ -n "$PROVIDER_JSON" ]] || fail "Production WIF provider is missing."
PROVIDER_JSON_INPUT="$PROVIDER_JSON" python3 - "$EXPECTED_WIF_PROVIDER" "$GITHUB_OIDC_ISSUER" "$EXPECTED_MAPPING" "$EXPECTED_CONDITION" <<'PY' || fail "Production WIF provider boundary mismatch."
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

mapfile -t WIF_MEMBERS < <(gcloud iam service-accounts get-iam-policy "$EXPECTED_DEPLOY_SA" \
  --project="$PROJECT_ID" --flatten='bindings[].members' \
  --filter='bindings.role=roles/iam.workloadIdentityUser' --format='value(bindings.members)' 2>/dev/null \
  | sed '/^[[:space:]]*$/d' | sort -u)
[[ "${#WIF_MEMBERS[@]}" -eq 1 && "${WIF_MEMBERS[0]}" == "$EXPECTED_WIF_MEMBER" ]] || fail "Production WIF impersonation boundary is missing or broader than intended."

CUSTOM_ROLE_JSON="$(gcloud iam roles describe "$CUSTOM_DEPLOY_ROLE_ID" --project="$PROJECT_ID" --format=json 2>/dev/null || true)"
if [[ -z "$CUSTOM_ROLE_JSON" ]]; then
  gcloud iam roles create "$CUSTOM_DEPLOY_ROLE_ID" \
    --project="$PROJECT_ID" \
    --title="$CUSTOM_DEPLOY_ROLE_TITLE" \
    --description="ClickAndSaveAI Production Firebase deployer: Cloud Run IAM policy and existing Cloud Scheduler job updates only." \
    --permissions="$CUSTOM_DEPLOY_ROLE_PERMISSIONS" \
    --stage=GA \
    --quiet >/dev/null
else
  if ! verify_custom_deploy_role "$CUSTOM_ROLE_JSON"; then
    gcloud iam roles update "$CUSTOM_DEPLOY_ROLE_ID" \
      --project="$PROJECT_ID" \
      --title="$CUSTOM_DEPLOY_ROLE_TITLE" \
      --description="ClickAndSaveAI Production Firebase deployer: Cloud Run IAM policy and existing Cloud Scheduler job updates only." \
      --permissions="$CUSTOM_DEPLOY_ROLE_PERMISSIONS" \
      --stage=GA \
      --quiet >/dev/null
  fi
fi
CUSTOM_ROLE_JSON="$(gcloud iam roles describe "$CUSTOM_DEPLOY_ROLE_ID" --project="$PROJECT_ID" --format=json 2>/dev/null || true)"
[[ -n "$CUSTOM_ROLE_JSON" ]] || fail "Custom Production deploy role is missing after bootstrap."
verify_custom_deploy_role "$CUSTOM_ROLE_JSON" || fail "Custom Production deploy role is not exactly least-privilege; refusing IAM mutation."

mapfile -t CURRENT_PROJECT_ROLES < <(gcloud projects get-iam-policy "$PROJECT_ID" \
  --flatten='bindings[].members' --filter="bindings.members=serviceAccount:${EXPECTED_DEPLOY_SA}" \
  --format='value(bindings.role)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sort -u)
PRESERVED_PREEXISTING_ROLES=()
for role in "${CURRENT_PROJECT_ROLES[@]}"; do
  if contains "$role" "${FORBIDDEN_ROLES[@]}" || [[ "$role" =~ ^roles/.+\.serviceAgent$ ]]; then
    fail "Forbidden project-level role already exists on deploy SA: $role"
  fi
  if contains "$role" "${INTENDED_ROLES[@]}"; then
    continue
  fi
  if contains "$role" "${APPROVED_PREEXISTING_ROLES[@]}"; then
    PRESERVED_PREEXISTING_ROLES+=("$role")
    continue
  fi
  fail "Unexpected pre-existing project role on deploy SA: $role"
done

for role in "${INTENDED_ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${EXPECTED_DEPLOY_SA}" \
    --role="$role" \
    --condition=None \
    --quiet >/dev/null

  if ! gcloud projects get-iam-policy "$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=${role} AND bindings.members=serviceAccount:${EXPECTED_DEPLOY_SA}" \
    --format='value(bindings.role)' 2>/dev/null | grep -Fqx "$role"; then
    fail "Post-write verification failed for $role"
  fi
done

mapfile -t FINAL_PROJECT_ROLES < <(gcloud projects get-iam-policy "$PROJECT_ID" \
  --flatten='bindings[].members' --filter="bindings.members=serviceAccount:${EXPECTED_DEPLOY_SA}" \
  --format='value(bindings.role)' 2>/dev/null | sed '/^[[:space:]]*$/d' | sort -u)
mapfile -t EXPECTED_SORTED < <(printf '%s\n' "${INTENDED_ROLES[@]}" "${PRESERVED_PREEXISTING_ROLES[@]}" | sed '/^[[:space:]]*$/d' | sort -u)
[[ "$(printf '%s\n' "${FINAL_PROJECT_ROLES[@]}")" == "$(printf '%s\n' "${EXPECTED_SORTED[@]}")" ]] || fail "Final deploy-SA project role set is not exactly the intended set plus approved pre-existing roles."

configure_firebase_adc
ARTIFACT_CLEANUP_POLICY_FILE="$(mktemp)"
trap 'rm -f "$ARTIFACT_CLEANUP_POLICY_FILE"' EXIT
cat > "$ARTIFACT_CLEANUP_POLICY_FILE" <<'JSON'
[
  {
    "name": "firebase-functions-cleanup",
    "action": {"type": "Delete"},
    "condition": {
      "tagState": "any",
      "olderThan": "7d"
    }
  }
]
JSON

configure_artifact_cleanup europe-west1
configure_artifact_cleanup us-central1

printf 'Production deploy IAM foundation configured for %s.\n' "$EXPECTED_DEPLOY_SA"
printf 'Custom deploy role permissions exact: %s.\n' "$CUSTOM_DEPLOY_ROLE_PERMISSIONS"
printf 'Artifact cleanup retention configured: %s days where gcf-artifacts exists in europe-west1 and us-central1.\n' "$EXPECTED_ARTIFACT_CLEANUP_DAYS"
printf 'Block 3B.3 runtime/build service-account actAs relationships remain intentionally NOT configured.\n'
