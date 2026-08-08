"use strict";

const AVAILABILITY_MODES = new Set([
  "NATIONWIDE",
  "USER_VERIFIED",
  "ELIGIBILITY_REQUIRED",
]);

function normalizeText(value) {
  return String(value || "").trim();
}

function normalizeAvailabilityMode(value) {
  const mode = normalizeText(value).toUpperCase();
  return AVAILABILITY_MODES.has(mode) ? mode : null;
}

function userVerifiedOfferIds(opportunity) {
  const ids = new Set();
  const direct = normalizeText(opportunity?.availabilityVerifiedOfferId);
  if (direct) ids.add(direct);
  for (const value of Array.isArray(opportunity?.availabilityVerifiedOfferIds)
    ? opportunity.availabilityVerifiedOfferIds
    : []) {
    const id = normalizeText(value);
    if (id) ids.add(id);
  }
  return ids;
}

function offerAvailabilityEligible(opportunity, offer) {
  const mode = normalizeAvailabilityMode(offer?.availabilityMode);
  if (!mode) return false;
  if (mode === "NATIONWIDE") return true;
  if (mode === "ELIGIBILITY_REQUIRED") return false;
  if (mode === "USER_VERIFIED") {
    const offerId = normalizeText(offer?.offerId || offer?.id);
    return Boolean(offerId) && userVerifiedOfferIds(opportunity).has(offerId);
  }
  return false;
}

function normalizeConsumerPricingEvidence(offer) {
  if (!offer || typeof offer !== "object") return null;
  if (offer.consumerPriceIncludesVat !== true) return null;

  const requiredRecurringFees = Number(offer.requiredRecurringFees);
  if (!Number.isFinite(requiredRecurringFees) || requiredRecurringFees < 0 || requiredRecurringFees >= 1_000_000) {
    return null;
  }

  const recurringFeesDescription = normalizeText(offer.requiredRecurringFeesDescription);
  if (requiredRecurringFees > 0 && !recurringFeesDescription) return null;

  return {
    consumerPriceIncludesVat: true,
    requiredRecurringFees: Math.round((requiredRecurringFees + Number.EPSILON) * 100) / 100,
    requiredRecurringFeesDescription,
  };
}

module.exports = {
  AVAILABILITY_MODES,
  normalizeAvailabilityMode,
  userVerifiedOfferIds,
  offerAvailabilityEligible,
  normalizeConsumerPricingEvidence,
};
