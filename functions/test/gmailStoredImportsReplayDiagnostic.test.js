"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

require("../src/entry");
const status = require("../src/gmailSyncStatusFunctions");

test("stored-import replay diagnostic uses current normalization and recurring selection semantics", () => {
  assert.equal(
    typeof status._buildStoredImportsReplayDiagnostic,
    "function",
    "stored-import replay diagnostic helper must exist"
  );

  const result = status._buildStoredImportsReplayDiagnostic([
    {
      candidates: [
        {
          sourceMessageId: "source-recurring",
          providerName: "Synthetic Recurring",
          category: "internet",
          monthlyCost: 100,
          receivedDate: "2026-08-01",
          documentClass: "RECURRING_BILL",
          recurrenceEvidence: "SUBSCRIPTION",
          recurrenceType: "FIXED_MONTHLY",
        },
        {
          sourceMessageId: "invalid-missing-provider",
          monthlyCost: 15,
          receivedDate: "2026-08-02",
          documentClass: "RECURRING_BILL",
          recurrenceEvidence: "SUBSCRIPTION",
        },
      ],
    },
    {
      invoices: [
        {
          sourceMessageId: "source-history-1",
          providerName: "Synthetic Utility",
          category: "utility",
          serviceType: "service",
          monthlyCost: 70,
          receivedDate: "2026-07-01",
          documentClass: "RECEIPT_ONLY",
          recurrenceEvidence: "NONE",
          recurrenceType: "UNKNOWN",
        },
      ],
    },
    {
      invoice: {
        sourceMessageId: "source-history-2",
        providerName: "Synthetic Utility",
        category: "utility",
        serviceType: "service",
        monthlyCost: 80,
        receivedDate: "2026-08-01",
        documentClass: "RECEIPT_ONLY",
        recurrenceEvidence: "NONE",
        recurrenceType: "UNKNOWN",
      },
    },
    {
      candidates: [
        {
          sourceMessageId: "source-one-off",
          providerName: "Synthetic One Off",
          category: "other",
          monthlyCost: 20,
          receivedDate: "2026-08-03",
          documentClass: "ONE_OFF",
          recurrenceEvidence: "NONE",
          recurrenceType: "UNKNOWN",
        },
        {
          sourceMessageId: "source-recurring",
          providerName: "Synthetic Recurring",
          category: "internet",
          monthlyCost: 100,
          receivedDate: "2026-08-01",
          documentClass: "RECURRING_BILL",
          recurrenceEvidence: "SUBSCRIPTION",
          recurrenceType: "FIXED_MONTHLY",
        },
      ],
    },
  ]);

  assert.deepEqual(result, {
    storedCandidateCount: 6,
    normalizedCandidateCount: 5,
    replayableCandidateCount: 5,
    replayableRecurringCount: 3,
    uniqueReplayableSourceCount: 4,
    duplicateCandidateCount: 1,
  });
});

test("recovery state returns replay counts only and never candidate content", () => {
  const result = status._buildGmailRecoveryState({
    connection: {
      initialBackfillCompleted: true,
      parserVersion: 7,
      encryptedRefreshToken: "forbidden-token",
    },
    authoritativeInvoiceCount: 0,
    gmailMessageImportCount: 2,
    importDocs: [
      {
        parserVersion: 7,
        candidates: [{
          sourceMessageId: "forbidden-message-id",
          providerName: "Forbidden Provider",
          category: "internet",
          monthlyCost: 100,
          receivedDate: "2026-08-01",
          documentClass: "RECURRING_BILL",
          recurrenceEvidence: "SUBSCRIPTION",
          recurrenceType: "FIXED_MONTHLY",
        }],
      },
      {
        parserVersion: 7,
        candidates: [{
          sourceMessageId: "forbidden-message-id-2",
          providerName: "Forbidden Provider 2",
          category: "other",
          monthlyCost: 15,
          receivedDate: "2026-08-02",
          documentClass: "ONE_OFF",
          recurrenceEvidence: "NONE",
          recurrenceType: "UNKNOWN",
        }],
      },
    ],
    importsTruncated: false,
  });

  assert.equal(result.normalizedCandidateCount, 2);
  assert.equal(result.replayableCandidateCount, 2);
  assert.equal(result.replayableRecurringCount, 1);
  assert.equal(result.uniqueReplayableSourceCount, 2);
  assert.equal(result.duplicateCandidateCount, 0);

  const serialized = JSON.stringify(result);
  for (const forbidden of [
    "forbidden-token",
    "forbidden-message-id",
    "Forbidden Provider",
    "monthlyCost",
    "providerName",
    "sourceMessageId",
  ]) {
    assert.equal(serialized.includes(forbidden), false, `diagnostic leaked ${forbidden}`);
  }
});
