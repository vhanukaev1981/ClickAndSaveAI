"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { evaluateProviderSla, SLA_STATES } = require("../src/providerSlaPolicy");

const policy = { acknowledgementMs: 1000, statusFreshnessMs: 10000 };

test("unconfigured SLA remains explicit", () => {
  assert.equal(evaluateProviderSla({}).state, SLA_STATES.UNCONFIGURED);
});

test("healthy SLA remains healthy", () => {
  assert.equal(evaluateProviderSla({ policy, ackLatencyMs: 400, statusAgeMs: 2000 }).state, SLA_STATES.HEALTHY);
});

test("near-threshold SLA yields warning", () => {
  assert.equal(evaluateProviderSla({ policy, ackLatencyMs: 850, statusAgeMs: 2000 }).state, SLA_STATES.WARNING);
});

test("threshold breach is surfaced", () => {
  assert.equal(evaluateProviderSla({ policy, ackLatencyMs: 1200, statusAgeMs: 2000 }).state, SLA_STATES.BREACHED);
});
