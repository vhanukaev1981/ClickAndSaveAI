#!/usr/bin/env bash
set -euo pipefail

EXPECTED_PROJECT_ID="click-save-ai-production"
EXPECTED_PROJECT_NUMBER="991489557172"
CLOUD_BUILD_SERVICE="cloudbuild.googleapis.com"
REGION="europe-west1"
ACCEPTED_VERIFIER_BLOB="1a60a70dba55eff3423b2599c8a30810aecb79a8"
EXPECTED_HARDENABLE_BUILD_SA="991489557172-compute@developer.gserviceaccount.com"
EDITOR_ROLE_PREFIX="roles/"
EDITOR_ROLE_NAME="editor"
EXPECTED_EDITOR_ROLE="${EDITOR_ROLE_PREFIX}${EDITOR_ROLE_NAME}"
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
HARDENER="$ROOT/scripts/harden-production-build-identity.sh"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
info() { printf 'INFO  %s\n' "$*"; }

case "$PROJECT_ID" in clickandsaveai|clickandsaveai-staging) fail "Refusing forbidden non-Production project: $PROJECT_ID";; esac
[[ "$PROJECT_ID" == "$EXPECTED_PROJECT_ID" ]] || fail "PROJECT_ID must be exactly $EXPECTED_PROJECT_ID"
[[ -x "$BASE_VERIFIER" ]] || fail "Accepted verifier missing: $BASE_VERIFIER"
[[ -x "$RUNTIME_BOOTSTRAP" ]] || fail "Accepted runtime bootstrap missing: $RUNTIME_BOOTSTRAP"
[[ -x "$CLOSURE_VERIFIER" ]] || fail "Block 3C closure verifier missing: $CLOSURE_VERIFIER"
for c in gcloud git timeout date sleep; do command -v "$c" >/dev/null || fail "$c is required"; done

actual_verifier_blob="$(git -C "$ROOT" hash-object "scripts/verify-production-runtime-build-actas.sh" 2>/dev/null || true)"
[[ "$actual_verifier_blob" == "$ACCEPTED_VERIFIER_BLOB" ]] || fail "accepted Block 3B.3C verifier blob mismatch: ${actual_verifier_blob:-missing}"
PID="$(gcloud projects describe "$EXPECTED_PROJECT_ID" --format='value(projectId)' 2>/dev/null || true)"
PNUM="$(gcloud projects describe "$EXPECTED_PROJECT_ID" --format='value(projectNumber)' 2>/dev/null || true)"
[[ "$PID" == "$EXPECTED_PROJECT_ID" ]] || fail "Project ID mismatch: ${PID:-missing}"
[[ "$PNUM" == "$EXPECTED_PROJECT_NUMBER" ]] || fail "Project Number mismatch: ${PNUM:-missing}"

umask 077
DISCOVERY_FILE="$(mktemp)"
PRECHECK_OUTPUT_FILE="$(mktemp)"
PRECHECK_ERROR_FILE="$(mktemp)"
HARDEN_PREFLIGHT_FILE="$(mktemp)"
INIT_CONFIG=""
INIT_OUTPUT_FILE="$(mktemp)"
cleanup() { rm -f "$DISCOVERY_FILE" "$PRECHECK_OUTPUT_FILE" "$PRECHECK_ERROR_FILE" "$HARDEN_PREFLIGHT_FILE" "$INIT_OUTPUT_FILE"; [[ -z "$INIT_CONFIG" ]] || rm -f "$INIT_CONFIG"; }
trap cleanup EXIT

