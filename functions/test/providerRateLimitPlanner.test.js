"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  RATE_LIMIT_ACTIONS,
  normalizeRateLimitPolicy,
  planRateLimit,
} = require("../src/providerRateLimitPlanner");

const NOW = 1_000_000;
const policy = { maxRequests: 3, windowMs: 60_000 };

test("missing provider rate limit remains explicitly unconfigured", () => {
  const decision = planRateLimit({ policy: null, nowMs: NOW });
  assert.equal(decision.action, RATE_LIMIT_ACTIONS.UNCONFIGURED);
});

test("configured window allows request and returns deterministic next usage", () => {
  const decision = planRateLimit({
    policy,
    usage: { usedRequests: 1, windowStartedAtMs: NOW },
    nowMs: NOW + 1_000,
  });
  assert.equal(decision.action, RATE_LIMIT_ACTIONS.ALLOW);
  assert.equal(decision.remainingRequests, 1);
  assert.deepEqual(decision.nextUsage, {
    usedRequests: 2,
    windowStartedAtMs: NOW,
  });
});

test("exhausted provider window waits until reset", () => {
  const decision = planRateLimit({
    policy,
    usage: { usedRequests: 3, windowStartedAtMs: NOW },
    nowMs: NOW + 10_000,
  });
  assert.equal(decision.action, RATE_LIMIT_ACTIONS.WAIT);
  assert.equal(decision.delayMs, 50_000);
  assert.equal(decision.remainingRequests, 0);
});

test("expired window resets usage before planning", () => {
  const decision = planRateLimit({
    policy,
    usage: { usedRequests: 99, windowStartedAtMs: NOW },
    nowMs: NOW + 60_000,
  });
  assert.equal(decision.action, RATE_LIMIT_ACTIONS.ALLOW);
  assert.equal(decision.remainingRequests, 2);
  assert.equal(decision.nextUsage.usedRequests, 1);
  assert.equal(decision.nextUsage.windowStartedAtMs, NOW + 60_000);
});

test("framework refuses to invent invalid vendor limits", () => {
  assert.throws(() => normalizeRateLimitPolicy({ maxRequests: 0, windowMs: 60_000 }), /maxRequests/);
  assert.throws(() => normalizeRateLimitPolicy({ maxRequests: 10, windowMs: 0 }), /windowMs/);
});

test("usage counters must be explicit and valid", () => {
  assert.throws(() => planRateLimit({
    policy,
    usage: { usedRequests: -1, windowStartedAtMs: NOW },
    nowMs: NOW,
  }), /usedRequests/);
});
