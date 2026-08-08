"use strict";

function clamp01(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 0;
  return Math.min(1, Math.max(0, n));
}

function calculateAdapterHealthScore(input = {}) {
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new TypeError("adapter health input must be an object");

  const successRate = clamp01(input.successRate);
  const ackWithinSlaRate = clamp01(input.ackWithinSlaRate);
  const freshnessRate = clamp01(input.freshnessRate);
  const deadLetterRate = clamp01(input.deadLetterRate);
  const authFailureRate = clamp01(input.authFailureRate);

  const raw = (successRate * 0.4) + (ackWithinSlaRate * 0.25) + (freshnessRate * 0.2) + ((1 - deadLetterRate) * 0.1) + ((1 - authFailureRate) * 0.05);
  const score = Math.round(raw * 10000) / 100;

  return {
    score,
    state: score >= 90 ? "HEALTHY" : score >= 75 ? "WARNING" : score >= 50 ? "DEGRADED" : "CRITICAL",
  };
}

module.exports = {
  calculateAdapterHealthScore,
};
