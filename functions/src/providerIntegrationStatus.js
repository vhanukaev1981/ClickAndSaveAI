"use strict";

const OPERATOR_STATES = Object.freeze({
  DISABLED: "DISABLED",
  READY_FOR_ADAPTER: "READY_FOR_ADAPTER",
  CONNECTION_UNVERIFIED: "CONNECTION_UNVERIFIED",
  ACTUALLY_CONNECTED: "ACTUALLY_CONNECTED",
  DEGRADED: "DEGRADED",
});

function number(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
}

function deriveIntegrationStatus(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("integration status input must be an object");
  }

  if (input.enabled !== true) {
    return { state: OPERATOR_STATES.DISABLED, actionable: false, reason: "provider integration disabled" };
  }
  if (input.adapterImplemented !== true || input.credentialsConfigured !== true) {
    return { state: OPERATOR_STATES.READY_FOR_ADAPTER, actionable: false, reason: "adapter or credentials not configured" };
  }
  if (input.connectionVerified !== true) {
    return { state: OPERATOR_STATES.CONNECTION_UNVERIFIED, actionable: false, reason: "provider connection not verified" };
  }

  const attempted = number(input.attemptedLast24h);
  const failed = number(input.failedLast24h);
  const deadLetter = number(input.deadLetterCount);
  const failureRate = attempted > 0 ? failed / attempted : 0;

  if (deadLetter > 0 || failureRate >= 0.2) {
    return {
      state: OPERATOR_STATES.DEGRADED,
      actionable: true,
      reason: deadLetter > 0 ? "dead-letter items require operator review" : "provider failure rate is elevated",
      failureRate: Math.round(failureRate * 10000) / 10000,
      deadLetterCount: deadLetter,
    };
  }

  return {
    state: OPERATOR_STATES.ACTUALLY_CONNECTED,
    actionable: true,
    reason: "provider connection verified",
    failureRate: Math.round(failureRate * 10000) / 10000,
    deadLetterCount: deadLetter,
  };
}

function buildPrivacySafePartnerFunnel(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("partner funnel input must be an object");
  }
  const verifiedOpportunities = number(input.verifiedOpportunities);
  const consentedRequests = number(input.consentedRequests);
  const acknowledgedDispatches = number(input.acknowledgedDispatches);
  const activations = number(input.activations);
  const confirmedCommission = Math.round(number(input.confirmedCommission) * 100) / 100;

  return {
    verifiedOpportunities,
    consentedRequests,
    acknowledgedDispatches,
    activations,
    confirmedCommission,
    requestRate: verifiedOpportunities > 0 ? Math.round((consentedRequests / verifiedOpportunities) * 10000) / 10000 : 0,
    activationRate: acknowledgedDispatches > 0 ? Math.round((activations / acknowledgedDispatches) * 10000) / 10000 : 0,
  };
}

module.exports = {
  OPERATOR_STATES,
  deriveIntegrationStatus,
  buildPrivacySafePartnerFunnel,
};
