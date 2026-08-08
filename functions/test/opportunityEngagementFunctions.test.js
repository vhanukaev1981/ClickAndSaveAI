"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _validateIntentInput: validateIntentInput,
  _intentEventId: intentEventId,
  _buildActionStartedEvent: buildActionStartedEvent,
} = require("../src/opportunityEngagementFunctions");

function opportunity(overrides = {}) {
  return {
    id: "opp-1",
    category: "אינטרנט",
    actionMode: "IN_APP_PROVIDER_REQUEST",
    potentialMonthlySaving: 40,
    potentialAnnualSaving: 480,
    matchedOffer: {
      offerId: "offer-1",
      providerName: "Provider A",
    },
    ...overrides,
  };
}

function commerce(overrides = {}) {
  return {
    uid: "user-1",
    opportunityId: "opp-1",
    offerId: "offer-1",
    agreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    ...overrides,
  };
}

test("intent input requires exact opportunity and offer identifiers", () => {
  const result = validateIntentInput({ opportunityId: "opp-1", expectedOfferId: "offer-1" });
  assert.equal(result.opportunityId, "opp-1");
  assert.equal(result.expectedOfferId, "offer-1");
  assert.throws(() => validateIntentInput({ opportunityId: "opp-1" }), /expectedOfferId/i);
});

test("trackable in-app offer creates privacy-safe ACTION_STARTED event", () => {
  const result = buildActionStartedEvent("user-1", opportunity(), commerce(), "offer-1");
  assert.ok(result);
  assert.equal(result.eventType, "ACTION_STARTED");
  assert.equal(result.providerName, "Provider A");
  assert.equal(result.potentialMonthlySaving, 40);
  assert.equal(Object.hasOwn(result, "commissionValue"), false);
});

test("view-only or non-trackable offer cannot create attributable intent", () => {
  assert.equal(
    buildActionStartedEvent(
      "user-1",
      opportunity({ actionMode: "VIEW_ONLY" }),
      commerce(),
      "offer-1"
    ),
    null
  );
  assert.equal(
    buildActionStartedEvent(
      "user-1",
      opportunity(),
      commerce({ agreementActive: false, commissionType: "NONE", commissionValue: null }),
      "offer-1"
    ),
    null
  );
});

test("stale offer or attribution mismatch cannot create intent", () => {
  assert.equal(buildActionStartedEvent("user-1", opportunity(), commerce(), "offer-old"), null);
  assert.equal(
    buildActionStartedEvent("user-1", opportunity(), commerce({ offerId: "other" }), "offer-1"),
    null
  );
});

test("ACTION_STARTED event id is stable for the same user opportunity and offer", () => {
  assert.equal(
    intentEventId("user-1", "opp-1", "offer-1"),
    intentEventId("user-1", "opp-1", "offer-1")
  );
  assert.notEqual(
    intentEventId("user-1", "opp-1", "offer-1"),
    intentEventId("user-1", "opp-1", "offer-2")
  );
});
