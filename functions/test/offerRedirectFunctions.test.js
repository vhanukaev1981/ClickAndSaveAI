"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const {
  _parseDestinationUrl: parseDestinationUrl,
  _offerVersionHash: offerVersionHash,
  _clickIdentity: clickIdentity,
  _buildRedirectUrl: buildRedirectUrl,
} = require("../src/offerRedirectFunctions");

test("redirect target is HTTPS-only and fragment-free", () => {
  assert.equal(parseDestinationUrl("http://provider.example/join"), "");
  assert.equal(
    parseDestinationUrl("https://provider.example/join#private"),
    "https://provider.example/join"
  );
});

test("click identity is deterministic for the exact offer version", () => {
  const offer = {
    offerId: "offer-1",
    verifiedAt: "2026-09-18T08:00:00.000Z",
    validUntil: "2026-10-18T08:00:00.000Z",
    monthlyPrice: 89,
    requiredRecurringFees: 0,
    oneTimeFees: 0,
  };
  const version = offerVersionHash(offer, "https://provider.example/join");
  assert.equal(
    clickIdentity("user-1", "opp-1", "offer-1", version),
    clickIdentity("user-1", "opp-1", "offer-1", version)
  );
  assert.notEqual(
    clickIdentity("user-1", "opp-1", "offer-1", version),
    clickIdentity("user-1", "opp-2", "offer-1", version)
  );
});

test("partner attribution adds only the configured sub-id parameter", () => {
  const url = buildRedirectUrl(
    "https://provider.example/join?campaign=savings",
    "click-123",
    "click_id"
  );
  const parsed = new URL(url);
  assert.equal(parsed.searchParams.get("campaign"), "savings");
  assert.equal(parsed.searchParams.get("click_id"), "click-123");
});


test("offer version identity changes when attribution configuration changes", () => {
  const offer = {
    offerId: "offer-1",
    verifiedAt: "2026-09-18T08:00:00.000Z",
    validUntil: "2026-10-18T08:00:00.000Z",
    monthlyPrice: 89,
    requiredRecurringFees: 0,
    oneTimeFees: 0,
  };
  const base = offerVersionHash(offer, "https://provider.example/join", {
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    partnerSubIdParam: "click_id",
  });
  const changedParam = offerVersionHash(offer, "https://provider.example/join", {
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
    partnerSubIdParam: "subid",
  });
  const inactive = offerVersionHash(offer, "https://provider.example/join", {
    commercialAgreementActive: false,
    commissionType: "NONE",
    commissionValue: null,
    partnerSubIdParam: "",
  });
  assert.notEqual(base, changedParam);
  assert.notEqual(base, inactive);
});
