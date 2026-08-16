"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { requiredString } = require("./validation");
const {
  normalizeHandoffTruth,
  applyDeliveryEvidence,
  applyProviderContactEvidence,
  applyCompletionEvidence,
  applyRejectionEvidence,
  applySavingRealizationEvidence,
} = require("./handoffTruth");

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

function optionalReference(value, field = "externalReference") {
  return value == null || value === "" ? "" : requiredString(String(value), field, 200);
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

  const externalReference = optionalReference(data.externalReference);
  if (["CONTACTED", "ACTIVATED", "REJECTED"].includes(newStatus) && !externalReference) {
    throw new TypeError(`${newStatus} requires an authoritative externalReference`);
  }

  return {
    leadId,
    newStatus,
    actualCommissionAmount,
    externalReference,
  };
}

function validateDeliveryEvidenceInput(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("request data must be an object");
  }
  const leadId = requiredString(data.leadId, "leadId", 128);
  const attemptId = requiredString(data.attemptId, "attemptId", 128);
  const transport = requiredString(data.transport, "transport", 80);
  if (typeof data.submissionAccepted !== "boolean") {
    throw new TypeError("submissionAccepted must be explicit");
  }
  if (typeof data.deliveryConfirmed !== "boolean") {
    throw new TypeError("deliveryConfirmed must be explicit");
  }
  if (data.deliveryConfirmed === true && data.submissionAccepted !== true) {
    throw new TypeError("deliveryConfirmed cannot be true unless submissionAccepted is true");
  }
  const externalReceiptReference = optionalReference(
    data.externalReceiptReference,
    "externalReceiptReference"
  );
  if (data.deliveryConfirmed === true && !externalReceiptReference) {
    throw new TypeError("confirmed delivery requires authoritative receipt evidence");
  }
  return {
    leadId,
    attemptId,
    transport,
    submissionAccepted: data.submissionAccepted,
    deliveryConfirmed: data.deliveryConfirmed,
    externalReceiptReference,
    failureCode: optionalReference(data.failureCode, "failureCode"),
  };
}

function validateSavingRealizationInput(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("request data must be an object");
  }
  const leadId = requiredString(data.leadId, "leadId", 128);
  const currentComparableMonthlyCost = Number(data.currentComparableMonthlyCost);
  const realizedComparableMonthlyCost = Number(data.realizedComparableMonthlyCost);
  if (!Number.isFinite(currentComparableMonthlyCost) || currentComparableMonthlyCost <= 0) {
    throw new TypeError("currentComparableMonthlyCost must be a positive comparable cost");
  }
  if (!Number.isFinite(realizedComparableMonthlyCost) || realizedComparableMonthlyCost <= 0) {
    throw new TypeError("realizedComparableMonthlyCost must be a positive comparable cost");
  }
  const externalReference = optionalReference(data.externalReference);
  if (!externalReference) {
    throw new TypeError("saving realization requires an authoritative external reference");
  }
  return {
    leadId,
    currentComparableMonthlyCost,
    realizedComparableMonthlyCost,
    externalReference,
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
  if (status === "ACTIVATED" || status === "COMMISSION_CONFIRMED") return "DEAL_COMPLETED";
  if (status === "REJECTED") return "PROVIDER_REJECTED";
  return null;
}

function linkedRefs(lead) {
  const uid = String(lead.uid || "").trim();
  const opportunityId = String(lead.opportunityId || "").trim();
  return {
    uid,
    opportunityId,
    commerceRef: uid && opportunityId
      ? db.collection("commerceMatches").doc(`${uid}_${opportunityId}`)
      : null,
    opportunityRef: uid && opportunityId
      ? db.collection("users").doc(uid).collection("opportunities").doc(opportunityId)
      : null,
  };
}

