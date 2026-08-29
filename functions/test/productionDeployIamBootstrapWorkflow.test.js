"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");

const workflow = read(".github/workflows/production-release.yml");

function jobBlock(name) {
  const marker = `  ${name}:\n`;
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1, `missing workflow job: ${name}`);
  const tail = workflow.slice(start + marker.length);
  const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
  return nextJob ? tail.slice(0, nextJob.index) : tail;
}

test("bootstrap input is explicit and fail-closed by default", () => {
  assert.match(
    workflow,
    /authorize_production_bootstrap:\n        description:.*\n        required: true\n        default: NO_BOOTSTRAP\n        type: string/
  );
  assert.match(workflow, /BOOTSTRAP_PRODUCTION_IAM_ONCE/);
});

test("bootstrap job is isolated and cannot run with deploy or diagnostics", () => {
  const bootstrap = jobBlock("production-bootstrap-deploy-iam");
  assert.match(
    bootstrap,
    /if:\s*\$\{\{\s*inputs\.authorize_production_bootstrap == 'BOOTSTRAP_PRODUCTION_IAM_ONCE'[\s\S]*inputs\.confirm_environment == 'CLICKANDSAVEAI_PRODUCTION'[\s\S]*inputs\.authorize_firebase_deploy == 'NO_DEPLOY'[\s\S]*inputs\.authorize_wif_auth_proof == 'NO_WIF_PROOF'[\s\S]*inputs\.authorize_3f_metadata_probe == 'NO_3F_PROBE'[\s\S]*inputs\.authorize_3f_external_authority_probe == 'NO_3F_EXTERNAL_PROBE'[\s\S]*inputs\.authorize_3f_service_state_probe == 'NO_3F_SERVICE_STATE_PROBE'[\s\S]*inputs\.authorize_3f_firebase_iam_permission_probe == 'NO_3F_FIREBASE_IAM_PERMISSION_PROBE'\s*\}\}/
  );

  for (const jobName of [
    "production-candidate",
    "production-wif-auth-proof",
    "production-3f-metadata-probe",
    "production-3f-firebase-authority-probe",
    "production-3f-firebase-service-state-probe",
    "production-3f-firebase-iam-permission-probe",
    "deploy-firebase-production",
  ]) {
    const block = jobBlock(jobName);
    assert.ok(
      block.includes("inputs.authorize_production_bootstrap != 'BOOTSTRAP_PRODUCTION_IAM_ONCE'"),
      `${jobName} must be excluded when bootstrap path is authorized`
    );
  }
});

test("bootstrap job enforces canonical identity boundary and source SHA", () => {
  const bootstrap = jobBlock("production-bootstrap-deploy-iam");
  for (const required of [
    "EXPECTED_REPOSITORY: vhanukaev1981/ClickAndSaveAI",
    "EXPECTED_REPOSITORY_ID: '1314210715'",
    "EXPECTED_REPOSITORY_OWNER_ID: '64756523'",
    "EXPECTED_ENVIRONMENT: production",
    "EXPECTED_REF: refs/heads/main",
    "EXPECTED_WORKFLOW_REF: vhanukaev1981/ClickAndSaveAI/.github/workflows/production-release.yml@refs/heads/main",
    "EXPECTED_PROJECT_ID: click-save-ai-production",
    "EXPECTED_PROJECT_NUMBER: '991489557172'",
    "EXPECTED_BOOTSTRAP_SA: clickandsaveai-github-bootstra@click-save-ai-production.iam.gserviceaccount.com",
    "EXPECTED_DEPLOY_SA: clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com",
    "ACTUAL_GITHUB_REPOSITORY",
    "ACTUAL_GITHUB_REPOSITORY_ID",
    "ACTUAL_GITHUB_REPOSITORY_OWNER_ID",
    "ACTUAL_GITHUB_REF",
    "ACTUAL_GITHUB_WORKFLOW_REF",
    "ACTUAL_GITHUB_SHA",
  ]) {
    assert.ok(bootstrap.includes(required), `missing boundary guard: ${required}`);
  }
});

test("bootstrap job uses WIF with bootstrap SA only and runs canonical scripts", () => {
  const bootstrap = jobBlock("production-bootstrap-deploy-iam");
  assert.match(bootstrap, /environment: production/);
  assert.match(bootstrap, /permissions:\n      contents: read\n      id-token: write\n/);
  assert.match(bootstrap, /uses: google-github-actions\/auth@v3/);
  assert.match(bootstrap, /project_id: \$\{\{ env\.EXPECTED_PROJECT_ID \}\}/);
  assert.match(bootstrap, /workload_identity_provider: \$\{\{ vars\.GCP_WORKLOAD_IDENTITY_PROVIDER \}\}/);
  assert.match(
    bootstrap,
    /service_account: clickandsaveai-github-bootstra@click-save-ai-production\.iam\.gserviceaccount\.com/
  );
  assert.doesNotMatch(bootstrap, /service_account: \$\{\{ vars\.GCP_DEPLOY_SERVICE_ACCOUNT \}\}/);

  assert.match(bootstrap, /bash scripts\/bootstrap-production-deploy-iam\.sh/);
  assert.match(bootstrap, /bash scripts\/verify-production-deploy-iam\.sh/);
});

test("bootstrap exports WIF ADC credentials for non-interactive Firebase CLI authentication", () => {
  const bootstrap = jobBlock("production-bootstrap-deploy-iam");
  assert.match(
    bootstrap,
    /Authenticate bootstrap identity with GitHub OIDC[\s\S]*create_credentials_file: 'true'[\s\S]*export_environment_variables: 'true'/
  );
  assert.doesNotMatch(bootstrap, /firebase\s+login|FIREBASE_TOKEN|--token/);
});

test("bootstrap path includes key-material rejection guards and excludes deploy actions or broad admin grants", () => {
  const bootstrap = jobBlock("production-bootstrap-deploy-iam");
  assert.match(bootstrap, /external_account/);
  assert.match(bootstrap, /private_key/);
  assert.match(bootstrap, /service_account_impersonation_url/);
  assert.match(bootstrap, /gcloud iam service-accounts keys list/);

  assert.doesNotMatch(bootstrap, /\$\{\{\s*secrets\.[^}]*GCP[^}]*\}\}/);
  assert.doesNotMatch(bootstrap, /credentials_json|GOOGLE_APPLICATION_CREDENTIALS/);
  assert.doesNotMatch(bootstrap, /firebase\s+deploy/i);
  assert.doesNotMatch(bootstrap, /google\s+play|play.*publish/i);
  assert.doesNotMatch(bootstrap, /roles\/owner|roles\/editor|roles\/run\.admin|roles\/cloudfunctions\.admin/);
});
