"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { isTrackableCommercialOffer } = require("./commercialPolicy");
const { requiredString } = require("./validation");

const db = getFirestore();

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function validateIntentInput(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new TypeError("request data must be an object");
  }
  return {
    opportunityId: requiredString(data.opportunityId, "opportunityId", 128),
    expectedOfferId: requiredString(data.expectedOfferId, "expectedOfferId", 128),
  };
}

function intentEventId(uid, opportunityId, offerId) {
  return crypto
    .createHash("sha256")
    .update(["ACTION_STARTED", uid, opportunityId, offerId].join("|"))
    .digest("hex");
}

function buildActionStartedEvent(uid, opportunity, commerceMatch, expectedOfferId) {
  if (!uid || !opportunity || !commerceMatch) return null;
  const opportunityId = String(opportunity.id || "").trim();
  const offerId = String(opportunity.matchedOffer?.offerId || "").trim();
  const expected = String(expectedOfferId || "").trim();
  if (!opportunityId || !offerId || !expected || offerId !== expected) return null;
  if (String(opportunity.actionMode || "") !== "IN_APP_PROVIDER_REQUEST") return null;
  if (String(commerceMatch.uid || "") !== String(uid)) return null;
  if (String(commerceMatch.opportunityId || "") !== opportunityId) return null;
  if (String(commerceMatch.offerId || "") !== offerId) return null;
  if (!isTrackableCommercialOffer({
    commercialAgreementActive: commerceMatch.agreementActive === true,
    commissionType: commerceMatch.commissionType,
    commissionValue: commerceMatch.commissionValue,
  })) return null;

  return {
    eventType: "ACTION_STARTED",
    uid: String(uid),
    opportunityId,
    offerId,
    providerName: String(opportunity.matchedOffer?.providerName || ""),
    category: String(opportunity.category || ""),
    potentialMonthlySaving: Number(opportunity.potentialMonthlySaving) || null,
    potentialAnnualSaving: Number(opportunity.potentialAnnualSaving) || null,
    commerceMatchId: `${uid}_${opportunityId}`,
  };
}

exports.recordSavingsActionStarted = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    let input;
    try {
      input = validateIntentInput(request.data);
    } catch (error) {
      throw new HttpsError("invalid-argument", error instanceof Error ? error.message : "Invalid request");
    }

    const userRef = db.collection("users").doc(uid);
    const opportunityRef = userRef.collection("opportunities").doc(input.opportunityId);
    const commerceRef = db.collection("commerceMatches").doc(`${uid}_${input.opportunityId}`);
    const eventRef = db.collection("commerceEvents").doc(
      intentEventId(uid, input.opportunityId, input.expectedOfferId)
    );
    let duplicate = false;

    await db.runTransaction(async (transaction) => {
      const [opportunitySnapshot, commerceSnapshot, existingEvent] = await Promise.all([
        transaction.get(opportunityRef),
        transaction.get(commerceRef),
        transaction.get(eventRef),
      ]);
      if (!opportunitySnapshot.exists || !commerceSnapshot.exists) {
        throw new HttpsError("failed-precondition", "The savings action is no longer attributable.");
      }
      if (existingEvent.exists) {
        duplicate = true;
        return;
      }

      const event = buildActionStartedEvent(
        uid,
        { id: opportunitySnapshot.id, ...opportunitySnapshot.data() },
        commerceSnapshot.data() || {},
        input.expectedOfferId
      );
      if (!event) {
        throw new HttpsError("failed-precondition", "The savings action is not currently eligible for in-app provider contact.");
      }

      transaction.create(eventRef, {
        ...event,
        createdAt: FieldValue.serverTimestamp(),
        schemaVersion: 1,
      });
    });

    return {
      recorded: true,
      duplicate,
      opportunityId: input.opportunityId,
      offerId: input.expectedOfferId,
    };
  }
);

exports._validateIntentInput = validateIntentInput;
exports._intentEventId = intentEventId;
exports._buildActionStartedEvent = buildActionStartedEvent;
