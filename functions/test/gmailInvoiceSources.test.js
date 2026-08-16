"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  gmailMessageIdFromInvoiceSource,
  gmailInvoiceDocumentId,
  staleInvoiceSourceIds,
  invoiceSourceBelongsToMessage,
} = require("../src/gmailInvoiceSources");

test("PDF invoice source keeps the Gmail message as its stable root", () => {
  assert.equal(
    gmailMessageIdFromInvoiceSource("message-1:pdf:abc123"),
    "message-1"
  );
  assert.equal(gmailMessageIdFromInvoiceSource("message-1"), "message-1");
});

test("body to PDF parser migration marks only the old body source as stale", () => {
  assert.deepEqual(
    staleInvoiceSourceIds(
      [{ sourceMessageId: "message-1" }],
      [{ sourceMessageId: "message-1:pdf:abc123" }]
    ),
    ["message-1"]
  );
});

test("multiple current PDFs are preserved while removed siblings are cleaned up", () => {
  assert.deepEqual(
    staleInvoiceSourceIds(
      [
        { sourceMessageId: "message-2:pdf:first" },
        { sourceMessageId: "message-2:pdf:old" },
      ],
      [
        { sourceMessageId: "message-2:pdf:first" },
        { sourceMessageId: "message-2:pdf:second" },
      ]
    ),
    ["message-2:pdf:old"]
  );
});

test("source family matching does not collapse unrelated Gmail messages", () => {
  assert.equal(invoiceSourceBelongsToMessage("message-3", "message-3"), true);
  assert.equal(invoiceSourceBelongsToMessage("message-3:pdf:x", "message-3"), true);
  assert.equal(invoiceSourceBelongsToMessage("message-30:pdf:x", "message-3"), false);
});

test("Firestore invoice document id is deterministic for a source id", () => {
  const first = gmailInvoiceDocumentId("message-4:pdf:x");
  const second = gmailInvoiceDocumentId("message-4:pdf:x");
  assert.equal(first, second);
  assert.equal(first.length, 64);
});
