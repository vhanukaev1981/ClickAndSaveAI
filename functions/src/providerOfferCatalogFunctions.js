"use strict";

const { FieldValue, getFirestore, Timestamp } = require("firebase-admin/firestore");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { MONETIZABLE_CATEGORIES } = require("./financialIntelligence");
const { _runSweep: runFinancialAgentSweep } = require("./financialAgentFunctions");
const {
  FIXED_MONTHLY_CATEGORIES,
  SUPPORTED_PRICING_MODEL,
  MIN_PRICE_GUARANTEE_MONTHS,
} = require("./commerceEngine");
const {
  normalizeAvailabilityMode,
  normalizeConsumerPricingEvidence,
} = require("./offerEligibilityPolicy");
const { normalizeServiceType } = require("./serviceProfile");
const { requiredString, optionalString } = require("./validation");

const db = getFirestore();
const ALLOWED_COMMISSION_TYPES = new Set([
  "NONE",
  "CPA",
  "CPL",
  "REVENUE_SHARE",
  "RETENTION",
]);
const ALLOWED_AVAILABILITY = new Set(["AVAILABLE", "UNAVAILABLE"]);
const MAX_OFFER_VALIDITY_MS = 370 * 24 * 60 * 60 * 1000;
const MAX_FUTURE_VERIFICATION_SKEW_MS = 5 * 60 * 1000;

function requireCatalogOperator(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  const token = request.auth?.token || {};
  if (token.admin !== true && token.operator !== true) {
    throw new HttpsError("permission-denied", "Provider catalog operator permission is required.");
  }
  return uid;
}

function parseHttpsUrl(value, field) {
  const text = requiredString(value, field, 2000);
  let parsed;
  try {
    parsed = new URL(text);
  } catch {
    throw new TypeError(`${field} must be a valid URL`);
  }
  if (parsed.protocol !== "https:" || !parsed.hostname) {
    throw new TypeError(`${field} must use https`);
  }
  parsed.hash = "";
  return parsed.toString();
}

function parseIsoDate(value, field) {
  const text = requiredString(value, field, 80);
  const ms = Date.parse(text);
  if (!Number.isFinite(ms)) throw new TypeError(`${field} must be an ISO date/time`);
  return ms;
}

