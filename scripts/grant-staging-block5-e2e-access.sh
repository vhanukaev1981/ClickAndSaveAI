#!/usr/bin/env bash
set -euo pipefail

# Grants only the staging E2E harness permissions required by Block 5.
# This script does NOT create keys, change WIF trust, or grant Owner/Editor/Firebase Admin.

PROJECT_ID="clickandsaveai-staging"
DEPLOY_SA_EMAIL="clickandsaveai-github-deployer@clickandsaveai-staging.iam.gserviceaccount.com"
MEMBER="serviceAccount:${DEPLOY_SA_EMAIL}"
REQUIRED_ROLES=(
  roles/firebaseauth.admin
  roles/datastore.user
)

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v gcloud >/dev/null 2>&1 || fail "gcloud is required. Run this in Google Cloud Shell."
ACTIVE_ACCOUNT="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' | head -n 1)"
[[ -n "$ACTIVE_ACCOUNT" ]] || fail "No active gcloud administrator account."

ACTUAL_PROJECT="$(gcloud config get-value project 2>/dev/null || true)"
if [[ "$ACTUAL_PROJECT" != "$PROJECT_ID" ]]; then
  gcloud config set project "$PROJECT_ID" >/dev/null
fi

gcloud projects describe "$PROJECT_ID" --format='value(projectId)' | grep -Fxq "$PROJECT_ID" \
  || fail "Could not verify staging project $PROJECT_ID."
gcloud iam service-accounts describe "$DEPLOY_SA_EMAIL" --project="$PROJECT_ID" >/dev/null \
  || fail "Staging deploy service account was not found."

printf 'Granting Block 5 staging E2E permissions to %s only...\n' "$DEPLOY_SA_EMAIL"
for role in "${REQUIRED_ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="$MEMBER" \
    --role="$role" \
    --condition=None \
    --quiet >/dev/null
  printf 'Granted: %s\n' "$role"
done

POLICY="$(gcloud projects get-iam-policy "$PROJECT_ID" --format=json)"
for role in "${REQUIRED_ROLES[@]}"; do
  ROLE="$role" MEMBER="$MEMBER" POLICY="$POLICY" python3 - <<'PY'
import json, os, sys
policy = json.loads(os.environ["POLICY"])
role = os.environ["ROLE"]
member = os.environ["MEMBER"]
ok = any(
    binding.get("role") == role and member in binding.get("members", [])
    for binding in policy.get("bindings", [])
)
if not ok:
    print(f"Missing verified binding: {role}", file=sys.stderr)
    sys.exit(1)
PY
done

cat <<EOF
PASS: Block 5 staging E2E IAM access verified.
Project: ${PROJECT_ID}
Service account: ${DEPLOY_SA_EMAIL}
Roles added/verified:
- roles/firebaseauth.admin
- roles/datastore.user
No service-account key was created and WIF trust was not changed.
EOF
