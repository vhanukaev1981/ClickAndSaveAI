#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
EXPECTED_BUILD_SA="991489557172-compute@developer.gserviceaccount.com"
LEGACY_BUILD_SA="991489557172@cloudbuild.gserviceaccount.com"
EXPECTED_V1_RUNTIME_SA="clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_V2_RUNTIME_SA="clicksave-v2-runtime@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_DEPLOY_SA="clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_V1_RUNTIME_ROLE="roles/datastore.user"
EDITOR_ROLE="roles/editor"
BUILDER_ROLE="roles/cloudbuild.builds.builder"
CLOUD_BUILD_SERVICE="cloudbuild.googleapis.com"
REGION="europe-west1"
ACCEPTED_VERIFIER_BLOB="1a60a70dba55eff3423b2599c8a30810aecb79a8"
ACCEPTED_RUNTIME_BOOTSTRAP_BLOB="53ecc26c2842df891699c4b3e2446dc5bd406354"
REPO="vhanukaev1981/ClickAndSaveAI"
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_VERIFIER="$ROOT/scripts/verify-production-runtime-build-actas.sh"
RUNTIME_BOOTSTRAP="$ROOT/scripts/bootstrap-production-runtime-build-actas.sh"
MODE="apply"

fatal() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS  %s\n' "$*"; }
contains() { local needle="$1" item; shift; for item in "$@"; do [[ "$item" == "$needle" ]] && return 0; done; return 1; }
roles_for() {
  gcloud projects get-iam-policy "$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.members=serviceAccount:$1" \
    --format='value(bindings.role)' 2>/dev/null | sed '/^$/d' | sort -u
}
has_actas() {
  gcloud iam service-accounts get-iam-policy "$1" --project="$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=roles/iam.serviceAccountUser AND bindings.members=serviceAccount:${EXPECTED_DEPLOY_SA}" \
    --format='value(bindings.members)' 2>/dev/null | grep -Fqx "serviceAccount:${EXPECTED_DEPLOY_SA}"
}
exact_email() {
  gcloud iam service-accounts describe "$1" --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true
}
user_key_count() {
  gcloud iam service-accounts keys list --iam-account="$1" --managed-by=user --format='value(name)' 2>/dev/null | sed '/^$/d' | wc -l | tr -d '[:space:]'
}

case "${1:-}" in
  "") ;;
  --preflight) MODE="preflight" ;;
  *) fatal "unsupported argument: $1" ;;
esac

case "$PROJECT_ID" in clickandsaveai|clickandsaveai-staging) fatal "Refusing forbidden non-Production project: $PROJECT_ID";; esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fatal "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
for c in gcloud git python3 curl timeout; do command -v "$c" >/dev/null || fatal "$c is required"; done
[[ -n "$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' 2>/dev/null | head -1)" ]] || fatal 'no active gcloud account'

actual_verifier_blob="$(git -C "$ROOT" hash-object "scripts/verify-production-runtime-build-actas.sh" 2>/dev/null || true)"
actual_runtime_blob="$(git -C "$ROOT" hash-object "scripts/bootstrap-production-runtime-build-actas.sh" 2>/dev/null || true)"
[[ "$actual_verifier_blob" == "$ACCEPTED_VERIFIER_BLOB" ]] || fatal "accepted verifier blob mismatch: ${actual_verifier_blob:-missing}"
[[ "$actual_runtime_blob" == "$ACCEPTED_RUNTIME_BOOTSTRAP_BLOB" ]] || fatal "accepted runtime bootstrap blob mismatch: ${actual_runtime_blob:-missing}"

PID="$(gcloud projects describe "$EXPECTED_PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
PNUM="$(gcloud projects describe "$EXPECTED_PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$PID" == "$EXPECTED_PROJECT_ID" ]] || fatal "Project ID mismatch: ${PID:-missing}"
[[ "$PNUM" == "$EXPECTED_PROJECT_NUMBER" ]] || fatal "Project Number mismatch: ${PNUM:-missing}"

