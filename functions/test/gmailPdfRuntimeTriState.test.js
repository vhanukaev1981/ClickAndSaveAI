"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

require("../src/index");
const recovery = require("../src/gmailRecoveryDryRunFunctions");
const {
  bodyCandidate,
  PDF_ANALYSIS_STATES,
  resolvePdfBodyCandidates,
} = require("../src/gmailRecurringIngestionEngine");
const {
  candidatePdfAnalysisState,
  tagCandidatePdfAnalysisState,
} = require("../src/gmailPdfAnalysisState");
const { parseGmailMessage } = require("../src/gmailParser");

const VERSION = "staging-controlled-gmail-recovery-dry-run-v1";
const READONLY = "https://www.googleapis.com/auth/gmail.readonly";

function base64Url(value) {
  return Buffer.from(String(value), "utf8")
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function textPart(body) {
  return {
    mimeType: "text/plain",
    filename: "",
    body: { data: base64Url(body) },
  };
}

function pdfPart() {
  return {
    mimeType: "application/pdf",
    filename: "synthetic.pdf",
    body: { attachmentId: "synthetic-pdf", size: 100 },
  };
}

function gmailMessage({
  id = "synthetic-message",
  subject = "Account update",
  body = "Internet account information. ₪99.",
  withPdf = false,
} = {}) {
  const headers = [
    { name: "Subject", value: subject },
    { name: "From", value: "billing@example.test" },
    { name: "Date", value: "Wed, 05 Aug 2026 10:00:00 +0300" },
  ];
  return {
    id,
    snippet: "",
    payload: withPdf
      ? { mimeType: "multipart/mixed", headers, parts: [textPart(body), pdfPart()] }
      : { mimeType: "text/plain", headers, body: { data: base64Url(body) } },
  };
}

function canonicalCandidate(overrides = {}) {
  return {
    sourceMessageId: "synthetic-source",
    providerName: "Synthetic Provider",
    category: "אינטרנט",
    monthlyCost: 99,
    receivedDate: "2026-08-05",
    documentClass: "UNKNOWN",
    recurrenceEvidence: "NONE",
    recurrenceType: "UNKNOWN",
    contentFingerprint: "",
    ...overrides,
  };
}

function request() {
  return {
    auth: { uid: "synthetic-user" },
    data: { recoveryDryRunVersion: VERSION },
  };
}

function passingDependencies(scanResult) {
  return {
    projectId: "clickandsaveai-staging",
    loadConnection: async () => ({
      scopes: [READONLY],
      encryptedRefreshToken: "synthetic-encrypted-token",
    }),
    decryptRefreshToken: () => "synthetic-refresh-token",
    refreshAccessToken: async () => "synthetic-access-token",
    fetchMailboxIdentity: async () => "synthetic@example.invalid",
    scanMailbox: async () => scanResult,
  };
}

async function withMockedFetch(message, callback) {
  const originalFetch = global.fetch;
  global.fetch = async (url) => {
    const value = String(url);
    if (value.includes("/messages?") || value.includes("/messages?")) {
      return { ok: true, json: async () => ({ messages: [{ id: message.id }] }) };
    }
    if (value.includes(`/messages/${encodeURIComponent(message.id)}?format=full`)) {
      return { ok: true, json: async () => message };
    }
    if (value.includes("/attachments/")) {
      return { ok: false, json: async () => ({}) };
    }
    throw new Error(`Unexpected synthetic fetch: ${value}`);
  };
  try {
    return await callback();
  } finally {
    global.fetch = originalFetch;
  }
}

function recurringBodyCandidate(id = "body-recurring") {
  const message = gmailMessage({
    id,
    subject: "Your monthly bill",
    body: "Internet service. Billing period: July 2026. Amount due: ₪89.00.",
  });
  return bodyCandidate(parseGmailMessage(message));
}

test("runtime scan and controlled recovery source paths use the shared PDF tri-state resolver", () => {
  const scanSource = fs.readFileSync(path.resolve(__dirname, "../src/gmailScanV5Functions.js"), "utf8");
  const recoverySource = fs.readFileSync(path.resolve(__dirname, "../src/gmailRecoveryDryRunFunctions.js"), "utf8");

  for (const source of [scanSource, recoverySource]) {
    assert.match(source, /resolvePdfBodyCandidates\s*\(/);
    assert.match(source, /pdfClassificationResults\s*\(/);
  }
  assert.doesNotMatch(scanSource, /pdfCandidates\.length\s*>\s*0\s*\?/);
  assert.doesNotMatch(recoverySource, /messagePdfCandidates\.length\s*>\s*0\s*\?/);
});

test("NO PDF plus body UNKNOWN reports only BODY_FALLBACK_NO_PDF_CANDIDATE", () => {
  const summary = recovery._summarizeSelection([canonicalCandidate()], {
    messagesExamined: 1,
    candidateMessageCount: 1,
    pdfCandidateCount: 0,
  });
  assert.deepEqual(summary.unknownReasonCounts, {
    BODY_FALLBACK_NO_PDF_CANDIDATE: 1,
    BODY_FALLBACK_PDF_ANALYSIS_FAILURE: 0,
    PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS: 0,
    NORMALIZED_UNSUPPORTED_DOCUMENT_CLASS: 0,
  });
});

test("PDF analysis failure plus body UNKNOWN reports only BODY_FALLBACK_PDF_ANALYSIS_FAILURE", () => {
  assert.equal(typeof tagCandidatePdfAnalysisState, "function");
  const item = tagCandidatePdfAnalysisState(
    canonicalCandidate(),
    PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE
  );
  const summary = recovery._summarizeSelection([item], {
    messagesExamined: 1,
    candidateMessageCount: 1,
    pdfCandidateCount: 0,
  });
  assert.deepEqual(summary.unknownReasonCounts, {
    BODY_FALLBACK_NO_PDF_CANDIDATE: 0,
    BODY_FALLBACK_PDF_ANALYSIS_FAILURE: 1,
    PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS: 0,
    NORMALIZED_UNSUPPORTED_DOCUMENT_CLASS: 0,
  });
});

test("PDF analysis failure plus strong body recurring is accepted without UNKNOWN reason", () => {
  assert.equal(typeof tagCandidatePdfAnalysisState, "function");
  const item = tagCandidatePdfAnalysisState(
    recurringBodyCandidate(),
    PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE
  );
  const summary = recovery._summarizeSelection([item], {
    messagesExamined: 1,
    candidateMessageCount: 1,
    pdfCandidateCount: 0,
  });
  assert.equal(summary.recurringBillCount, 1);
  assert.equal(summary.unknownCount, 0);
  assert.deepEqual(summary.unknownReasonCounts, {
    BODY_FALLBACK_NO_PDF_CANDIDATE: 0,
    BODY_FALLBACK_PDF_ANALYSIS_FAILURE: 0,
    PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS: 0,
    NORMALIZED_UNSUPPORTED_DOCUMENT_CLASS: 0,
  });
});

test("successful PDF semantic UNKNOWN suppresses recurring-looking body", () => {
  const body = recurringBodyCandidate();
  const pdfUnknown = canonicalCandidate({
    sourceMessageId: "pdf-unknown",
    documentClass: "UNKNOWN",
    contentFingerprint: `sha256:${"a".repeat(64)}`,
  });
  const resolution = resolvePdfBodyCandidates({
    pdfAttachmentCount: 1,
    pdfOutcomes: [{
      state: PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT,
      documentClass: "UNKNOWN",
      candidate: pdfUnknown,
    }],
    fallbackBody: body,
  });
  assert.equal(resolution.pdfState, PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT);
  assert.deepEqual(resolution.candidates.map((item) => item.sourceMessageId), ["pdf-unknown"]);

  const summary = recovery._summarizeSelection(resolution.candidates, {
    messagesExamined: 1,
    candidateMessageCount: 1,
    pdfCandidateCount: 1,
  });
  assert.equal(summary.recurringBillCount, 0);
  assert.equal(summary.unknownReasonCounts.PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS, 1);
  assert.equal(summary.unknownReasonCounts.BODY_FALLBACK_NO_PDF_CANDIDATE, 0);
  assert.equal(summary.unknownReasonCounts.BODY_FALLBACK_PDF_ANALYSIS_FAILURE, 0);
});

for (const documentClass of ["CONTRACT", "REFUND", "ONE_OFF"]) {
  test(`successful PDF ${documentClass} suppresses recurring-looking body`, () => {
    const body = recurringBodyCandidate();
    const pdfCandidate = canonicalCandidate({
      sourceMessageId: `pdf-${documentClass.toLowerCase()}`,
      documentClass,
      contentFingerprint: `sha256:${documentClass.charCodeAt(0).toString(16).padStart(2, "0").repeat(32)}`,
    });
    const resolution = resolvePdfBodyCandidates({
      pdfAttachmentCount: 1,
      pdfOutcomes: [{
        state: PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT,
        documentClass,
        candidate: pdfCandidate,
      }],
      fallbackBody: body,
    });
    assert.equal(resolution.pdfState, PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT);
    assert.equal(resolution.candidates.length, 1);
    assert.equal(resolution.candidates[0].documentClass, documentClass);
  });
}

test("NO_PDF plus strong body recurring is accepted", () => {
  const body = recurringBodyCandidate();
  const resolution = resolvePdfBodyCandidates({
    pdfAttachmentCount: 0,
    pdfOutcomes: [],
    fallbackBody: body,
  });
  assert.equal(resolution.pdfState, PDF_ANALYSIS_STATES.NO_PDF);
  const summary = recovery._summarizeSelection(resolution.candidates, {
    messagesExamined: 1,
    candidateMessageCount: 1,
    pdfCandidateCount: 0,
  });
  assert.equal(summary.recurringBillCount, 1);
  assert.equal(summary.unknownCount, 0);
});

test("actual controlled Recovery scan distinguishes NO_PDF from PDF_ANALYSIS_FAILURE", async () => {
  assert.equal(typeof recovery._scanMailbox, "function");
  assert.equal(typeof candidatePdfAnalysisState, "function");

  const noPdf = gmailMessage({ id: "no-pdf" });
  const noPdfScan = await withMockedFetch(noPdf, () => recovery._scanMailbox("synthetic-token"));
  assert.equal(noPdfScan.candidates.length, 1);
  assert.equal(candidatePdfAnalysisState(noPdfScan.candidates[0]), PDF_ANALYSIS_STATES.NO_PDF);
  const noPdfResult = await recovery._executeRecoveryDryRun(request(), passingDependencies(noPdfScan));
  assert.equal(noPdfResult.unknownReasonCounts.BODY_FALLBACK_NO_PDF_CANDIDATE, 1);
  assert.equal(noPdfResult.unknownReasonCounts.BODY_FALLBACK_PDF_ANALYSIS_FAILURE, 0);

  const failedPdf = gmailMessage({ id: "failed-pdf", withPdf: true });
  const failedPdfScan = await withMockedFetch(failedPdf, () => recovery._scanMailbox("synthetic-token"));
  assert.equal(failedPdfScan.candidates.length, 1);
  assert.equal(candidatePdfAnalysisState(failedPdfScan.candidates[0]), PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE);
  const failedPdfResult = await recovery._executeRecoveryDryRun(request(), passingDependencies(failedPdfScan));
  assert.equal(failedPdfResult.unknownReasonCounts.BODY_FALLBACK_NO_PDF_CANDIDATE, 0);
  assert.equal(failedPdfResult.unknownReasonCounts.BODY_FALLBACK_PDF_ANALYSIS_FAILURE, 1);
});
