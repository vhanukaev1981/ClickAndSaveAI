"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { calculateAdapterHealthScore } = require("../src/providerAdapterHealthScore");

test("perfect provider health scores 100", () => {
  assert.deepEqual(calculateAdapterHealthScore({ successRate: 1, ackWithinSlaRate: 1, freshnessRate: 1, deadLetterRate: 0, authFailureRate: 0 }), { score: 100, state: "HEALTHY" });
});

test("elevated failures degrade provider health", () => {
  const result = calculateAdapterHealthScore({ successRate: 0.6, ackWithinSlaRate: 0.7, freshnessRate: 0.8, deadLetterRate: 0.2, authFailureRate: 0.1 });
  assert.ok(result.score < 90);
  assert.notEqual(result.state, "HEALTHY");
});

test("rates are clamped instead of creating impossible scores", () => {
  const result = calculateAdapterHealthScore({ successRate: 2, ackWithinSlaRate: 2, freshnessRate: 2, deadLetterRate: -1, authFailureRate: -1 });
  assert.equal(result.score, 100);
});
