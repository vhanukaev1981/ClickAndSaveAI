"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");

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
    priceGuaranteedMonths: finiteOrNull(offer.priceGuaranteedMonths),
    oneTimeFees: finiteOrNull(offer.oneTimeFees),
    firstYearCost: finiteOrNull(offer.firstYearCost),
    serviceType: String(offer.serviceType || ""),
    verifiedAt: String(offer.verifiedAt || ""),
    validUntil: String(offer.validUntil || ""),
  };
}

function userFacingOpportunity(id, data) {
  const matchedOffer = userFacingOffer(data?.matchedOffer);
  return {
    id: String(id || ""),
    type: String(data?.type || ""),
    status: String(data?.status || "OPEN"),
    providerName: String(data?.providerName || ""),
    category: String(data?.category || ""),
    serviceType: String(data?.serviceType || ""),
    currentMonthlyCost: finiteOrNull(data?.currentMonthlyCost),
    previousMonthlyCost: finiteOrNull(data?.previousMonthlyCost),
    monthlyIncrease: finiteOrNull(data?.monthlyIncrease),
    percentIncrease: finiteOrNull(data?.percentIncrease),
    potentialMonthlySaving: finiteOrNull(data?.potentialMonthlySaving),
    potentialAnnualSaving: finiteOrNull(data?.potentialAnnualSaving),
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
      const aSaving = Number(a.potentialAnnualSaving || 0);
      const bSaving = Number(b.potentialAnnualSaving || 0);
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
