"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { normalizePushData } = require("../src/pushContract");

test("test push always routes to dashboard", () => {
  assert.deepEqual(normalizePushData({ type: "PUSH_TEST" }), {
    type: "PUSH_TEST",
    destination: "DASHBOARD",
  });
});

test("new invoice push carries exact invoice id", () => {
  assert.deepEqual(normalizePushData({
    type: "NEW_INVOICE",
    invoiceId: "invoice-1",
    importedCount: 2,
  }), {
    type: "NEW_INVOICE",
    destination: "INVOICES",
    invoiceId: "invoice-1",
    importedCount: "2",
  });
});

test("savings push carries exact opportunity and offer", () => {
  assert.deepEqual(normalizePushData({
    type: "SAVINGS_OPPORTUNITY",
    opportunityId: "opp-1",
    offerId: "offer-1",
  }), {
    type: "SAVINGS_OPPORTUNITY",
    destination: "SAVINGS_OPPORTUNITY",
    opportunityId: "opp-1",
    offerId: "offer-1",
  });
});

test("unsupported push type is rejected", () => {
  assert.throws(() => normalizePushData({ type: "MAGIC" }), /unsupported/);
});