function truthFields(truth) {
  return {
    consentState: truth.consentState,
    requestState: truth.requestState,
    deliveryAttemptState: truth.deliveryAttemptState,
    submissionState: truth.submissionState,
    deliveryState: truth.deliveryState,
    providerContactState: truth.providerContactState,
    completionState: truth.completionState,
    savingRealizationState: truth.savingRealizationState,
    realizedMonthlySaving: truth.realizedMonthlySaving,
    realizedAnnualSaving: truth.realizedAnnualSaving,
  };
}

function classifyDeliveryAttemptReplay(lead, input) {
  const storedAttemptId = String(lead?.lastDeliveryAttemptId || "").trim();
  if (!storedAttemptId || storedAttemptId !== input.attemptId) return "NEW";

  const expectedSubmissionState = input.submissionAccepted ? "SUBMITTED" : "NOT_SUBMITTED";
  const expectedDeliveryState = input.deliveryConfirmed
    ? "DELIVERY_CONFIRMED"
    : (input.submissionAccepted ? "NOT_CONFIRMED" : "DELIVERY_FAILED");
  const same = String(lead?.lastDeliveryTransport || "").trim() === input.transport &&
    String(lead?.submissionState || "").trim().toUpperCase() === expectedSubmissionState &&
    String(lead?.deliveryState || "").trim().toUpperCase() === expectedDeliveryState &&
    String(lead?.externalReceiptReference || "").trim() === input.externalReceiptReference &&
    String(lead?.deliveryFailureCode || "").trim() === input.failureCode;
  return same ? "IDEMPOTENT" : "CONFLICT";
}

