"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { _sendPushToUser: sendPushToUser } = require("./pushFunctions");

const db = getFirestore();

function verifiedOpportunitySignature(data) {
  if (!data || typeof data !== "object") return null;
  const opportunityId = String(data.id || "").trim();
  const offerId = String(data.matchedOffer?.offerId || "").trim();
  const providerName = String(data.matchedOffer?.providerName || "").trim();
  const monthlySaving = Number(data.potentialMonthlySaving);
  const annualSaving = Number(data.potentialAnnualSaving);
  if (!opportunityId || !offerId || !providerName) return null;
  if (!Number.isFinite(monthlySaving) || monthlySaving <= 0) return null;
  if (!Number.isFinite(annualSaving) || annualSaving <= 0) return null;
  return {
    opportunityId,
    offerId,
    providerName,
    monthlySaving,
    annualSaving,
  };
}

function notificationId(uid, signature) {
  return crypto
    .createHash("sha256")
    .update(`${uid}|${signature.opportunityId}|${signature.offerId}|${signature.monthlySaving}`)
    .digest("hex");
}

async function claimNotification(uid, signature) {
  const ref = db
    .collection("users")
    .doc(uid)
    .collection("agentNotifications")
    .doc(notificationId(uid, signature));
  let claimed = false;

  await db.runTransaction(async (transaction) => {
    const existing = await transaction.get(ref);
    if (existing.exists) return;
    transaction.create(ref, {
      opportunityId: signature.opportunityId,
      offerId: signature.offerId,
      monthlySaving: signature.monthlySaving,
      status: "CLAIMED",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    claimed = true;
  });
  return { claimed, ref };
}

exports.onVerifiedSavingsOpportunity = onDocumentWritten(
  {
    document: "users/{uid}/opportunities/{opportunityId}",
    region: "europe-west1",
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    const uid = String(event.params.uid || "").trim();
    if (!uid) return;

    const beforeSignature = verifiedOpportunitySignature(event.data?.before?.data());
    const afterSignature = verifiedOpportunitySignature(event.data?.after?.data());
    if (!afterSignature) return;

    const unchanged = beforeSignature &&
      beforeSignature.offerId === afterSignature.offerId &&
      beforeSignature.monthlySaving === afterSignature.monthlySaving;
    if (unchanged) return;

    const { claimed, ref } = await claimNotification(uid, afterSignature);
    if (!claimed) return;

    try {
      const delivery = await sendPushToUser(uid, {
        title: "Click&SaveAI מצאה חיסכון מאומת",
        body: `אפשר לחסוך ${afterSignature.monthlySaving.toFixed(0)} ₪ בחודש (${afterSignature.annualSaving.toFixed(0)} ₪ בשנה) עם ${afterSignature.providerName}.`,
        data: {
          type: "VERIFIED_SAVINGS_OPPORTUNITY",
          opportunityId: afterSignature.opportunityId,
          offerId: afterSignature.offerId,
        },
      });

      if (delivery.attempted === 0) {
        await ref.delete().catch(() => undefined);
        logger.info("Verified opportunity has no registered push device yet", {
          uid,
          opportunityId: afterSignature.opportunityId,
        });
        return;
      }

      await ref.set({
        status: delivery.delivered > 0 ? "DELIVERED" : "ATTEMPTED",
        attemptedDevices: delivery.attempted,
        deliveredDevices: delivery.delivered,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    } catch (error) {
      await ref.delete().catch(() => undefined);
      logger.error("Verified savings push failed", {
        uid,
        opportunityId: afterSignature.opportunityId,
        errorName: error instanceof Error ? error.name : typeof error,
      });
      throw error;
    }
  }
);

exports._verifiedOpportunitySignature = verifiedOpportunitySignature;
