"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

// Initialize Firebase Admin through the historical bootstrap before loading the
// focused recovery module, matching the production module loading contract.
require("../src/index");
const recovery = require("../src/gmailRecoveryDryRunFunctions");

const STAGING_PROJECT_ID = "clickandsaveai-staging";
const VERSION = "staging-controlled-gmail-recovery-dry-run-v1";
const READONLY = "https://www.googleapis.com/auth/gmail.readonly";

function request(overrides = {}) {
  return {
    auth: {
      uid: "synthetic-user",
      token: { email: "synthetic@example.invalid" },
    },
    data: { recoveryDryRunVersion: VERSION },
    ...overrides,
  };
}

function candidate(overrides = {}) {
  return {
    sourceMessageId: "synthetic-message:pdf:one",
    providerName: "Synthetic Recurring Provider",
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

function passingDependencies(candidates = [candidate()]) {
  return {
    projectId: STAGING_PROJECT_ID,
    loadConnection: async () => ({
      scopes: [READONLY],
      encryptedRefreshToken: "synthetic-encrypted-token",
      email: "synthetic@example.invalid",
    }),
    decryptRefreshToken: () => "synthetic-refresh-token",
    refreshAccessToken: async () => "synthetic-access-token",
    fetchMailboxIdentity: async () => "synthetic@example.invalid",
    scanMailbox: async () => ({
      messagesExamined: 3,
      candidateMessageCount: 2,
      pdfCandidateCount: candidates.length,
      candidates,
    }),
  };
}

test("controlled recovery dry-run is staging-only, authenticated, and exact-version gated", async () => {
  await assert.rejects(
    recovery._executeRecoveryDryRun(
      request({ auth: null }),
      passingDependencies()
    ),
    (error) => error?.code === "unauthenticated"
  );

  await assert.rejects(
    recovery._executeRecoveryDryRun(
      request(),
      { ...passingDependencies(), projectId: "click-save-ai-production" }
    ),
    (error) => error?.code === "failed-precondition"
  );

  await assert.rejects(
    recovery._executeRecoveryDryRun(
      request({ data: { recoveryDryRunVersion: "wrong-version" } }),
      passingDependencies()
    ),
    (error) => error?.code === "failed-precondition"
  );
});

test("credential preflight failure blocks the six-month scan", async () => {
  let scanCalls = 0;
  const deps = passingDependencies();
  deps.decryptRefreshToken = () => {
    throw new Error("synthetic decrypt failure");
  };
  deps.scanMailbox = async () => {
    scanCalls += 1;
    return { messagesExamined: 999, candidateMessageCount: 999, pdfCandidateCount: 999, candidates: [] };
  };

  const result = await recovery._executeRecoveryDryRun(request(), deps);

  assert.equal(scanCalls, 0);
  assert.equal(result.result, "BLOCKED");
  assert.equal(result.credentialPreflight, "FAIL");
  assert.equal(result.failureStage, "DECRYPT_REFRESH_TOKEN");
  assert.equal(result.messagesExamined, 0);
});

test("dry-run returns sanitized count-only evidence using the current recurring policy", async () => {
  const duplicateFingerprint = `sha256:${"b".repeat(64)}`;
  const candidates = [
    candidate({
      sourceMessageId: "recurring-original",
      providerName: "SECRET_RECURRING_PROVIDER",
      contentFingerprint: duplicateFingerprint,
    }),
    candidate({
      sourceMessageId: "recurring-forwarded",
      providerName: "SECRET_RECURRING_PROVIDER",
      contentFingerprint: duplicateFingerprint,
    }),
    candidate({
      sourceMessageId: "one-off",
      providerName: "SECRET_ONE_OFF_PROVIDER",
      documentClass: "ONE_OFF",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: `sha256:${"c".repeat(64)}`,
      receivedDate: "2026-07-02",
    }),
    candidate({
      sourceMessageId: "refund",
      providerName: "SECRET_REFUND_PROVIDER",
      documentClass: "REFUND",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: `sha256:${"d".repeat(64)}`,
      receivedDate: "2026-07-03",
    }),
    candidate({
      sourceMessageId: "receipt",
      providerName: "SECRET_RECEIPT_PROVIDER",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: `sha256:${"e".repeat(64)}`,
      receivedDate: "2026-07-04",
    }),
    candidate({
      sourceMessageId: "contract",
      providerName: "SECRET_CONTRACT_PROVIDER",
      documentClass: "CONTRACT",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: `sha256:${"f".repeat(64)}`,
      receivedDate: "2026-07-05",
    }),
    candidate({
      sourceMessageId: "unknown",
      providerName: "SECRET_UNKNOWN_PROVIDER",
      documentClass: "UNKNOWN",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      contentFingerprint: `sha256:${"1".repeat(64)}`,
      receivedDate: "2026-07-06",
    }),
  ];

  const deps = passingDependencies(candidates);
  deps.scanMailbox = async () => ({
    messagesExamined: 12,
    candidateMessageCount: 6,
    pdfCandidateCount: 7,
    candidates,
  });

  const result = await recovery._executeRecoveryDryRun(request(), deps);

  assert.equal(result.result, "CONTROLLED_RECOVERY_VIABLE");
  assert.equal(result.credentialPreflight, "PASS");
  assert.equal(result.failureStage, "");
  assert.equal(result.messagesExamined, 12);
  assert.equal(result.candidateMessageCount, 6);
  assert.equal(result.pdfCandidateCount, 7);
  assert.equal(result.normalizedCandidateCount, 7);
  assert.equal(result.recurringBillCount, 1);
  assert.equal(result.uniqueRecurringSourceCount, 1);
  assert.equal(result.duplicateCount, 1);
  assert.equal(result.rejectedOneOffCount, 1);
  assert.equal(result.rejectedRefundCount, 1);
  assert.equal(result.rejectedReceiptOnlyCount, 1);
  assert.equal(result.rejectedContractCount, 1);
  assert.equal(result.unknownCount, 1);
  assert.deepEqual(result.unknownReasonCounts, {
    BODY_FALLBACK_NO_PDF_CANDIDATE: 0,
    BODY_FALLBACK_PDF_ANALYSIS_FAILURE: 0,
    PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS: 1,
    NORMALIZED_UNSUPPORTED_DOCUMENT_CLASS: 0,
  });

  const serialized = JSON.stringify(result);
  for (const forbidden of [
    "SECRET_RECURRING_PROVIDER",
    "SECRET_ONE_OFF_PROVIDER",
    "SECRET_REFUND_PROVIDER",
    "SECRET_RECEIPT_PROVIDER",
    "SECRET_CONTRACT_PROVIDER",
    "SECRET_UNKNOWN_PROVIDER",
    "synthetic@example.invalid",
    "synthetic-message",
  ]) {
    assert.equal(serialized.includes(forbidden), false, `dry-run response leaked ${forbidden}`);
  }

  assert.deepEqual(Object.keys(result).sort(), [
    "candidateMessageCount",
    "credentialPreflight",
    "duplicateCount",
    "failureStage",
    "messagesExamined",
    "normalizedCandidateCount",
    "pdfCandidateCount",
    "recoveryDryRunVersion",
    "recurringBillCount",
    "rejectedContractCount",
    "rejectedOneOffCount",
    "rejectedReceiptOnlyCount",
    "rejectedRefundCount",
    "result",
    "uniqueRecurringSourceCount",
    "unknownCount",
    "unknownReasonCounts",
  ].sort());
});

test("dry-run implementation has no persistence, agent, notification, disconnect, or scope expansion path", () => {
  const source = fs.readFileSync(
    path.resolve(__dirname, "..", "src", "gmailRecoveryDryRunFunctions.js"),
    "utf8"
  );

  assert.match(source, new RegExp(READONLY.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.equal(source.includes("gmail.modify"), false);
  assert.equal(source.includes("gmail.send"), false);
  assert.equal(source.includes("runFinancialAgent"), false);
  assert.equal(source.includes("sendPush"), false);
  assert.equal(source.includes("disconnectGmail"), false);
  assert.doesNotMatch(
    source,
    /await[\s\S]{0,160}\.(?:set|update|delete|create)\s*\(/
  );
  assert.doesNotMatch(source, /FieldValue/);
});

test("normal refresh, parser policy and app-independent history policy cannot invoke recovery dry-run", () => {
  const sourceFiles = [
    "gmailReliableScanFunctions.js",
    "gmailScanV5Functions.js",
    "gmailHistoryPolicy.js",
  ];
  for (const file of sourceFiles) {
    const source = fs.readFileSync(path.resolve(__dirname, "..", "src", file), "utf8");
    assert.equal(source.includes("runGmailRecoveryDryRun"), false, `${file} invokes dry-run`);
    assert.equal(source.includes(VERSION), false, `${file} knows the recovery dry-run version`);
  }
});

test("normal scan INTERNAL errors are sanitized with an exact reconciliation stage", () => {
  const source = fs.readFileSync(
    path.resolve(__dirname, "..", "src", "gmailReliableScanFunctions.js"),
    "utf8"
  );
  assert.match(source, /let stage = "LOAD_CONNECTION"/);
  assert.match(source, /GMAIL_RECONCILIATION_INTERNAL_\$\{stage\}/);
  assert.match(source, /details:\s*\{[\s\S]*stage,/);
});
