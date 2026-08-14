"use strict";

const { normalizeServiceType } = require("./serviceProfile");
const {
  normalizeAvailabilityMode,
  offerAvailabilityEligible,
  normalizeConsumerPricingEvidence,
} = require("./offerEligibilityPolicy");

const FIXED_MONTHLY_CATEGORIES = new Set([
  "אינטרנט",
  "סלולר",
  "טלוויזיה",
  "תקשורת",
]);
const SUPPORTED_PRICING_MODEL = "FIXED_MONTHLY";
const MIN_PRICE_GUARANTEE_MONTHS = 12;

function normalizeText(value) {
  return String(value || "").trim();
}

function roundMoney(value) {
  return Math.round((Number(value) + Number.EPSILON) * 100) / 100;
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
  const pricingModel = normalizeText(offer.pricingModel || SUPPORTED_PRICING_MODEL).toUpperCase();
  const monthlyPrice = Number(offer.monthlyPrice);
  const priceGuaranteedMonths = Number(offer.priceGuaranteedMonths);
  const oneTimeFees = Number(offer.oneTimeFees);
  const verifiedAtMs = toMillis(offer.verifiedAt);
  const validUntilMs = toMillis(offer.validUntil);
  const availabilityMode = normalizeAvailabilityMode(offer.availabilityMode);
  const pricingEvidence = normalizeConsumerPricingEvidence(offer);

  if (!offerId || !providerName || !category) return null;
  if (pricingModel !== SUPPORTED_PRICING_MODEL) return null;
  if (!FIXED_MONTHLY_CATEGORIES.has(category)) return null;
  if (!Number.isFinite(monthlyPrice) || monthlyPrice <= 0 || monthlyPrice >= 1_000_000) return null;
  if (!Number.isInteger(priceGuaranteedMonths) || priceGuaranteedMonths < MIN_PRICE_GUARANTEE_MONTHS || priceGuaranteedMonths > 120) return null;
  if (!Number.isFinite(oneTimeFees) || oneTimeFees < 0 || oneTimeFees >= 1_000_000) return null;
  if (!Number.isFinite(verifiedAtMs) || !Number.isFinite(validUntilMs)) return null;
  if (verifiedAtMs > nowMs) return null;
  if (validUntilMs <= nowMs) return null;
  if (offer.officialSourceVerified !== true) return null;
  if (offer.availabilityStatus !== "AVAILABLE") return null;
  if (!availabilityMode || !pricingEvidence) return null;

  const serviceType = normalizeServiceType(category, offer.serviceType);
  if (!serviceType) return null;

  const userFitScore = Number(offer.userFitScore);
  const effectiveMonthlyPrice = roundMoney(monthlyPrice + pricingEvidence.requiredRecurringFees);
  const firstYearCost = roundMoney((effectiveMonthlyPrice * 12) + oneTimeFees);
  return {
    offerId,
    providerName,
    category,
    country,
    pricingModel,
    monthlyPrice: roundMoney(monthlyPrice),
    effectiveMonthlyPrice,
    priceGuaranteedMonths,
    oneTimeFees: roundMoney(oneTimeFees),
    firstYearCost,
    serviceType,
    availabilityMode,
    consumerPriceIncludesVat: pricingEvidence.consumerPriceIncludesVat,
    requiredRecurringFees: pricingEvidence.requiredRecurringFees,
    requiredRecurringFeesDescription: pricingEvidence.requiredRecurringFeesDescription,
    verificationState: "VERIFIED",
    freshnessState: "FRESH",
    officialSourceUrl: normalizeText(offer.officialSourceUrl),
    officialSourceName: normalizeText(offer.officialSourceName),
    verificationMethod: normalizeText(offer.verificationMethod) || "OFFICIAL_SOURCE_OPERATOR_ATTESTATION",
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
  const category = normalizeText(opportunity?.category || offer?.category);
  const offeredServiceType = normalizeServiceType(category, offer?.serviceType);
  if (!offeredServiceType) return false;
  if (offeredServiceType === "ANY") return true;
  const requiredServiceType = normalizeServiceType(category, opportunity?.serviceType);
  if (!requiredServiceType) return false;
  return requiredServiceType === offeredServiceType;
}

function matchVerifiedOffers(opportunity, offers, options = {}) {
  if (!opportunity || typeof opportunity !== "object") return [];
  const currentMonthlyCost = Number(opportunity.currentMonthlyCost);
  const category = normalizeText(opportunity.category);
  const country = normalizeText(options.country || opportunity.country || "IL").toUpperCase();
  const nowMs = Number.isFinite(Number(options.nowMs)) ? Number(options.nowMs) : Date.now();
  if (!Number.isFinite(currentMonthlyCost) || currentMonthlyCost <= 0 || !category) return [];
  if (!FIXED_MONTHLY_CATEGORIES.has(category)) return [];

  const currentFirstYearCost = roundMoney(currentMonthlyCost * 12);
  const matches = [];
  for (const rawOffer of Array.isArray(offers) ? offers : []) {
    const offer = normalizeOffer(rawOffer, nowMs);
    if (!offer) continue;
    if (offer.country !== country) continue;
    if (offer.category !== category) continue;
    if (!serviceCompatible(opportunity, offer)) continue;
    if (!offerAvailabilityEligible(opportunity, offer)) continue;

    const annualSaving = roundMoney(currentFirstYearCost - offer.firstYearCost);
    if (annualSaving <= 0) continue;
    const monthlySaving = roundMoney(annualSaving / 12);
    const headlineMonthlySaving = roundMoney(currentMonthlyCost - offer.monthlyPrice);
    matches.push({
      ...offer,
      eligibilityState: "ELIGIBLE",
      currentFirstYearCost,
      headlineMonthlySaving,
      monthlySaving,
      annualSaving,
      commercial: {
        agreementActive: offer.commercialAgreementActive,
        commissionType: offer.commissionType,
        commissionValue: offer.commissionValue,
      },
    });
  }

  matches.sort((a, b) => {
    if (a.annualSaving !== b.annualSaving) return b.annualSaving - a.annualSaving;
    if (a.userFitScore !== b.userFitScore) return b.userFitScore - a.userFitScore;
    if (a.firstYearCost !== b.firstYearCost) return a.firstYearCost - b.firstYearCost;
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
      offerVerificationState: "UNKNOWN",
      offerFreshnessState: "UNKNOWN",
      userEligibilityState: "UNKNOWN",
      truthfulness: {
        ...(opportunity.truthfulness || {}),
        savingsClaimAvailable: false,
        reason: FIXED_MONTHLY_CATEGORIES.has(String(opportunity?.category || "").trim())
          ? "No verified compatible current offer with trustworthy consumer pricing and availability is available."
          : "This category requires a category-specific pricing model before a savings amount can be claimed.",
      },
    };
  }

  const best = matches[0];
  return {
    ...opportunity,
    matchedOffer: {
      offerId: best.offerId,
      providerName: best.providerName,
      pricingModel: best.pricingModel,
      monthlyPrice: best.monthlyPrice,
      effectiveMonthlyPrice: best.effectiveMonthlyPrice,
      priceGuaranteedMonths: best.priceGuaranteedMonths,
      oneTimeFees: best.oneTimeFees,
      firstYearCost: best.firstYearCost,
      serviceType: best.serviceType,
      availabilityMode: best.availabilityMode,
      consumerPriceIncludesVat: best.consumerPriceIncludesVat,
      requiredRecurringFees: best.requiredRecurringFees,
      requiredRecurringFeesDescription: best.requiredRecurringFeesDescription,
      verificationState: best.verificationState,
      freshnessState: best.freshnessState,
      eligibilityState: best.eligibilityState,
      verificationMethod: best.verificationMethod,
      officialSourceUrl: best.officialSourceUrl,
      officialSourceName: best.officialSourceName,
      verifiedAt: best.verifiedAt,
      validUntil: best.validUntil,
      userFitScore: best.userFitScore,
      commercial: best.commercial,
    },
    potentialMonthlySaving: best.monthlySaving,
    potentialAnnualSaving: best.annualSaving,
    offerVerificationState: best.verificationState,
    offerFreshnessState: best.freshnessState,
    userEligibilityState: best.eligibilityState,
    truthfulness: {
      ...(opportunity.truthfulness || {}),
      savingsClaimAvailable: true,
      reason: "Potential savings are calculated from a verified, fresh and eligible VAT-inclusive first-year offer, including declared mandatory recurring and one-time fees. They are not realized savings.",
    },
  };
}

module.exports = {
  FIXED_MONTHLY_CATEGORIES,
  SUPPORTED_PRICING_MODEL,
  MIN_PRICE_GUARANTEE_MONTHS,
  roundMoney,
  toMillis,
  normalizeOffer,
  serviceCompatible,
  matchVerifiedOffers,
  enrichOpportunityWithBestOffer,
};
