"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const probePath = path.join(root, "scripts/production-3f-firebase-iam-permission-probe.mjs");
const workflowPath = path.join(root, ".github/workflows/production-release.yml");
const standaloneWorkflowPath = path.join(
  root,
  ".github/workflows/production-3f-firebase-iam-permission-diagnostic.yml"
);

const EXPECTED_PROJECT_ID = "click-save-ai-production";
const EXPECTED_PROJECT_NUMBER = "991489557172";
const EXPECTED_URL =
  "https://cloudresourcemanager.googleapis.com/v1/projects/click-save-ai-production:testIamPermissions";
const EXPECTED_WORKFLOW_REF =
  "vhanukaev1981/ClickAndSaveAI/.github/workflows/production-release.yml@refs/heads/main";
const EXPECTED_PERMISSIONS = [
  "firebase.projects.get",
  "firebase.clients.list",
  "firebase.clients.get",
];
const UNKNOWN_LINES = [
  "firebase_permission_projects_get=UNKNOWN_NO_ACCESS",
  "firebase_permission_clients_list=UNKNOWN_NO_ACCESS",
  "firebase_permission_clients_get=UNKNOWN_NO_ACCESS",
];

function fakeJsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return body;
    },
  };
}

function invalidJsonResponse(status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      throw new SyntaxError("SENSITIVE_INVALID_JSON_MUST_NOT_LEAK");
    },
  };
}

async function loadProbeModule() {
  return import(`${pathToFileURL(probePath).href}?t=${Date.now()}-${Math.random()}`);
}

function callProbe(probeFirebaseIamPermissions, fetchImpl, accessToken = "SENSITIVE_ACCESS_TOKEN") {
  return probeFirebaseIamPermissions({
    accessToken,
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    fetchImpl,
  });
}

function assertUnknown(result, transport) {
  assert.deepEqual(result.lines, [
    ...UNKNOWN_LINES,
    `firebase_iam_permission_test_transport=${transport}`,
  ]);
  assert.doesNotMatch(result.lines.join("\n"), /=NOT_GRANTED$/m);
}

function extractJob(workflow, jobName) {
  const lines = workflow.split("\n");
  const start = lines.findIndex((line) => line === `  ${jobName}:`);
  assert.notEqual(start, -1, `missing job ${jobName}`);
  let end = lines.length;
  for (let i = start + 1; i < lines.length; i += 1) {
    if (/^  [A-Za-z0-9_-]+:\s*$/.test(lines[i])) {
      end = i;
      break;
    }
  }
  return lines.slice(start, end).join("\n");
}

test("standalone IAM diagnostic workflow is removed and canonical release workflow owns the mode", () => {
  assert.equal(fs.existsSync(standaloneWorkflowPath), false);
  const workflow = fs.readFileSync(workflowPath, "utf8");
  assert.match(workflow, /authorize_3f_firebase_iam_permission_probe:/);
  assert.match(workflow, /default: NO_3F_FIREBASE_IAM_PERMISSION_PROBE/);
  assert.match(workflow, /PROBE_3F_FIREBASE_IAM_PERMISSIONS_READ_ONLY/);
  assert.match(workflow, /^  production-3f-firebase-iam-permission-probe:/m);
});

test("IAM permission probe request is exactly the three approved Resource Manager permissions", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const calls = [];
  const result = await callProbe(probeFirebaseIamPermissions, async (url, options) => {
    calls.push({ url: String(url), options });
    return fakeJsonResponse(200, {
      permissions: ["firebase.projects.get", "firebase.clients.get"],
      ignored: "SENSITIVE_RESPONSE_FIELD_MUST_NOT_LEAK",
    });
  });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, EXPECTED_URL);
  assert.equal(calls[0].options.method, "POST");
  assert.equal(calls[0].options.headers.Accept, "application/json");
  assert.equal(calls[0].options.headers["Content-Type"], "application/json");
  assert.match(calls[0].options.headers.Authorization, /^Bearer /);
  assert.deepEqual(JSON.parse(calls[0].options.body), { permissions: EXPECTED_PERMISSIONS });
  assert.deepEqual(result.lines, [
    "firebase_permission_projects_get=GRANTED",
    "firebase_permission_clients_list=NOT_GRANTED",
    "firebase_permission_clients_get=GRANTED",
    "firebase_iam_permission_test_transport=SUCCESS_2XX",
  ]);
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_RESPONSE_FIELD_MUST_NOT_LEAK/);
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_ACCESS_TOKEN/);
});

