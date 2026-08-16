#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
ACCEPTED_VERIFIER_BLOB="1a60a70dba55eff3423b2599c8a30810aecb79a8"
REPO="vhanukaev1981/ClickAndSaveAI"
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_VERIFIER="$ROOT/scripts/verify-production-runtime-build-actas.sh"

fatal() { printf 'FAIL  %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS  %s\n' "$*"; }

case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging) fatal "forbidden non-Production project: $PROJECT_ID" ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fatal "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
[[ -x "$BASE_VERIFIER" ]] || fatal "accepted Block 3B.3C verifier is missing or not executable: $BASE_VERIFIER"
for c in gcloud git curl python3 timeout; do command -v "$c" >/dev/null || fatal "$c is required"; done

actual_blob="$(git -C "$ROOT" hash-object "scripts/verify-production-runtime-build-actas.sh" 2>/dev/null || true)"
[[ "$actual_blob" == "$ACCEPTED_VERIFIER_BLOB" ]] || fatal "accepted Block 3B.3C verifier blob mismatch: ${actual_blob:-missing}"
pass 'accepted Block 3B.3C verifier blob exact'

PID="$(gcloud projects describe "$EXPECTED_PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
PNUM="$(gcloud projects describe "$EXPECTED_PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$PID" == "$EXPECTED_PROJECT_ID" ]] || fatal "Project ID mismatch: ${PID:-missing}"
[[ "$PNUM" == "$EXPECTED_PROJECT_NUMBER" ]] || fatal "Project Number mismatch: ${PNUM:-missing}"
pass 'Project ID/Number exact'

umask 077
DISCOVERY_FILE="$(mktemp)"
trap 'rm -f "$DISCOVERY_FILE"' EXIT

PROJECT_ID="$PROJECT_ID" \
ALLOW_MISSING_ACTAS=0 \
ALLOW_RUNTIME_BOOTSTRAP_GAP=0 \
DISCOVERY_OUTPUT="$DISCOVERY_FILE" \
  bash "$BASE_VERIFIER"

# shellcheck disable=SC1090
source "$DISCOVERY_FILE"
: "${CLOUD_BUILD_SERVICE_ENABLED:?accepted verifier did not emit CLOUD_BUILD_SERVICE_ENABLED}"
: "${BUILD_IDENTITY_DISCOVERY_ATTEMPTED:?accepted verifier did not emit BUILD_IDENTITY_DISCOVERY_ATTEMPTED}"
: "${PRODUCTION_BUILD_IDENTITY_STATUS:?accepted verifier did not emit PRODUCTION_BUILD_IDENTITY_STATUS}"
[[ ${BUILD_SA+x} ]] || fatal "accepted verifier did not emit BUILD_SA"

[[ "$CLOUD_BUILD_SERVICE_ENABLED" == true ]] || fatal "Cloud Build service is not enabled"
[[ "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED" == true ]] || fatal "Cloud Build default identity discovery was not attempted"
[[ "$PRODUCTION_BUILD_IDENTITY_STATUS" == READY ]] || fatal "Production build identity status is not READY: $PRODUCTION_BUILD_IDENTITY_STATUS"
[[ -n "$BUILD_SA" ]] || fatal "Production build identity is READY but BUILD_SA is empty"
pass "accepted verifier proved ready build identity: $BUILD_SA"

POL="$(gcloud projects get-iam-policy "$PROJECT_ID" --format=json 2>/dev/null || true)"
[[ -n "$POL" ]] || fatal 'project IAM policy unreadable'
POL="$POL" python3 - <<'PY' || fatal 'project-wide roles/iam.serviceAccountUser exists'
import json, os
policy = json.loads(os.environ["POL"])
assert not any(
    binding.get("role") == "roles/iam.serviceAccountUser" and binding.get("members")
    for binding in policy.get("bindings", [])
)
PY
pass 'no project-wide roles/iam.serviceAccountUser grant'

URL="https://api.github.com/repos/$REPO/deployments?environment=production&per_page=1"
HEADERS=(-H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28')
[[ -n "${GH_TOKEN:-}" ]] && HEADERS+=(-H "Authorization: Bearer $GH_TOKEN")
[[ -z "${GH_TOKEN:-}" && -n "${GITHUB_TOKEN:-}" ]] && HEADERS+=(-H "Authorization: Bearer $GITHUB_TOKEN")
DEPLOYMENTS="$(curl -fsSL "${HEADERS[@]}" "$URL" 2>/dev/null || true)"
[[ -n "$DEPLOYMENTS" ]] || fatal 'unable to verify Production deployment inventory remains empty'
printf '%s' "$DEPLOYMENTS" | python3 -c 'import json,sys; x=json.load(sys.stdin); assert isinstance(x,list) and not x' || \
  fatal 'Production deployment inventory is not empty'
pass 'Production deployment inventory remains empty'

printf '\nProduction Block 3C build identity verification PASSED.\n'
printf 'buildServiceAccount=%s\n' "$BUILD_SA"
printf 'productionCloudBuildServiceEnabled=true\n'
printf 'productionBuildIdentityReady=true\n'
printf 'productionBuildIdentityStatus=READY\n'
printf 'productionBuildIdentityConfigured=true\n'
printf 'productionBuildActAsConfigured=true\n'
printf 'productionRuntimeBuildActAsConfigured=true\n'
printf 'productionWifEndToEndVerified=false\n'
printf 'productionDeployEndToEndReady=false\n'
printf 'productionIdentityReady=false\n'
printf 'productionDeployed=false\n'
