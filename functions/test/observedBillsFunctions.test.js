"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const entry = require("../src/entry");
const observedBills = require("../src/observedBillsFunctions");

function bill(index, overrides = {}) {
  return {
    sourceMessageId: `message-${index}`,
    providerName: `Provider ${index}`,
    category: "internet",
    monthlyCost: 100 + index,
    receivedDate: `2026-08-${String((index % 28) + 1).padStart(2, "0")}`,
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
    rawBody: "must never escape",
    subject: "must never escape",
    accountNumber: "secret",
    ...overrides,
  };
}

test("observed bills callable shares Android backend region and public export", () => {
  assert.equal(observedBills._REGION, "europe-west1");
  assert.equal(entry.getObservedBills, observedBills.getObservedBills);
});

test("observed bills snapshot exposes normalized minimum fields only", () => {
  const payload = observedBills._buildObservedBillsPayload([bill(1)], new Date("2026-08-09T07:00:00.000Z"));

  assert.equal(payload.bills.length, 1);
  assert.deepEqual(Object.keys(payload.bills[0]).sort(), [
    "category",
    "monthlyCost",
    "providerName",
    "receivedDate",
    "sourceMessageId",
    "verificationStatus",
  ]);
  assert.equal(payload.bills[0].sourceMessageId, "message-1");
  assert.equal(payload.bills[0].rawBody, undefined);
  assert.equal(payload.bills[0].subject, undefined);
  assert.equal(payload.bills[0].accountNumber, undefined);
  assert.equal(payload.generatedAt, "2026-08-09T07:00:00.000Z");
});

test("invalid observed bills are dropped instead of fabricated", () => {
  const payload = observedBills._buildObservedBillsPayload([
    bill(1),
    bill(2, { sourceMessageId: "" }),
    bill(3, { providerName: "" }),
    bill(4, { monthlyCost: 0 }),
    bill(5, { monthlyCost: Number.NaN }),
  ]);

  assert.equal(payload.bills.length, 1);
  assert.equal(payload.bills[0].sourceMessageId, "message-1");
});

test("customer bill list is bounded to 100 while authoritative source set reaches 500", () => {
  const documents = Array.from({ length: 500 }, (_, index) => bill(index));
  const payload = observedBills._buildObservedBillsPayload(documents);

  assert.equal(payload.bills.length, 100);
  assert.equal(payload.sourceMessageIds.length, 500);
  assert.equal(payload.sourceCount, 500);
  assert.equal(payload.sourceSetComplete, true);
});

test("501 documents mark the source set incomplete so clients cannot delete from a partial snapshot", () => {
  const documents = Array.from({ length: 501 }, (_, index) => bill(index));
  const payload = observedBills._buildObservedBillsPayload(documents);

  assert.equal(payload.bills.length, 100);
  assert.equal(payload.sourceMessageIds.length, 500);
  assert.equal(payload.sourceSetComplete, false);
});

test("source identifiers are de-duplicated", () => {
  const payload = observedBills._buildObservedBillsPayload([
    bill(1),
    bill(2, { sourceMessageId: "message-1", providerName: "Replacement representation" }),
  ]);

  assert.deepEqual(payload.sourceMessageIds, ["message-1"]);
});
