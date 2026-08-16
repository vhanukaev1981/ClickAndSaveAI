#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
CLOUD_BUILD_SERVICE="cloudbuild.googleapis.com"
REGION="europe-west1"
ACCEPTED_VERIFIER_BLOB="1a60a70dba55eff3423b2599c8a30810aecb79a8"
BUILD_DEFERRED_STATUS="DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION"
SERVICE_ENABLE_TIMEOUT_SECONDS=90
SERVICE_ENABLE_POLL_SECONDS=2
DISCOVERY_TIMEOUT_SECONDS=60
DISCOVERY_POLL_SECONDS=2
DISCOVERY_COMMAND_TIMEOUT_SECONDS=30
MAX_INITIALIZATION_BUILDS=1
PROJECT_ID="${PROJECT_ID:-$EXPECTED_PROJECT_ID}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_VERIFIER="$ROOT/scripts/verify-production-runtime-build-actas.sh"
RUNTIME_BOOTSTRAP="$ROOT/scripts/bootstrap-production-runtime-build-actas.sh"
CLOSURE_VERIFIER="$ROOT/scripts/verify-production-build-identity.sh"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
info() { printf 'INFO  %s\n' "$*"; }

case "$PROJECT_ID" in
  clickandsaveai|clickandsaveai-staging) fail "Refusing forbidden non-Production project: $PROJECT_ID" ;;
esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
[[ -x "$BASE_VERIFIER" ]] || fail "Accepted Block 3B.3C verifier is missing or not executable: $BASE_VERIFIER"
[[ -x "$RUNTIME_BOOTSTRAP" ]] || fail "Accepted runtime/build bootstrap is missing or not executable: $RUNTIME_BOOTSTRAP"
[[ -x "$CLOSURE_VERIFIER" ]] || fail "Block 3C closure verifier is missing or not executable: $CLOSURE_VERIFIER"
for c in gcloud git timeout date sleep; do command -v "$c" >/dev/null || fail "$c is required"; done

actual_blob="$(git -C "$ROOT" hash-object "scripts/verify-production-runtime-build-actas.sh" 2>/dev/null || true)"
[[ "$actual_blob" == "$ACCEPTED_VERIFIER_BLOB" ]] || fail "accepted Block 3B.3C verifier blob mismatch: ${actual_blob:-missing}"

PID="$(gcloud projects describe "$EXPECTED_PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
PNUM="$(gcloud projects describe "$EXPECTED_PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$PID" == "$EXPECTED_PROJECT_ID" ]] || fail "Project ID mismatch: ${PID:-missing}"
[[ "$PNUM" == "$EXPECTED_PROJECT_NUMBER" ]] || fail "Project Number mismatch: ${PNUM:-missing}"

umask 077
DISCOVERY_FILE="$(mktemp)"
DISCOVERY_ERROR_FILE="$(mktemp)"
INIT_CONFIG=""
INIT_OUTPUT_FILE="$(mktemp)"
cleanup() {
  rm -f "$DISCOVERY_FILE" "$DISCOVERY_ERROR_FILE" "$INIT_OUTPUT_FILE"
  [[ -z "$INIT_CONFIG" ]] || rm -f "$INIT_CONFIG"
}
trap cleanup EXIT

service_state_exact() {
  local rows rc
  set +e
  rows="$(gcloud services list \
    --project="$PROJECT_ID" \
    --enabled \
    --filter="config.name=$CLOUD_BUILD_SERVICE" \
    --format='value(config.name)' 2>"$DISCOVERY_ERROR_FILE")"
  rc=$?
  set -e
  [[ $rc -eq 0 ]] || fail "Cloud Build enabled-service query failed with exit code $rc"
  mapfile -t services < <(printf '%s\n' "$rows" | sed '/^$/d' | sort -u)
  case ${#services[@]} in
    0) return 1 ;;
    1)
      [[ "${services[0]}" == "$CLOUD_BUILD_SERVICE" ]] || fail "unexpected enabled-service row: ${services[0]}"
      return 0
      ;;
    *) fail "ambiguous Cloud Build enabled-service inventory: ${services[*]}" ;;
  esac
}

