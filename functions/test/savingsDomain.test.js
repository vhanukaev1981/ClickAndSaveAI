"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  createOpportunityId,
  normalizeSavingsOpportunity,
  canDisplaySavings,
} = require("../src/savingsDomain");

function opportunity(overrides = {}) {
  return {
    opportunityId: "opp-1",
    uid: "user-1",
    sourceId: "invoice-1",
    sourceType: "GMAIL_INVOICE",
    providerName: "Provider",
    category: "אינטרנט",
    offerId: "offer-1",
    currentMonthlyCost: 150,
    offeredMonthlyCost: 100,
    status: "VERIFIED",
    evidenceVerified: true,
    evidenceSource: "https://provider.example/plan-a",
    evidenceCheckedAt: "2026-08-08T18:00:00Z",
    confidence: 0.95,
    ...overrides,
  };
}

test("opportunity ids bind user source and exact offer", () => {
  const a = createOpportunityId({ uid: "u1", sourceId: "s1", offerId: "o1" });
  const b = createOpportunityId({ uid: "u1", sourceId: "s1", offerId: "o1" });
  const c = createOpportunityId({ uid: "u1", sourceId: "s1", offerId: "o2" });
  assert.equal(a, b);
  assert.notEqual(a, c);
});

test("verified opportunity calculates monthly and annual savings", () => {
  const normalized = normalizeSavingsOpportunity(opportunity());
  assert.equal(normalized.monthlySavings, 50);
  assert.equal(normalized.annualSavings, 600);
});

test("verified opportunity requires evidence", () => {
  assert.throws(
    () => normalizeSavingsOpportunity(opportunity({ evidenceVerified: false })),
    /verified evidence/
  );
});

test("verified opportunity cannot claim zero or negative savings", () => {
  assert.throws(
    () => normalizeSavingsOpportunity(opportunity({ currentMonthlyCost: 100, offeredMonthlyCost: 120 })),
    /positive savings/
  );
});

test("only verified evidence-backed savings can be displayed", () => {
  assert.equal(canDisplaySavings(opportunity()), true);
  assert.equal(canDisplaySavings(opportunity({ status: "CANDIDATE", evidenceVerified: false })), false);
});
