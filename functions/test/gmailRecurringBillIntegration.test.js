"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");
const { collectPdfAttachments } = require("../src/gmailParser");
const { syncMode } = require("../src/gmailHistoryPolicy");

const scanSource = fs.readFileSync(path.join(__dirname, "../src/gmailScanV5Functions.js"), "utf8");
const watchSource = fs.readFileSync(path.join(__dirname, "../src/gmailWatchFunctions.js"), "utf8");
const engineSource = fs.readFileSync(path.join(__dirname, "../src/gmailRecurringIngestionEngine.js"), "utf8");
const notificationSource = fs.readFileSync(path.join(__dirname, "../src/gmailInvoiceNotificationFunctions.js"), "utf8");
const versionSource = fs.readFileSync(path.join(__dirname, "../src/gmailParserVersion.js"), "utf8");

test("parser revision 7 activates recurring-bill classification without reopening historic backfill", () => {
  assert.match(versionSource, /ACTIVE_GMAIL_PARSER_VERSION\s*=\s*7\s*;/);
  assert.equal(syncMode({ initialBackfillCompleted: true, parserVersion: 6 }, 7), "INCREMENTAL");
});

test("shared PDF analyzer emits explicit recurring classification evidence", () => {
  for (const field of ["documentClass", "recurrenceEvidence", "recurrenceType"]) {
    assert.match(engineSource, new RegExp(`\\b${field}\\b`));
  }
  for (const documentClass of ["RECURRING_BILL", "ONE_OFF", "REFUND", "RECEIPT_ONLY", "CONTRACT", "UNKNOWN"]) {
    assert.match(engineSource, new RegExp(documentClass));
  }
});

test("backfill applies the shared recurring-bill policy before user-visible persistence", () => {
  assert.match(scanSource, /require\("\.\/gmailRecurringBillPolicy"\)/);
  assert.match(scanSource, /pdfContentFingerprint\(pdfBase64\)/);
  assert.match(scanSource, /selectRecurringBills\(candidates\)/);
  assert.ok(
    scanSource.indexOf("selectRecurringBills(candidates)") < scanSource.lastIndexOf("persistInvoiceDocuments(uid, recurringInvoices)"),
    "recurring selection must happen before user-visible persistence"
  );
});

test("six-month backfill is one-time only", () => {
  assert.match(scanSource, /initialBackfillCompletedAt/);
  assert.match(scanSource, /hasCompletedInitialBackfill/);
  assert.match(scanSource, /alreadyCompleted:\s*true/);
  assert.match(scanSource, /INITIAL_GMAIL_LOOKBACK\s*=\s*"6m"/);
});

test("completed backfill returns the authoritative snapshot without reopening Gmail history", () => {
  assert.match(scanSource, /async function currentAuthoritativeInvoices\(uid\)/);
  const completedBranchStart = scanSource.indexOf("if (hasCompletedInitialBackfill(connection))");
  const permissionBranchStart = scanSource.indexOf("if (!Array.isArray(connection.scopes)", completedBranchStart);
  assert.notEqual(completedBranchStart, -1);
  assert.notEqual(permissionBranchStart, -1);
  const completedBranch = scanSource.slice(completedBranchStart, permissionBranchStart);
  assert.match(completedBranch, /const invoices = await currentAuthoritativeInvoices\(uid\);/);
  assert.match(completedBranch, /invoices,/);
  assert.doesNotMatch(completedBranch, /listGmailCandidateMessageIds|newer_than:/);
});

test("real-time watch stays on Gmail History and applies the same recurring-bill policy", () => {
  assert.match(watchSource, /require\("\.\/gmailRecurringBillPolicy"\)/);
  assert.match(watchSource, /users\/me\/history/);
  assert.match(watchSource, /selectRecurringBills/);
  assert.match(watchSource, /persistInvoiceDocuments\(uid, recurringInvoices\)/);
  assert.doesNotMatch(watchSource, /newer_than:/);
});

test("live push is emitted only after an accepted bill creates an authoritative gmailInvoices document", () => {
  assert.match(notificationSource, /onDocumentCreated/);
  assert.match(notificationSource, /users\/\{uid\}\/gmailInvoices\/\{invoiceId\}/);
  assert.match(notificationSource, /sendPushToUser/);
  assert.match(notificationSource, /NEW_INVOICE/);
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

test("six-month initial scan keeps filename:pdf as an independent candidate path for no-subject mail", () => {
  assert.match(scanSource, /INITIAL_GMAIL_LOOKBACK\s*=\s*"6m"/);
  assert.match(scanSource, /pdfFallbackQuery/);
  assert.match(scanSource, /has:attachment filename:pdf/);
});