wait_for_service_enabled() {
  local now deadline
  now="$(date +%s)"
  deadline=$((now + SERVICE_ENABLE_TIMEOUT_SECONDS))
  while true; do
    if service_state_exact; then return 0; fi
    now="$(date +%s)"
    (( now < deadline )) || fail "Timed out waiting for $CLOUD_BUILD_SERVICE to become enabled"
    sleep "$SERVICE_ENABLE_POLL_SECONDS"
  done
}

normalize_build_sa() {
  local value="$1"
  value="${value#projects/$PROJECT_ID/serviceAccounts/}"
  value="${value//$'\r'/}"
  value="${value//$'\n'/}"
  printf '%s' "$value"
}

discover_build_sa() {
  local raw rc sanitized_error
  : >"$DISCOVERY_ERROR_FILE"
  set +e
  raw="$(timeout "${DISCOVERY_COMMAND_TIMEOUT_SECONDS}s" gcloud builds get-default-service-account \
    --project="$PROJECT_ID" \
    --region="$REGION" \
    --format='value(serviceAccountEmail)' 2>"$DISCOVERY_ERROR_FILE")"
  rc=$?
  set -e
  if [[ $rc -eq 124 ]]; then
    printf 'ERROR: Cloud Build default service-account discovery timed out after %s seconds\n' \
      "$DISCOVERY_COMMAND_TIMEOUT_SECONDS" >&2
    return 124
  fi
  if [[ $rc -ne 0 ]]; then
    sanitized_error="$(tr '\r\n' '  ' <"$DISCOVERY_ERROR_FILE" | sed -E 's/[^[:print:]\t]/?/g; s/[[:space:]]+/ /g; s/^ //; s/ $//')"
    printf 'ERROR: Cloud Build default service-account discovery failed with exit code %s after enabled-service verification%s\n' \
      "$rc" "${sanitized_error:+: $sanitized_error}" >&2
    return 2
  fi
  normalize_build_sa "$raw"
}

discover_build_sa_bounded() {
  local now deadline observed rc
  now="$(date +%s)"
  deadline=$((now + DISCOVERY_TIMEOUT_SECONDS))
  while true; do
    set +e
    observed="$(discover_build_sa)"
    rc=$?
    set -e
    [[ $rc -eq 0 ]] || return "$rc"
    if [[ -n "$observed" ]]; then
      printf '%s\n' "$observed"
      return 0
    fi
    now="$(date +%s)"
    if (( now >= deadline )); then return 1; fi
    sleep "$DISCOVERY_POLL_SECONDS"
  done
}

printf 'Running accepted pre-mutation runtime/build verification...\n'
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
[[ ${BUILD_SA+x} ]] || fail "accepted verifier did not emit BUILD_SA"

PRE_BUILD_SA="$BUILD_SA"
case "$CLOUD_BUILD_SERVICE_ENABLED:$PRODUCTION_BUILD_IDENTITY_STATUS" in
  false:"$BUILD_DEFERRED_STATUS")
    [[ "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED" == false && -z "$PRE_BUILD_SA" ]] || \
      fail "disabled Cloud Build pre-state must be deferred with no build identity discovery"
    ;;
  true:"$BUILD_DEFERRED_STATUS")
    [[ "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED" == true && -z "$PRE_BUILD_SA" ]] || \
      fail "enabled deferred pre-state must have attempted discovery and no build identity"
    ;;
  true:READY)
    [[ "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED" == true && -n "$PRE_BUILD_SA" ]] || \
      fail "READY pre-state must have attempted discovery and a non-empty build identity"
    ;;
  *)
    fail "unsupported Block 3C pre-state: service=$CLOUD_BUILD_SERVICE_ENABLED status=$PRODUCTION_BUILD_IDENTITY_STATUS"
    ;;
esac

if [[ "$CLOUD_BUILD_SERVICE_ENABLED" == false ]]; then
  printf 'Enabling exactly %s in %s\n' "$CLOUD_BUILD_SERVICE" "$PROJECT_ID"
  gcloud services enable "$CLOUD_BUILD_SERVICE" --project="$PROJECT_ID" --quiet
  wait_for_service_enabled
