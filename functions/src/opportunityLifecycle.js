"use strict";

const LOCKED_OPPORTUNITY_STATUSES = new Set([
  "USER_ACCEPTED",
  "PROVIDER_PROCESSING",
  "ACTIVATED",
  "DEAL_COMPLETED",
  "COMPLETED",
]);

function normalizeStatus(value) {
  return String(value || "OPEN").trim().toUpperCase() || "OPEN";
}

function isOpportunityLifecycleLocked(existingOpportunity) {
  return LOCKED_OPPORTUNITY_STATUSES.has(normalizeStatus(existingOpportunity?.status));
}

function engineOpportunityPayload(opportunity, existingOpportunity) {
  if (!opportunity || typeof opportunity !== "object") return {};
  if (isOpportunityLifecycleLocked(existingOpportunity)) {
    // The financial agent may observe the service again, but must not change the
    // accepted offer, accepted price or attributed saving while fulfillment is in progress.
    return {};
  }
  return { ...opportunity };
}

function shouldRefreshCommerceMatch(existingOpportunity) {
  return !isOpportunityLifecycleLocked(existingOpportunity);
}

module.exports = {
  LOCKED_OPPORTUNITY_STATUSES,
  normalizeStatus,
  isOpportunityLifecycleLocked,
  engineOpportunityPayload,
  shouldRefreshCommerceMatch,
};
