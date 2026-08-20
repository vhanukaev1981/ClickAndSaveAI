"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");
const { collectPdfAttachments } = require("../src/gmailParser");

const scanSource = fs.readFileSync(path.join(__dirname, "../src/gmailScanV5Functions.js"), "utf8");
const watchSource = fs.readFileSync(path.join(__dirname, "../src/gmailWatchFunctions.js"), "utf8");
const versionSource = fs.readFileSync(path.join(__dirname, "../src/gmailParserVersion.js"), "utf8");

test("parser revision 7 forces legacy broad imports through recurring-bill re-evaluation", () => {
  assert.match(versionSource, /ACTIVE_GMAIL_PARSER_VERSION\s*=\s*7\s*;/);
});

test("backfill scan classifies PDFs and applies one shared recurring-bill policy before persistence", () => {
  assert.match(scanSource, /require\("\.\/gmailRecurringBillPolicy"\)/);
  assert.match(scanSource, /documentClass/);
  assert.match(scanSource, /recurrenceEvidence/);
  assert.match(scanSource, /recurrenceType/);
  assert.match(scanSource, /pdfContentFingerprint\(pdfBase64\)/);
  assert.match(scanSource, /selectRecurringBills\(candidates\)/);
  assert.ok(
    scanSource.indexOf("selectRecurringBills(candidates)") < scanSource.lastIndexOf("persistInvoiceDocuments(uid, recurringInvoices)"),
    "recurring selection must happen before user-visible persistence"
  );
});

test("six-month backfill is marked complete and is not the steady-state ingestion path", () => {
  assert.match(scanSource, /initialBackfillCompletedAt/);
  assert.match(scanSource, /hasCompletedInitialBackfill/);
  assert.match(scanSource, /alreadyCompleted:\s*true/);
  assert.match(scanSource, /INITIAL_GMAIL_LOOKBACK\s*=\s*"6m"/);
});

test("real-time watch uses Gmail history plus the same recurring-bill policy and pushes only selected bills", () => {
  assert.match(watchSource, /require\("\.\/gmailRecurringBillPolicy"\)/);
  assert.match(watchSource, /users\/me\/history/);
  assert.match(watchSource, /selectRecurringBills/);
  assert.match(watchSource, /sendPushToUser/);
  assert.doesNotMatch(watchSource, /persistInvoiceDocuments\(uid, parsedInvoices\)/);
});

test("octet-stream attachments with a .pdf filename are still discovered", () => {
  const attachments = collectPdfAttachments({
    parts: [{
      mimeType: "application/octet-stream",
      filename: "billing-document.pdf",
      body: { attachmentId: "attachment-1", size: 1234 },
    }],
  });
  assert.equal(attachments.length, 1);
  assert.equal(attachments[0].filename, "billing-document.pdf");
});

test("six-month scan keeps filename:pdf as an independent candidate path for no-subject mail", () => {
  assert.match(scanSource, /INITIAL_GMAIL_LOOKBACK\s*=\s*"6m"/);
  assert.match(scanSource, /filename:pdf/);
});
