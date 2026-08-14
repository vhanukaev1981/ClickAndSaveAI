"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { normalizeOffer, serviceCompatible, roundMoney } = require("./commerceEngine");
const { offerAvailabilityEligible } = require("./offerEligibilityPolicy");
const {
  IN_APP_PROVIDER_REQUEST,
  commercialActionMode,
  isTrackableCommercialOffer,
} = require("./commercialPolicy");
const { createHandoffTruth, normalizeHandoffTruth } = require("./handoffTruth");
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
    expectedOfferId: requiredString(data.expectedOfferId, "expectedOfferId", 128),
    contactName: requiredString(data.contactName, "contactName", 120),
    phone: validatePhone(data.phone),
    contactEmail: validateEmail(data.contactEmail),
    consentVersion,
  };
}

function verifiedActionSnapshot(opportunity, currentOffer, expectedOfferId = "") {
  if (!opportunity || typeof opportunity !== "object") return null;
  const matchedOffer = opportunity.matchedOffer;
  const opportunityId = String(opportunity.id || "").trim();
  const offerId = String(matchedOffer?.offerId || "").trim();
  const expected = String(expectedOfferId || "").trim();
  const category = String(opportunity.category || "").trim();
  const currentProvider = String(opportunity.providerName || "").trim();
  const requestedProvider = String(matchedOffer?.providerName || "").trim();
  const currentMonthlyCost = Number(opportunity.currentMonthlyCost);
  const matchedMonthlyPrice = Number(matchedOffer?.monthlyPrice);
  const matchedFirstYearCost = Number(matchedOffer?.firstYearCost);
  const monthlySaving = Number(opportunity.potentialMonthlySaving);
  const annualSaving = Number(opportunity.potentialAnnualSaving);
  if (!opportunityId || !offerId || !category || !currentProvider || !requestedProvider) return null;
  if (expected && expected !== offerId) return null;
  if (!Number.isFinite(currentMonthlyCost) || currentMonthlyCost <= 0) return null;
  if (!Number.isFinite(matchedMonthlyPrice) || matchedMonthlyPrice <= 0) return null;
  if (!Number.isFinite(matchedFirstYearCost) || matchedFirstYearCost <= 0) return null;
  if (!Number.isFinite(monthlySaving) || monthlySaving <= 0) return null;
  if (!Number.isFinite(annualSaving) || annualSaving <= 0) return null;

  const normalizedOffer = normalizeOffer(currentOffer);
  if (!normalizedOffer || normalizedOffer.offerId !== offerId) return null;
  if (normalizedOffer.category !== category) return null;
  if (normalizedOffer.providerName !== requestedProvider) return null;
  if (!serviceCompatible(opportunity, normalizedOffer)) return null;
  if (!offerAvailabilityEligible(opportunity, normalizedOffer)) return null;
  if (Math.abs(normalizedOffer.monthlyPrice - matchedMonthlyPrice) > 0.001) return null;
  if (Math.abs(normalizedOffer.firstYearCost - matchedFirstYearCost) > 0.001) return null;

  const recalculatedAnnualSaving = roundMoney((currentMonthlyCost * 12) - normalizedOffer.firstYearCost);
  const recalculatedMonthlySaving = roundMoney(recalculatedAnnualSaving / 12);
  if (recalculatedAnnualSaving <= 0 || recalculatedMonthlySaving <= 0) return null;
  if (Math.abs(recalculatedAnnualSaving - annualSaving) > 0.01) return null;
  if (Math.abs(recalculatedMonthlySaving - monthlySaving) > 0.01) return null;

  return {
    opportunityId,
    offerId,
    category,
    currentProvider,
    requestedProvider,
    currentMonthlyCost,
    offeredMonthlyPrice: normalizedOffer.monthlyPrice,
    effectiveMonthlyPrice: normalizedOffer.effectiveMonthlyPrice,
    firstYearCost: normalizedOffer.firstYearCost,
    potentialMonthlySaving: recalculatedMonthlySaving,
    potentialAnnualSaving: recalculatedAnnualSaving,
    offerVerificationState: normalizedOffer.verificationState,
    offerFreshnessState: normalizedOffer.freshnessState,
    userEligibilityState: "ELIGIBLE",
    commissionType: normalizedOffer.commissionType,
    commissionValue: normalizedOffer.commissionValue,
    commercialAgreementActive: normalizedOffer.commercialAgreementActive,
    actionMode: commercialActionMode(normalizedOffer),
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
    const leadId = crypto
      .createHash("sha256")
      .update(`${uid}|${input.opportunityId}|${input.expectedOfferId}`)
      .digest("hex");
    const leadRef = db.collection("providerLeads").doc(leadId);
    const commerceRef = db.collection("commerceMatches").doc(`${uid}_${input.opportunityId}`);
    let duplicate = false;
    let action = null;
    let handoffTruth = null;

    await db.runTransaction(async (transaction) => {
      const currentOpportunity = await transaction.get(opportunityRef);
      if (!currentOpportunity.exists) {
        throw new HttpsError("not-found", "The savings opportunity no longer exists.");
      }

      const opportunity = { id: currentOpportunity.id, ...currentOpportunity.data() };
      const currentOfferId = String(opportunity.matchedOffer?.offerId || "").trim();
      if (!currentOfferId || currentOfferId !== input.expectedOfferId) {
        throw new HttpsError(
          "failed-precondition",
          "The provider offer changed. Refresh the opportunity before approving it."
        );
      }

      const offerRef = db.collection("providerOffers").doc(currentOfferId);
      const [offerSnapshot, existingLead] = await Promise.all([
        transaction.get(offerRef),
        transaction.get(leadRef),
      ]);
      if (!offerSnapshot.exists) {
        throw new HttpsError("failed-precondition", "The provider offer is no longer available.");
      }

      action = verifiedActionSnapshot(
        opportunity,
        { id: offerSnapshot.id, ...offerSnapshot.data() },
        input.expectedOfferId
      );
      if (!action) {
        throw new HttpsError(
          "failed-precondition",
          "The provider offer changed, is not eligible, or can no longer be verified."
        );
      }
      if (action.actionMode !== IN_APP_PROVIDER_REQUEST || !isTrackableCommercialOffer({
        commercialAgreementActive: action.commercialAgreementActive,
        commissionType: action.commissionType,
        commissionValue: action.commissionValue,
      })) {
        throw new HttpsError(
          "failed-precondition",
          "This verified offer is view-only because an attributable in-app provider agreement is not active."
        );
      }

      if (existingLead.exists) {
        duplicate = true;
        handoffTruth = normalizeHandoffTruth(existingLead.data() || {});
        return;
      }

      handoffTruth = createHandoffTruth({ consentAccepted: true, requestCreated: true });
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
        effectiveMonthlyPrice: action.effectiveMonthlyPrice,
        firstYearCost: action.firstYearCost,
        potentialMonthlySaving: action.potentialMonthlySaving,
        potentialAnnualSaving: action.potentialAnnualSaving,
        offerVerificationState: action.offerVerificationState,
        offerFreshnessState: action.offerFreshnessState,
        userEligibilityState: action.userEligibilityState,
        consentVersion: input.consentVersion,
        consentAccepted: true,
        ...handoffTruth,
        source: "AI_PROACTIVE_OPPORTUNITY",
        status: "NEW",
        createdAt: FieldValue.serverTimestamp(),
        consentedAt: FieldValue.serverTimestamp(),
        requestCreatedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });

      transaction.set(opportunityRef, {
        status: "USER_ACCEPTED",
        actionLeadId: leadId,
        acceptedOfferId: action.offerId,
        offerVerificationState: action.offerVerificationState,
        offerFreshnessState: action.offerFreshnessState,
        userEligibilityState: action.userEligibilityState,
        ...handoffTruth,
        userAcceptedAt: FieldValue.serverTimestamp(),
        consentedAt: FieldValue.serverTimestamp(),
        requestCreatedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      transaction.set(commerceRef, {
        uid,
        opportunityId: action.opportunityId,
        offerId: action.offerId,
        providerName: action.requestedProvider,
        potentialMonthlySaving: action.potentialMonthlySaving,
        potentialAnnualSaving: action.potentialAnnualSaving,
        offerVerificationState: action.offerVerificationState,
        offerFreshnessState: action.offerFreshnessState,
        userEligibilityState: action.userEligibilityState,
        agreementActive: action.commercialAgreementActive,
        commissionType: action.commissionType,
        commissionValue: action.commissionValue,
        leadId,
        ...handoffTruth,
        attributionStatus: "LEAD_CREATED",
        leadCreatedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });

    if (!action || !handoffTruth) {
      throw new HttpsError("internal", "The verified opportunity action was not resolved.");
    }

    logger.info("Verified savings opportunity request created", {
      uid,
      opportunityId: action.opportunityId,
      offerId: action.offerId,
      leadId,
      duplicate,
      requestState: handoffTruth.requestState,
      submissionState: handoffTruth.submissionState,
      deliveryState: handoffTruth.deliveryState,
    });

    return {
      leadId,
      status: duplicate ? "EXISTING" : "NEW",
      duplicate,
      opportunityId: action.opportunityId,
      offerId: action.offerId,
      potentialMonthlySaving: action.potentialMonthlySaving,
      potentialAnnualSaving: action.potentialAnnualSaving,
      consentState: handoffTruth.consentState,
      requestState: handoffTruth.requestState,
      deliveryAttemptState: handoffTruth.deliveryAttemptState,
      submissionState: handoffTruth.submissionState,
      deliveryState: handoffTruth.deliveryState,
      providerContactState: handoffTruth.providerContactState,
      completionState: handoffTruth.completionState,
      savingRealizationState: handoffTruth.savingRealizationState,
      realizedMonthlySaving: handoffTruth.realizedMonthlySaving,
      realizedAnnualSaving: handoffTruth.realizedAnnualSaving,
    };
  }
);

exports._validateOpportunityActionInput = validateOpportunityActionInput;
exports._verifiedActionSnapshot = verifiedActionSnapshot;
exports.OPPORTUNITY_ACTION_CONSENT_VERSION = OPPORTUNITY_ACTION_CONSENT_VERSION;