mapfile -t enabled_rows < <(gcloud services list --project="$PROJECT_ID" --enabled --filter="config.name=$CLOUD_BUILD_SERVICE" --format='value(config.name)' 2>/dev/null | sed '/^$/d' | sort -u)
[[ ${#enabled_rows[@]} -eq 1 && "${enabled_rows[0]}" == "$CLOUD_BUILD_SERVICE" ]] || fatal "Cloud Build service must be exactly enabled before hardening"

set +e
raw_build_sa="$(timeout 30s gcloud builds get-default-service-account --project="$PROJECT_ID" --region="$REGION" --format='value(serviceAccountEmail)' 2>/dev/null)"
discovery_rc=$?
set -e
[[ $discovery_rc -eq 0 ]] || fatal "Cloud Build default service-account discovery failed with exit code $discovery_rc"
build_sa="${raw_build_sa#projects/$PROJECT_ID/serviceAccounts/}"
build_sa="${build_sa//$'\r'/}"
build_sa="${build_sa//$'\n'/}"
[[ -n "$build_sa" ]] || fatal "Cloud Build default service-account discovery returned empty"
if [[ "$build_sa" == "$LEGACY_BUILD_SA" ]]; then fatal "legacy Cloud Build service account is not eligible for automatic Compute Editor remediation: $build_sa"; fi
[[ "$build_sa" == "$EXPECTED_BUILD_SA" ]] || fatal "custom or unexpected build identity is not eligible for automatic Compute Editor remediation: $build_sa"
[[ "$(exact_email "$EXPECTED_BUILD_SA")" == "$EXPECTED_BUILD_SA" ]] || fatal "discovered build identity mismatch or absent: $EXPECTED_BUILD_SA"
[[ "$(user_key_count "$EXPECTED_BUILD_SA")" == 0 ]] || fatal "discovered build identity has user-managed keys"

[[ "$(exact_email "$EXPECTED_V1_RUNTIME_SA")" == "$EXPECTED_V1_RUNTIME_SA" ]] || fatal "dedicated v1 runtime identity mismatch or absent"
[[ "$(exact_email "$EXPECTED_V2_RUNTIME_SA")" == "$EXPECTED_V2_RUNTIME_SA" ]] || fatal "dedicated v2 runtime identity mismatch or absent"
[[ "$(user_key_count "$EXPECTED_V1_RUNTIME_SA")" == 0 ]] || fatal "dedicated v1 runtime SA has user-managed keys"
[[ "$(user_key_count "$EXPECTED_V2_RUNTIME_SA")" == 0 ]] || fatal "dedicated v2 runtime SA has user-managed keys"
mapfile -t v1_roles < <(roles_for "$EXPECTED_V1_RUNTIME_SA")
mapfile -t v2_roles < <(roles_for "$EXPECTED_V2_RUNTIME_SA")
[[ ${#v1_roles[@]} -eq 1 && "${v1_roles[0]}" == "$EXPECTED_V1_RUNTIME_ROLE" ]] || fatal "dedicated v1 runtime SA project roles must equal $EXPECTED_V1_RUNTIME_ROLE"
[[ ${#v2_roles[@]} -eq 0 ]] || fatal "dedicated v2 runtime SA must have zero project roles"
has_actas "$EXPECTED_V1_RUNTIME_SA" || fatal "missing deployer actAs on exact v1 runtime identity"
has_actas "$EXPECTED_V2_RUNTIME_SA" || fatal "missing deployer actAs on exact v2 runtime identity"

POL="$(gcloud projects get-iam-policy "$PROJECT_ID" --format=json 2>/dev/null || true)"
[[ -n "$POL" ]] || fatal "project IAM policy unreadable"
POL="$POL" python3 - <<'PY' || fatal 'project IAM policy violates hardening preconditions'
import json, os
p = json.loads(os.environ['POL'])
assert not any(b.get('role') == 'roles/iam.serviceAccountUser' and b.get('members') for b in p.get('bindings', []))
text = json.dumps(p, sort_keys=True)
assert 'clickandsaveai-staging' not in text
assert 'serviceAccount:clickandsaveai-github-deployer@clickandsaveai.iam.gserviceaccount.com' not in text
PY

URL="https://api.github.com/repos/$REPO/deployments?environment=production&per_page=1"
HEADERS=(-H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28')
[[ -n "${GH_TOKEN:-}" ]] && HEADERS+=(-H "Authorization: Bearer $GH_TOKEN")
[[ -z "${GH_TOKEN:-}" && -n "${GITHUB_TOKEN:-}" ]] && HEADERS+=(-H "Authorization: Bearer $GITHUB_TOKEN")
DEPLOYMENTS="$(curl -fsSL "${HEADERS[@]}" "$URL" 2>/dev/null || true)"
[[ -n "$DEPLOYMENTS" ]] || fatal "unable to verify Production deployments remain zero"
printf '%s' "$DEPLOYMENTS" | python3 -c 'import json,sys;x=json.load(sys.stdin);assert isinstance(x,list) and not x' || fatal "Production deployment inventory is not empty"

mapfile -t roles_before < <(roles_for "$EXPECTED_BUILD_SA")
has_editor=false
has_builder=false
for role in "${roles_before[@]}"; do
  case "$role" in
    "$EDITOR_ROLE") has_editor=true ;;
    "$BUILDER_ROLE") has_builder=true ;;
    *) fatal "discovered build identity has unknown additional project role: $role" ;;
  esac
done

HARDENING_STATE=""
if [[ "$has_editor" == true && "$has_builder" == false ]]; then
  HARDENING_STATE="EDITOR_ONLY"
elif [[ "$has_editor" == true && "$has_builder" == true ]]; then
  HARDENING_STATE="EDITOR_BUILDER"
elif [[ "$has_editor" == false && "$has_builder" == true ]]; then
  HARDENING_STATE="BUILDER_ONLY"
else
  fatal "discovered build identity has neither $EDITOR_ROLE nor $BUILDER_ROLE; refusing to invent a transition"
fi
printf 'productionBuildIdentityHardeningState=%s\n' "$HARDENING_STATE"
printf 'buildServiceAccount=%s\n' "$EXPECTED_BUILD_SA"

if [[ "$MODE" == preflight ]]; then
  pass "hardening preflight exact: $HARDENING_STATE"
  exit 0
fi

if [[ "$has_builder" == false ]]; then
  printf 'Granting exact narrow build role %s to %s\n' "$BUILDER_ROLE" "$EXPECTED_BUILD_SA"
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${EXPECTED_BUILD_SA}" \
    --role="$BUILDER_ROLE" \
    --condition=None \
    --quiet >/dev/null
  mapfile -t roles_after_add < <(roles_for "$EXPECTED_BUILD_SA")
  [[ ${#roles_after_add[@]} -eq 2 ]] || fatal "builder role not visible with exact two-role transitional set"
  contains "$EDITOR_ROLE" "${roles_after_add[@]}" || fatal "Editor unexpectedly absent before approved removal boundary"
  contains "$BUILDER_ROLE" "${roles_after_add[@]}" || fatal "builder role not visible; Editor removal is forbidden"
fi

mapfile -t roles_before_remove < <(roles_for "$EXPECTED_BUILD_SA")
contains "$BUILDER_ROLE" "${roles_before_remove[@]}" || fatal "builder role is not visible immediately before Editor removal"
if contains "$EDITOR_ROLE" "${roles_before_remove[@]}"; then
  printf 'Removing legacy broad role %s from exact build identity %s\n' "$EDITOR_ROLE" "$EXPECTED_BUILD_SA"
  gcloud projects remove-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${EXPECTED_BUILD_SA}" \
    --role="$EDITOR_ROLE" \
    --condition=None \
    --quiet >/dev/null
fi

mapfile -t roles_final < <(roles_for "$EXPECTED_BUILD_SA")
[[ ${#roles_final[@]} -eq 1 && "${roles_final[0]}" == "$BUILDER_ROLE" ]] || fatal "post-hardening build role set is not exactly $BUILDER_ROLE: ${roles_final[*]:-none}"
pass "build identity hardening complete with exact role set $BUILDER_ROLE"
printf 'productionBuildIdentityHardeningComplete=true\n'
