"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

require("../src/index");
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

function base64Url(value) {
  return Buffer.from(String(value), "utf8")
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function gmailMessage({
  id = "synthetic-message",
  subject = "Account update",
  body = "Internet account information. ₪99.",
} = {}) {
  const headers = [
    { name: "Subject", value: subject },
    { name: "From", value: "billing@example.test" },
    { name: "Date", value: "Wed, 05 Aug 2026 10:00:00 +0300" },
  ];
  return {
    id,
    snippet: "",
    payload: { mimeType: "text/plain", headers, body: { data: base64Url(body) } },
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

function recurringBodyCandidate(id = "body-recurring") {
  const message = gmailMessage({
    id,
    subject: "Your monthly bill",
    body: "Internet service. Billing period: July 2026. Amount due: ₪89.00.",
  });
  return bodyCandidate(parseGmailMessage(message));
}

test("production runtime scan uses the shared PDF tri-state resolver", () => {
  const scanSource = fs.readFileSync(path.resolve(__dirname, "../src/gmailScanV5Functions.js"), "utf8");
  assert.match(scanSource, /resolvePdfBodyCandidates\s*\(/);
  assert.match(scanSource, /pdfClassificationResults\s*\(/);
  assert.doesNotMatch(scanSource, /pdfCandidates\.length\s*>\s*0\s*\?/);
});

test("NO_PDF permits conservative body fallback", () => {
  const body = recurringBodyCandidate();
  const resolution = resolvePdfBodyCandidates({
    pdfAttachmentCount: 0,
    pdfOutcomes: [],
    fallbackBody: body,
  });
  assert.equal(resolution.pdfState, PDF_ANALYSIS_STATES.NO_PDF);
  assert.deepEqual(resolution.candidates.map((item) => item.sourceMessageId), [body.sourceMessageId]);
  assert.equal(candidatePdfAnalysisState(resolution.candidates[0]), PDF_ANALYSIS_STATES.NO_PDF);
});

test("PDF analysis failure permits body fallback and carries explicit failure state", () => {
  const body = recurringBodyCandidate("failed-pdf-body");
  const resolution = resolvePdfBodyCandidates({
    pdfAttachmentCount: 1,
    pdfOutcomes: [{ state: PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE }],
    fallbackBody: body,
  });
  assert.equal(resolution.pdfState, PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE);
  assert.equal(resolution.candidates.length, 1);
  assert.equal(candidatePdfAnalysisState(resolution.candidates[0]), PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE);
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
  assert.equal(candidatePdfAnalysisState(resolution.candidates[0]), PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT);
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

test("tri-state tagging is deterministic and privacy-neutral", () => {
  const candidate = canonicalCandidate();
  const tagged = tagCandidatePdfAnalysisState(candidate, PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE);
  assert.equal(candidatePdfAnalysisState(tagged), PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE);
  assert.equal(tagged.sourceMessageId, candidate.sourceMessageId);
  assert.equal(tagged.providerName, candidate.providerName);
});
