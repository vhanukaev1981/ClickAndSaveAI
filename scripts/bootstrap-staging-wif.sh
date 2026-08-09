#!/usr/bin/env bash
set -euo pipefail

# Click&SaveAI staging GitHub Actions -> Google Cloud Workload Identity Federation bootstrap.
#
# Run this once from a trusted administrator shell that has gcloud installed and an
# active account allowed to create IAM service accounts, WIF pools/providers and
# project IAM bindings. The script never creates or downloads a service-account key.

PROJECT_ID="${PROJECT_ID:-clickandsaveai-staging}"
POOL_ID="${POOL_ID:-github-actions}"
PROVIDER_ID="${PROVIDER_ID:-clickandsaveai}"
DEPLOY_SA_ID="${DEPLOY_SA_ID:-clickandsaveai-github-deployer}"
GITHUB_REPOSITORY_ID="${GITHUB_REPOSITORY_ID:-1314210715}"
GITHUB_REPOSITORY_OWNER_ID="${GITHUB_REPOSITORY_OWNER_ID:-64756523}"
GITHUB_ENVIRONMENT="${GITHUB_ENVIRONMENT:-staging}"
GITHUB_REF="${GITHUB_REF:-refs/heads/main}"
GITHUB_JOB_WORKFLOW_REF="${GITHUB_JOB_WORKFLOW_REF:-vhanukaev1981/ClickAndSaveAI/.github/workflows/deploy-staging.yml@refs/heads/main}"
GITHUB_OIDC_ISSUER="https://token.actions.githubusercontent.com"

