"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  normalizeFinancialEvent,
  monthlyEquivalent,
  buildFinancialSnapshot,
} = require("../src/financialDomain");

function event(overrides = {}) {
  return {
    sourceType: "GMAIL_INVOICE",
    sourceId: "gmail-1",
    providerName: "Provider",
    category: "אינטרנט",
    amount: 120,
    currency: "ILS",
    occurredAt: "2026-08-08T12:00:00Z",
    cadence: "MONTHLY",
    verificationStatus: "SOURCE_VERIFIED",
    ...overrides,
  };
}

test("normalizes a financial event without leaking connector-specific structure", () => {
  const normalized = normalizeFinancialEvent(event());
  assert.equal(normalized.sourceType, "GMAIL_INVOICE");
  assert.equal(normalized.amount, 120);
  assert.equal(normalized.currency, "ILS");
  assert.equal(normalized.category, "אינטרנט");
});

test("rejects unsupported source types", () => {
  assert.throws(() => normalizeFinancialEvent(event({ sourceType: "SCRAPER_MAGIC" })), /unsupported/);
});

test("calculates monthly equivalents deterministically", () => {
  assert.equal(monthlyEquivalent(event({ amount: 1200, cadence: "YEARLY" })), 100);
  assert.equal(monthlyEquivalent(event({ amount: 300, cadence: "QUARTERLY" })), 100);
  assert.equal(monthlyEquivalent(event({ amount: 25, cadence: "WEEKLY" })), 108.33);
  assert.equal(monthlyEquivalent(event({ amount: 99, cadence: "ONE_TIME" })), 0);
});

test("builds an AI-native dashboard-ready recurring spend snapshot", () => {
  const snapshot = buildFinancialSnapshot([
    event({ sourceId: "1", category: "אינטרנט", amount: 120 }),
    event({ sourceId: "2", category: "סלולר", amount: 80 }),
    event({ sourceId: "3", category: "ביטוח", amount: 1200, cadence: "YEARLY" }),
    event({ sourceId: "4", category: "קניות", amount: 500, cadence: "ONE_TIME" }),
  ]);
  assert.equal(snapshot.monthlyRecurringSpend, 300);
  assert.equal(snapshot.annualRecurringSpend, 3600);
  assert.equal(snapshot.recurringCount, 3);
  assert.equal(snapshot.eventCount, 4);
  assert.deepEqual(snapshot.byCategory, { אינטרנט: 120, סלולר: 80, ביטוח: 100 });
});
