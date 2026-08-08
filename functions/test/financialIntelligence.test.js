"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildFinancialContext,
  detectFinancialSignals,
} = require("../src/financialIntelligence");

function invoice({ id, provider = "Partner", category = "אינטרנט", cost, date }) {
  return {
    sourceMessageId: id,
    providerName: provider,
    category,
    monthlyCost: cost,
    receivedDate: date,
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  };
}

test("buildFinancialContext reports observed Gmail recurring spend without claiming full household coverage", () => {
  const context = buildFinancialContext([
    invoice({ id: "a", cost: 99, date: "2026-06-01T08:00:00Z" }),
    invoice({ id: "b", cost: 99, date: "2026-07-01T08:00:00Z" }),
    invoice({ id: "c", provider: "Netflix", category: "טלוויזיה", cost: 69.9, date: "2026-07-02T08:00:00Z" }),
    invoice({ id: "d", provider: "Netflix", category: "טלוויזיה", cost: 69.9, date: "2026-08-02T08:00:00Z" }),
  ]);

  assert.equal(context.isCompleteHouseholdSpend, false);
  assert.deepEqual(context.sourceCoverage, ["GMAIL_READONLY"]);
  assert.equal(context.recurringServiceCount, 2);
  assert.equal(context.observedRecurringMonthlySpend, 168.9);
});

test("price increase creates a comparison opportunity but no invented savings", () => {
  const { insights, opportunities } = detectFinancialSignals([
    invoice({ id: "old", cost: 99, date: "2026-07-01T08:00:00Z" }),
    invoice({ id: "new", cost: 129, date: "2026-08-01T08:00:00Z" }),
  ]);

  const increase = insights.find((item) => item.type === "PRICE_INCREASE_DETECTED");
  assert.ok(increase);
  assert.equal(increase.monthlyIncrease, 30);
  assert.equal(increase.percentIncrease, 30.3);

  assert.equal(opportunities.length, 1);
  assert.equal(opportunities[0].type, "COMPARE_AFTER_PRICE_INCREASE");
  assert.equal(opportunities[0].potentialMonthlySaving, null);
  assert.equal(opportunities[0].potentialAnnualSaving, null);
  assert.equal(opportunities[0].truthfulness.savingsClaimAvailable, false);
  assert.equal(opportunities[0].commercial.userIntent, "SYSTEM_DETECTED_SAVINGS_NEED");
});

test("small price noise does not create a proactive opportunity", () => {
  const { opportunities } = detectFinancialSignals([
    invoice({ id: "old", cost: 100, date: "2026-07-01T08:00:00Z" }),
    invoice({ id: "new", cost: 103, date: "2026-08-01T08:00:00Z" }),
  ]);

  assert.equal(opportunities.length, 0);
});

test("known household-service categories are marked as potentially monetizable without claiming a partner", () => {
  const { opportunities } = detectFinancialSignals([
    invoice({ id: "old", category: "סלולר", cost: 120, date: "2026-07-01T08:00:00Z" }),
    invoice({ id: "new", category: "סלולר", cost: 150, date: "2026-08-01T08:00:00Z" }),
  ]);

  assert.equal(opportunities[0].commercial.monetizableCategory, true);
  assert.equal(opportunities[0].commercial.partnerMatchStatus, "NOT_CHECKED");
  assert.equal(opportunities[0].commercial.commissionStatus, "UNKNOWN");
});