function validateProviderOfferInput(data, nowMs = Date.now()) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("request data must be an object");
  }

  const offerId = requiredString(data.offerId, "offerId", 128);
  if (!/^[A-Za-z0-9._:-]+$/.test(offerId)) {
    throw new TypeError("offerId contains unsupported characters");
  }
  const providerName = requiredString(data.providerName, "providerName", 160);
  const category = requiredString(data.category, "category", 80);
  if (!MONETIZABLE_CATEGORIES.has(category)) {
    throw new TypeError("category is not a supported monetizable household service");
  }

  const pricingModel = requiredString(
    data.pricingModel || SUPPORTED_PRICING_MODEL,
    "pricingModel",
    40
  ).toUpperCase();
  if (pricingModel !== SUPPORTED_PRICING_MODEL) {
    throw new TypeError("pricingModel is not implemented yet");
  }
  if (!FIXED_MONTHLY_CATEGORIES.has(category)) {
    throw new TypeError(
      "this category requires a category-specific pricing model before automatic savings can be claimed"
    );
  }

  const country = requiredString(data.country || "IL", "country", 2).toUpperCase();
  if (country !== "IL") throw new TypeError("only IL offers are currently supported");

  const rawServiceType = requiredString(data.serviceType, "serviceType", 160);
  const serviceType = normalizeServiceType(category, rawServiceType);
  if (!serviceType) {
    throw new TypeError("serviceType is not a supported explicit service profile");
  }

  const monthlyPrice = Number(data.monthlyPrice);
  if (!Number.isFinite(monthlyPrice) || monthlyPrice <= 0 || monthlyPrice >= 100_000) {
    throw new TypeError("monthlyPrice is invalid");
  }

  if (data.priceGuaranteedMonths === undefined || data.priceGuaranteedMonths === null) {
    throw new TypeError("priceGuaranteedMonths must be verified explicitly");
  }
  const priceGuaranteedMonths = Number(data.priceGuaranteedMonths);
  if (
    !Number.isInteger(priceGuaranteedMonths) ||
    priceGuaranteedMonths < MIN_PRICE_GUARANTEE_MONTHS ||
    priceGuaranteedMonths > 120
  ) {
    throw new TypeError(`priceGuaranteedMonths must be between ${MIN_PRICE_GUARANTEE_MONTHS} and 120`);
  }

  if (data.oneTimeFees === undefined || data.oneTimeFees === null || data.oneTimeFees === "") {
    throw new TypeError("oneTimeFees must be stated explicitly, including zero");
  }
  const oneTimeFees = Number(data.oneTimeFees);
  if (!Number.isFinite(oneTimeFees) || oneTimeFees < 0 || oneTimeFees >= 1_000_000) {
    throw new TypeError("oneTimeFees is invalid");
  }

  const pricingEvidence = normalizeConsumerPricingEvidence(data);
  if (!pricingEvidence) {
    throw new TypeError(
      "consumer pricing must be VAT-inclusive and all mandatory recurring fees must be stated explicitly"
    );
  }

  const availabilityMode = normalizeAvailabilityMode(data.availabilityMode);
  if (!availabilityMode) {
    throw new TypeError("availabilityMode must be NATIONWIDE, USER_VERIFIED or ELIGIBILITY_REQUIRED");
  }

  const availabilityStatus = requiredString(
    data.availabilityStatus || "AVAILABLE",
    "availabilityStatus",
    30
  ).toUpperCase();
  if (!ALLOWED_AVAILABILITY.has(availabilityStatus)) {
    throw new TypeError("availabilityStatus is unsupported");
  }

  const verifiedAtMs = parseIsoDate(data.verifiedAt, "verifiedAt");
  const validUntilMs = parseIsoDate(data.validUntil, "validUntil");
  if (verifiedAtMs > nowMs + MAX_FUTURE_VERIFICATION_SKEW_MS) {
    throw new TypeError("verifiedAt cannot be in the future");
  }
  if (validUntilMs <= nowMs) throw new TypeError("validUntil must be in the future");
  if (validUntilMs <= verifiedAtMs) throw new TypeError("validUntil must be after verifiedAt");
  if (validUntilMs - verifiedAtMs > MAX_OFFER_VALIDITY_MS) {
    throw new TypeError("offer validity window is too long");
  }

  const commercialAgreementActive = data.commercialAgreementActive === true;
  const commissionType = requiredString(
    data.commissionType || "NONE",
    "commissionType",
    40
  ).toUpperCase();
  if (!ALLOWED_COMMISSION_TYPES.has(commissionType)) {
    throw new TypeError("commissionType is unsupported");
  }
  if (!commercialAgreementActive && commissionType !== "NONE") {
    throw new TypeError("commissionType must be NONE without an active commercial agreement");
  }

  let commissionValue = null;
  if (data.commissionValue !== undefined && data.commissionValue !== null && data.commissionValue !== "") {
    commissionValue = Number(data.commissionValue);
    if (!Number.isFinite(commissionValue) || commissionValue < 0 || commissionValue > 1_000_000) {
      throw new TypeError("commissionValue is invalid");
    }
  }
  if (commissionType === "NONE" && commissionValue !== null) {
    throw new TypeError("commissionValue must be empty when commissionType is NONE");
  }
  if (commercialAgreementActive && commissionType === "NONE") {
    throw new TypeError("an active commercial agreement requires a commission model");
  }
  if (commercialAgreementActive && commissionType !== "NONE" && !(commissionValue > 0)) {
    throw new TypeError("a positive commissionValue is required for an active commission model");
  }

  return {
    offerId,
    providerName,
    category,
    pricingModel,
    country,
    serviceType,
    monthlyPrice: Math.round(monthlyPrice * 100) / 100,
    priceGuaranteedMonths,
    oneTimeFees: Math.round(oneTimeFees * 100) / 100,
    consumerPriceIncludesVat: pricingEvidence.consumerPriceIncludesVat,
    requiredRecurringFees: pricingEvidence.requiredRecurringFees,
    requiredRecurringFeesDescription: pricingEvidence.requiredRecurringFeesDescription,
    availabilityMode,
    availabilityStatus,
    officialSourceUrl: parseHttpsUrl(data.officialSourceUrl, "officialSourceUrl"),
    officialSourceName: requiredString(data.officialSourceName, "officialSourceName", 200),
    sourceEvidenceNote: optionalString(data.sourceEvidenceNote, "sourceEvidenceNote", 1000),
    verifiedAtMs,
    validUntilMs,
    commercialAgreementActive,
    commissionType,
    commissionValue,
  };
}

