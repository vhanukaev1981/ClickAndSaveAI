"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

require("../src/index");
const recovery = require("../src/gmailRecoveryDryRunFunctions");
const { selectRecurringBills } = require("../src/gmailRecurringBillPolicy");

const STAGING_PROJECT_ID = "clickandsaveai-staging";
const VERSION = "staging-controlled-gmail-recovery-dry-run-v1";
const READONLY = "https://www.googleapis.com/auth/gmail.readonly";

function candidate(overrides = {}) {
  return {
    sourceMessageId: "synthetic-source",
    providerName: "Synthetic Provider",
    category: "synthetic-category",
    monthlyCost: 100,
    receivedDate: "2026-07-01",
    documentClass: "RECURRING_BILL",
    recurrenceEvidence: "EXPLICIT_BILLING_PERIOD",
    recurrenceType: "FIXED_MONTHLY",
    contentFingerprint: `sha256:${"a".repeat(64)}`,
    ...overrides,
  };
}

function request() {
  return {
    auth: { uid: "synthetic-user" },
    data: { recoveryDryRunVersion: VERSION },
  };
}

function passingDependencies(candidates) {
  return {
    projectId: STAGING_PROJECT_ID,
    loadConnection: async () => ({
      scopes: [READONLY],
      encryptedRefreshToken: "synthetic-encrypted-token",
    }),
    decryptRefreshToken: () => "synthetic-refresh-token",
    refreshAccessToken: async () => "synthetic-access-token",
    fetchMailboxIdentity: async () => "synthetic@example.invalid",
    scanMailbox: async () => ({
      messagesExamined: candidates.length,
      candidateMessageCount: candidates.length,
      pdfCandidateCount: 2,
      candidates,
    }),
  };
}

