"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { normalizeOffer } = require("./commerceEngine");
const {
  requiredString,
  validateEmail,
  validatePhone,
} = require("./validation");

const db = getFirestore();
const OPPORTUNITY_ACTION_CONSENT_VERSION = "opportunity-action-v1";

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function validateOpportunityActionInput(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("request data must be an object");
  }
  if (data.consentAccepted !== true) {
    throw new TypeError("explicit provider-contact consent is required");
  }
  const consentVersion = requiredString(data.consentVersion, "consentVersion", 50);
  if (consentVersion !== OPPORTUNITY_ACTION_CONSENT_VERSION) {
    throw new TypeError("unsupported opportunity consent version");
  }
  return {
    opportunityId: requiredString(data.opportunityId, "opportunityId", 128),
    contactName: requiredString(data.contactName, "contactName", 120),
    phone: validatePhone(data.phone),
    contactEmail: validateEmail(data.contactEmail),
    consentVersion,
  };
}

function verifiedActionSnapshot(opportunity, currentOffer) {
  if (!opportunity || typeof opportunity !== "object") return null;
  const matchedOffer = opportunity.matchedOffer;
  const opportunityId = String(opportunity.id || "").trim();
  const offerId = String(matchedOffer?.offerId || "").trim();
  const category = String(opportunity.category || "").trim();
  const currentProvider = String(opportunity.providerName || "").trim();
  const requestedProvider = String(matchedOffer?.providerName || "").trim();
  const currentMonthlyCost = Number(opportunity.currentMonthlyCost);
  const matchedMonthlyPrice = Number(matchedOffer?.monthlyPrice);
  const monthlySaving = Number(opportunity.potentialMonthlySaving);
  const annualSaving = Number(opportunity.potentialAnnualSaving);
  if (!opportunityId || !offerId || !category || !currentProvider || !requestedProvider) return null;
  if (!Number.isFinite(currentMonthlyCost) || currentMonthlyCost <= 0) return null;
  if (!Number.isFinite(matchedMonthlyPrice) || matchedMonthlyPrice <= 0) return null;
  if (!Number.isFinite(monthlySaving) || monthlySaving <= 0) return null;
  if (!Number.isFinite(annualSaving) || annualSaving <= 0) return null;

  const normalizedOffer = normalizeOffer(currentOffer);
  if (!normalizedOffer || normalizedOffer.offerId !== offerId) return null;
  if (normalizedOffer.category !== category) return null;
  if (normalizedOffer.providerName !== requestedProvider) return null;
  if (Math.abs(normalizedOffer.monthlyPrice - matchedMonthlyPrice) > 0.001) return null;

  return {
    opportunityId,
    offerId,
    category,
    currentProvider,
    requestedProvider,
    currentMonthlyCost,
    offeredMonthlyPrice: matchedMonthlyPrice,
    potentialMonthlySaving: monthlySaving,
    potentialAnnualSaving: annualSaving,
    commissionType: normalizedOffer.commissionType,
    commissionValue: normalizedOffer.commissionValue,
    commercialAgreementActive: normalizedOffer.commercialAgreementActive,
  };
}

exports.acceptSavingsOpportunity = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    let input;
    try {
      input = validateOpportunityActionInput(request.data);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid request");
    }

    const userRef = db.collection("users").doc(uid);
    const opportunityRef = userRef.collection("opportunities").doc(input.opportunityId);
    const opportunitySnapshot = await opportunityRef.get();
    if (!opportunitySnapshot.exists) {
      throw new HttpsError("not-found", "The savings opportunity no longer exists.");
    }

    const opportunity = { id: opportunitySnapshot.id, ...opportunitySnapshot.data() };
    const offerId = String(opportunity.matchedOffer?.offerId || "").trim();
    if (!offerId) {
      throw new HttpsError("failed-precondition", "This opportunity does not have a verified provider offer.");
    }

    const offerSnapshot = await db.collection("providerOffers").doc(offerId).get();
    if (!offerSnapshot.exists) {
      throw new HttpsError("failed-precondition", "The provider offer is no longer available.");
    }
    const action = verifiedActionSnapshot(opportunity, { id: offerSnapshot.id, ...offerSnapshot.data() });
    if (!action) {
      throw new HttpsError("failed-precondition", "The provider offer changed or can no longer be verified.");
    }

    const leadId = crypto
      .createHash("sha256")
      .update(`${uid}|${action.opportunityId}|${action.offerId}`)
      .digest("hex");
    const leadRef = db.collection("providerLeads").doc(leadId);
    const commerceRef = db.collection("commerceMatches").doc(`${uid}_${action.opportunityId}`);
    let duplicate = false;

    await db.runTransaction(async (transaction) => {
      const [existingLead, currentOpportunity, commerceSnapshot] = await Promise.all([
        transaction.get(leadRef),
        transaction.get(opportunityRef),
        transaction.get(commerceRef),
      ]);
      if (!currentOpportunity.exists) {
        throw new HttpsError("not-found", "The savings opportunity no longer exists.");
      }
      if (existingLead.exists) {
        duplicate = true;
        return;
      }

      transaction.create(leadRef, {
        uid,
        contactName: input.contactName,
        phone: input.phone,
        contactEmail: input.contactEmail,
        authenticatedEmail: String(request.auth.token.email || "").toLowerCase(),
        category: action.category,
        currentProvider: action.currentProvider,
        requestedProvider: action.requestedProvider,
        opportunityId: action.opportunityId,
        offerId: action.offerId,
        currentMonthlyCost: action.currentMonthlyCost,
        offeredMonthlyPrice: action.offeredMonthlyPrice,
        potentialMonthlySaving: action.potentialMonthlySaving,
        potentialAnnualSaving: action.potentialAnnualSaving,
        consentVersion: input.consentVersion,
        consentAccepted: true,
        source: "AI_PROACTIVE_OPPORTUNITY",
        status: "NEW",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });

      transaction.set(opportunityRef, {
        status: "USER_ACCEPTED",
        actionLeadId: leadId,
        userAcceptedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      transaction.set(commerceRef, {
        uid,
        opportunityId: action.opportunityId,
        offerId: action.offerId,
        providerName: action.requestedProvider,
        potentialMonthlySaving: action.potentialMonthlySaving,
        potentialAnnualSaving: action.potentialAnnualSaving,
        agreementActive: action.commercialAgreementActive,
        commissionType: action.commissionType || "NONE",
        commissionValue: action.commissionValue ?? null,
        leadId,
        attributionStatus: "LEAD_CREATED",
        leadCreatedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: commerceSnapshot.exists });
    });

    logger.info("Verified savings opportunity accepted", {
      uid,
      opportunityId: action.opportunityId,
      offerId: action.offerId,
      leadId,
      duplicate,
      trackable: action.commercialAgreementActive,
    });

    return {
      leadId,
      status: duplicate ? "EXISTING" : "NEW",
      duplicate,
      opportunityId: action.opportunityId,
      offerId: action.offerId,
      potentialMonthlySaving: action.potentialMonthlySaving,
      potentialAnnualSaving: action.potentialAnnualSaving,
    };
  }
);

exports._validateOpportunityActionInput = validateOpportunityActionInput;
exports._verifiedActionSnapshot = verifiedActionSnapshot;
exports.OPPORTUNITY_ACTION_CONSENT_VERSION = OPPORTUNITY_ACTION_CONSENT_VERSION;
