"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

const workflowPath = path.resolve(__dirname, "..", "..", ".github", "workflows", "deploy-staging.yml");

async function loadSmokeModule() {
  const modulePath = path.resolve(__dirname, "..", "..", "scripts", "staging-core-smoke.mjs");
  return import(`${pathToFileURL(modulePath).href}?t=${Date.now()}-${Math.random()}`);
}

test("staging smoke summary keeps unknown financial values null rather than inventing zero", async () => {
  const { sanitizeSmokeSummary } = await loadSmokeModule();
  const summary = sanitizeSmokeSummary({
    projectId: "clickandsaveai-staging",
    sourceSha: "a".repeat(40),
    gmailResponse: { connected: true, consentVersion: "gmail-readonly-v1" },
    scanResponse: { invoices: [], scannedMessages: 12, importedCount: 0, parserVersion: 6, agentRefreshed: true },
    financialHomeResponse: { context: { sourceCoverage: ["GMAIL_READONLY"] }, insights: [], opportunities: [] },
  });

  assert.equal(summary.projectId, "clickandsaveai-staging");
  assert.equal(summary.sourceSha, "a".repeat(40));
  assert.equal(summary.gmail.connected, true);
  assert.equal(summary.scan.scannedMessages, 12);
  assert.equal(summary.scan.returnedInvoices, 0);
  assert.equal(summary.financialHome.recurringServiceCount, null);
  assert.equal(summary.financialHome.observedRecurringMonthlySpend, null);
  assert.deepEqual(summary.financialHome.sourceCoverage, ["GMAIL_READONLY"]);
});

test("staging smoke summary exposes only aggregate evidence and strips raw Gmail/secrets", async () => {
  const { sanitizeSmokeSummary } = await loadSmokeModule();
  const summary = sanitizeSmokeSummary({
    projectId: "clickandsaveai-staging",
    sourceSha: "b".repeat(40),
    gmailResponse: {
      connected: true,
      email: "private@example.com",
      consentVersion: "gmail-readonly-v1",
      refreshToken: "never-log-me",
    },
    scanResponse: {
      scannedMessages: 42,
      importedCount: 3,
      parserVersion: 6,
      agentRefreshed: true,
      invoices: [
        {
          sourceMessageId: "raw-message-id",
          providerName: "Provider",
          monthlyCost: 123.45,
          subject: "private subject",
          body: "private body",
          snippet: "private snippet",
        },
      ],
      accessToken: "never-log-access-token",
    },
    financialHomeResponse: {
      context: {
        recurringServiceCount: 2,
        observedRecurringMonthlySpend: 250.5,
        sourceCoverage: ["GMAIL_READONLY"],
        rawIds: ["internal-id"],
      },
      insights: [{ id: "insight-secret" }],
      opportunities: [{ id: "opportunity-secret" }],
    },
  });

  assert.deepEqual(summary, {
    projectId: "clickandsaveai-staging",
    sourceSha: "b".repeat(40),
    gmail: {
      connected: true,
      consentVersion: "gmail-readonly-v1",
    },
    scan: {
      scannedMessages: 42,
      returnedInvoices: 1,
      importedCount: 3,
      parserVersion: 6,
      agentRefreshed: true,
    },
    financialHome: {
      recurringServiceCount: 2,
      observedRecurringMonthlySpend: 250.5,
      sourceCoverage: ["GMAIL_READONLY"],
      insightCount: 1,
      opportunityCount: 1,
    },
  });

  const serialized = JSON.stringify(summary);
  for (const forbidden of [
    "private@example.com",
    "never-log-me",
    "never-log-access-token",
    "raw-message-id",
    "private subject",
    "private body",
    "private snippet",
    "insight-secret",
    "opportunity-secret",
    "internal-id",
  ]) {
    assert.equal(serialized.includes(forbidden), false, `summary leaked ${forbidden}`);
  }
});

test("staging smoke rejects a non-staging project or non-immutable source SHA", async () => {
  const { sanitizeSmokeSummary } = await loadSmokeModule();

  assert.throws(
    () => sanitizeSmokeSummary({ projectId: "production-project", sourceSha: "c".repeat(40) }),
    /staging/i
  );
  assert.throws(
    () => sanitizeSmokeSummary({ projectId: "clickandsaveai-staging", sourceSha: "branch-name" }),
    /40-character/i
  );
});

