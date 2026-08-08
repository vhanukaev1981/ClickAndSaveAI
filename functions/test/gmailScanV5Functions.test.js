"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const v5 = require("../src/gmailScanV5Functions");
const entry = require("../src/entry");

test("public scanGmailInvoices export is routed to parser v5", () => {
  assert.equal(v5.GMAIL_PARSER_VERSION_V5, 5);
  assert.equal(entry.scanGmailInvoices, v5.scanGmailInvoices);
});

test("parser-v5 stored invoice normalization preserves an explicit canonical service type", () => {
  const invoice = v5._v5NormalizeStoredInvoice({
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

test("parser-v5 stored invoice normalization does not invent a service type", () => {
  const invoice = v5._v5NormalizeStoredInvoice({
    sourceMessageId: "message-2",
    providerName: "Partner",
    category: "אינטרנט",
    monthlyCost: 129,
    receivedDate: "2026-08-01",
  });

  assert.equal(Object.hasOwn(invoice, "serviceType"), false);
});