exports.upsertProviderOffer = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const operatorUid = requireCatalogOperator(request);
    let offer;
    try {
      offer = validateProviderOfferInput(request.data);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid offer");
    }

    const ref = db.collection("providerOffers").doc(offer.offerId);
    const existing = await ref.get();
    await ref.set({
      offerId: offer.offerId,
      providerName: offer.providerName,
      category: offer.category,
      pricingModel: offer.pricingModel,
      country: offer.country,
      serviceType: offer.serviceType,
      monthlyPrice: offer.monthlyPrice,
      priceGuaranteedMonths: offer.priceGuaranteedMonths,
      oneTimeFees: offer.oneTimeFees,
      consumerPriceIncludesVat: offer.consumerPriceIncludesVat,
      requiredRecurringFees: offer.requiredRecurringFees,
      requiredRecurringFeesDescription: offer.requiredRecurringFeesDescription,
      availabilityMode: offer.availabilityMode,
      availabilityStatus: offer.availabilityStatus,
      officialSourceVerified: true,
      officialSourceUrl: offer.officialSourceUrl,
      officialSourceName: offer.officialSourceName,
      sourceEvidenceNote: offer.sourceEvidenceNote,
      verifiedAt: Timestamp.fromMillis(offer.verifiedAtMs),
      validUntil: Timestamp.fromMillis(offer.validUntilMs),
      commercialAgreementActive: offer.commercialAgreementActive,
      commissionType: offer.commissionType,
      commissionValue: offer.commissionValue,
      updatedByOperatorUid: operatorUid,
      updatedAt: FieldValue.serverTimestamp(),
      ...(existing.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
      catalogVersion: 5,
    }, { merge: true });

    logger.info("Provider offer catalog entry upserted", {
      operatorUid,
      offerId: offer.offerId,
      providerName: offer.providerName,
      category: offer.category,
      pricingModel: offer.pricingModel,
      serviceType: offer.serviceType,
      monthlyPrice: offer.monthlyPrice,
      requiredRecurringFees: offer.requiredRecurringFees,
      priceGuaranteedMonths: offer.priceGuaranteedMonths,
      oneTimeFees: offer.oneTimeFees,
      availabilityMode: offer.availabilityMode,
      availabilityStatus: offer.availabilityStatus,
      commercialAgreementActive: offer.commercialAgreementActive,
    });

    return {
      offerId: offer.offerId,
      pricingModel: offer.pricingModel,
      serviceType: offer.serviceType,
      monthlyPrice: offer.monthlyPrice,
      requiredRecurringFees: offer.requiredRecurringFees,
      priceGuaranteedMonths: offer.priceGuaranteedMonths,
      oneTimeFees: offer.oneTimeFees,
      availabilityMode: offer.availabilityMode,
      saved: true,
      availabilityStatus: offer.availabilityStatus,
      officialSourceVerified: true,
    };
  }
);

exports.disableProviderOffer = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const operatorUid = requireCatalogOperator(request);
    let offerId;
    try {
      offerId = requiredString(request.data?.offerId, "offerId", 128);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid offer id");
    }
    const ref = db.collection("providerOffers").doc(offerId);
    const snapshot = await ref.get();
    if (!snapshot.exists) throw new HttpsError("not-found", "Provider offer was not found.");

    await ref.set({
      availabilityStatus: "UNAVAILABLE",
      disabledByOperatorUid: operatorUid,
      disabledAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    logger.info("Provider offer disabled", { operatorUid, offerId });
    return { offerId, disabled: true };
  }
);

exports.onProviderOfferCatalogChanged = onDocumentWritten(
  {
    document: "providerOffers/{offerId}",
    region: "europe-west1",
    memory: "512MiB",
    timeoutSeconds: 540,
  },
  async (event) => {
    const offerId = String(event.params.offerId || "").trim();
    logger.info("Provider offer changed; refreshing financial agent matches", { offerId });
    await runFinancialAgentSweep();
  }
);

exports._validateProviderOfferInput = validateProviderOfferInput;
exports._parseHttpsUrl = parseHttpsUrl;
