"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  IN_APP_PROVIDER_REQUEST,
  VIEW_ONLY,
  commercialActionMode,
  isTrackableCommercialOffer,
} = require("../src/commercialPolicy");

test("in-app provider request requires active attributable positive commission terms", () => {
  assert.equal(isTrackableCommercialOffer({
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
  }), true);
  assert.equal(commercialActionMode({
    commercialAgreementActive: true,
    commissionType: "CPA",
    commissionValue: 180,
  }), IN_APP_PROVIDER_REQUEST);
});

test("non-partner and zero-value arrangements remain view-only", () => {
  for (const terms of [
    { commercialAgreementActive: false, commissionType: "NONE", commissionValue: null },
    { commercialAgreementActive: true, commissionType: "NONE", commissionValue: null },
    { commercialAgreementActive: true, commissionType: "CPA", commissionValue: 0 },
    { commercialAgreementActive: true, commissionType: "CPA", commissionValue: null },
  ]) {
    assert.equal(isTrackableCommercialOffer(terms), false);
    assert.equal(commercialActionMode(terms), VIEW_ONLY);
  }
});

test("engine matched-offer commercial shape uses the same policy", () => {
  const commercial = {
    agreementActive: true,
    commissionType: "REVENUE_SHARE",
    commissionValue: 7.5,
  };
  assert.equal(commercialActionMode(commercial), IN_APP_PROVIDER_REQUEST);
  assert.equal(commercialActionMode({ commercial }), IN_APP_PROVIDER_REQUEST);
});
