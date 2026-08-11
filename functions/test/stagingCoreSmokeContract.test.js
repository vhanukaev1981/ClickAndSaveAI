"use strict";

const path = require("node:path");
const { pathToFileURL } = require("node:url");
const test = require("node:test");
const assert = require("node:assert/strict");

async function loadSmokeModule() {
  const modulePath = path.resolve(__dirname, "..", "..", "scripts", "staging-core-smoke.mjs");
  return import(pathToFileURL(modulePath).href);
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
