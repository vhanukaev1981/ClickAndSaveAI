"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(process.cwd(), "..");
const workflowPath = path.join(root, ".github/workflows/production-release.yml");
const probePath = path.join(root, "scripts/production-3f-firebase-authority-probe.mjs");
const workflow = fs.readFileSync(workflowPath, "utf8");

const AUTH_PHRASE = "PROBE_3F_FIREBASE_AUTHORITY_READ_ONLY";
const EXTERNAL_AUTHORITY = "EXTERNALLY_AUTHORITATIVE_VERIFIED";
const EXPECTED_PROJECT_ID = "click-save-ai-production";
const EXPECTED_PROJECT_NUMBER = "991489557172";
const EXPECTED_PACKAGE = "com.aistudio.clickandsaveai.app";
const EXPECTED_DEPLOY_SA =
  "clickandsaveai-github-deployer@click-save-ai-production.iam.gserviceaccount.com";

function jobBlock(name) {
  const marker = `  ${name}:\n`;
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1, `missing workflow job: ${name}`);
  const tail = workflow.slice(start + marker.length);
  const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
  return nextJob ? tail.slice(0, nextJob.index) : tail;
}

function fakeJsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return body;
    },
  };
}

async function loadProbeModule() {
  assert.ok(
    fs.existsSync(probePath),
    "missing scripts/production-3f-firebase-authority-probe.mjs"
  );
  return import(`${pathToFileURL(probePath).href}?t=${Date.now()}-${Math.random()}`);
}

function canonicalProject() {
  return {
    projectId: EXPECTED_PROJECT_ID,
    projectNumber: EXPECTED_PROJECT_NUMBER,
    state: "ACTIVE",
  };
}

function canonicalApp(appId = "1:991489557172:android:abcdef") {
  return {
    name: `projects/${EXPECTED_PROJECT_ID}/androidApps/${appId}`,
    appId,
    projectId: EXPECTED_PROJECT_ID,
    packageName: EXPECTED_PACKAGE,
    state: "ACTIVE",
  };
}

function canonicalConfig() {
  return {
    project_info: {
      project_id: EXPECTED_PROJECT_ID,
      project_number: EXPECTED_PROJECT_NUMBER,
    },
    client: [
      {
        client_info: {
          mobilesdk_app_id: "1:991489557172:android:abcdef",
          android_client_info: { package_name: EXPECTED_PACKAGE },
        },
        api_key: [{ current_key: "MUST_NOT_LEAK" }],
        oauth_client: [{ client_id: "MUST_NOT_LEAK_OAUTH" }],
      },
    ],
  };
}

test("Block 3F external-authority probe has an exact manual authorization input with a closed default", () => {
  assert.match(workflow, /^on:\n  workflow_dispatch:\n/m);
  assert.doesNotMatch(workflow, /\n  (?:push|pull_request|schedule|workflow_call):/);
  assert.match(
    workflow,
    /authorize_3f_external_authority_probe:\n        description:.*PROBE_3F_FIREBASE_AUTHORITY_READ_ONLY.*\n        required: true\n        default: NO_3F_EXTERNAL_PROBE\n        type: string/
  );
});

test("external-authority mode is mutually exclusive with every existing Production path", () => {
  const candidate = jobBlock("production-candidate");
  const wifProof = jobBlock("production-wif-auth-proof");
  const metadataProbe = jobBlock("production-3f-metadata-probe");
  const deploy = jobBlock("deploy-firebase-production");
  const externalProbe = jobBlock("production-3f-firebase-authority-probe");

  for (const [name, block] of [
    ["production-candidate", candidate],
    ["production-wif-auth-proof", wifProof],
    ["production-3f-metadata-probe", metadataProbe],
    ["deploy-firebase-production", deploy],
  ]) {
    assert.ok(
      block.includes(`inputs.authorize_3f_external_authority_probe != '${AUTH_PHRASE}'`),
      `${name} must be skipped in external-authority probe mode`
    );
  }

  for (const required of [
    `inputs.authorize_3f_external_authority_probe == '${AUTH_PHRASE}'`,
    "inputs.authorize_firebase_deploy == 'NO_DEPLOY'",
    "inputs.authorize_wif_auth_proof == 'NO_WIF_PROOF'",
    "inputs.authorize_3f_metadata_probe == 'NO_3F_PROBE'",
    "inputs.confirm_environment == 'CLICKANDSAVEAI_PRODUCTION'",
  ]) {
    assert.ok(externalProbe.includes(required), `probe eligibility is missing: ${required}`);
  }
});

