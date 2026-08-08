"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { createAdapterRegistry, CAPABILITIES } = require("../src/providerAdapterRegistry");
const { buildDispatchEnvelope, beginAttempt, applyAcknowledgement, applyFailure } = require("../src/providerDispatchEnvelope");
const { PLAN_ACTIONS, planDispatch } = require("../src/providerDispatchPlanner");

function registry(capabilities = [CAPABILITIES.DISPATCH]) {
  return createAdapterRegistry([{
    adapterKey: "provider-a-v1",
    providerId: "provider-a",
    implemented: true,
    capabilities,
  }]);
}

function providerConfig(overrides = {}) {
  return {
    providerId: "provider-a",
    adapterKey: "provider-a-v1",
    enabled: true,
    adapterImplemented: true,
    credentialsConfigured: true,
    connectionVerified: true,
    ...overrides,
  };
}

function contract(overrides = {}) {
  return {
    contractId: "contract-1",
    providerId: "provider-a",
    commercialModel: "CPA",
    deliveryMode: "API",
    conversionEvidenceMode: "POSTBACK",
    adapterKey: "provider-a-v1",
    credentialSecretName: "PROVIDER_A_API_CREDENTIALS",
    attributionField: "sub_id",
    activeFrom: "2026-08-01T00:00:00Z",
    activeUntil: "2026-09-01T00:00:00Z",
    enabled: true,
    ...overrides,
  };
}

function envelope() {
  return buildDispatchEnvelope({
    leadId: "lead-1",
    providerId: "provider-a",
    offerId: "offer-1",
    contractId: "contract-1",
    adapterKey: "provider-a-v1",
    payload: { leadId: "lead-1", offerId: "offer-1", phone: "0501234567" },
  });
}

function input(overrides = {}) {
  return {
    envelope: envelope(),
    providerConfig: providerConfig(),
    contract: contract(),
    adapterRegistry: registry(),
    nowMs: Date.parse("2026-08-15T12:00:00Z"),
    ...overrides,
  };
}

test("ready dispatch is planned only after provider, contract and adapter capability all verify", () => {
  const plan = planDispatch(input());
  assert.equal(plan.action, PLAN_ACTIONS.DISPATCH);
});

test("planner rejects provider that is not actually connected", () => {
  assert.throws(() => planDispatch(input({
    providerConfig: providerConfig({ connectionVerified: false }),
  })), /not actually connected/);
});

test("planner rejects adapter without DISPATCH capability", () => {
  assert.throws(() => planDispatch(input({
    adapterRegistry: registry([CAPABILITIES.STATUS_LOOKUP]),
  })), /does not support capability/);
});

test("inactive commercial contract never dispatches", () => {
  const plan = planDispatch(input({ nowMs: Date.parse("2026-10-01T00:00:00Z") }));
  assert.equal(plan.action, PLAN_ACTIONS.NOOP);
  assert.match(plan.reason, /inactive/);
});

test("terminal acknowledged dispatch is a no-op", () => {
  const acknowledged = applyAcknowledgement(beginAttempt(envelope()), "crm-123");
  const plan = planDispatch(input({ envelope: acknowledged }));
  assert.equal(plan.action, PLAN_ACTIONS.NOOP);
});

test("in-flight dispatch is never duplicated", () => {
  const inFlight = beginAttempt(envelope());
  const plan = planDispatch(input({ envelope: inFlight }));
  assert.equal(plan.action, PLAN_ACTIONS.WAIT);
});

test("retry wait honors bounded backoff", () => {
  const failed = applyFailure(beginAttempt(envelope()), { retryable: true, errorCode: "HTTP_503" });
  const plan = planDispatch(input({ envelope: failed, retryAfterMs: 120000 }));
  assert.equal(plan.action, PLAN_ACTIONS.WAIT);
  assert.equal(plan.delayMs, 120000);
});

test("retry limit routes item to dead letter instead of endless retry", () => {
  const failed = { ...applyFailure(beginAttempt(envelope()), { retryable: true, errorCode: "HTTP_503" }), attempt: 5 };
  const plan = planDispatch(input({ envelope: failed, maxAttempts: 5 }));
  assert.equal(plan.action, PLAN_ACTIONS.DEAD_LETTER);
});

test("provider, contract and adapter identity must agree", () => {
  assert.throws(() => planDispatch(input({ contract: contract({ providerId: "provider-b" }) })), /does not match provider contract/);
});