service_state_exact() {
  local rows
  rows="$(gcloud services list --project="$PROJECT_ID" --enabled --filter="config.name=$CLOUD_BUILD_SERVICE" --format='value(config.name)' 2>/dev/null)" || fail "Cloud Build enabled-service query failed"
  mapfile -t services < <(printf '%s\n' "$rows" | sed '/^$/d' | sort -u)
  case ${#services[@]} in 0) return 1;; 1) [[ "${services[0]}" == "$CLOUD_BUILD_SERVICE" ]] || fail "unexpected enabled-service row: ${services[0]}"; return 0;; *) fail "ambiguous Cloud Build enabled-service inventory";; esac
}
wait_for_service_enabled() {
  local now deadline; now="$(date +%s)"; deadline=$((now + SERVICE_ENABLE_TIMEOUT_SECONDS))
  while true; do service_state_exact && return 0; now="$(date +%s)"; (( now < deadline )) || fail "Timed out waiting for $CLOUD_BUILD_SERVICE"; sleep "$SERVICE_ENABLE_POLL_SECONDS"; done
}
normalize_build_sa() { local v="$1"; v="${v#projects/$PROJECT_ID/serviceAccounts/}"; v="${v//$'\r'/}"; v="${v//$'\n'/}"; printf '%s' "$v"; }
discover_build_sa() {
  local raw rc
  set +e
  raw="$(timeout "${DISCOVERY_COMMAND_TIMEOUT_SECONDS}s" gcloud builds get-default-service-account --project="$PROJECT_ID" --region="$REGION" --format='value(serviceAccountEmail)' 2>/dev/null)"; rc=$?
  set -e
  [[ $rc -ne 124 ]] || return 124
  [[ $rc -eq 0 ]] || return 2
  normalize_build_sa "$raw"
}
discover_build_sa_bounded() {
  local now deadline observed rc; now="$(date +%s)"; deadline=$((now + DISCOVERY_TIMEOUT_SECONDS))
  while true; do set +e; observed="$(discover_build_sa)"; rc=$?; set -e; [[ $rc -eq 0 ]] || return "$rc"; [[ -z "$observed" ]] || { printf '%s\n' "$observed"; return 0; }; now="$(date +%s)"; (( now < deadline )) || return 1; sleep "$DISCOVERY_POLL_SECONDS"; done
}

run_accepted_precheck() {
  : >"$DISCOVERY_FILE"; : >"$PRECHECK_OUTPUT_FILE"; : >"$PRECHECK_ERROR_FILE"
  set +e
  PROJECT_ID="$PROJECT_ID" ALLOW_MISSING_ACTAS=1 ALLOW_RUNTIME_BOOTSTRAP_GAP=0 DISCOVERY_OUTPUT="$DISCOVERY_FILE" \
    bash "$BASE_VERIFIER" >"$PRECHECK_OUTPUT_FILE" 2>"$PRECHECK_ERROR_FILE"
  PRECHECK_RC=$?
  set -e
}
parse_precheck() {
  cat "$PRECHECK_OUTPUT_FILE"
  [[ -s "$DISCOVERY_FILE" ]] || fail "accepted verifier passed without discovery output"
  mapfile -t runtime_actas_truth < <(grep '^productionRuntimeActAsConfigured=' "$PRECHECK_OUTPUT_FILE" || true)
  [[ ${#runtime_actas_truth[@]} -eq 1 && "${runtime_actas_truth[0]}" == "productionRuntimeActAsConfigured=true" ]] || fail "accepted pre-mutation verifier did not prove productionRuntimeActAsConfigured=true"
  # shellcheck disable=SC1090
  source "$DISCOVERY_FILE"
  : "${CLOUD_BUILD_SERVICE_ENABLED:?accepted verifier did not emit CLOUD_BUILD_SERVICE_ENABLED}"
  : "${BUILD_IDENTITY_DISCOVERY_ATTEMPTED:?accepted verifier did not emit BUILD_IDENTITY_DISCOVERY_ATTEMPTED}"
  : "${PRODUCTION_BUILD_IDENTITY_STATUS:?accepted verifier did not emit PRODUCTION_BUILD_IDENTITY_STATUS}"
  [[ ${BUILD_SA+x} ]] || fail "accepted verifier did not emit BUILD_SA"
}

printf 'Running accepted pre-mutation runtime/build verification...\n'
run_accepted_precheck
if [[ $PRECHECK_RC -ne 0 ]]; then
  cat "$PRECHECK_OUTPUT_FILE"
  cat "$PRECHECK_ERROR_FILE" >&2
  mapfile -t precheck_error_lines < <(sed '/^$/d' "$PRECHECK_ERROR_FILE")
  [[ ${#precheck_error_lines[@]} -eq 1 ]] || fail "accepted verifier failure is not the exact known remediable Editor failure"
  normalized_precheck_error="$(printf '%s\n' "${precheck_error_lines[0]}" | sed -E 's/^FAIL[[:space:]]+/FAIL /')"
  expected_editor_failure="FAIL discovered build identity $EXPECTED_HARDENABLE_BUILD_SA holds forbidden role: $EXPECTED_EDITOR_ROLE"
  [[ "$normalized_precheck_error" == "$expected_editor_failure" ]] || fail "accepted verifier failure is not the exact known remediable Editor failure"
  [[ -f "$HARDENER" ]] || fail "accepted verifier failed and dedicated hardening script is unavailable"
  : >"$HARDEN_PREFLIGHT_FILE"
  set +e
  PROJECT_ID="$PROJECT_ID" bash "$HARDENER" --preflight >"$HARDEN_PREFLIGHT_FILE"
  harden_preflight_rc=$?
  set -e
  [[ $harden_preflight_rc -eq 0 ]] || fail "accepted verifier failure is not an approved build-identity hardening state"
  mapfile -t hardening_truth < <(grep '^productionBuildIdentityHardeningState=' "$HARDEN_PREFLIGHT_FILE" || true)
  [[ ${#hardening_truth[@]} -eq 1 ]] || fail "hardening preflight did not emit exactly one state"
  HARDENING_STATE="${hardening_truth[0]#productionBuildIdentityHardeningState=}"
  case "$HARDENING_STATE" in EDITOR_ONLY|EDITOR_BUILDER) :;; *) fail "accepted verifier failure is not remediable from hardening state $HARDENING_STATE";; esac
  cat "$HARDEN_PREFLIGHT_FILE"
  PROJECT_ID="$PROJECT_ID" bash "$HARDENER" || fail "approved build-identity hardening failed"
  printf 'Re-running unchanged accepted verifier after hardening...\n'
  run_accepted_precheck
  [[ $PRECHECK_RC -eq 0 ]] || { cat "$PRECHECK_OUTPUT_FILE"; cat "$PRECHECK_ERROR_FILE" >&2; fail "accepted verifier still fails after hardening"; }
fi
parse_precheck

PRE_BUILD_SA="$BUILD_SA"
case "$CLOUD_BUILD_SERVICE_ENABLED:$PRODUCTION_BUILD_IDENTITY_STATUS" in
  false:"$BUILD_DEFERRED_STATUS") [[ "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED" == false && -z "$PRE_BUILD_SA" ]] || fail "invalid disabled deferred pre-state";;
  true:"$BUILD_DEFERRED_STATUS") [[ "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED" == true && -z "$PRE_BUILD_SA" ]] || fail "invalid enabled deferred pre-state";;
  true:READY) [[ "$BUILD_IDENTITY_DISCOVERY_ATTEMPTED" == true && -n "$PRE_BUILD_SA" ]] || fail "invalid READY pre-state";;
  *) fail "unsupported Block 3C pre-state: service=$CLOUD_BUILD_SERVICE_ENABLED status=$PRODUCTION_BUILD_IDENTITY_STATUS";;
esac

if [[ "$CLOUD_BUILD_SERVICE_ENABLED" == false ]]; then
  gcloud services enable "$CLOUD_BUILD_SERVICE" --project="$PROJECT_ID" --quiet
  wait_for_service_enabled
else
  service_state_exact || fail "$CLOUD_BUILD_SERVICE reported enabled but verification is empty"
fi

set +e
DISCOVERED_BUILD_SA="$(discover_build_sa_bounded)"; discovery_rc=$?
set -e
case "$discovery_rc" in 0) :;; 1) DISCOVERED_BUILD_SA="";; 124) fail "Cloud Build default service-account discovery timed out";; *) fail "Cloud Build default service-account discovery failed; initialization fallback forbidden";; esac

