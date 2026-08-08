"use strict";

const SLA_STATES = Object.freeze({
  HEALTHY: "HEALTHY",
  WARNING: "WARNING",
  BREACHED: "BREACHED",
  UNCONFIGURED: "UNCONFIGURED",
});

function normalizeSlaPolicy(input) {
  if (input == null) return null;
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new TypeError("provider SLA policy must be an object");
  const acknowledgementMs = Number(input.acknowledgementMs);
  const statusFreshnessMs = Number(input.statusFreshnessMs);
  if (!Number.isFinite(acknowledgementMs) || acknowledgementMs <= 0) throw new TypeError("acknowledgementMs must be positive");
  if (!Number.isFinite(statusFreshnessMs) || statusFreshnessMs <= 0) throw new TypeError("statusFreshnessMs must be positive");
  return {
    acknowledgementMs: Math.round(acknowledgementMs),
    statusFreshnessMs: Math.round(statusFreshnessMs),
  };
}

function evaluateProviderSla(input = {}) {
  const policy = normalizeSlaPolicy(input.policy);
  if (!policy) return { state: SLA_STATES.UNCONFIGURED, reason: "provider SLA policy is not configured" };

  const ackLatencyMs = Number(input.ackLatencyMs);
  const statusAgeMs = Number(input.statusAgeMs);
  const ackRatio = Number.isFinite(ackLatencyMs) && ackLatencyMs >= 0 ? ackLatencyMs / policy.acknowledgementMs : 0;
  const statusRatio = Number.isFinite(statusAgeMs) && statusAgeMs >= 0 ? statusAgeMs / policy.statusFreshnessMs : 0;
  const worstRatio = Math.max(ackRatio, statusRatio);

  if (worstRatio > 1) return { state: SLA_STATES.BREACHED, reason: "provider SLA threshold exceeded", worstRatio };
  if (worstRatio >= 0.8) return { state: SLA_STATES.WARNING, reason: "provider SLA is approaching threshold", worstRatio };
  return { state: SLA_STATES.HEALTHY, reason: "provider SLA within threshold", worstRatio };
}

module.exports = {
  SLA_STATES,
  normalizeSlaPolicy,
  evaluateProviderSla,
};