log() {
  printf '\n==> %s\n' "$*"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v gcloud >/dev/null 2>&1 || fail "gcloud is required. Run this from Google Cloud Shell or another trusted admin shell."

ACTIVE_ACCOUNT="$(gcloud auth list --filter='status:ACTIVE' --format='value(account)' | head -n 1)"
[[ -n "$ACTIVE_ACCOUNT" ]] || fail "No active gcloud account. Authenticate an administrator first."

log "Validating Google Cloud project $PROJECT_ID as $ACTIVE_ACCOUNT"
gcloud projects describe "$PROJECT_ID" --format='value(projectId)' >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
[[ "$PROJECT_NUMBER" =~ ^[0-9]+$ ]] || fail "Could not resolve the numeric project number for $PROJECT_ID."

DEPLOY_SA_EMAIL="${DEPLOY_SA_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
RUNTIME_SA_EMAIL="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

log "Enabling APIs required for keyless federation and deployment"
gcloud services enable \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  cloudresourcemanager.googleapis.com \
  --project="$PROJECT_ID"

log "Ensuring deploy service account exists: $DEPLOY_SA_EMAIL"
if ! gcloud iam service-accounts describe "$DEPLOY_SA_EMAIL" --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$DEPLOY_SA_ID" \
    --project="$PROJECT_ID" \
    --display-name="Click&SaveAI GitHub staging deployer" \
    --description="Keyless GitHub Actions deploy identity for Click&SaveAI staging only"
fi

log "Ensuring Workload Identity Pool exists: $POOL_ID"
if ! gcloud iam workload-identity-pools describe "$POOL_ID" \
  --project="$PROJECT_ID" \
  --location="global" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "$POOL_ID" \
    --project="$PROJECT_ID" \
    --location="global" \
    --display-name="GitHub Actions" \
    --description="Keyless GitHub Actions identities for Click&SaveAI"
fi

ATTRIBUTE_MAPPING="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.environment=assertion.environment"
ATTRIBUTE_CONDITION="assertion.repository_id=='${GITHUB_REPOSITORY_ID}' && assertion.repository_owner_id=='${GITHUB_REPOSITORY_OWNER_ID}' && assertion.environment=='${GITHUB_ENVIRONMENT}' && assertion.ref=='${GITHUB_REF}' && assertion.job_workflow_ref=='${GITHUB_JOB_WORKFLOW_REF}'"

log "Ensuring OIDC provider exists and converges to the locked repository/workflow/environment policy"
if gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project="$PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers update-oidc "$PROVIDER_ID" \
    --project="$PROJECT_ID" \
    --location="global" \
    --workload-identity-pool="$POOL_ID" \
    --issuer-uri="$GITHUB_OIDC_ISSUER" \
    --attribute-mapping="$ATTRIBUTE_MAPPING" \
    --attribute-condition="$ATTRIBUTE_CONDITION" \
    --display-name="Click&SaveAI GitHub"
else
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
    --project="$PROJECT_ID" \
    --location="global" \
    --workload-identity-pool="$POOL_ID" \
    --issuer-uri="$GITHUB_OIDC_ISSUER" \
    --attribute-mapping="$ATTRIBUTE_MAPPING" \
    --attribute-condition="$ATTRIBUTE_CONDITION" \
    --display-name="Click&SaveAI GitHub" \
    --description="Trust only Click&SaveAI main deploy-staging workflow in the staging environment"
fi

WIF_MEMBER="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository_id/${GITHUB_REPOSITORY_ID}"

log "Allowing only the trusted federated repository principal set to impersonate the deploy service account"
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA_EMAIL" \
  --project="$PROJECT_ID" \
  --member="$WIF_MEMBER" \
  --role="roles/iam.workloadIdentityUser" \
  --condition=None >/dev/null

# Product-specific deployment roles. Firebase Viewer supplies the read-only Firebase
# project metadata permissions that the CLI needs; write access remains scoped to the
# services actually deployed by deploy-staging.yml.
PROJECT_ROLES=(
  roles/firebase.viewer
  roles/cloudfunctions.admin
  roles/firebaserules.admin
  roles/datastore.indexAdmin
  roles/cloudscheduler.admin
  roles/secretmanager.viewer
)

log "Granting least-privilege project deployment roles to $DEPLOY_SA_EMAIL"
for role in "${PROJECT_ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${DEPLOY_SA_EMAIL}" \
    --role="$role" \
    --condition=None \
    --quiet >/dev/null
done

log "Granting Service Account User on the Cloud Run functions runtime service account"
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA_EMAIL" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:${DEPLOY_SA_EMAIL}" \
  --role="roles/iam.serviceAccountUser" \
  --condition=None >/dev/null

log "Resolving the project's actual Cloud Build default service account"
CLOUD_BUILD_SA_EMAIL="$(gcloud builds get-default-service-account --project="$PROJECT_ID" --format='value(serviceAccountEmail)' 2>/dev/null || true)"
if [[ -z "$CLOUD_BUILD_SA_EMAIL" ]]; then
  # Some gcloud versions return the email as plain output instead of a structured field.
  CLOUD_BUILD_SA_EMAIL="$(gcloud builds get-default-service-account --project="$PROJECT_ID" 2>/dev/null | tail -n 1 | tr -d '[:space:]')"
fi
[[ "$CLOUD_BUILD_SA_EMAIL" == *@*.gserviceaccount.com ]] || fail "Could not resolve the Cloud Build default service account."

log "Granting deployer Service Account User on Cloud Build account: $CLOUD_BUILD_SA_EMAIL"
gcloud iam service-accounts add-iam-policy-binding "$CLOUD_BUILD_SA_EMAIL" \
  --project="$PROJECT_ID" \
  --member="serviceAccount:${DEPLOY_SA_EMAIL}" \
  --role="roles/iam.serviceAccountUser" \
  --condition=None >/dev/null

log "Ensuring the Cloud Build service account can execute builds"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${CLOUD_BUILD_SA_EMAIL}" \
  --role="roles/cloudbuild.builds.builder" \
  --condition=None \
  --quiet >/dev/null

WIF_PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"

log "Bootstrap complete"
cat <<EOF

Set these two GitHub Environment variables on:
  Repository: vhanukaev1981/ClickAndSaveAI
  Environment: staging

GCP_WORKLOAD_IDENTITY_PROVIDER=${WIF_PROVIDER_RESOURCE}
GCP_DEPLOY_SERVICE_ACCOUNT=${DEPLOY_SA_EMAIL}

Security boundary applied at the Google provider:
  repository_id       = ${GITHUB_REPOSITORY_ID}
  repository_owner_id = ${GITHUB_REPOSITORY_OWNER_ID}
  environment         = ${GITHUB_ENVIRONMENT}
  ref                 = ${GITHUB_REF}
  job_workflow_ref    = ${GITHUB_JOB_WORKFLOW_REF}

No service-account key was created.
EOF
