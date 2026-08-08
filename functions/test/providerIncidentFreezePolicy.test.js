"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { evaluateProviderFreeze } = require("../src/providerIncidentFreezePolicy");

test("unfrozen provider remains dispatchable", () => {
  const result = evaluateProviderFreeze({ providerId: "provider-a", frozen: false });
  assert.equal(result.blocksDispatch, false);
});

test("incident freeze blocks dispatch", () => {
  const result = evaluateProviderFreeze({
    providerId: "provider-a",
    frozen: true,
    reason: "PROVIDER_OUTAGE",
    frozenAt: "2026-08-08T20:00:00Z",
    operatorReference: "incident-1",
  });
  assert.equal(result.blocksDispatch, true);
  assert.equal(result.reason, "PROVIDER_OUTAGE");
});

test("unsupported freeze reason is rejected", () => {
  assert.throws(() => evaluateProviderFreeze({ providerId: "provider-a", frozen: true, reason: "UNKNOWN", frozenAt: "2026-08-08T20:00:00Z" }), /unsupported provider freeze reason/);
});