test("WIF smoke mints short-lived Firebase Auth and App Check tokens for an explicit staging UID", async () => {
  const { mintSmokeTokensWithAdmin } = await loadSmokeModule();
  const calls = [];
  const fakeApp = { name: "fake-smoke-app" };
  const adminProvider = async () => ({
    applicationDefault: () => ({ kind: "adc" }),
    getApps: () => [],
    initializeApp: (options, name) => {
      calls.push(["initializeApp", options, name]);
      return fakeApp;
    },
    getAuth: (app) => {
      assert.equal(app, fakeApp);
      return {
        createCustomToken: async (uid) => {
          calls.push(["createCustomToken", uid]);
          return "short-lived-custom-auth-token";
        },
      };
    },
    getAppCheck: (app) => {
      assert.equal(app, fakeApp);
      return {
        createToken: async (appId, options) => {
          calls.push(["createAppCheckToken", appId, options]);
          return { token: "short-lived-app-check-token", ttlMillis: options.ttlMillis };
        },
      };
    },
  });
  const fetchImpl = async (url, options) => {
    calls.push(["fetch", String(url), JSON.parse(options.body)]);
    return {
      ok: true,
      status: 200,
      json: async () => ({ idToken: "short-lived-firebase-id-token" }),
    };
  };

  const tokens = await mintSmokeTokensWithAdmin({
    uid: "staging-user-uid",
    projectId: "clickandsaveai-staging",
    appId: "1:1234567890:android:abcdef",
    apiKey: "api-key-for-test",
    serviceAccountId: "clickandsaveai-github-deployer@clickandsaveai-staging.iam.gserviceaccount.com",
    fetchImpl,
    adminProvider,
  });

  assert.deepEqual(tokens, {
    idToken: "short-lived-firebase-id-token",
    appCheckToken: "short-lived-app-check-token",
  });
  assert.deepEqual(calls[0], [
    "initializeApp",
    {
      credential: { kind: "adc" },
      projectId: "clickandsaveai-staging",
      serviceAccountId: "clickandsaveai-github-deployer@clickandsaveai-staging.iam.gserviceaccount.com",
    },
    "clickandsaveai-staging-smoke",
  ]);
  assert.deepEqual(calls[1], ["createCustomToken", "staging-user-uid"]);
  assert.equal(calls[2][0], "createAppCheckToken");
  assert.equal(calls[2][1], "1:1234567890:android:abcdef");
  assert.equal(calls[2][2].ttlMillis, 30 * 60 * 1000);
  assert.match(calls[3][1], /accounts:signInWithCustomToken/);
  assert.equal(calls[3][2].token, "short-lived-custom-auth-token");
  assert.equal(calls[3][2].returnSecureToken, true);
});

test("deployment runs authenticated staging truth smoke only after Firebase deploy and uploads sanitized evidence", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  const deployIndex = workflow.indexOf("Deploy functions and Firestore to staging");
  const smokeIndex = workflow.indexOf("Run authenticated staging truth smoke");

  assert.ok(deployIndex >= 0, "Firebase deploy step is missing");
  assert.ok(smokeIndex > deployIndex, "staging smoke must run only after Firebase deploy");
  assert.match(workflow, /STAGING_SMOKE_USER_UID/);
  assert.match(workflow, /GCP_DEPLOY_SERVICE_ACCOUNT/);
  assert.doesNotMatch(workflow, /STAGING_TEST_FIREBASE_REFRESH_TOKEN/);
  assert.doesNotMatch(workflow, /STAGING_APPCHECK_DEBUG_TOKEN/);
  assert.match(workflow, /node scripts\/staging-core-smoke\.mjs/);
  assert.match(workflow, /actions\/upload-artifact@v4/);
  assert.match(workflow, /staging-core-smoke\.json/);
});

test("deployment derives Firebase API key and Android app ID from the already-provisioned staging google-services config", () => {
  const workflow = fs.readFileSync(workflowPath, "utf8");

  assert.match(workflow, /STAGING_GOOGLE_SERVICES_JSON_B64/);
  assert.match(workflow, /Derive staging Firebase smoke config/);
  assert.match(workflow, /com\.aistudio\.clickandsaveai\.app/);
  assert.match(workflow, /STAGING_FIREBASE_API_KEY=/);
  assert.match(workflow, /STAGING_APPCHECK_APP_ID=/);
  assert.doesNotMatch(workflow, /secrets\.STAGING_FIREBASE_API_KEY/);
  assert.doesNotMatch(workflow, /vars\.STAGING_APPCHECK_APP_ID/);
});
