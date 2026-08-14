"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { normalizeHandoffTruth } = require("./handoffTruth");

const db = getFirestore();
const MAX_OPPORTUNITIES = 30;

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function finiteOrNull(value) {
  if (value === null || value === undefined || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function userFacingOffer(offer) {
  if (!offer || typeof offer !== "object") return null;
  const offerId = String(offer.offerId || "").trim();
  const providerName = String(offer.providerName || "").trim();
  if (!offerId || !providerName) return null;

  return {
    offerId,
    providerName,
    pricingModel: String(offer.pricingModel || ""),
    monthlyPrice: finiteOrNull(offer.monthlyPrice),
    effectiveMonthlyPrice: finiteOrNull(offer.effectiveMonthlyPrice),
    priceGuaranteedMonths: finiteOrNull(offer.priceGuaranteedMonths),
    requiredRecurringFees: finiteOrNull(offer.requiredRecurringFees),
    requiredRecurringFeesDescription: String(offer.requiredRecurringFeesDescription || ""),
    oneTimeFees: finiteOrNull(offer.oneTimeFees),
    firstYearCost: finiteOrNull(offer.firstYearCost),
    serviceType: String(offer.serviceType || ""),
    verificationState: String(offer.verificationState || "UNKNOWN"),
    freshnessState: String(offer.freshnessState || "UNKNOWN"),
    eligibilityState: String(offer.eligibilityState || "UNKNOWN"),
    verificationMethod: String(offer.verificationMethod || ""),
    officialSourceUrl: String(offer.officialSourceUrl || ""),
    officialSourceName: String(offer.officialSourceName || ""),
    verifiedAt: String(offer.verifiedAt || ""),
    validUntil: String(offer.validUntil || ""),
  };
}

function safeHandoffTruth(data) {
  const consentState = String(data?.consentState || "").toUpperCase();
  const source = {
    ...(data || {}),
    consentAccepted: data?.consentAccepted === true || consentState === "CONSENTED",
  };
  try {
    return normalizeHandoffTruth(source);
  } catch {
    return normalizeHandoffTruth({});
  }
}

function userFacingOpportunity(id, data) {
  const matchedOffer = userFacingOffer(data?.matchedOffer);
  const truth = safeHandoffTruth(data);
  return {
    id: String(id || ""),
    type: String(data?.type || ""),
    status: String(data?.status || "OPEN"),
    actionMode: String(data?.actionMode || "VIEW_ONLY"),
    providerName: String(data?.providerName || ""),
    category: String(data?.category || ""),
    serviceType: String(data?.serviceType || ""),
    currentMonthlyCost: finiteOrNull(data?.currentMonthlyCost),
    previousMonthlyCost: finiteOrNull(data?.previousMonthlyCost),
    monthlyIncrease: finiteOrNull(data?.monthlyIncrease),
    percentIncrease: finiteOrNull(data?.percentIncrease),
    potentialMonthlySaving: finiteOrNull(data?.potentialMonthlySaving),
    potentialAnnualSaving: finiteOrNull(data?.potentialAnnualSaving),
    realizedMonthlySaving: finiteOrNull(data?.realizedMonthlySaving),
    realizedAnnualSaving: finiteOrNull(data?.realizedAnnualSaving),
    currentCostEvidenceState: String(data?.currentCostEvidenceState || "UNKNOWN"),
    offerVerificationState: matchedOffer?.verificationState || String(data?.offerVerificationState || "UNKNOWN"),
    offerFreshnessState: matchedOffer?.freshnessState || String(data?.offerFreshnessState || "UNKNOWN"),
    userEligibilityState: matchedOffer?.eligibilityState || String(data?.userEligibilityState || "UNKNOWN"),
    consentState: truth.consentState,
    requestState: truth.requestState,
    deliveryAttemptState: truth.deliveryAttemptState,
    submissionState: truth.submissionState,
    deliveryState: truth.deliveryState,
    providerContactState: truth.providerContactState,
    completionState: truth.completionState,
    savingRealizationState: truth.savingRealizationState,
    matchedOffer,
  };
}

exports.getSavingsOpportunities = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const userRef = db.collection("users").doc(uid);
    const contextSnapshot = await userRef.collection("financialContext").doc("current").get();
    const ids = Array.isArray(contextSnapshot.data()?.activeOpportunityIds)
      ? contextSnapshot.data().activeOpportunityIds.slice(0, MAX_OPPORTUNITIES).map(String)
      : [];

    const snapshots = await Promise.all(
      ids.map((id) => userRef.collection("opportunities").doc(id).get())
    );

    const opportunities = snapshots
      .filter((snapshot) => snapshot.exists)
      .map((snapshot) => userFacingOpportunity(snapshot.id, snapshot.data() || {}));

    opportunities.sort((a, b) => {
      const aSaving = a.potentialAnnualSaving == null ? Number.NEGATIVE_INFINITY : a.potentialAnnualSaving;
      const bSaving = b.potentialAnnualSaving == null ? Number.NEGATIVE_INFINITY : b.potentialAnnualSaving;
      if (aSaving !== bSaving) return bSaving - aSaving;
      return String(a.providerName).localeCompare(String(b.providerName), "he");
    });

    return {
      opportunities,
      count: opportunities.length,
    };
  }
);

exports._userFacingOffer = userFacingOffer;
exports._userFacingOpportunity = userFacingOpportunity;
exports._safeHandoffTruth = safeHandoffTruth;
