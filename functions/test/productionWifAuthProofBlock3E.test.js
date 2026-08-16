"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");

const workflow = read(".github/workflows/production-release.yml");
const bootstrap = read("scripts/bootstrap-production-wif.sh");

function jobBlock(name) {
  const marker = `  ${name}:\n`;
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1, `missing workflow job: ${name}`);
  const tail = workflow.slice(start + marker.length);
  const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
  return nextJob ? tail.slice(0, nextJob.index) : tail;
}

test("Block 3E exposes an exact manual auth-only authorization input and stays workflow_dispatch-only", () => {
  assert.match(workflow, /^on:\n  workflow_dispatch:\n/m);
  assert.doesNotMatch(workflow, /\n  (?:push|pull_request|schedule|workflow_call):/);
  assert.match(
    workflow,
    /authorize_wif_auth_proof:\n        description:.*\n        required: true\n        default: NO_WIF_PROOF\n        type: string/
  );
  assert.match(workflow, /PROVE_WIF_AUTH_ONLY/);
});

test("auth-only mode is mutually exclusive with production candidate and Firebase deploy", () => {
  const candidate = jobBlock("production-candidate");
  const deploy = jobBlock("deploy-firebase-production");
  const proof = jobBlock("production-wif-auth-proof");

  assert.ok(
    candidate.includes("inputs.authorize_wif_auth_proof != 'PROVE_WIF_AUTH_ONLY'"),
    "production-candidate must be skipped in auth-only mode"
  );
  assert.doesNotMatch(candidate, /id-token:\s*write/);

  assert.ok(
    deploy.includes("inputs.authorize_wif_auth_proof != 'PROVE_WIF_AUTH_ONLY'"),
    "Firebase deploy must be explicitly excluded from auth-only mode"
  );
  assert.ok(
    deploy.includes("inputs.authorize_firebase_deploy == 'DEPLOY_FIREBASE_PRODUCTION'"),
    "Firebase deploy must retain its exact deployment authorization phrase"
  );

  assert.ok(
    proof.includes("inputs.authorize_wif_auth_proof == 'PROVE_WIF_AUTH_ONLY'"),
    "auth proof must require the exact proof phrase"
  );
  assert.ok(
    proof.includes("inputs.authorize_firebase_deploy == 'NO_DEPLOY'"),
    "auth proof must require the deployment gate to remain NO_DEPLOY"
  );
  assert.ok(
    proof.includes("inputs.confirm_environment == 'CLICKANDSAVEAI_PRODUCTION'"),
    "auth proof must require the exact Production confirmation"
  );
});

test("dedicated auth-proof job has the Production environment and minimal proof permissions", () => {
  const proof = jobBlock("production-wif-auth-proof");

  assert.match(proof, /environment: production/);
  assert.match(proof, /permissions:\n      contents: read\n      id-token: write\n/);
  assert.doesNotMatch(proof, /actions:\s*(?:read|write)/);
  assert.doesNotMatch(proof, /(?:issues|pull-requests|packages|deployments|checks|statuses):\s*write/);

  assert.match(proof, /uses: google-github-actions\/auth@v3/);
  assert.match(proof, /workload_identity_provider: \$\{\{ vars\.GCP_WORKLOAD_IDENTITY_PROVIDER \}\}/);
  assert.match(proof, /service_account: \$\{\{ vars\.GCP_DEPLOY_SERVICE_ACCOUNT \}\}/);
});

