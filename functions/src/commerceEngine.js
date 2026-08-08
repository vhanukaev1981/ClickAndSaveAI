"use strict";

function normalizeText(value) {
  return String(value || "").trim();
}

function toMillis(value) {
  if (value instanceof Date) return value.getTime();
  if (value && typeof value.toDate === "function") {
    const date = value.toDate();
    return date instanceof Date ? date.getTime() : Number.NaN;
  }
  if (value && Number.isFinite(Number(value.seconds))) {
    const seconds = Number(value.seconds);
    const nanos = Number.isFinite(Number(value.nanoseconds)) ? Number(value.nanoseconds) : 0;
    return (seconds * 1000) + Math.floor(nanos / 1_000_000);
  }
  if (typeof value === "number" && Number.isFinite(value)) return value;
  const parsed = Date.parse(normalizeText(value));
  return Number.isFinite(parsed) ? parsed : Number.NaN;
}

function normalizeOffer(offer, nowMs = Date.now()) {
  if (!offer || typeof offer !== "object") return null;
  const offerId = normalizeText(offer.offerId || offer.id);
  const providerName = normalizeText(offer.providerName);
  const category = normalizeText(offer.category);
  const country = normalizeText(offer.country || "IL").toUpperCase();
  const monthlyPrice = Number(offer.monthlyPrice);
  const verifiedAtMs = toMillis(offer.verifiedAt);
  const validUntilMs = toMillis(offer.validUntil);

  if (!offerId || !providerName || !category) return null;
  if (!Number.isFinite(monthlyPrice) || monthlyPrice <= 0 || monthlyPrice >= 1_000_000) return null;
  if (!Number.isFinite(verifiedAtMs) || !Number.isFinite(validUntilMs)) return null;
  if (verifiedAtMs > nowMs) return null;
  if (validUntilMs <= nowMs) return null;
  if (offer.officialSourceVerified !== true) return null;
  if (offer.availabilityStatus !== "AVAILABLE") return null;

  const serviceType = normalizeText(offer.serviceType);
  if (!serviceType) return null;

  const userFitScore = Number(offer.userFitScore);
  return {
    offerId,
    providerName,
    category,
    country,
    monthlyPrice,
    serviceType,
    verifiedAt: new Date(verifiedAtMs).toISOString(),
    validUntil: new Date(validUntilMs).toISOString(),
    userFitScore: Number.isFinite(userFitScore)
      ? Math.max(0, Math.min(1, userFitScore))
      : 0.5,
    commercialAgreementActive: offer.commercialAgreementActive === true,
    commissionType: normalizeText(offer.commissionType) || "NONE",
    commissionValue: Number.isFinite(Number(offer.commissionValue))
      ? Number(offer.commissionValue)
      : null,
  };
}

function serviceCompatible(opportunity, offer) {
  const requiredServiceType = normalizeText(opportunity?.serviceType);
  const offeredServiceType = normalizeText(offer?.serviceType);
  if (!offeredServiceType) return false;
  if (offeredServiceType.toUpperCase() === "ANY") return true;
  if (!requiredServiceType) return false;
  return requiredServiceType.toLowerCase() === offeredServiceType.toLowerCase();
}

function matchVerifiedOffers(opportunity, offers, options = {}) {
  if (!opportunity || typeof opportunity !== "object") return [];
  const currentMonthlyCost = Number(opportunity.currentMonthlyCost);
  const category = normalizeText(opportunity.category);
  const country = normalizeText(options.country || opportunity.country || "IL").toUpperCase();
  const nowMs = Number.isFinite(Number(options.nowMs)) ? Number(options.nowMs) : Date.now();
  if (!Number.isFinite(currentMonthlyCost) || currentMonthlyCost <= 0 || !category) return [];

  const matches = [];
  for (const rawOffer of Array.isArray(offers) ? offers : []) {
    const offer = normalizeOffer(rawOffer, nowMs);
    if (!offer) continue;
    if (offer.country !== country) continue;
    if (offer.category !== category) continue;
    if (!serviceCompatible(opportunity, offer)) continue;
    if (offer.monthlyPrice >= currentMonthlyCost) continue;

    const monthlySaving = Math.round((currentMonthlyCost - offer.monthlyPrice) * 100) / 100;
    matches.push({
      ...offer,
      monthlySaving,
      annualSaving: Math.round(monthlySaving * 12 * 100) / 100,
      commercial: {
        agreementActive: offer.commercialAgreementActive,
        commissionType: offer.commissionType,
        commissionValue: offer.commissionValue,
      },
    });
  }

  // User value is the ranking rule. Commission is deliberately excluded from this comparator.
  matches.sort((a, b) => {
    if (a.monthlySaving !== b.monthlySaving) return b.monthlySaving - a.monthlySaving;
    if (a.userFitScore !== b.userFitScore) return b.userFitScore - a.userFitScore;
    if (a.monthlyPrice !== b.monthlyPrice) return a.monthlyPrice - b.monthlyPrice;
    return a.offerId.localeCompare(b.offerId);
  });
  return matches;
}

function enrichOpportunityWithBestOffer(opportunity, offers, options = {}) {
  const matches = matchVerifiedOffers(opportunity, offers, options);
  if (matches.length === 0) {
    return {
      ...opportunity,
      matchedOffer: null,
      potentialMonthlySaving: null,
      potentialAnnualSaving: null,
      truthfulness: {
        ...(opportunity.truthfulness || {}),
        savingsClaimAvailable: false,
        reason: "No verified compatible current offer is available.",
      },
    };
  }

  const best = matches[0];
  return {
    ...opportunity,
    matchedOffer: {
      offerId: best.offerId,
      providerName: best.providerName,
      monthlyPrice: best.monthlyPrice,
      serviceType: best.serviceType,
      verifiedAt: best.verifiedAt,
      validUntil: best.validUntil,
      userFitScore: best.userFitScore,
      commercial: best.commercial,
    },
    potentialMonthlySaving: best.monthlySaving,
    potentialAnnualSaving: best.annualSaving,
    truthfulness: {
      ...(opportunity.truthfulness || {}),
      savingsClaimAvailable: true,
      reason: "Savings are calculated from a verified compatible current offer.",
    },
  };
}

module.exports = {
  toMillis,
  normalizeOffer,
  serviceCompatible,
  matchVerifiedOffers,
  enrichOpportunityWithBestOffer,
};
