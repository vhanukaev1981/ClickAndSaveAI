"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const probePath = path.join(root, "scripts/production-3f-firebase-service-state-probe.mjs");
const workflowPath = path.join(root, ".github/workflows/production-release.yml");
const workflow = fs.readFileSync(workflowPath, "utf8");

const EXPECTED_PROJECT_ID = "click-save-ai-production";
const EXPECTED_PROJECT_NUMBER = "991489557172";
const EXPECTED_SERVICE_NAME =
  "projects/991489557172/services/firebase.googleapis.com";
const EXPECTED_URL =
  "https://serviceusage.googleapis.com/v1/projects/991489557172/services/firebase.googleapis.com";
const UNKNOWN = "UNKNOWN_NO_ACCESS";

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

function callProbe(probeFirebaseServiceState, fetchImpl, accessToken = "SENSITIVE_ACCESS_TOKEN") {
  return probeFirebaseServiceState({
    accessToken,
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    fetchImpl,
  });
}

function assertUnknown(result, transport) {
  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    `firebase_management_api_service_state=${UNKNOWN}`,
    `firebase_management_api_service_read_transport=${transport}`,
  ]);
  assert.doesNotMatch(result.lines.join("\n"), /ENABLED|DISABLED/);
}

test("Service Usage GET classifies ENABLED only from the canonical service resource", async () => {
  const { probeFirebaseServiceState } = await loadProbeModule();
  let observed;
  const result = await callProbe(probeFirebaseServiceState, async (url, options) => {
    observed = { url: String(url), options };
    return fakeJsonResponse(200, {
      name: EXPECTED_SERVICE_NAME,
      state: "ENABLED",
      config: { name: "SENSITIVE_CONFIG_MUST_NOT_LEAK" },
    });
  });

  assert.equal(observed.url, EXPECTED_URL);
  assert.equal(observed.options.method, "GET");
  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    "firebase_management_api_service_state=ENABLED",
    "firebase_management_api_service_read_transport=SUCCESS_2XX",
  ]);
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_CONFIG_MUST_NOT_LEAK/);
});