test("dedicated Firebase authority probe preserves the accepted WIF identity boundary", () => {
  const probe = jobBlock("production-3f-firebase-authority-probe");

  assert.match(probe, /environment: production/);
  assert.match(probe, /permissions:\n      contents: read\n      id-token: write\n/);
  assert.doesNotMatch(
    probe,
    /(?:actions|issues|pull-requests|packages|deployments|checks|statuses):\s*write/
  );

  for (const expected of [
    `EXPECTED_PROJECT_ID: ${EXPECTED_PROJECT_ID}`,
    `EXPECTED_PROJECT_NUMBER: '${EXPECTED_PROJECT_NUMBER}'`,
    `EXPECTED_PACKAGE: ${EXPECTED_PACKAGE}`,
    `EXPECTED_DEPLOY_SA: ${EXPECTED_DEPLOY_SA}`,
    "GCP_WORKLOAD_IDENTITY_PROVIDER: ${{ vars.GCP_WORKLOAD_IDENTITY_PROVIDER }}",
    "GCP_DEPLOY_SERVICE_ACCOUNT: ${{ vars.GCP_DEPLOY_SERVICE_ACCOUNT }}",
  ]) {
    assert.ok(probe.includes(expected), `missing fixed boundary: ${expected}`);
  }

  for (const forbidden of [
    "PRODUCTION_FIREBASE_PROJECT_ID",
    "PRODUCTION_GOOGLE_WEB_CLIENT_ID",
    "PRODUCTION_APP_SIGNING_CERT_SHA1",
    "PRODUCTION_APP_SIGNING_CERT_SHA256",
    "PRODUCTION_UPLOAD_KEY_ALIAS",
    "PRODUCTION_GOOGLE_SERVICES_JSON_B64",
    "PRODUCTION_UPLOAD_KEYSTORE_B64",
    "PRODUCTION_UPLOAD_STORE_PASSWORD",
    "PRODUCTION_UPLOAD_KEY_PASSWORD",
  ]) {
    assert.ok(!probe.includes(forbidden), `${forbidden} must not be required by this probe`);
  }
});

test("WIF authentication boundary stays exact, ephemeral and firebase.readonly scoped", () => {
  const probe = jobBlock("production-3f-firebase-authority-probe");

  assert.match(probe, /uses: google-github-actions\/auth@v3/);
  assert.match(probe, /create_credentials_file: 'true'/);
  assert.match(probe, /cleanup_credentials: 'true'/);
  assert.match(probe, /token_format: 'access_token'/);
  assert.match(
    probe,
    /access_token_scopes: 'https:\/\/www\.googleapis\.com\/auth\/firebase\.readonly'/
  );
  assert.match(
    probe,
    /workload_identity_provider: \$\{\{ vars\.GCP_WORKLOAD_IDENTITY_PROVIDER \}\}/
  );
  assert.match(probe, /service_account: \$\{\{ vars\.GCP_DEPLOY_SERVICE_ACCOUNT \}\}/);
  assert.match(probe, /\$\{\{ github\.sha \}\}.*\$SOURCE_SHA/);
  assert.match(probe, /\$\{\{ github\.ref \}\}.*\$EXPECTED_REF/);
  assert.match(probe, /\$GCP_DEPLOY_SERVICE_ACCOUNT.*\$EXPECTED_DEPLOY_SA/);
  assert.match(
    probe,
    /projects\/\$\{EXPECTED_PROJECT_NUMBER\}\/locations\/global\/workloadIdentityPools/
  );
  assert.doesNotMatch(probe, /service-accounts\s+keys\s+create|private_key|private_key_id/i);
});