test("empty successful permissions response means all three requested permissions are NOT_GRANTED", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const result = await callProbe(probeFirebaseIamPermissions, async () => fakeJsonResponse(200, {}));
  assert.deepEqual(result.lines, [
    "firebase_permission_projects_get=NOT_GRANTED",
    "firebase_permission_clients_list=NOT_GRANTED",
    "firebase_permission_clients_get=NOT_GRANTED",
    "firebase_iam_permission_test_transport=SUCCESS_2XX",
  ]);
});

for (const [status, transport] of [
  [401, "HTTP_401"],
  [403, "HTTP_403"],
  [404, "HTTP_404"],
  [429, "HTTP_429"],
  [500, "HTTP_5XX"],
  [599, "HTTP_5XX"],
  [418, "HTTP_OTHER_NON_2XX"],
]) {
  test(`HTTP ${status} makes all permission states UNKNOWN_NO_ACCESS`, async () => {
    const { probeFirebaseIamPermissions } = await loadProbeModule();
    const result = await callProbe(probeFirebaseIamPermissions, async () =>
      fakeJsonResponse(status, {
        error: {
          message: "SENSITIVE_ERROR_MESSAGE_MUST_NOT_LEAK",
          details: "SENSITIVE_ERROR_DETAILS_MUST_NOT_LEAK",
        },
      })
    );
    assertUnknown(result, transport);
    assert.doesNotMatch(
      result.lines.join("\n"),
      /SENSITIVE_ERROR_MESSAGE_MUST_NOT_LEAK|SENSITIVE_ERROR_DETAILS_MUST_NOT_LEAK/
    );
  });
}

test("network failure makes all permission states UNKNOWN_NO_ACCESS", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const result = await callProbe(probeFirebaseIamPermissions, async () => {
    throw new Error("SENSITIVE_NETWORK_EXCEPTION_MUST_NOT_LEAK");
  });
  assertUnknown(result, "NETWORK_ERROR");
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_NETWORK_EXCEPTION_MUST_NOT_LEAK/);
});

test("invalid JSON makes all permission states UNKNOWN_NO_ACCESS", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const result = await callProbe(probeFirebaseIamPermissions, async () => invalidJsonResponse());
  assertUnknown(result, "INVALID_JSON");
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_INVALID_JSON_MUST_NOT_LEAK/);
});

test("unexpected, duplicate, malformed permission data fails closed as UNKNOWN_NO_ACCESS plus INVALID_JSON", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const invalidBodies = [
    { permissions: ["firebase.projects.get", "resourcemanager.projects.setIamPolicy"] },
    { permissions: ["firebase.projects.get", "firebase.projects.get"] },
    { permissions: "firebase.projects.get" },
    { permissions: ["firebase.projects.get", 7] },
    [],
  ];

  for (const body of invalidBodies) {
    const result = await callProbe(probeFirebaseIamPermissions, async () => fakeJsonResponse(200, body));
    assertUnknown(result, "INVALID_JSON");
  }
});

test("guard failure is fail-closed and never fabricates NOT_GRANTED", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  let calls = 0;
  const result = await probeFirebaseIamPermissions({
    accessToken: "SENSITIVE_ACCESS_TOKEN",
    expectedProjectId: "clickandsaveai-staging",
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    fetchImpl: async () => {
      calls += 1;
      return fakeJsonResponse(200, { permissions: EXPECTED_PERMISSIONS });
    },
  });
  assert.equal(calls, 0);
  assertUnknown(result, "NOT_ATTEMPTED_GUARD_FAILURE");
});