exports.recordProviderDeliveryEvidence = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const operatorUid = requireCommerceOperator(request);
    let input;
    try {
      input = validateDeliveryEvidenceInput(request.data);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid request");
    }

    const leadRef = db.collection("providerLeads").doc(input.leadId);
    const queueRef = db.collection("providerDispatchQueue").doc(input.leadId);
    let result = null;

    await db.runTransaction(async (transaction) => {
      const [leadSnapshot, queueSnapshot] = await Promise.all([
        transaction.get(leadRef),
        transaction.get(queueRef),
      ]);
      if (!leadSnapshot.exists) throw new HttpsError("not-found", "Provider lead was not found.");
      if (!queueSnapshot.exists) {
        throw new HttpsError("failed-precondition", "No attributable provider dispatch request exists.");
      }
      const lead = leadSnapshot.data() || {};
      if (String(lead.source || "") !== "AI_PROACTIVE_OPPORTUNITY") {
        throw new HttpsError("failed-precondition", "Delivery evidence is only accepted for attributable savings requests.");
      }
      const currentTruth = normalizeHandoffTruth(lead);
      const replay = classifyDeliveryAttemptReplay(lead, input);
      if (replay === "CONFLICT") {
        throw new HttpsError(
          "failed-precondition",
          "The delivery attempt ID already exists with different authoritative evidence."
        );
      }
      if (replay === "IDEMPOTENT") {
        result = { leadId: input.leadId, idempotent: true, ...truthFields(currentTruth) };
        return;
      }

      let nextTruth;
      try {
        nextTruth = applyDeliveryEvidence(currentTruth, input);
      } catch (error) {
        throw new HttpsError("failed-precondition", error instanceof Error ? error.message : "Invalid delivery transition");
      }
      const refs = linkedRefs(lead);
      const fields = truthFields(nextTruth);
      const queueStatus = nextTruth.deliveryState === "DELIVERY_CONFIRMED"
        ? "DELIVERED"
        : (nextTruth.submissionState === "SUBMITTED" ? "SUBMITTED_UNCONFIRMED" : "FAILED");

      transaction.set(leadRef, {
        ...fields,
        lastDeliveryAttemptId: input.attemptId,
        lastDeliveryTransport: input.transport,
        externalReceiptReference: input.externalReceiptReference || FieldValue.delete(),
        deliveryFailureCode: input.failureCode || FieldValue.delete(),
        deliveryAttemptedAt: FieldValue.serverTimestamp(),
        ...(input.submissionAccepted ? { submittedAt: FieldValue.serverTimestamp() } : {}),
        ...(input.deliveryConfirmed ? { deliveryConfirmedAt: FieldValue.serverTimestamp() } : {}),
        lastUpdatedByOperatorUid: operatorUid,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      transaction.set(queueRef, {
        ...fields,
        status: queueStatus,
        attempts: FieldValue.increment(1),
        lastAttemptId: input.attemptId,
        lastTransport: input.transport,
        externalReceiptReference: input.externalReceiptReference || FieldValue.delete(),
        failureCode: input.failureCode || FieldValue.delete(),
        lastAttemptAt: FieldValue.serverTimestamp(),
        ...(input.deliveryConfirmed ? { deliveredAt: FieldValue.serverTimestamp() } : {}),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      if (refs.commerceRef) transaction.set(refs.commerceRef, { ...fields, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      if (refs.opportunityRef) transaction.set(refs.opportunityRef, { ...fields, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      result = { leadId: input.leadId, idempotent: false, ...fields };
    });

    logger.info("Provider delivery evidence recorded", {
      operatorUid,
      leadId: input.leadId,
      idempotent: result?.idempotent === true,
      submissionState: result.submissionState,
      deliveryState: result.deliveryState,
    });
    return result;
  }
);

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
      const refs = linkedRefs(lead);
      const offerId = String(lead.offerId || "").trim();
      let nextTruth = normalizeHandoffTruth(lead);

      if (transition === "IDEMPOTENT") {
        result = { leadId: input.leadId, status: currentStatus, idempotent: true, ...truthFields(nextTruth) };
        return;
      }

      if (["CONTACTED", "QUOTED", "ACTIVATED", "COMMISSION_CONFIRMED"].includes(input.newStatus) &&
          nextTruth.deliveryState !== "DELIVERY_CONFIRMED") {
        throw new HttpsError(
          "failed-precondition",
          "Provider outcome cannot advance before delivery is authoritatively confirmed."
        );
      }

      try {
        if (input.newStatus === "CONTACTED") {
          nextTruth = applyProviderContactEvidence(nextTruth, {
            contacted: true,
            externalReference: input.externalReference,
          });
        } else if (input.newStatus === "ACTIVATED") {
          nextTruth = applyCompletionEvidence(nextTruth, {
            dealCompleted: true,
            externalReference: input.externalReference,
          });
        } else if (input.newStatus === "REJECTED") {
          nextTruth = applyRejectionEvidence(nextTruth, {
            externalReference: input.externalReference,
          });
        }
      } catch (error) {
        throw new HttpsError("failed-precondition", error instanceof Error ? error.message : "Invalid provider outcome transition");
      }

      const fields = truthFields(nextTruth);
      transaction.set(leadRef, {
        status: input.newStatus,
        ...fields,
        externalReference: input.externalReference || FieldValue.delete(),
        lastUpdatedByOperatorUid: operatorUid,
        updatedAt: FieldValue.serverTimestamp(),
        ...(input.newStatus === "CONTACTED" ? { providerContactedAt: FieldValue.serverTimestamp() } : {}),
        ...(input.newStatus === "ACTIVATED" ? { activatedAt: FieldValue.serverTimestamp(), dealCompletedAt: FieldValue.serverTimestamp() } : {}),
        ...(input.newStatus === "REJECTED" ? { rejectedAt: FieldValue.serverTimestamp() } : {}),
        ...(input.newStatus === "COMMISSION_CONFIRMED"
          ? {
              actualCommissionAmount: input.actualCommissionAmount,
              commissionCurrency: "ILS",
              commissionConfirmedAt: FieldValue.serverTimestamp(),
            }
          : {}),
      }, { merge: true });

      if (refs.commerceRef) {
        transaction.set(refs.commerceRef, {
          uid: refs.uid,
          opportunityId: refs.opportunityId,
          offerId,
          leadId: input.leadId,
          attributionStatus: input.newStatus,
          ...fields,
          externalReference: input.externalReference || FieldValue.delete(),
          updatedAt: FieldValue.serverTimestamp(),
          ...(input.newStatus === "ACTIVATED"
            ? { dealCompletedAt: FieldValue.serverTimestamp() }
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
      if (refs.opportunityRef && opportunityStatus) {
        transaction.set(refs.opportunityRef, {
          status: opportunityStatus,
          ...fields,
          updatedAt: FieldValue.serverTimestamp(),
          ...(input.newStatus === "CONTACTED" ? { providerContactedAt: FieldValue.serverTimestamp() } : {}),
          ...(input.newStatus === "ACTIVATED" ? { dealCompletedAt: FieldValue.serverTimestamp() } : {}),
        }, { merge: true });
      }

      result = { leadId: input.leadId, status: input.newStatus, idempotent: false, ...fields };
    });

    logger.info("Provider lead outcome updated", {
      operatorUid,
      leadId: input.leadId,
      status: result?.status || input.newStatus,
      idempotent: result?.idempotent === true,
      deliveryState: result?.deliveryState,
      providerContactState: result?.providerContactState,
      completionState: result?.completionState,
    });
    return result;
  }
);

exports.recordSavingRealizationEvidence = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const operatorUid = requireCommerceOperator(request);
    let input;
    try {
      input = validateSavingRealizationInput(request.data);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid request");
    }

    const leadRef = db.collection("providerLeads").doc(input.leadId);
    let result = null;
    await db.runTransaction(async (transaction) => {
      const leadSnapshot = await transaction.get(leadRef);
      if (!leadSnapshot.exists) throw new HttpsError("not-found", "Provider lead was not found.");
      const lead = leadSnapshot.data() || {};
      const observedCurrentCost = Number(lead.currentMonthlyCost);
      if (!Number.isFinite(observedCurrentCost) || Math.abs(observedCurrentCost - input.currentComparableMonthlyCost) > 0.01) {
        throw new HttpsError("failed-precondition", "Realized saving baseline does not match the accepted comparable current cost.");
      }
      let nextTruth;
      try {
        nextTruth = applySavingRealizationEvidence(normalizeHandoffTruth(lead), input);
      } catch (error) {
        throw new HttpsError("failed-precondition", error instanceof Error ? error.message : "Invalid saving realization transition");
      }
      const refs = linkedRefs(lead);
      const fields = truthFields(nextTruth);
      const realizationFields = {
        ...fields,
        currentComparableMonthlyCost: nextTruth.currentComparableMonthlyCost,
        realizedComparableMonthlyCost: nextTruth.realizedComparableMonthlyCost,
        savingEvidenceReference: nextTruth.savingEvidenceReference,
        savingRealizationRecordedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      };
      transaction.set(leadRef, {
        ...realizationFields,
        lastUpdatedByOperatorUid: operatorUid,
      }, { merge: true });
      if (refs.commerceRef) transaction.set(refs.commerceRef, realizationFields, { merge: true });
      if (refs.opportunityRef) transaction.set(refs.opportunityRef, realizationFields, { merge: true });
      result = { leadId: input.leadId, ...fields };
    });

    logger.info("Saving realization evidence recorded", {
      operatorUid,
      leadId: input.leadId,
      savingRealizationState: result.savingRealizationState,
      realizedMonthlySaving: result.realizedMonthlySaving,
    });
    return result;
  }
);

exports._validateCommerceOutcomeInput = validateCommerceOutcomeInput;
exports._validateDeliveryEvidenceInput = validateDeliveryEvidenceInput;
exports._validateSavingRealizationInput = validateSavingRealizationInput;
exports._assertTransition = assertTransition;
exports._classifyDeliveryAttemptReplay = classifyDeliveryAttemptReplay;
exports._opportunityStatusForLeadStatus = opportunityStatusForLeadStatus;
exports._truthFields = truthFields;