test("auth-proof job fail-closes on the canonical repository, workflow, identity and project boundary", () => {
  const proof = jobBlock("production-wif-auth-proof");

  for (const required of [
    "EXPECTED_REPOSITORY: vhanukaev1981/ClickAndSaveAI",
    "EXPECTED_REPOSITORY_ID: '1314210715'",
    "EXPECTED_REPOSITORY_OWNER_ID: '64756523'",
    "EXPECTED_ENVIRONMENT: production",
    "EXPECTED_REF: refs/heads/main",
    "EXPECTED_WORKFLOW_REF: vhanukaev1981/ClickAndSaveAI/.github/workflows/production-release.yml@refs/heads/main",
    "EXPECTED_PROJECT_ID: click-save-ai-production",
    "EXPECTED_PROJECT_NUMBER: '991489557172'",
    "EXPECTED_DEPLOY_SA: clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com",
    "${{ github.repository }}",
    "${{ github.repository_id }}",
    "${{ github.repository_owner_id }}",
    "${{ github.ref }}",
    "${{ github.workflow_ref }}",
  ]) {
    assert.ok(proof.includes(required), `missing fail-closed boundary: ${required}`);
  }

  assert.match(proof, /external_account/);
  assert.match(proof, /service_account/);
  assert.match(proof, /private_key/);
  assert.match(proof, /credentials_file_path/);
});

test("auth-only path contains no signing, deployment, token logging, IAM mutation or staging reuse", () => {
  const proof = jobBlock("production-wif-auth-proof");

  assert.doesNotMatch(proof, /\$\{\{\s*secrets\./);
  assert.doesNotMatch(proof, /PRODUCTION_UPLOAD_|PRODUCTION_GOOGLE_SERVICES_JSON_B64|PRODUCTION_RELEASE_CANDIDATE/);
  assert.doesNotMatch(proof, /assembleRelease|bundleRelease|Build signed production candidate|apksigner|keytool/);
  assert.doesNotMatch(proof, /firebase\s+deploy/i);
  assert.doesNotMatch(proof, /gcloud\s+(?:functions|run|app)\s+deploy/i);
  assert.doesNotMatch(proof, /gcloud\s+firestore|firestore.*deploy/i);
  assert.doesNotMatch(proof, /google\s+play|play.*(?:upload|publish)/i);
  assert.doesNotMatch(proof, /services\s+enable|add-iam-policy-binding|workload-identity-pools.*(?:create|update)|service-accounts.*create/i);
  assert.doesNotMatch(proof, /bootstrap-production-wif|bootstrap-staging-wif|verify-staging-wif/);
  assert.doesNotMatch(proof, /staging/i);

  assert.doesNotMatch(proof, /token_format:\s*(?:access_token|id_token)/i);
  assert.doesNotMatch(proof, /steps\.auth\.outputs\.(?:access_token|id_token)/i);
  assert.doesNotMatch(proof, /ACTIONS_ID_TOKEN|OIDC_TOKEN|REFRESH_TOKEN/i);
  assert.doesNotMatch(proof, /(?:echo|printf).*\b(?:access|refresh|oidc)\b.*token/i);
  assert.doesNotMatch(proof, /(?:cat|sed|head|tail)\s+[^\n]*(?:GOOGLE_APPLICATION_CREDENTIALS|CREDENTIALS_FILE)/i);
});

test("canonical Production WIF trust boundary remains exact and is not broadened", () => {
  assert.match(bootstrap, /GITHUB_REPOSITORY_ID="1314210715"/);
  assert.match(bootstrap, /GITHUB_REPOSITORY_OWNER_ID="64756523"/);
  assert.match(bootstrap, /GITHUB_ENVIRONMENT="production"/);
  assert.match(bootstrap, /GITHUB_REF="refs\/heads\/main"/);
  assert.match(
    bootstrap,
    /GITHUB_WORKFLOW_REF="vhanukaev1981\/ClickAndSaveAI\/\.github\/workflows\/production-release\.yml@refs\/heads\/main"/
  );
  assert.match(bootstrap, /attribute\.repository_id==/);
  assert.match(bootstrap, /attribute\.repository_owner_id==/);
  assert.match(bootstrap, /attribute\.environment==/);
  assert.match(bootstrap, /attribute\.ref==/);
  assert.match(bootstrap, /attribute\.workflow_ref==/);
});