test("probe source contains only the approved IAM permission diagnostic and no mutating IAM/API/deploy path", () => {
  const source = fs.readFileSync(probePath, "utf8");
  assert.match(source, /https:\/\/cloudresourcemanager\.googleapis\.com\/v1/);
  assert.match(source, /:testIamPermissions/);
  for (const permission of EXPECTED_PERMISSIONS) assert.match(source, new RegExp(permission.replaceAll(".", "\\.")));
  assert.match(source, /method:\s*["']POST["']/);
  assert.doesNotMatch(source, /firebase_iam_permission_firebase_/);

  for (const forbidden of [
    /setIamPolicy|getIamPolicy/i,
    /add-iam-policy-binding|remove-iam-policy-binding/i,
    /roles\/(?:owner|editor|viewer|firebase\.)/i,
    /services\.(?:enable|disable|batchEnable)/i,
    /service-accounts\s+keys\s+create/i,
    /firebase\s+deploy/i,
    /assembleRelease|bundleRelease|gradle\s/i,
    /google\s+play|play.*(?:upload|publish)/i,
    /method:\s*["'](?:PUT|PATCH|DELETE)["']/i,
  ]) {
    assert.doesNotMatch(source, forbidden);
  }
});

test("canonical IAM probe job preserves exact Production WIF identity guards and iam.test token contract", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  const job = extractJob(workflow, "production-3f-firebase-iam-permission-probe");

  assert.match(job, /environment: production/);
  assert.match(job, /contents: read/);
  assert.match(job, /id-token: write/);
  assert.match(job, /EXPECTED_REPOSITORY: vhanukaev1981\/ClickAndSaveAI/);
  assert.match(job, /EXPECTED_REPOSITORY_ID: '1314210715'/);
  assert.match(job, /EXPECTED_REPOSITORY_OWNER_ID: '64756523'/);
  assert.match(job, /EXPECTED_ENVIRONMENT: production/);
  assert.match(job, /EXPECTED_REF: refs\/heads\/main/);
  assert.match(job, new RegExp(EXPECTED_WORKFLOW_REF.replaceAll(".", "\\.")));
  assert.match(job, /EXPECTED_PROJECT_ID: click-save-ai-production/);
  assert.match(job, /EXPECTED_PROJECT_NUMBER: '991489557172'/);
  assert.match(
    job,
    /EXPECTED_DEPLOY_SA: clickandsaveai-github-deployer@click-save-ai-production\.iam\.gserviceaccount\.com/
  );
  assert.match(job, /GCP_WORKLOAD_IDENTITY_PROVIDER: \$\{\{ vars\.GCP_WORKLOAD_IDENTITY_PROVIDER \}\}/);
  assert.match(job, /GCP_DEPLOY_SERVICE_ACCOUNT: \$\{\{ vars\.GCP_DEPLOY_SERVICE_ACCOUNT \}\}/);
  assert.match(job, /github\.sha.*SOURCE_SHA/);
  assert.match(job, /github\.workflow_ref.*EXPECTED_WORKFLOW_REF/);
  assert.match(job, /uses: google-github-actions\/auth@v3/);
  assert.match(job, /token_format: 'access_token'/);
  assert.match(job, /access_token_lifetime: '600s'/);
  assert.match(job, /access_token_scopes: 'https:\/\/www\.googleapis\.com\/auth\/iam\.test'/);
  assert.match(job, /node scripts\/production-3f-firebase-iam-permission-probe\.mjs/);
});

test("IAM mode requires the exact closed-gate authorization tuple", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  const job = extractJob(workflow, "production-3f-firebase-iam-permission-probe");
  for (const required of [
    "inputs.authorize_3f_firebase_iam_permission_probe == 'PROBE_3F_FIREBASE_IAM_PERMISSIONS_READ_ONLY'",
    "inputs.authorize_firebase_deploy == 'NO_DEPLOY'",
    "inputs.authorize_wif_auth_proof == 'NO_WIF_PROOF'",
    "inputs.authorize_3f_metadata_probe == 'NO_3F_PROBE'",
    "inputs.authorize_3f_external_authority_probe == 'NO_3F_EXTERNAL_PROBE'",
    "inputs.authorize_3f_service_state_probe == 'NO_3F_SERVICE_STATE_PROBE'",
    "inputs.confirm_environment == 'CLICKANDSAVEAI_PRODUCTION'",
  ]) {
    assert.match(job, new RegExp(required.replaceAll(".", "\\.")));
  }
});

test("IAM mode is mutually exclusive with every existing Production mode", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  for (const jobName of [
    "production-candidate",
    "production-wif-auth-proof",
    "production-3f-metadata-probe",
    "production-3f-firebase-authority-probe",
    "production-3f-firebase-service-state-probe",
    "deploy-firebase-production",
  ]) {
    const job = extractJob(workflow, jobName);
    assert.match(
      job,
      /inputs\.authorize_3f_firebase_iam_permission_probe != 'PROBE_3F_FIREBASE_IAM_PERMISSIONS_READ_ONLY'/,
      `${jobName} must be ineligible in IAM diagnostic mode`
    );
  }
});