initializationBuildSubmitted=false
initialization_builds=0
if [[ -z "$DISCOVERED_BUILD_SA" ]]; then
  service_state_exact || fail "Cloud Build must remain enabled before initialization build"
  INIT_CONFIG="$(mktemp)"
  cat >"$INIT_CONFIG" <<'YAML'
steps:
  - name: gcr.io/cloud-builders/gcloud
    entrypoint: bash
    args: ["-ceu", "true"]
YAML
  initialization_builds=$((initialization_builds + 1)); (( initialization_builds <= MAX_INITIALIZATION_BUILDS )) || fail "initialization build bound exceeded"
  initializationBuildSubmitted=true
  set +e
  gcloud builds submit --project="$PROJECT_ID" --region="$REGION" --no-source --config="$INIT_CONFIG" --format='value(id,status)' --quiet >"$INIT_OUTPUT_FILE" 2>/dev/null
  init_rc=$?
  set -e
  if [[ $init_rc -eq 0 ]]; then
    set +e; DISCOVERED_BUILD_SA="$(discover_build_sa_bounded)"; post_rc=$?; set -e
    [[ $post_rc -eq 0 && -n "$DISCOVERED_BUILD_SA" ]] || fail "Cloud Build initialization completed but no default build identity became discoverable"
  else
    set +e; DISCOVERED_BUILD_SA="$(discover_build_sa)"; final_rc=$?; set -e
    [[ $final_rc -eq 0 && -n "$DISCOVERED_BUILD_SA" ]] || fail "Cloud Build initialization failed and no default build identity is discoverable"
  fi
fi

[[ -n "$DISCOVERED_BUILD_SA" ]] || fail "Cloud Build default build identity is empty"
if [[ "$PRODUCTION_BUILD_IDENTITY_STATUS" == READY && "$DISCOVERED_BUILD_SA" != "$PRE_BUILD_SA" ]]; then fail "Cloud Build default identity changed during Block 3C verification"; fi
info "Cloud Build default identity discovered: $DISCOVERED_BUILD_SA"

PROJECT_ID="$PROJECT_ID" bash "$RUNTIME_BOOTSTRAP"
PROJECT_ID="$PROJECT_ID" bash "$CLOSURE_VERIFIER"
printf 'Production Block 3C Cloud Build identity configured and verified.\n'
printf 'buildServiceAccount=%s\n' "$DISCOVERED_BUILD_SA"
printf 'initializationBuildSubmitted=%s\n' "$initializationBuildSubmitted"
