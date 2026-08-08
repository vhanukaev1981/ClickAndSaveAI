"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { OFFBOARD_ACTIONS, planProviderOffboarding } = require("../src/providerOffboardingPolicy");

function state(overrides = {}) {
  return {
    providerId: "provider-a",
    newDispatchDisabled: false,
    inFlightCount: 0,
    credentialsRotated: false,
    contractArchived: false,
    ...overrides,
  };
}

test("offboarding starts by disabling new dispatch", () => {
  assert.equal(planProviderOffboarding(state()).action, OFFBOARD_ACTIONS.DISABLE_NEW_DISPATCH);
});

test("in-flight traffic must drain before credential rotation", () => {
  const result = planProviderOffboarding(state({ newDispatchDisabled: true, inFlightCount: 2 }));
  assert.equal(result.action, OFFBOARD_ACTIONS.DRAIN_IN_FLIGHT);
});

test("credentials rotate before contract archival", () => {
  assert.equal(planProviderOffboarding(state({ newDispatchDisabled: true })).action, OFFBOARD_ACTIONS.ROTATE_CREDENTIALS);
  assert.equal(planProviderOffboarding(state({ newDispatchDisabled: true, credentialsRotated: true })).action, OFFBOARD_ACTIONS.ARCHIVE_CONTRACT);
});

test("completed offboarding is safe to archive", () => {
  const result = planProviderOffboarding(state({ newDispatchDisabled: true, credentialsRotated: true, contractArchived: true }));
  assert.equal(result.action, OFFBOARD_ACTIONS.COMPLETE);
  assert.equal(result.safeToArchive, true);
});