test("UNKNOWN candidates produce only sanitized aggregate reason counts while classification remains unchanged", () => {
  const candidates = [
    candidate({ sourceMessageId: "recurring", providerName: "SECRET_RECURRING" }),
    candidate({ sourceMessageId: "one-off", providerName: "SECRET_ONE_OFF", documentClass: "ONE_OFF", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
    candidate({ sourceMessageId: "refund", providerName: "SECRET_REFUND", documentClass: "REFUND", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
    candidate({ sourceMessageId: "receipt", providerName: "SECRET_RECEIPT", documentClass: "RECEIPT_ONLY", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
    candidate({ sourceMessageId: "contract", providerName: "SECRET_CONTRACT", documentClass: "CONTRACT", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
    candidate({
      sourceMessageId: "body-unknown",
      providerName: "SECRET_BODY_UNKNOWN",
      documentClass: "UNKNOWN",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: "",
    }),
    candidate({
      sourceMessageId: "pdf-unknown",
      providerName: "SECRET_PDF_UNKNOWN",
      documentClass: "UNKNOWN",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: `sha256:${"b".repeat(64)}`,
    }),
    candidate({
      sourceMessageId: "normalized-fallback",
      providerName: "SECRET_NORMALIZED_UNKNOWN",
      documentClass: "UNSUPPORTED_CLASSIFIER_VALUE",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: `sha256:${"c".repeat(64)}`,
    }),
  ];

  const summary = recovery._summarizeSelection(candidates, {
    messagesExamined: 8,
    candidateMessageCount: 8,
    pdfCandidateCount: 2,
  });

  assert.equal(summary.recurringBillCount, 1);
  assert.equal(summary.rejectedOneOffCount, 1);
  assert.equal(summary.rejectedRefundCount, 1);
  assert.equal(summary.rejectedReceiptOnlyCount, 1);
  assert.equal(summary.rejectedContractCount, 1);
  assert.equal(summary.unknownCount, 3);
  assert.deepEqual(summary.unknownReasonCounts, {
    BODY_FALLBACK_NO_PDF_CANDIDATE: 1,
    PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS: 1,
    NORMALIZED_UNSUPPORTED_DOCUMENT_CLASS: 1,
  });

  const serialized = JSON.stringify(summary);
  for (const forbidden of [
    "SECRET_RECURRING",
    "SECRET_ONE_OFF",
    "SECRET_REFUND",
    "SECRET_RECEIPT",
    "SECRET_CONTRACT",
    "SECRET_BODY_UNKNOWN",
    "SECRET_PDF_UNKNOWN",
    "SECRET_NORMALIZED_UNKNOWN",
    "body-unknown",
    "pdf-unknown",
    "normalized-fallback",
    "2026-07-01",
  ]) {
    assert.equal(serialized.includes(forbidden), false, `diagnostic response leaked ${forbidden}`);
  }
});

test("controlled recovery response keeps the count-only contract and exposes reason counts only", async () => {
  const candidates = [
    candidate({
      sourceMessageId: "runtime-body-fallback",
      providerName: "PRIVATE_PROVIDER",
      documentClass: "UNKNOWN",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: "",
    }),
  ];

  const result = await recovery._executeRecoveryDryRun(request(), passingDependencies(candidates));

  assert.equal(result.result, "CONTROLLED_RECOVERY_NOT_VIABLE");
  assert.equal(result.unknownCount, 1);
  assert.deepEqual(result.unknownReasonCounts, {
    BODY_FALLBACK_NO_PDF_CANDIDATE: 1,
    PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS: 0,
    NORMALIZED_UNSUPPORTED_DOCUMENT_CLASS: 0,
  });

  const serialized = JSON.stringify(result);
  for (const forbidden of [
    "PRIVATE_PROVIDER",
    "runtime-body-fallback",
    "providerName",
    "sourceMessageId",
    "monthlyCost",
    "receivedDate",
    "subject",
    "sender",
    "messageId",
  ]) {
    assert.equal(serialized.includes(forbidden), false, `recovery response leaked ${forbidden}`);
  }
});

test("classification policy behavior is unchanged for all document classes", () => {
  const selected = selectRecurringBills([
    candidate({ sourceMessageId: "recurring" }),
    candidate({ sourceMessageId: "one-off", documentClass: "ONE_OFF", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
    candidate({ sourceMessageId: "refund", documentClass: "REFUND", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
    candidate({ sourceMessageId: "receipt", providerName: "Receipt Provider", documentClass: "RECEIPT_ONLY", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
    candidate({ sourceMessageId: "contract", documentClass: "CONTRACT", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
    candidate({ sourceMessageId: "unknown", documentClass: "UNKNOWN", recurrenceEvidence: "NONE", recurrenceType: "UNKNOWN" }),
  ]);

  assert.deepEqual(selected.map((item) => item.sourceMessageId), ["recurring"]);
});

test("diagnostic change cannot introduce persistence, reconnect, scope expansion, or automatic recovery", () => {
  const recoverySource = fs.readFileSync(path.resolve(__dirname, "../src/gmailRecoveryDryRunFunctions.js"), "utf8");
  const normalRefreshSources = [
    "gmailReliableScanFunctions.js",
    "gmailScanV5Functions.js",
    "gmailHistoryPolicy.js",
  ].map((file) => fs.readFileSync(path.resolve(__dirname, "../src", file), "utf8"));

  assert.match(recoverySource, new RegExp(READONLY.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.equal(recoverySource.includes("gmail.modify"), false);
  assert.equal(recoverySource.includes("gmail.send"), false);
  assert.equal(recoverySource.includes("disconnectGmail"), false);
  assert.doesNotMatch(recoverySource, /await[\s\S]{0,160}\.(?:set|update|delete|create)\s*\(/);
  assert.doesNotMatch(recoverySource, /FieldValue/);

  for (const source of normalRefreshSources) {
    assert.equal(source.includes("runGmailRecoveryDryRun"), false);
    assert.equal(source.includes(VERSION), false);
  }
});
