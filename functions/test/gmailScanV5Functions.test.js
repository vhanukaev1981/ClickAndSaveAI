"use strict";

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
