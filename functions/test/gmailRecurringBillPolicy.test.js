"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  pdfContentFingerprint,
  selectRecurringBills,
} = require("../src/gmailRecurringBillPolicy");

function bill(overrides = {}) {
  return {
    sourceMessageId: "m1:pdf:one",
    providerName: "Example Telecom",
    category: "תקשורת",
    monthlyCost: 120,
    receivedDate: "2026-07-01",
    documentClass: "RECURRING_BILL",
    recurrenceEvidence: "EXPLICIT_BILLING_PERIOD",
    recurrenceType: "PERIODIC_VARIABLE",
    contentFingerprint: "sha256:a",
    ...overrides,
  };
}

test("accepts an explicitly recurring bill", () => {
  const selected = selectRecurringBills([bill()]);
  assert.equal(selected.length, 1);
  assert.equal(selected[0].sourceMessageId, "m1:pdf:one");
});

test("rejects one-off, refund and contract documents even when monetary", () => {
  const selected = selectRecurringBills([
    bill({ sourceMessageId: "one", documentClass: "ONE_OFF" }),
    bill({ sourceMessageId: "refund", documentClass: "REFUND" }),
    bill({ sourceMessageId: "contract", documentClass: "CONTRACT" }),
  ]);
  assert.deepEqual(selected, []);
});

test("promotes repeated receipt-only service charges across distinct billing dates", () => {
  const selected = selectRecurringBills([
    bill({
      sourceMessageId: "usage-1",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-08",
      contentFingerprint: "sha256:usage-1",
    }),
    bill({
      sourceMessageId: "usage-2",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-28",
      contentFingerprint: "sha256:usage-2",
    }),
  ]);

  assert.equal(selected.length, 2);
  assert.ok(selected.every((item) => item.recurrenceEvidence === "REPEATED_PROVIDER_HISTORY"));
});

test("does not promote repeated one-off purchases from the same merchant", () => {
  const selected = selectRecurringBills([
    bill({ sourceMessageId: "gift-1", providerName: "Example Gift Store", documentClass: "ONE_OFF", receivedDate: "2026-05-01" }),
    bill({ sourceMessageId: "gift-2", providerName: "Example Gift Store", documentClass: "ONE_OFF", receivedDate: "2026-06-01" }),
  ]);
  assert.deepEqual(selected, []);
});

test("deduplicates identical PDFs across forwarded Gmail messages by content hash", () => {
  const selected = selectRecurringBills([
    bill({ sourceMessageId: "original:pdf:a", contentFingerprint: "sha256:same" }),
    bill({ sourceMessageId: "forwarded:pdf:b", contentFingerprint: "sha256:same" }),
  ]);
  assert.equal(selected.length, 1);
});

test("deduplicates invoice and receipt representations of the same provider amount and date", () => {
  const selected = selectRecurringBills([
    bill({ sourceMessageId: "invoice", documentClass: "RECURRING_BILL", contentFingerprint: "sha256:invoice" }),
    bill({ sourceMessageId: "receipt", documentClass: "RECEIPT_ONLY", contentFingerprint: "sha256:receipt" }),
  ]);
  assert.equal(selected.length, 1);
  assert.equal(selected[0].sourceMessageId, "invoice");
});

test("content fingerprint is deterministic SHA-256 over PDF bytes", () => {
  const base64 = Buffer.from("synthetic-pdf-bytes", "utf8").toString("base64");
  assert.equal(pdfContentFingerprint(base64), pdfContentFingerprint(base64));
  assert.match(pdfContentFingerprint(base64), /^sha256:[a-f0-9]{64}$/);
});
