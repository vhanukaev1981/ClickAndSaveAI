"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const probePath = path.join(root, "scripts/production-3f-firebase-iam-permission-probe.mjs");
const workflowPath = path.join(
  root,
  ".github/workflows/production-3f-firebase-iam-permission-diagnostic.yml"
);

const EXPECTED_PROJECT_ID = "click-save-ai-production";
const EXPECTED_PROJECT_NUMBER = "991489557172";
const EXPECTED_URL =
  "https://cloudresourcemanager.googleapis.com/v1/projects/click-save-ai-production:testIamPermissions";
const EXPECTED_PERMISSIONS = [
  "firebase.projects.get",
  "firebase.clients.list",
  "firebase.clients.get",
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

function assertTransportOnly(result, transport) {
  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [`firebase_iam_permission_test_transport=${transport}`]);
  assert.doesNotMatch(result.lines.join("\n"), /=(?:PRESENT|ABSENT)$/m);
}

test("testIamPermissions sends one canonical read-only permission test request", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const calls = [];
  const result = await callProbe(probeFirebaseIamPermissions, async (url, options) => {
    calls.push({ url: String(url), options });
    return fakeJsonResponse(200, {
      permissions: ["firebase.projects.get", "firebase.clients.get"],
      sensitive: "SENSITIVE_RESPONSE_FIELD_MUST_NOT_LEAK",
    });
  });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, EXPECTED_URL);
  assert.equal(calls[0].options.method, "POST");
  assert.equal(calls[0].options.headers.Accept, "application/json");
  assert.equal(calls[0].options.headers["Content-Type"], "application/json");
  assert.match(calls[0].options.headers.Authorization, /^Bearer /);
  assert.deepEqual(JSON.parse(calls[0].options.body), {
    permissions: EXPECTED_PERMISSIONS,
  });
  assert.deepEqual(result.lines, [
    "firebase_iam_permission_firebase_projects_get=PRESENT",
    "firebase_iam_permission_firebase_clients_list=ABSENT",
    "firebase_iam_permission_firebase_clients_get=PRESENT",
    "firebase_iam_permission_test_transport=SUCCESS_2XX",
  ]);
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_RESPONSE_FIELD_MUST_NOT_LEAK/);
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_ACCESS_TOKEN/);
});

test("empty successful permission response classifies all requested permissions ABSENT", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const result = await callProbe(probeFirebaseIamPermissions, async () =>
    fakeJsonResponse(200, {})
  );

  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    "firebase_iam_permission_firebase_projects_get=ABSENT",
    "firebase_iam_permission_firebase_clients_list=ABSENT",
    "firebase_iam_permission_firebase_clients_get=ABSENT",
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
  test(`HTTP ${status} emits sanitized transport only and never fabricates permission absence`, async () => {
    const { probeFirebaseIamPermissions } = await loadProbeModule();
    const result = await callProbe(probeFirebaseIamPermissions, async () =>
      fakeJsonResponse(status, {
        error: {
          message: "SENSITIVE_ERROR_MESSAGE_MUST_NOT_LEAK",
          details: "SENSITIVE_ERROR_DETAILS_MUST_NOT_LEAK",
        },
      })
    );

    assertTransportOnly(result, transport);
    assert.doesNotMatch(
      result.lines.join("\n"),
      /SENSITIVE_ERROR_MESSAGE_MUST_NOT_LEAK|SENSITIVE_ERROR_DETAILS_MUST_NOT_LEAK/
    );
  });
}

test("network failure emits sanitized transport only", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const result = await callProbe(probeFirebaseIamPermissions, async () => {
    throw new Error("SENSITIVE_NETWORK_EXCEPTION_MUST_NOT_LEAK");
  });
  assertTransportOnly(result, "NETWORK_ERROR");
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_NETWORK_EXCEPTION_MUST_NOT_LEAK/);
});

test("invalid successful JSON emits sanitized transport only", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();
  const result = await callProbe(probeFirebaseIamPermissions, async () => invalidJsonResponse());
  assertTransportOnly(result, "INVALID_JSON");
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_INVALID_JSON_MUST_NOT_LEAK/);
});

test("unexpected or duplicate permissions in a successful response fail closed", async () => {
  const { probeFirebaseIamPermissions } = await loadProbeModule();

  for (const permissions of [
    ["firebase.projects.get", "resourcemanager.projects.setIamPolicy"],
    ["firebase.projects.get", "firebase.projects.get"],
    "firebase.projects.get",
  ]) {
    const result = await callProbe(probeFirebaseIamPermissions, async () =>
      fakeJsonResponse(200, { permissions })
    );
    assert.equal(result.exitCode, 1);
    assert.deepEqual(result.lines, ["firebase_iam_permission_test_transport=INVALID_JSON"]);
  }
});

test("noncanonical Production identity fails before any network request", async () => {
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
  assert.equal(result.exitCode, 1);
  assert.deepEqual(result.lines, ["firebase_iam_permission_test_transport=NOT_ATTEMPTED_GUARD_FAILURE"]);
});

test("probe source is limited to the exact Resource Manager testIamPermissions call", () => {
  const source = fs.readFileSync(probePath, "utf8");
  assert.match(source, /cloudresourcemanager\.googleapis\.com\/v1/);
  assert.match(source, /:testIamPermissions/);
  assert.match(source, /firebase\.projects\.get/);
  assert.match(source, /firebase\.clients\.list/);
  assert.match(source, /firebase\.clients\.get/);
  assert.match(source, /method:\s*["']POST["']/);

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

test("dedicated workflow is manual-only, SHA-bound, Production-scoped, and least-privilege", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");

  assert.match(workflow, /^name: Block 3F Firebase IAM Permission Diagnostic$/m);
  assert.match(workflow, /workflow_dispatch:/);
  assert.doesNotMatch(workflow, /^\s*(?:push|pull_request|schedule):/m);
  assert.match(workflow, /authorize_3f_iam_permission_probe:/);
  assert.match(workflow, /default: NO_3F_IAM_PERMISSION_PROBE/);
  assert.match(workflow, /PROBE_3F_FIREBASE_IAM_PERMISSIONS_READ_ONLY/);
  assert.match(workflow, /group: clickandsaveai-production-release/);
  assert.match(workflow, /environment: production/);
  assert.match(workflow, /contents: read/);
  assert.match(workflow, /id-token: write/);
  assert.match(workflow, /EXPECTED_PROJECT_ID: click-save-ai-production/);
  assert.match(workflow, /EXPECTED_PROJECT_NUMBER: '991489557172'/);
  assert.match(
    workflow,
    /EXPECTED_DEPLOY_SA: clickandsaveai-github-deployer@click-save-ai-production\.iam\.gserviceaccount\.com/
  );
  assert.match(workflow, /github\.sha.*SOURCE_SHA/);
  assert.match(workflow, /github\.ref.*EXPECTED_REF/);
  assert.match(workflow, /uses: google-github-actions\/auth@v3/);
  assert.match(
    workflow,
    /access_token_scopes: 'https:\/\/www\.googleapis\.com\/auth\/iam\.test'/
  );
  assert.match(workflow, /node scripts\/production-3f-firebase-iam-permission-probe\.mjs/);
});

test("dedicated workflow cannot build, deploy, publish, enable APIs, or mutate IAM", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");
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
    assert.doesNotMatch(workflow, forbidden);
  }
});
