"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const entry = require("../src/entry");
const status = require("../src/gmailSyncStatusFunctions");

test("connected Gmail requests the one-time initial backfill until it completes", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 7,
  });

  assert.equal(result.connected, true);
  assert.equal(result.activeParserVersion, 7);
  assert.equal(result.storedParserVersion, 7);
  assert.equal(result.upgradeRequired, true);
  assert.equal(result.lookback, "6m");
});

test("completed initial backfill stays online across parser upgrades", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 6,
    initialBackfillCompleted: true,
  });

  assert.equal(result.activeParserVersion, 7);
  assert.equal(result.storedParserVersion, 6);
  assert.equal(result.upgradeRequired, false);
  assert.equal(entry.getGmailSyncStatus, status.getGmailSyncStatus);
});

test("historic completion timestamp also prevents another six-month backfill", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 6,
    initialBackfillCompletedAt: "2026-08-20T08:00:00.000Z",
  });

  assert.equal(result.upgradeRequired, false);
});

test("disconnected account never requests initial backfill", () => {
  const result = status._buildGmailSyncStatus({ parserVersion: 0 });
  assert.equal(result.connected, false);
  assert.equal(result.upgradeRequired, false);
});

test("authoritative product context preserves unknown values", () => {
  const result = status._normalizeFinancialHomeContext({
    sourceCoverage: ["GMAIL_READONLY"],
    isCompleteHouseholdSpend: false,
  });

  assert.equal(result.observedRecurringMonthlySpend, null);
  assert.equal(result.recurringServiceCount, null);
  assert.deepEqual(result.sourceCoverage, ["GMAIL_READONLY"]);
});

test("authoritative activity ledger contains only timestamp-backed events", () => {
  const result = status._buildFinancialActivityLedger({
    connection: {
      consentedAt: "2026-08-13T08:00:00.000Z",
      lastScanAt: "2026-08-13T09:00:00.000Z",
    },
    imports: [{
      id: "import-1",
      importedAt: "2026-08-13T08:30:00.000Z",
      invoices: [{
        sourceMessageId: "source-1",
        providerName: "Provider A",
        category: "INTERNET",
        monthlyCost: 99,
        verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
      }],
    }],
  });

  assert.deepEqual(result.events.map((event) => event.type), [
    "SCAN_COMPLETED",
    "BILL_DETECTED",
    "GMAIL_CONNECTED",
  ]);
  assert.equal(result.events[1].observedAmount, 99);
  assert.equal(result.events[1].providerName, "Provider A");
  assert.equal(result.isCompleteHistory, false);
});

test("missing activity timestamps never fabricate history", () => {
  const result = status._buildFinancialActivityLedger({
    connection: {},
    imports: [{ id: "import-1", invoices: [{ providerName: "Provider A" }] }],
  });
  assert.deepEqual(result.events, []);
});

test("entry exposes the authoritative product-state callables", () => {
  assert.equal(entry.getFinancialHome, status.getFinancialHome);
  assert.equal(entry.getFinancialActivity, status.getFinancialActivity);
});

test("recovery diagnostic is sanitized counts and parser metadata only", () => {
  const result = status._buildGmailRecoveryState({
    connection: {
      initialBackfillCompleted: true,
      initialBackfillCompletedAt: "2026-08-20T08:00:00.000Z",
      parserVersion: 6,
      email: "must-not-leak@example.invalid",
      encryptedRefreshToken: "must-not-leak-token",
    },
    authoritativeInvoiceCount: 0,
    importDocs: [
      { parserVersion: 5, candidates: [{ sourceMessageId: "a", providerName: "Secret A", monthlyCost: 1 }] },
      { parserVersion: 6, invoices: [{ sourceMessageId: "b", providerName: "Secret B", monthlyCost: 2 }] },
      { parserVersion: 6, invoice: { sourceMessageId: "c", providerName: "Secret C", monthlyCost: 3 } },
    ],
    importsTruncated: false,
  });

  assert.deepEqual(result, {
    initialBackfillCompleted: true,
    initialBackfillCompletedAt: "2026-08-20T08:00:00.000Z",
    storedParserVersion: 6,
    activeParserVersion: 7,
    authoritativeInvoiceCount: 0,
    gmailMessageImportCount: 3,
    gmailMessageImportsParserVersionDistribution: { "5": 1, "6": 2 },
    storedCandidateCount: 3,
    normalizedCandidateCount: 3,
    replayableCandidateCount: 3,
    replayableRecurringCount: 0,
    uniqueReplayableSourceCount: 3,
    duplicateCandidateCount: 0,
    importsTruncated: false,
  });

  const serialized = JSON.stringify(result);
  for (const forbidden of [
    "must-not-leak@example.invalid",
    "must-not-leak-token",
    "Secret A",
    "Secret B",
    "Secret C",
    "sourceMessageId",
    "providerName",
    "monthlyCost",
  ]) {
    assert.equal(serialized.includes(forbidden), false, `diagnostic leaked ${forbidden}`);
  }
});
