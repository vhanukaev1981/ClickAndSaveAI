#!/usr/bin/env bash
set -euo pipefail

readonly STAGING_PROJECT="clickandsaveai-staging"
current_project="${GCLOUD_PROJECT:-}"

if [[ -z "$current_project" ]]; then
  echo 'GCLOUD_PROJECT is required by the Artifact Registry predeploy guard.' >&2
  exit 1
fi

if [[ "$current_project" != "$STAGING_PROJECT" ]]; then
  echo 'Artifact Registry cleanup preflight is not applicable to this non-Staging Firebase target.'
  exit 0
fi

if [[ "${STAGING_ARTIFACT_CLEANUP_PREFLIGHT_VERIFIED:-}" == "1" ]]; then
  echo 'Staging Artifact Registry cleanup preflight was already verified in this job.'
  exit 0
fi

repo_root="${PROJECT_DIR:-$(pwd)}"
exec bash "$repo_root/scripts/staging-artifact-cleanup-preflight.sh"
