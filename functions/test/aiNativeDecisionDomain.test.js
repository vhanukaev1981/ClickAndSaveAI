"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { ACTIONS, selectNextAction, shouldNotify } = require("../src/aiNativeDecisionDomain");

test("verified savings outrank every other action", () => {
  const decision = selectNextAction({
    connectedSources: 1,
    unverifiedInvoices: 3,
    urgentPriceAlerts: 2,
    verifiedOpportunityCount: 1,
    verifiedSavingsMonthly: 180,
  });
  assert.equal(decision.action, ACTIONS.SHOW_VERIFIED_SAVINGS);
  assert.equal(decision.priority, 100);
  assert.match(decision.userMessage, /180/);
  assert.equal(shouldNotify(decision), true);
});

test("urgent price change outranks invoice review", () => {
  const decision = selectNextAction({
    connectedSources: 1,
    unverifiedInvoices: 3,
    urgentPriceAlerts: 1,
  });
  assert.equal(decision.action, ACTIONS.ALERT_PRICE_CHANGE);
  assert.equal(shouldNotify(decision), true);
});

test("unverified invoice becomes a quiet in-app review action", () => {
  const decision = selectNextAction({ connectedSources: 1, unverifiedInvoices: 1 });
  assert.equal(decision.action, ACTIONS.REVIEW_INVOICE);
  assert.equal(shouldNotify(decision), false);
});

test("no connected source requests onboarding", () => {
  const decision = selectNextAction({ connectedSources: 0 });
  assert.equal(decision.action, ACTIONS.CONNECT_SOURCE);
});

test("healthy account generates no noisy action", () => {
  const decision = selectNextAction({ connectedSources: 1 });
  assert.equal(decision.action, ACTIONS.NONE);
  assert.equal(shouldNotify(decision), false);
});
