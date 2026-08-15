#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_RUNTIME_SA_ID="clicksave-auth-cleanup"
EXPECTED_RUNTIME_SA="clicksave-auth-cleanup@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_V2_RUNTIME_SA="991489557172-compute@developer.gserviceaccount.com"
EXPECTED_DEPLOY_SA="clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com"
EXPECTED_RUNTIME_ROLE="roles/datastore.user"
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFIER="$ROOT/scripts/verify-production-runtime-build-actas.sh"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
append_unique() {
  local value="$1" array_name="$2" item
  local -n array_ref="$array_name"
  for item in "${array_ref[@]:-}"; do [[ "$item" == "$value" ]] && return 0; done
  array_ref+=("$value")
}
contains() {
  local needle="$1" item
  shift
  for item in "$@"; do [[ "$item" == "$needle" ]] && return 0; done
  return 1
}
runtime_roles() {
  gcloud projects get-iam-policy "$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.members=serviceAccount:${EXPECTED_RUNTIME_SA}" \
    --format='value(bindings.role)' 2>/dev/null | sed '/^$/d' | sort -u
}

case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging) fail "Refusing forbidden non-Production project: $PROJECT_ID" ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
[[ -x "$VERIFIER" ]] || fail "Verifier is missing or not executable: $VERIFIER"
command -v gcloud >/dev/null || fail "gcloud is required"

DISCOVERY_FILE="$(mktemp)"
trap 'rm -f "$DISCOVERY_FILE"' EXIT

printf 'Running fail-closed pre-mutation verification and live identity discovery...\n'
PROJECT_ID="$PROJECT_ID" \
ALLOW_MISSING_ACTAS=1 \
ALLOW_RUNTIME_BOOTSTRAP_GAP=1 \
DISCOVERY_OUTPUT="$DISCOVERY_FILE" \
  bash "$VERIFIER"
# shellcheck disable=SC1090
source "$DISCOVERY_FILE"
declare -p RUNTIME_SAS >/dev/null 2>&1 || fail "Verifier did not emit a proven runtime identity array."
[[ "${#RUNTIME_SAS[@]}" -gt 0 && -n "${BUILD_SA:-}" ]] || fail "Verifier did not emit proven runtime/build identities."
contains "$EXPECTED_RUNTIME_SA" "${RUNTIME_SAS[@]}" || fail "Verifier did not derive the exact dedicated v1 runtime identity."
contains "$EXPECTED_V2_RUNTIME_SA" "${RUNTIME_SAS[@]}" || fail "Verifier did not independently derive the exact v2 runtime identity."

runtime_email="$(gcloud iam service-accounts describe "$EXPECTED_RUNTIME_SA" \
  --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true)"
if [[ -z "$runtime_email" ]]; then
  printf 'Creating exact dedicated v1 runtime service account %s\n' "$EXPECTED_RUNTIME_SA"
  gcloud iam service-accounts create "$EXPECTED_RUNTIME_SA_ID" \
    --project="$PROJECT_ID" \
    --display-name="Click & Save Auth Cleanup Runtime" \
    --description="Dedicated runtime identity for v1 Firebase Auth onDelete push-token cleanup only" \
    --quiet >/dev/null
  runtime_email="$(gcloud iam service-accounts describe "$EXPECTED_RUNTIME_SA" \
    --project="$PROJECT_ID" --format='value(email)' 2>/dev/null || true)"
fi
[[ "$runtime_email" == "$EXPECTED_RUNTIME_SA" ]] || fail "Dedicated runtime SA mismatch after create/reuse: ${runtime_email:-missing}"

key_count="$(gcloud iam service-accounts keys list \
  --iam-account="$EXPECTED_RUNTIME_SA" \
  --managed-by=user \
  --format='value(name)' 2>/dev/null | sed '/^$/d' | wc -l | tr -d '[:space:]')"
[[ "$key_count" == 0 ]] || fail "Dedicated runtime SA has user-managed keys: $key_count"

mapfile -t roles_before < <(runtime_roles)
for role in "${roles_before[@]}"; do
  [[ "$role" == "$EXPECTED_RUNTIME_ROLE" ]] || fail "Dedicated runtime SA has unexpected project role: $role"
done
if ! contains "$EXPECTED_RUNTIME_ROLE" "${roles_before[@]}"; then
  printf 'Granting exact runtime data role %s to %s\n' "$EXPECTED_RUNTIME_ROLE" "$EXPECTED_RUNTIME_SA"
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${EXPECTED_RUNTIME_SA}" \
    --role="$EXPECTED_RUNTIME_ROLE" \
    --condition=None \
    --quiet >/dev/null
fi
mapfile -t roles_after < <(runtime_roles)
[[ "${#roles_after[@]}" -eq 1 && "${roles_after[0]}" == "$EXPECTED_RUNTIME_ROLE" ]] || \
  fail "Dedicated runtime SA project roles are not exactly ${EXPECTED_RUNTIME_ROLE}"

INTENDED_SAS=()
for runtime_sa in "${RUNTIME_SAS[@]}"; do append_unique "$runtime_sa" INTENDED_SAS; done
append_unique "$BUILD_SA" INTENDED_SAS
for sa in "${INTENDED_SAS[@]}"; do
  if gcloud iam service-accounts get-iam-policy "$sa" --project="$PROJECT_ID" \
    --flatten='bindings[].members' \
    --filter="bindings.role=roles/iam.serviceAccountUser AND bindings.members=serviceAccount:${EXPECTED_DEPLOY_SA}" \
    --format='value(bindings.members)' 2>/dev/null | grep -Fqx "serviceAccount:${EXPECTED_DEPLOY_SA}"; then
    printf 'actAs already present on intended identity %s; no write needed.\n' "$sa"
    continue
  fi
  printf 'Granting deployer actAs only on individual identity %s\n' "$sa"
  gcloud iam service-accounts add-iam-policy-binding "$sa" \
    --project="$PROJECT_ID" \
    --member="serviceAccount:${EXPECTED_DEPLOY_SA}" \
    --role="roles/iam.serviceAccountUser" \
    --condition=None \
    --quiet >/dev/null
done

printf 'Running immediate independent post-write verification...\n'
PROJECT_ID="$PROJECT_ID" ALLOW_MISSING_ACTAS=0 ALLOW_RUNTIME_BOOTSTRAP_GAP=0 bash "$VERIFIER"
printf 'Production dedicated v1 runtime identity and runtime/build actAs boundary configured and verified.\n'
