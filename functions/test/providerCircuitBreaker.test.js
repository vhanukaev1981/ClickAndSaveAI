"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  CIRCUIT_STATES,
  normalizeCircuitState,
  evaluateCircuit,
  recordCircuitSuccess,
  recordCircuitFailure,
} = require("../src/providerCircuitBreaker");

const NOW = 1_000_000;

function closed(overrides = {}) {
  return {
    state: CIRCUIT_STATES.CLOSED,
    failureThreshold: 3,
    recoveryTimeoutMs: 60_000,
    consecutiveFailures: 0,
    ...overrides,
  };
}

test("closed circuit allows dispatch", () => {
  assert.deepEqual(evaluateCircuit(closed(), NOW), {
    state: CIRCUIT_STATES.CLOSED,
    allowRequest: true,
    delayMs: 0,
    probe: false,
  });
});

test("circuit opens only after configured consecutive failure threshold", () => {
  const first = recordCircuitFailure(closed(), NOW);
  const second = recordCircuitFailure(first, NOW + 1);
  const third = recordCircuitFailure(second, NOW + 2);
  assert.equal(first.state, CIRCUIT_STATES.CLOSED);
  assert.equal(second.state, CIRCUIT_STATES.CLOSED);
  assert.equal(third.state, CIRCUIT_STATES.OPEN);
  assert.equal(third.openedAtMs, NOW + 2);
});

test("open circuit blocks dispatch until recovery timeout", () => {
  const open = {
    ...closed({ state: CIRCUIT_STATES.OPEN, consecutiveFailures: 3 }),
    openedAtMs: NOW,
  };
  const decision = evaluateCircuit(open, NOW + 10_000);
  assert.equal(decision.allowRequest, false);
  assert.equal(decision.delayMs, 50_000);
});

test("open circuit transitions to a single half-open probe after timeout", () => {
  const open = {
    ...closed({ state: CIRCUIT_STATES.OPEN, consecutiveFailures: 3 }),
    openedAtMs: NOW,
  };
  const decision = evaluateCircuit(open, NOW + 60_000);
  assert.equal(decision.state, CIRCUIT_STATES.HALF_OPEN);
  assert.equal(decision.allowRequest, true);
  assert.equal(decision.probe, true);

  const blocked = evaluateCircuit({ ...open, probeInFlight: true }, NOW + 60_000);
  assert.equal(blocked.allowRequest, false);
});

test("successful probe closes and resets circuit", () => {
  const reset = recordCircuitSuccess({
    ...closed(),
    state: CIRCUIT_STATES.HALF_OPEN,
    consecutiveFailures: 3,
    probeInFlight: true,
  });
  assert.equal(reset.state, CIRCUIT_STATES.CLOSED);
  assert.equal(reset.consecutiveFailures, 0);
  assert.equal(reset.openedAtMs, null);
});

test("failed half-open probe reopens immediately", () => {
  const failed = recordCircuitFailure({
    ...closed(),
    state: CIRCUIT_STATES.HALF_OPEN,
    consecutiveFailures: 3,
  }, NOW);
  assert.equal(failed.state, CIRCUIT_STATES.OPEN);
  assert.equal(failed.openedAtMs, NOW);
});

test("invalid open state without openedAt timestamp is rejected", () => {
  assert.throws(() => normalizeCircuitState({ state: CIRCUIT_STATES.OPEN }), /openedAtMs/);
});
