"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const entry = require("../src/entry");
const status = require("../src/gmailSyncStatusFunctions");

test("Gmail sync status requires one-time upgrade below active parser revision", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 5,
  });

  assert.equal(result.connected, true);
  assert.equal(result.activeParserVersion, 6);
  assert.equal(result.storedParserVersion, 5);
  assert.equal(result.upgradeRequired, true);
  assert.equal(result.lookback, "6m");
});

test("Gmail sync status is current after revision 6 backfill", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 6,
  });

  assert.equal(result.upgradeRequired, false);
  assert.equal(entry.getGmailSyncStatus, status.getGmailSyncStatus);
});

test("disconnected account never requests parser backfill", () => {
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