test("Service Usage GET classifies DISABLED only from the canonical service resource", async () => {
  const { probeFirebaseServiceState } = await loadProbeModule();
  const result = await callProbe(probeFirebaseServiceState, async () =>
    fakeJsonResponse(200, { name: EXPECTED_SERVICE_NAME, state: "DISABLED" })
  );

  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    "firebase_management_api_service_state=DISABLED",
    "firebase_management_api_service_read_transport=SUCCESS_2XX",
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
  test(`HTTP ${status} remains UNKNOWN_NO_ACCESS and never implies DISABLED`, async () => {
    const { probeFirebaseServiceState } = await loadProbeModule();
    const result = await callProbe(probeFirebaseServiceState, async () =>
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

test("network failures remain UNKNOWN_NO_ACCESS without leaking exception text", async () => {
  const { probeFirebaseServiceState } = await loadProbeModule();
  const result = await callProbe(probeFirebaseServiceState, async () => {
    throw new Error("SENSITIVE_NETWORK_EXCEPTION_MUST_NOT_LEAK");
  });
  assertUnknown(result, "NETWORK_ERROR");
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_NETWORK_EXCEPTION_MUST_NOT_LEAK/);
});

test("invalid successful JSON remains UNKNOWN_NO_ACCESS without leaking parser content", async () => {
  const { probeFirebaseServiceState } = await loadProbeModule();
  const result = await callProbe(probeFirebaseServiceState, async () => invalidJsonResponse());
  assertUnknown(result, "INVALID_JSON");
  assert.doesNotMatch(result.lines.join("\n"), /SENSITIVE_INVALID_JSON_MUST_NOT_LEAK/);
});

test("mismatched resource name fails closed and cannot classify service state", async () => {
  const { probeFirebaseServiceState } = await loadProbeModule();
  const result = await callProbe(probeFirebaseServiceState, async () =>
    fakeJsonResponse(200, {
      name: "projects/991489557172/services/other.googleapis.com",
      state: "ENABLED",
    })
  );

  assert.equal(result.exitCode, 1);
  assert.deepEqual(result.lines, [
    `firebase_management_api_service_state=${UNKNOWN}`,
    "firebase_management_api_service_read_transport=SUCCESS_2XX",
  ]);
});

test("unexpected Service Usage state fails closed", async () => {
  const { probeFirebaseServiceState } = await loadProbeModule();
  const result = await callProbe(probeFirebaseServiceState, async () =>
    fakeJsonResponse(200, { name: EXPECTED_SERVICE_NAME, state: "STATE_UNSPECIFIED" })
  );
  assert.equal(result.exitCode, 1);
  assert.match(result.lines.join("\n"), /service_state=UNKNOWN_NO_ACCESS/);
});

test("noncanonical project inputs fail before any network request", async () => {
  const { probeFirebaseServiceState } = await loadProbeModule();
  let calls = 0;
  const result = await probeFirebaseServiceState({
    accessToken: "SENSITIVE_ACCESS_TOKEN",
    expectedProjectId: "clickandsaveai-staging",
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    fetchImpl: async () => {
      calls += 1;
      return fakeJsonResponse(200, { name: EXPECTED_SERVICE_NAME, state: "ENABLED" });
    },
  });
  assert.equal(calls, 0);
  assert.equal(result.exitCode, 1);
  assert.match(result.lines.join("\n"), /UNKNOWN_NO_ACCESS/);
});

test("probe source is GET-only and contains no API enablement, IAM, build, deploy, or publication mutation", () => {
  const source = fs.readFileSync(probePath, "utf8");
  assert.match(source, /serviceusage\.googleapis\.com\/v1/);
  assert.match(source, /firebase\.googleapis\.com/);
  assert.match(source, /method:\s*["']GET["']/);
  assert.doesNotMatch(source, /method:\s*["'](?:POST|PUT|PATCH|DELETE)["']/i);
  for (const forbidden of [
    /services\.(?:enable|disable|batchEnable)/i,
    /services\s+(?:enable|disable)/i,
    /add-iam-policy-binding|set-iam-policy|remove-iam-policy-binding/i,
    /roles\/(?:owner|editor|viewer|firebase\.)/i,
    /service-accounts\s+keys\s+create/i,
    /firebase\s+deploy/i,
    /assembleRelease|bundleRelease|gradle\s/i,
    /google\s+play|play.*(?:upload|publish)/i,
  ]) {
    assert.doesNotMatch(source, forbidden);
  }
});

test("workflow wires a dedicated fail-closed Service Usage probe with cloud-platform.read-only", () => {
  assert.match(workflow, /authorize_3f_service_state_probe:/);
  assert.match(workflow, /default: NO_3F_SERVICE_STATE_PROBE/);
  assert.match(workflow, /PROBE_3F_FIREBASE_SERVICE_STATE_READ_ONLY/);

  const marker = "  production-3f-firebase-service-state-probe:\n";
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1);
  const tail = workflow.slice(start + marker.length);
  const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
  const job = nextJob ? tail.slice(0, nextJob.index) : tail;

  assert.match(job, /environment: production/);
  assert.match(job, /permissions:\n      contents: read\n      id-token: write\n/);
  assert.match(job, /uses: google-github-actions\/auth@v3/);
  assert.match(
    job,
    /access_token_scopes: 'https:\/\/www\.googleapis\.com\/auth\/cloud-platform\.read-only'/
  );
  assert.match(job, /node scripts\/production-3f-firebase-service-state-probe\.mjs/);
  assert.ok(job.includes("inputs.authorize_firebase_deploy == 'NO_DEPLOY'"));
  assert.ok(job.includes("inputs.authorize_wif_auth_proof == 'NO_WIF_PROOF'"));
  assert.ok(job.includes("inputs.authorize_3f_metadata_probe == 'NO_3F_PROBE'"));
  assert.ok(job.includes("inputs.authorize_3f_external_authority_probe == 'NO_3F_EXTERNAL_PROBE'"));
  assert.ok(
    job.includes(
      "inputs.authorize_3f_service_state_probe == 'PROBE_3F_FIREBASE_SERVICE_STATE_READ_ONLY'"
    )
  );
});

test("all other Production modes explicitly exclude the Service Usage probe", () => {
  const phrase =
    "inputs.authorize_3f_service_state_probe != 'PROBE_3F_FIREBASE_SERVICE_STATE_READ_ONLY'";
  const jobMarkers = [
    "  production-candidate:\n",
    "  production-wif-auth-proof:\n",
    "  production-3f-metadata-probe:\n",
    "  production-3f-firebase-authority-probe:\n",
    "  deploy-firebase-production:\n",
  ];

  for (const marker of jobMarkers) {
    const start = workflow.indexOf(marker);
    assert.notEqual(start, -1, `missing ${marker.trim()}`);
    const tail = workflow.slice(start + marker.length);
    const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
    const job = nextJob ? tail.slice(0, nextJob.index) : tail;
    assert.ok(job.includes(phrase), `${marker.trim()} does not exclude Service Usage mode`);
  }
});

test("existing Firebase authority probe remains separate and keeps firebase.readonly scope", () => {
  const marker = "  production-3f-firebase-authority-probe:\n";
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1);
  const tail = workflow.slice(start + marker.length);
  const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
  const job = nextJob ? tail.slice(0, nextJob.index) : tail;
  assert.match(
    job,
    /access_token_scopes: 'https:\/\/www\.googleapis\.com\/auth\/firebase\.readonly'/
  );
  assert.match(job, /node scripts\/production-3f-firebase-authority-probe\.mjs/);
});
