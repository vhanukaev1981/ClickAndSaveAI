"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

// entry.js loads the production bootstrap first; index.js initializes the default
// Firebase Admin app. Requiring the stable scan module afterwards avoids creating
// a second default app with a different test-only configuration.
const entry = require("../src/entry");
const scan = require("../src/gmailScanV5Functions");

test("public scanGmailInvoices export stays on the stable scan module with active revision 6", () => {
  assert.equal(scan.GMAIL_PARSER_VERSION_ACTIVE, 6);
  assert.equal(scan.GMAIL_PARSER_VERSION_V5, 6);
  assert.equal(entry.scanGmailInvoices, scan.scanGmailInvoices);
});

test("stored invoice normalization preserves an explicit canonical service type", () => {
  const invoice = scan._v5NormalizeStoredInvoice({
    sourceMessageId: "message-1",
    providerName: "Partner",
    category: "אינטרנט",
    serviceType: "INTERNET_1000_MBPS",
    monthlyCost: 129,
    receivedDate: "2026-08-01",
  });

  assert.equal(invoice.serviceType, "INTERNET_1000_MBPS");
  assert.equal(invoice.monthlyCost, 129);
});

test("stored invoice normalization does not invent a service type", () => {
  const invoice = scan._v5NormalizeStoredInvoice({
    sourceMessageId: "message-2",
    providerName: "Partner",
    category: "אינטרנט",
    monthlyCost: 129,
    receivedDate: "2026-08-01",
  });

  assert.equal(Object.hasOwn(invoice, "serviceType"), false);
});

test("scan callable preserves aggregate recovery metadata required by staging proof", () => {
  const source = fs.readFileSync(
    path.resolve(__dirname, "..", "src", "gmailScanV5Functions.js"),
    "utf8"
  );
  const returnBlock = source.slice(source.lastIndexOf("return {"));

  for (const field of [
    "invoices",
    "scannedMessages",
    "importedCount",
    "lookback",
    "parserVersion",
    "upgradedMessages",
    "agentRefreshed",
  ]) {
    assert.match(returnBlock, new RegExp(`\\b${field}\\b`), `scan response lost ${field}`);
  }
  assert.match(source, /const INITIAL_GMAIL_LOOKBACK = "6m"/);
});