test("canonical workflow ref is unchanged and no standalone WIF trust boundary is introduced", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  const workflowRefs = [...workflow.matchAll(/EXPECTED_WORKFLOW_REF:\s*([^\n]+)/g)].map((match) => match[1].trim());
  assert.ok(workflowRefs.length >= 4);
  assert.ok(workflowRefs.every((value) => value === EXPECTED_WORKFLOW_REF));
  assert.doesNotMatch(workflow, /production-3f-firebase-iam-permission-diagnostic\.yml@refs\/heads\/main/);
});

test("IAM diagnostic job cannot build, deploy, publish, enable APIs, mutate IAM, or create service-account keys", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  const job = extractJob(workflow, "production-3f-firebase-iam-permission-probe");
  for (const forbidden of [
    /firebase\s+deploy/i,
    /gradle|assembleRelease|bundleRelease/i,
    /upload-artifact/i,
    /google\s+play|play.*(?:upload|publish)/i,
    /services\.(?:enable|disable|batchEnable)/i,
    /setIamPolicy|getIamPolicy/i,
    /add-iam-policy-binding|remove-iam-policy-binding/i,
    /service-accounts\s+keys\s+create/i,
    /PRODUCTION_GOOGLE_SERVICES_JSON_B64|PRODUCTION_UPLOAD_KEYSTORE_B64/,
  ]) {
    assert.doesNotMatch(job, forbidden);
  }
});

test("existing deploy path remains fail-closed while IAM mode is added", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  const deployJob = extractJob(workflow, "deploy-firebase-production");
  assert.match(deployJob, /needs: production-candidate/);
  assert.match(deployJob, /inputs\.authorize_firebase_deploy == 'DEPLOY_FIREBASE_PRODUCTION'/);
  assert.match(
    deployJob,
    /inputs\.authorize_3f_firebase_iam_permission_probe != 'PROBE_3F_FIREBASE_IAM_PERMISSIONS_READ_ONLY'/
  );
  assert.match(workflow, /authorize_firebase_deploy:[\s\S]*?default: NO_DEPLOY/);
});

test("sanitized output contract contains no response/token/header/credential leakage path", () => {
  const source = fs.readFileSync(probePath, "utf8");
  assert.doesNotMatch(source, /console\.(?:log|error)\([^\n]*(?:response|accessToken|Authorization|headers|credential|error\.message|error\.details)/i);
  assert.doesNotMatch(source, /JSON\.stringify\([^\n]*(?:response|error)/i);
  assert.match(source, /for \(const line of result\.lines\) console\.log\(line\)/);
});
