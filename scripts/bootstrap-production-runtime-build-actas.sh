#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_DEPLOY_SA="clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com"
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
case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging) fail "Refusing forbidden non-Production project: $PROJECT_ID" ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
[[ -x "$VERIFIER" ]] || fail "Verifier is missing or not executable: $VERIFIER"

DISCOVERY_FILE="$(mktemp)"
trap 'rm -f "$DISCOVERY_FILE"' EXIT

printf 'Running fail-closed pre-mutation verification and live identity discovery...\n'
PROJECT_ID="$PROJECT_ID" ALLOW_MISSING_ACTAS=1 DISCOVERY_OUTPUT="$DISCOVERY_FILE" bash "$VERIFIER"
# The verifier derives the runtime generation set from the configured export surface and validates
# every emitted runtime/build identity live before this bootstrap performs any IAM write.
# shellcheck disable=SC1090
source "$DISCOVERY_FILE"
declare -p RUNTIME_SAS >/dev/null 2>&1 || fail "Verifier did not emit a proven runtime identity array."
[[ "${#RUNTIME_SAS[@]}" -gt 0 && -n "${BUILD_SA:-}" ]] || fail "Verifier did not emit proven runtime/build identities."

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
  printf 'Granting %s actAs only on %s\n' "$EXPECTED_DEPLOY_SA" "$sa"
  gcloud iam service-accounts add-iam-policy-binding "$sa" \
    --project="$PROJECT_ID" \
    --member="serviceAccount:${EXPECTED_DEPLOY_SA}" \
    --role="roles/iam.serviceAccountUser" \
    --condition=None \
    --quiet >/dev/null
done

printf 'Running immediate independent post-write verification...\n'
PROJECT_ID="$PROJECT_ID" ALLOW_MISSING_ACTAS=0 bash "$VERIFIER"
printf 'Production runtime/build service-account actAs boundary configured and verified.\n'