else
  service_state_exact || fail "$CLOUD_BUILD_SERVICE was reported enabled but exact enabled-service verification is empty"
fi

DISCOVERED_BUILD_SA=""
set +e
DISCOVERED_BUILD_SA="$(discover_build_sa_bounded)"
discovery_rc=$?
set -e
case "$discovery_rc" in
  0) : ;;
  1) DISCOVERED_BUILD_SA="" ;;
  124) fail "Cloud Build default service-account discovery timed out" ;;
  *) fail "Cloud Build default service-account discovery failed; initialization fallback is forbidden after a discovery command error" ;;
esac

initializationBuildSubmitted=false
initialization_builds=0
if [[ -z "$DISCOVERED_BUILD_SA" ]]; then
  service_state_exact || fail "Cloud Build service must remain exactly enabled before initialization build"
  INIT_CONFIG="$(mktemp)"
  cat >"$INIT_CONFIG" <<'YAML'
steps:
  - name: gcr.io/cloud-builders/gcloud
    entrypoint: bash
    args: ["-ceu", "true"]
YAML
  initialization_builds=$((initialization_builds + 1))
  (( initialization_builds <= MAX_INITIALIZATION_BUILDS )) || fail "initialization build bound exceeded"
  initializationBuildSubmitted=true
  : >"$INIT_OUTPUT_FILE"
  set +e
  gcloud builds submit \
    --project="$PROJECT_ID" \
    --region="$REGION" \
    --no-source \
    --config="$INIT_CONFIG" \
    --format='value(id,status)' \
    --quiet >"$INIT_OUTPUT_FILE" 2>/dev/null
  init_rc=$?
  set -e
  if [[ $init_rc -eq 0 ]]; then
    init_evidence="$(head -1 "$INIT_OUTPUT_FILE" | tr '\r\n\t ' '_' | sed -E 's/[^A-Za-z0-9._:-]/?/g' | cut -c1-160)"
    [[ -z "$init_evidence" ]] || info "Cloud Build initialization evidence: $init_evidence"
    set +e
    DISCOVERED_BUILD_SA="$(discover_build_sa_bounded)"
    post_init_discovery_rc=$?
    set -e
    case "$post_init_discovery_rc" in
      0) : ;;
      1) fail "Cloud Build initialization completed but no default build identity became discoverable" ;;
      124) fail "Cloud Build default service-account discovery timed out after initialization" ;;
      *) fail "Cloud Build default service-account discovery failed after initialization" ;;
    esac
  else
    info "Cloud Build initialization command exited $init_rc; performing one final read-only identity discovery"
    set +e
    DISCOVERED_BUILD_SA="$(discover_build_sa)"
    final_discovery_rc=$?
    set -e
    case "$final_discovery_rc" in
      0) : ;;
      124) fail "Cloud Build final default service-account discovery timed out after initialization failure" ;;
      *) fail "Cloud Build final default service-account discovery failed after initialization failure" ;;
    esac
    [[ -n "$DISCOVERED_BUILD_SA" ]] || fail "Cloud Build initialization failed and no default build identity is discoverable"
  fi
fi

[[ -n "$DISCOVERED_BUILD_SA" ]] || fail "Cloud Build default build identity is empty"
if [[ "$PRODUCTION_BUILD_IDENTITY_STATUS" == READY && "$DISCOVERED_BUILD_SA" != "$PRE_BUILD_SA" ]]; then
  fail "Cloud Build default identity changed during Block 3C verification: pre=$PRE_BUILD_SA now=$DISCOVERED_BUILD_SA"
fi
info "Cloud Build default identity discovered: $DISCOVERED_BUILD_SA"

PROJECT_ID="$PROJECT_ID" bash "$ROOT/scripts/bootstrap-production-runtime-build-actas.sh"
PROJECT_ID="$PROJECT_ID" bash "$ROOT/scripts/verify-production-build-identity.sh"

printf 'Production Block 3C Cloud Build identity configured and verified.\n'
printf 'buildServiceAccount=%s\n' "$DISCOVERED_BUILD_SA"
printf 'initializationBuildSubmitted=%s\n' "$initializationBuildSubmitted"
