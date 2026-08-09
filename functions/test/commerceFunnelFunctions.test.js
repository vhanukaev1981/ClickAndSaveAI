"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _commerceMatchEvent: commerceMatchEvent,
  _leadStatusEvent: leadStatusEvent,
  _dispatchQueuedEvent: dispatchQueuedEvent,
  _eventId: eventId,
} = require("../src/commerceFunnelFunctions");

test("verified match creates a privacy-safe OFFER_MATCHED event", () => {
  const event = commerceMatchEvent("user-1_opp-1", {
    uid: "user-1",
    opportunityId: "opp-1",
    offerId: "offer-1",
    providerName: "Provider A",
    category: "אינטרנט",
    matchStatus: "VERIFIED_MATCH",
    actionMode: "IN_APP_PROVIDER_REQUEST",
    potentialMonthlySaving: 40,
    potentialAnnualSaving: 480,
    commissionValue: 180,
  });
  assert.ok(event);
  assert.equal(event.data.eventType, "OFFER_MATCHED");
  assert.equal(event.data.actionMode, "IN_APP_PROVIDER_REQUEST");
  assert.equal(Object.hasOwn(event.data, "commissionValue"), false);
});

test("AI provider lead lifecycle creates deterministic funnel events without contact data", () => {
  const data = {
    uid: "user-1",
    opportunityId: "opp-1",
    offerId: "offer-1",
    requestedProvider: "Provider A",
    category: "אינטרנט",
    source: "AI_PROACTIVE_OPPORTUNITY",
    status: "CONTACTED",
    phone: "0501234567",
  };
  const first = leadStatusEvent("lead-1", data);
  const second = leadStatusEvent("lead-1", data);
  assert.ok(first);
  assert.equal(first.id, second.id);
  assert.equal(first.data.eventType, "LEAD_CONTACTED");
  assert.equal(Object.hasOwn(first.data, "phone"), false);
});

test("commission confirmation records actual internal revenue outcome", () => {
  const event = leadStatusEvent("lead-1", {
    uid: "user-1",
    opportunityId: "opp-1",
    offerId: "offer-1",
    requestedProvider: "Provider A",
    category: "אינטרנט",
    source: "AI_PROACTIVE_OPPORTUNITY",
    status: "COMMISSION_CONFIRMED",
    actualCommissionAmount: 180,
    commissionCurrency: "ILS",
  });
  assert.ok(event);
  assert.equal(event.data.eventType, "LEAD_COMMISSION_CONFIRMED");
  assert.equal(event.data.actualCommissionAmount, 180);
  assert.equal(event.data.commissionCurrency, "ILS");
});

test("provider dispatch queue creates DISPATCH_QUEUED event", () => {
  const event = dispatchQueuedEvent("lead-1", {
    uid: "user-1",
    opportunityId: "opp-1",
    offerId: "offer-1",
    providerName: "Provider A",
    category: "אינטרנט",
    commerceMatchId: "user-1_opp-1",
    status: "PENDING",
  });
  assert.ok(event);
  assert.equal(event.data.eventType, "DISPATCH_QUEUED");
});

test("event IDs are deterministic but distinct by stage", () => {
  assert.equal(eventId(["A", "lead-1"]), eventId(["A", "lead-1"]));
  assert.notEqual(eventId(["A", "lead-1"]), eventId(["B", "lead-1"]));
});
