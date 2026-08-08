"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { planSandboxProbe } = require("../src/providerSandboxProbePlanner");

function probe(overrides = {}) {
  return {
    providerId: "provider-a",
    adapterKey: "adapter-a",
    adapterImplemented: true,
    credentialsReferenced: true,
    statusLookupCapability: true,
    productionTraffic: false,
    ...overrides,
  };
}

test("allows non-destructive sandbox probe", () => {
  const result = planSandboxProbe(probe());
  assert.equal(result.allowed, true);
  assert.equal(result.operation, "STATUS_LOOKUP");
  assert.equal(result.syntheticReference, true);
});

test("missing capability blocks probe", () => {
  const result = planSandboxProbe(probe({ statusLookupCapability: false }));
  assert.equal(result.allowed, false);
});

test("production traffic is never used for sandbox probe", () => {
  const result = planSandboxProbe(probe({ productionTraffic: true }));
  assert.equal(result.allowed, false);
});
