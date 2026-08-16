"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");

const workflow = read(".github/workflows/production-release.yml");
const probeScript = read("scripts/production-3f-metadata-probe.mjs");

function jobBlock(name) {
  const marker = `  ${name}:\n`;
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1, `missing workflow job: ${name}`);
  const tail = workflow.slice(start + marker.length);
  const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
  return nextJob ? tail.slice(0, nextJob.index) : tail;
}

test("Block 3F exposes an exact manual metadata-probe input with a closed default", () => {
  assert.match(workflow, /^on:\n  workflow_dispatch:\n/m);
  assert.doesNotMatch(workflow, /\n  (?:push|pull_request|schedule|workflow_call):/);
  assert.match(
    workflow,
    /authorize_3f_metadata_probe:\n        description:.*\n        required: true\n        default: NO_3F_PROBE\n        type: string/
  );
  assert.match(workflow, /PROBE_3F_METADATA_ONLY/);
});

test("metadata-probe mode is mutually exclusive with candidate, WIF proof and Firebase deploy", () => {
  const candidate = jobBlock("production-candidate");
  const wifProof = jobBlock("production-wif-auth-proof");
  const deploy = jobBlock("deploy-firebase-production");
  const probe = jobBlock("production-3f-metadata-probe");

  assert.ok(
    candidate.includes("inputs.authorize_3f_metadata_probe != 'PROBE_3F_METADATA_ONLY'"),
    "production-candidate must be skipped in Block 3F probe mode"
  );
  assert.ok(
    wifProof.includes("inputs.authorize_3f_metadata_probe != 'PROBE_3F_METADATA_ONLY'"),
    "WIF auth proof must be skipped in Block 3F probe mode"
  );
  assert.ok(
    deploy.includes("inputs.authorize_3f_metadata_probe != 'PROBE_3F_METADATA_ONLY'"),
    "Firebase deploy must be skipped in Block 3F probe mode"
  );

  for (const required of [
    "inputs.authorize_3f_metadata_probe == 'PROBE_3F_METADATA_ONLY'",
    "inputs.authorize_firebase_deploy == 'NO_DEPLOY'",
    "inputs.authorize_wif_auth_proof == 'NO_WIF_PROOF'",
    "inputs.confirm_environment == 'CLICKANDSAVEAI_PRODUCTION'",
  ]) {
    assert.ok(probe.includes(required), `probe eligibility is missing: ${required}`);
  }
});

test("dedicated probe uses the protected Production environment without OIDC or write permissions", () => {
  const probe = jobBlock("production-3f-metadata-probe");

  assert.match(probe, /environment: production/);
  assert.match(probe, /permissions:\n      contents: read\n/);
  assert.doesNotMatch(probe, /id-token:\s*write/);
  assert.doesNotMatch(probe, /(?:actions|issues|pull-requests|packages|deployments|checks|statuses):\s*write/);
  assert.doesNotMatch(probe, /google-github-actions\/auth@/);

  for (const name of [
    "PRODUCTION_FIREBASE_PROJECT_ID",
    "PRODUCTION_GOOGLE_WEB_CLIENT_ID",
    "PRODUCTION_APP_SIGNING_CERT_SHA1",
    "PRODUCTION_APP_SIGNING_CERT_SHA256",
    "PRODUCTION_UPLOAD_KEY_ALIAS",
    "GCP_WORKLOAD_IDENTITY_PROVIDER",
    "GCP_DEPLOY_SERVICE_ACCOUNT",
  ]) {
    const expectedMapping = `${name}: ` + "${{ vars." + name + " }}";
    assert.ok(probe.includes(expectedMapping), `missing protected variable mapping: ${name}`);
  }

  for (const name of [
    "PRODUCTION_GOOGLE_SERVICES_JSON_B64",
    "PRODUCTION_UPLOAD_KEYSTORE_B64",
    "PRODUCTION_UPLOAD_STORE_PASSWORD",
    "PRODUCTION_UPLOAD_KEY_PASSWORD",
  ]) {
    const expectedMapping = `${name}: ` + "${{ secrets." + name + " }}";
    assert.ok(probe.includes(expectedMapping), `missing protected secret mapping: ${name}`);
  }
});

