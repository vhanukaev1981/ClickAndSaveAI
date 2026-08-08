"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { requiredString } = require("./validation");

const db = getFirestore();

const LEAD_TRANSITIONS = new Map([
  ["NEW", new Set(["CONTACTED", "REJECTED"])],
  ["CONTACTED", new Set(["QUOTED", "REJECTED"])],
  ["QUOTED", new Set(["ACTIVATED", "REJECTED"])],
  ["ACTIVATED", new Set(["COMMISSION_CONFIRMED"])],
  ["REJECTED", new Set()],
  ["COMMISSION_CONFIRMED", new Set()],
]);

function requireCommerceOperator(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  const token = request.auth?.token || {};
  if (token.admin !== true && token.operator !== true) {
    throw new HttpsError("permission-denied", "Commerce operator permission is required.");
  }
  return uid;
}

function validateCommerceOutcomeInput(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("request data must be an object");
  }
  const leadId = requiredString(data.leadId, "leadId", 128);
  const newStatus = requiredString(data.newStatus, "newStatus", 40).toUpperCase();
  if (!LEAD_TRANSITIONS.has(newStatus)) {
    throw new TypeError("unsupported provider lead status");
  }

  let actualCommissionAmount = null;
  if (data.actualCommissionAmount !== undefined && data.actualCommissionAmount !== null) {
    actualCommissionAmount = Number(data.actualCommissionAmount);
    if (!Number.isFinite(actualCommissionAmount) || actualCommissionAmount < 0 || actualCommissionAmount > 1_000_000) {
      throw new TypeError("actualCommissionAmount is invalid");
    }
  }
  if (newStatus === "COMMISSION_CONFIRMED" && !(actualCommissionAmount > 0)) {
    throw new TypeError("COMMISSION_CONFIRMED requires a positive actualCommissionAmount");
  }

  return {
    leadId,
    newStatus,
    actualCommissionAmount,
    externalReference: data.externalReference == null
      ? ""
      : requiredString(String(data.externalReference), "externalReference", 200),
  };
}

function assertTransition(currentStatus, newStatus) {
  if (currentStatus === newStatus) return "IDEMPOTENT";
  const allowed = LEAD_TRANSITIONS.get(currentStatus);
  if (!allowed || !allowed.has(newStatus)) {
    throw new HttpsError(
      "failed-precondition",
      `Provider lead cannot move from ${currentStatus} to ${newStatus}.`
    );
  }
  return "CHANGE";
}

function opportunityStatusForLeadStatus(status) {
  if (status === "CONTACTED" || status === "QUOTED") return "PROVIDER_PROCESSING";
  if (status === "ACTIVATED") return "ACTIVATED";
  if (status === "COMMISSION_CONFIRMED") return "COMPLETED";
  if (status === "REJECTED") return "PROVIDER_REJECTED";
  return null;
}

exports.updateProviderLeadOutcome = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const operatorUid = requireCommerceOperator(request);
    let input;
    try {
      input = validateCommerceOutcomeInput(request.data);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid request");
    }

    const leadRef = db.collection("providerLeads").doc(input.leadId);
    let result = null;

    await db.runTransaction(async (transaction) => {
      const leadSnapshot = await transaction.get(leadRef);
      if (!leadSnapshot.exists) {
        throw new HttpsError("not-found", "Provider lead was not found.");
      }
      const lead = leadSnapshot.data() || {};
      const currentStatus = String(lead.status || "NEW").toUpperCase();
      const transition = assertTransition(currentStatus, input.newStatus);
      const uid = String(lead.uid || "").trim();
      const opportunityId = String(lead.opportunityId || "").trim();
      const offerId = String(lead.offerId || "").trim();
      const commerceRef = uid && opportunityId
        ? db.collection("commerceMatches").doc(`${uid}_${opportunityId}`)
        : null;
      const opportunityRef = uid && opportunityId
        ? db.collection("users").doc(uid).collection("opportunities").doc(opportunityId)
        : null;

      if (transition === "IDEMPOTENT") {
        result = { leadId: input.leadId, status: currentStatus, idempotent: true };
        return;
      }

      transaction.set(leadRef, {
        status: input.newStatus,
        externalReference: input.externalReference || FieldValue.delete(),
        lastUpdatedByOperatorUid: operatorUid,
        updatedAt: FieldValue.serverTimestamp(),
        ...(input.newStatus === "ACTIVATED"
          ? { activatedAt: FieldValue.serverTimestamp() }
          : {}),
        ...(input.newStatus === "REJECTED"
          ? { rejectedAt: FieldValue.serverTimestamp() }
          : {}),
        ...(input.newStatus === "COMMISSION_CONFIRMED"
          ? {
              actualCommissionAmount: input.actualCommissionAmount,
              commissionCurrency: "ILS",
              commissionConfirmedAt: FieldValue.serverTimestamp(),
            }
          : {}),
      }, { merge: true });

      if (commerceRef) {
        transaction.set(commerceRef, {
          uid,
          opportunityId,
          offerId,
          leadId: input.leadId,
          attributionStatus: input.newStatus,
          externalReference: input.externalReference || FieldValue.delete(),
          updatedAt: FieldValue.serverTimestamp(),
          ...(input.newStatus === "ACTIVATED"
            ? { activatedAt: FieldValue.serverTimestamp() }
            : {}),
          ...(input.newStatus === "COMMISSION_CONFIRMED"
            ? {
                actualCommissionAmount: input.actualCommissionAmount,
                commissionCurrency: "ILS",
                commissionConfirmedAt: FieldValue.serverTimestamp(),
              }
            : {}),
        }, { merge: true });
      }

      const opportunityStatus = opportunityStatusForLeadStatus(input.newStatus);
      if (opportunityRef && opportunityStatus) {
        transaction.set(opportunityRef, {
          status: opportunityStatus,
          updatedAt: FieldValue.serverTimestamp(),
          ...(input.newStatus === "ACTIVATED"
            ? { activatedAt: FieldValue.serverTimestamp() }
            : {}),
          ...(input.newStatus === "COMMISSION_CONFIRMED"
            ? { completedAt: FieldValue.serverTimestamp() }
            : {}),
        }, { merge: true });
      }

      result = { leadId: input.leadId, status: input.newStatus, idempotent: false };
    });

    logger.info("Provider lead outcome updated", {
      operatorUid,
      leadId: input.leadId,
      status: result?.status || input.newStatus,
      idempotent: result?.idempotent === true,
    });
    return result;
  }
);

exports._validateCommerceOutcomeInput = validateCommerceOutcomeInput;
exports._assertTransition = assertTransition;
exports._opportunityStatusForLeadStatus = opportunityStatusForLeadStatus;
