"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  pdfContentFingerprint,
  selectRecurringBills,
} = require("../src/gmailRecurringBillPolicy");

function pdfFingerprint(char) {
  return `sha256:${String(char).repeat(64)}`;
}

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
    contentFingerprint: pdfFingerprint("a"),
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

test("preserves trusted PDF-backed receipt-only historical recurrence", () => {
  const selected = selectRecurringBills([
    bill({
      sourceMessageId: "usage-1",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-08",
      contentFingerprint: pdfFingerprint("b"),
    }),
    bill({
      sourceMessageId: "usage-2",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-28",
      contentFingerprint: pdfFingerprint("c"),
    }),
  ]);

  assert.equal(selected.length, 2);
  assert.ok(selected.every((item) => item.recurrenceEvidence === "REPEATED_PROVIDER_HISTORY"));
});

test("does not promote two body-only RECEIPT_ONLY items across distinct dates", () => {
  const selected = selectRecurringBills([
    bill({
      sourceMessageId: "body-receipt-1",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-08",
      contentFingerprint: "",
    }),
    bill({
      sourceMessageId: "body-receipt-2",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-28",
      contentFingerprint: "",
    }),
  ]);
  assert.deepEqual(selected, []);
});

test("mixed body receipt plus trusted PDF receipts never promotes the body receipt", () => {
  const selected = selectRecurringBills([
    bill({
      sourceMessageId: "body-receipt",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-01",
      contentFingerprint: "",
    }),
    bill({
      sourceMessageId: "pdf-receipt-1",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-08",
      contentFingerprint: pdfFingerprint("d"),
    }),
    bill({
      sourceMessageId: "pdf-receipt-2",
      providerName: "Example Charging Network",
      category: "תחבורה",
      documentClass: "RECEIPT_ONLY",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-28",
      contentFingerprint: pdfFingerprint("e"),
    }),
  ]);
  assert.deepEqual(new Set(selected.map((item) => item.sourceMessageId)), new Set(["pdf-receipt-1", "pdf-receipt-2"]));
});

test("repeated UNKNOWN items remain rejected", () => {
  const selected = selectRecurringBills([
    bill({
      sourceMessageId: "unknown-1",
      documentClass: "UNKNOWN",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-05-01",
      contentFingerprint: "",
    }),
    bill({
      sourceMessageId: "unknown-2",
      documentClass: "UNKNOWN",
      recurrenceEvidence: "NONE",
      recurrenceType: "UNKNOWN",
      receivedDate: "2026-06-01",
      contentFingerprint: "",
    }),
  ]);
  assert.deepEqual(selected, []);
});

test("does not promote repeated one-off purchases from the same merchant", () => {
  const selected = selectRecurringBills([
    bill({ sourceMessageId: "gift-1", providerName: "Example Gift Store", documentClass: "ONE_OFF", receivedDate: "2026-05-01" }),
    bill({ sourceMessageId: "gift-2", providerName: "Example Gift Store", documentClass: "ONE_OFF", receivedDate: "2026-06-01" }),
  ]);
  assert.deepEqual(selected, []);
});

test("deduplicates identical PDFs across forwarded Gmail messages by exact content SHA-256", () => {
  const sharedFingerprint = pdfFingerprint("f");
  const selected = selectRecurringBills([
    bill({ sourceMessageId: "original:pdf:a", receivedDate: "2026-07-01", monthlyCost: 120, contentFingerprint: sharedFingerprint }),
    bill({ sourceMessageId: "forwarded:pdf:b", receivedDate: "2026-07-02", monthlyCost: 121, contentFingerprint: sharedFingerprint }),
  ]);
  assert.equal(selected.length, 1);
});

test("transaction dedupe still prefers recurring invoice over receipt for same provider amount and date", () => {
  const selected = selectRecurringBills([
    bill({ sourceMessageId: "invoice", documentClass: "RECURRING_BILL", contentFingerprint: pdfFingerprint("1") }),
    bill({ sourceMessageId: "receipt", documentClass: "RECEIPT_ONLY", contentFingerprint: pdfFingerprint("2") }),
  ]);
  assert.equal(selected.length, 1);
  assert.equal(selected[0].sourceMessageId, "invoice");
});

test("content fingerprint is deterministic SHA-256 over PDF bytes", () => {
  const base64 = Buffer.from("synthetic-pdf-bytes", "utf8").toString("base64");
  assert.equal(pdfContentFingerprint(base64), pdfContentFingerprint(base64));
  assert.match(pdfContentFingerprint(base64), /^sha256:[a-f0-9]{64}$/);
});
