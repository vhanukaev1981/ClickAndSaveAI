"use strict";

const RATE_LIMIT_ACTIONS = Object.freeze({
  ALLOW: "ALLOW",
  WAIT: "WAIT",
  UNCONFIGURED: "UNCONFIGURED",
});

const MAX_WINDOW_MS = 24 * 60 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 1_000_000;

function positiveInteger(value, field, max) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0 || number > max) {
    throw new TypeError(`${field} must be an integer between 1 and ${max}`);
  }
  return number;
}

function normalizeRateLimitPolicy(input) {
  if (input == null) return { configured: false };
  if (typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("rate limit policy must be an object");
  }
  if (input.configured === false) return { configured: false };

  return {
    configured: true,
    maxRequests: positiveInteger(input.maxRequests, "maxRequests", MAX_REQUESTS_PER_WINDOW),
    windowMs: positiveInteger(input.windowMs, "windowMs", MAX_WINDOW_MS),
  };
}

function normalizeUsage(input, policy, nowMs) {
  if (input == null) {
    return { usedRequests: 0, windowStartedAtMs: nowMs };
  }
  if (typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("rate limit usage must be an object");
  }

  const usedRequests = Number(input.usedRequests);
  if (!Number.isInteger(usedRequests) || usedRequests < 0) {
    throw new TypeError("usedRequests must be a non-negative integer");
  }
  const windowStartedAtMs = Number(input.windowStartedAtMs);
  if (!Number.isFinite(windowStartedAtMs)) {
    throw new TypeError("windowStartedAtMs must be finite");
  }

  if (nowMs >= windowStartedAtMs + policy.windowMs) {
    return { usedRequests: 0, windowStartedAtMs: nowMs };
  }

  return { usedRequests, windowStartedAtMs };
}

function planRateLimit({ policy: policyInput, usage: usageInput, nowMs = Date.now() } = {}) {
  const policy = normalizeRateLimitPolicy(policyInput);
  if (!policy.configured) {
    return {
      action: RATE_LIMIT_ACTIONS.UNCONFIGURED,
      reason: "provider rate limit policy is not configured",
    };
  }

  const now = Number(nowMs);
  if (!Number.isFinite(now)) throw new TypeError("nowMs must be finite");
  const usage = normalizeUsage(usageInput, policy, now);
  const resetAtMs = usage.windowStartedAtMs + policy.windowMs;

  if (usage.usedRequests >= policy.maxRequests) {
    return {
      action: RATE_LIMIT_ACTIONS.WAIT,
      reason: "provider rate limit reached",
      delayMs: Math.max(0, resetAtMs - now),
      resetAtMs,
      remainingRequests: 0,
    };
  }

  return {
    action: RATE_LIMIT_ACTIONS.ALLOW,
    reason: "provider rate limit capacity available",
    resetAtMs,
    remainingRequests: Math.max(0, policy.maxRequests - usage.usedRequests - 1),
    nextUsage: {
      usedRequests: usage.usedRequests + 1,
      windowStartedAtMs: usage.windowStartedAtMs,
    },
  };
}

module.exports = {
  RATE_LIMIT_ACTIONS,
  MAX_WINDOW_MS,
  MAX_REQUESTS_PER_WINDOW,
  normalizeRateLimitPolicy,
  planRateLimit,
};