test("probe materializes protected blobs only under RUNNER_TEMP and always removes them", () => {
  const probe = jobBlock("production-3f-metadata-probe");

  assert.match(probe, /BLOCK3F_GOOGLE_CONFIG_PATH: \$\{\{ runner\.temp \}\}\/block3f-google-services\.json/);
  assert.match(probe, /BLOCK3F_UPLOAD_KEYSTORE_PATH: \$\{\{ runner\.temp \}\}\/block3f-upload-keystore/);
  assert.match(probe, /base64 --decode > "\$BLOCK3F_GOOGLE_CONFIG_PATH"/);
  assert.match(probe, /base64 --decode > "\$BLOCK3F_UPLOAD_KEYSTORE_PATH"/);
  assert.doesNotMatch(probe, /cp\s+[^\n]*google-services[^\n]*app\//i);

  const cleanup = probe.slice(probe.indexOf("Remove Block 3F temporary decoded files"));
  assert.ok(cleanup.length > 0, "missing Block 3F always-cleanup step");
  assert.match(cleanup, /if: \$\{\{ always\(\) \}\}/);
  assert.match(cleanup, /rm -f "\$BLOCK3F_GOOGLE_CONFIG_PATH" "\$BLOCK3F_UPLOAD_KEYSTORE_PATH"/);
});

test("probe validates exact Production Firebase/package and OAuth relationships without logging config", () => {
  assert.match(probeScript, /click-save-ai-production/);
  assert.match(probeScript, /com\.aistudio\.clickandsaveai\.app/);
  assert.match(probeScript, /clickandsaveai-staging/);
  assert.match(probeScript, /project_info\?\.project_id/);
  assert.match(probeScript, /android_client_info\?\.package_name/);
  assert.match(probeScript, /certificate_hash/);
  assert.match(probeScript, /PRODUCTION_APP_SIGNING_CERT_SHA1/);
  assert.match(probeScript, /PRODUCTION_GOOGLE_WEB_CLIENT_ID/);
  assert.match(probeScript, /client_type/);

  assert.doesNotMatch(probeScript, /console\.(?:log|error)\([^\n]*(?:config|googleServices|rawJson|api_key)/i);
  assert.doesNotMatch(probeScript, /JSON\.stringify\([^\n]*(?:config|googleServices)/i);
});

test("upload keystore validation is metadata-only and proves upload certificate distinctness", () => {
  const probe = jobBlock("production-3f-metadata-probe");

  assert.match(probe, /keytool -list -v/);
  assert.match(probe, /-storepass:env PRODUCTION_UPLOAD_STORE_PASSWORD/);
  assert.match(probe, /-alias "\$PRODUCTION_UPLOAD_KEY_ALIAS"/);
  assert.match(probe, /SHA1:/);
  assert.match(probe, /SHA256:/);
  assert.match(probeScript, /PRODUCTION_UPLOAD_CERT_SHA1/);
  assert.match(probeScript, /PRODUCTION_UPLOAD_CERT_SHA256/);
  assert.match(probeScript, /PRODUCTION_APP_SIGNING_CERT_SHA256/);
  assert.match(probeScript, /upload_signing_certificate_distinct/);

  assert.doesNotMatch(probe, /keytool\s+-(?:genkeypair|import|importkeystore|delete|changealias|keypasswd|storepasswd)/i);
  assert.doesNotMatch(probe, /-storepass\s+"?\$PRODUCTION_UPLOAD_STORE_PASSWORD/i);
  assert.doesNotMatch(probe, /-keypass\s+"?\$PRODUCTION_UPLOAD_KEY_PASSWORD/i);
});

test("probe reports only presence/relationship status and contains no deploy, build, publish or IAM mutation path", () => {
  const probe = jobBlock("production-3f-metadata-probe");

  for (const forbidden of [
    /assembleRelease|bundleRelease|gradle\s/i,
    /firebase\s+deploy/i,
    /gcloud\s+(?:functions|run|app)\s+deploy/i,
    /google\s+play|play.*(?:upload|publish)/i,
    /services\s+enable|add-iam-policy-binding|set-iam-policy|remove-iam-policy-binding/i,
    /workload-identity-pools.*(?:create|update|delete)/i,
    /service-accounts.*(?:create|keys\s+create)/i,
  ]) {
    assert.doesNotMatch(probe, forbidden);
  }

  for (const status of ["VERIFIED_PRESENT", "VERIFIED_ABSENT", "MISMATCH", "UNKNOWN_NO_ACCESS"]) {
    assert.match(probeScript, new RegExp(status));
  }
  assert.doesNotMatch(probeScript, /process\.env\.PRODUCTION_(?:GOOGLE_SERVICES_JSON_B64|UPLOAD_KEYSTORE_B64).*console/i);
});

test("existing release security gates remain present outside the metadata-only probe", () => {
  assert.match(workflow, /SECRET_AUDIT_OUTPUT=.*repository-secret-audit\.mjs current/);
  assert.match(workflow, /SECRET_AUDIT_OUTPUT=.*repository-secret-audit\.mjs history/);
  assert.match(workflow, /node scripts\/production-readiness-guard\.mjs repository/);
  assert.match(workflow, /production-wif-auth-proof:/);
  assert.match(workflow, /deploy-firebase-production:/);
});