test("workflow invokes only the GET-only repository probe and preserves all no-mutation guards", () => {
  const probe = jobBlock("production-3f-firebase-authority-probe");

  assert.match(probe, /node scripts\/production-3f-firebase-authority-probe\.mjs/);
  for (const forbidden of [
    /assembleRelease|bundleRelease|gradle\s/i,
    /firebase\s+deploy/i,
    /gcloud\s+(?:functions|run|app)\s+deploy/i,
    /google\s+play|play.*(?:upload|publish)/i,
    /services\s+enable|add-iam-policy-binding|set-iam-policy|remove-iam-policy-binding/i,
    /workload-identity-pools.*(?:create|update|delete)/i,
    /service-accounts.*(?:create|keys\s+create)/i,
    /androidApps.*(?:create|patch|remove|undelete)/i,
  ]) {
    assert.doesNotMatch(probe, forbidden);
  }
});

test("probe implementation is fixed to Firebase Management GET reads and contains no mutation method", async () => {
  await loadProbeModule();
  const source = fs.readFileSync(probePath, "utf8");

  assert.match(source, /https:\/\/firebase\.googleapis\.com\/v1beta1/);
  assert.match(source, /\/projects\/\$\{encodeURIComponent\(expectedProjectId\)\}/);
  assert.match(source, /\/androidApps/);
  assert.match(source, /\/config/);
  assert.match(source, /method:\s*["']GET["']/);
  assert.doesNotMatch(source, /method:\s*["'](?:POST|PUT|PATCH|DELETE)["']/i);
  assert.doesNotMatch(
    source,
    /addFirebase|addGoogleAnalytics|services\.enable|create\(|patch\(|remove\(|undelete\(|setIamPolicy/i
  );
});

test("successful authoritative project, app and config reads use external-authority truth distinct from relationships", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const calls = [];
  const fetchImpl = async (url, options) => {
    calls.push({
      url: String(url),
      method: options?.method,
      authorization: options?.headers?.Authorization,
    });
    if (String(url).endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (String(url).endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      return fakeJsonResponse(200, { apps: [canonicalApp()] });
    }
    if (String(url).endsWith("/config")) {
      return fakeJsonResponse(200, {
        configFilename: "google-services.json",
        configFileContents: Buffer.from(JSON.stringify(canonicalConfig()), "utf8").toString("base64"),
      });
    }
    throw new Error(`unexpected URL: ${url}`);
  };

  const result = await probeFirebaseAuthority({
    accessToken: "SECRET_BEARER_TOKEN",
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    expectedPackage: EXPECTED_PACKAGE,
    fetchImpl,
  });

  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    `firebase_project_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_project_identity=VERIFIED_MATCH",
    `firebase_android_app_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_android_package=VERIFIED_MATCH",
    `firebase_android_config_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_android_config_project=VERIFIED_MATCH",
    "firebase_android_config_package=VERIFIED_MATCH",
  ]);
  assert.equal(calls.length, 3);
  assert.ok(calls.every((call) => call.method === "GET"));
  assert.ok(calls.every((call) => call.authorization === "Bearer SECRET_BEARER_TOKEN"));

  const output = result.lines.join("\n");
  assert.doesNotMatch(
    output,
    /SECRET_BEARER_TOKEN|MUST_NOT_LEAK|MUST_NOT_LEAK_OAUTH|current_key|oauth_client|configFileContents/
  );
  assert.doesNotMatch(output, /_external_authority=VERIFIED_PRESENT/);
  assert.match(output, /firebase_project_identity=VERIFIED_MATCH/);
  assert.match(output, /firebase_android_package=VERIFIED_MATCH/);
  assert.match(output, /firebase_android_config_project=VERIFIED_MATCH/);
  assert.match(output, /firebase_android_config_package=VERIFIED_MATCH/);
});

test("403, other non-2xx and network failure remain UNKNOWN_NO_ACCESS and never verified absence", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();

  for (const fetchImpl of [
    async () => fakeJsonResponse(403, { error: { status: "PERMISSION_DENIED" } }),
    async () => fakeJsonResponse(500, { error: { status: "INTERNAL" } }),
    async () => {
      throw new Error("network unavailable");
    },
  ]) {
    const result = await probeFirebaseAuthority({
      accessToken: "secret",
      expectedProjectId: EXPECTED_PROJECT_ID,
      expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
      expectedPackage: EXPECTED_PACKAGE,
      fetchImpl,
    });

    assert.equal(result.exitCode, 0);
    assert.deepEqual(result.lines, [
      "firebase_project_external_authority=UNKNOWN_NO_ACCESS",
      "firebase_project_identity=UNKNOWN_NO_ACCESS",
      "firebase_android_app_external_authority=UNKNOWN_NO_ACCESS",
      "firebase_android_package=UNKNOWN_NO_ACCESS",
      "firebase_android_config_authority=UNKNOWN_NO_ACCESS",
      "firebase_android_config_project=UNKNOWN_NO_ACCESS",
      "firebase_android_config_package=UNKNOWN_NO_ACCESS",
    ]);
    assert.doesNotMatch(result.lines.join("\n"), /VERIFIED_ABSENT/);
  }
});

test("complete authoritative Android inventory may prove absence while project authority stays externally verified", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const fetchImpl = async (url) => {
    if (String(url).endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (String(url).endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      return fakeJsonResponse(200, {
        apps: [
          {
            name: `projects/${EXPECTED_PROJECT_ID}/androidApps/other`,
            projectId: EXPECTED_PROJECT_ID,
            packageName: "com.example.other",
            state: "ACTIVE",
          },
        ],
      });
    }
    throw new Error(`unexpected URL: ${url}`);
  };

  const result = await probeFirebaseAuthority({
    accessToken: "secret",
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    expectedPackage: EXPECTED_PACKAGE,
    fetchImpl,
  });

  assert.equal(result.exitCode, 0);
  assert.deepEqual(result.lines, [
    `firebase_project_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_project_identity=VERIFIED_MATCH",
    "firebase_android_app_external_authority=VERIFIED_ABSENT",
    "firebase_android_package=VERIFIED_ABSENT",
    "firebase_android_config_authority=VERIFIED_ABSENT",
    "firebase_android_config_project=VERIFIED_ABSENT",
    "firebase_android_config_package=VERIFIED_ABSENT",
  ]);
});

test("authoritative project identity mismatch remains fail-closed", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const result = await probeFirebaseAuthority({
    accessToken: "secret",
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    expectedPackage: EXPECTED_PACKAGE,
    fetchImpl: async () =>
      fakeJsonResponse(200, {
        projectId: "different-project",
        projectNumber: "123",
        state: "ACTIVE",
      }),
  });

  assert.equal(result.exitCode, 1);
  assert.deepEqual(result.lines, [
    "firebase_project_external_authority=MISMATCH",
    "firebase_project_identity=MISMATCH",
    "firebase_android_app_external_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_package=UNKNOWN_NO_ACCESS",
    "firebase_android_config_authority=UNKNOWN_NO_ACCESS",
    "firebase_android_config_project=UNKNOWN_NO_ACCESS",
    "firebase_android_config_package=UNKNOWN_NO_ACCESS",
  ]);
});

test("successful config authority remains externally verified even when config relationships mismatch", async () => {
  const { probeFirebaseAuthority } = await loadProbeModule();
  const badConfig = canonicalConfig();
  badConfig.project_info.project_id = "different-project";
  badConfig.client[0].client_info.android_client_info.package_name = "com.example.other";

  const fetchImpl = async (url) => {
    if (String(url).endsWith(`/projects/${EXPECTED_PROJECT_ID}`)) {
      return fakeJsonResponse(200, canonicalProject());
    }
    if (String(url).endsWith(`/projects/${EXPECTED_PROJECT_ID}/androidApps`)) {
      return fakeJsonResponse(200, { apps: [canonicalApp()] });
    }
    if (String(url).endsWith("/config")) {
      return fakeJsonResponse(200, {
        configFileContents: Buffer.from(JSON.stringify(badConfig), "utf8").toString("base64"),
      });
    }
    throw new Error(`unexpected URL: ${url}`);
  };

  const result = await probeFirebaseAuthority({
    accessToken: "secret",
    expectedProjectId: EXPECTED_PROJECT_ID,
    expectedProjectNumber: EXPECTED_PROJECT_NUMBER,
    expectedPackage: EXPECTED_PACKAGE,
    fetchImpl,
  });

  assert.equal(result.exitCode, 1);
  assert.deepEqual(result.lines, [
    `firebase_project_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_project_identity=VERIFIED_MATCH",
    `firebase_android_app_external_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_android_package=VERIFIED_MATCH",
    `firebase_android_config_authority=${EXTERNAL_AUTHORITY}`,
    "firebase_android_config_project=MISMATCH",
    "firebase_android_config_package=MISMATCH",
  ]);
});
