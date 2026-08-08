"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { READINESS_STATES, evaluateIntegrationReadiness } = require("../src/providerIntegrationReadiness");

function ready(overrides = {}) {
  return {
    adapterImplemented: true,
    credentialsReferenced: true,
    connectionVerified: true,
    dispatchCapability: true,
    evidencePathVerified: true,
    webhookOrReportVerified: true,
    commercialContractActive: true,
    dataMinimizationVerified: true,
    ...overrides,
  };
}

test("missing adapter basics is not ready", () => {
  const result = evaluateIntegrationReadiness(ready({ adapterImplemented: false }));
  assert.equal(result.state, READINESS_STATES.NOT_READY);
  assert.ok(result.missing.includes("adapterImplemented"));
});

test("sandbox readiness does not pretend production readiness", () => {
  const result = evaluateIntegrationReadiness(ready({ connectionVerified: false, webhookOrReportVerified: false }));
  assert.equal(result.state, READINESS_STATES.READY_FOR_SANDBOX);
  assert.ok(result.missing.includes("connectionVerified"));
});

test("all evidence and commercial checks are required for production", () => {
  const result = evaluateIntegrationReadiness(ready());
  assert.equal(result.state, READINESS_STATES.READY_FOR_PRODUCTION);
  assert.deepEqual(result.missing, []);
});
