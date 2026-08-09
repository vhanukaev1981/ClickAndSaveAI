"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _buildDispatchQueueRecord: buildDispatchQueueRecord,
} = require("../src/providerDispatchFunctions");

function lead(overrides = {}) {
  return {
    uid: "user-1",
    contactName: "Test User",
    phone: "0501234567",
    contactEmail: "test@example.com",
    requestedProvider: "Provider A",
    category: "אינטרנט",
    offerId: "offer-1",
    opportunityId: "opp-1",
    consentVersion: "opportunity-action-v1",
    source: "AI_PROACTIVE_OPPORTUNITY",
    status: "NEW",
    ...overrides,
  };
}

function commerce(overrides = {}) {
  return {
    uid: "user-1",
    opportunityId: "opp-1",
    offerId: "offer-1",
    leadId: "lead-1",
    agreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    ...overrides,
  };
}

test("trackable AI lead becomes a minimal provider dispatch queue record", () => {
  const result = buildDispatchQueueRecord("lead-1", lead(), commerce());
  assert.ok(result);
  assert.equal(result.status, "PENDING");
  assert.equal(result.commerceMatchId, "user-1_opp-1");
  assert.equal(result.payload.offerId, "offer-1");
  assert.equal(result.payload.contactEmail, "test@example.com");
  assert.equal(Object.hasOwn(result.payload, "currentMonthlyCost"), false);
  assert.equal(Object.hasOwn(result.payload, "commissionValue"), false);
});

test("non-partner or zero-commission lead is never queued for dispatch", () => {
  assert.equal(buildDispatchQueueRecord(
    "lead-1",
    lead(),
    commerce({ agreementActive: false, commissionType: "NONE", commissionValue: null })
  ), null);
  assert.equal(buildDispatchQueueRecord(
    "lead-1",
    lead(),
    commerce({ commissionValue: 0 })
  ), null);
});

test("attribution mismatch prevents dispatch", () => {
  assert.equal(buildDispatchQueueRecord(
    "lead-1",
    lead(),
    commerce({ offerId: "different-offer" })
  ), null);
  assert.equal(buildDispatchQueueRecord(
    "lead-1",
    lead(),
    commerce({ leadId: "different-lead" })
  ), null);
});

test("legacy or manually-created provider lead does not enter AI dispatch queue", () => {
  assert.equal(buildDispatchQueueRecord(
    "lead-1",
    lead({ source: "MANUAL_PROVIDER_LEAD" }),
    commerce()
  ), null);
});
