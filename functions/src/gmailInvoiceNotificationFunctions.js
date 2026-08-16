"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { _sendPushToUser: sendPushToUser } = require("./pushFunctions");
const { safeNotificationEnvelope } = require("./notificationEnvelope");

const db = getFirestore();
const DELIVERY_LEASE_MS = 10 * 60 * 1000;

function deliveryDocumentId(eventId) {
  return crypto.createHash("sha256").update(eventId).digest("hex");
}

async function authenticatedAccountExists(uid) {
  try {
    await getAuth().getUser(uid);
    return true;
  } catch (error) {
    if (error?.code === "auth/user-not-found") return false;
    throw error;
  }
}

exports.onAuthoritativeGmailInvoiceCreated = onDocumentCreated(
  {
    document: "users/{uid}/gmailInvoices/{invoiceId}",
    region: "europe-west1",
    retry: true,
    timeoutSeconds: 120,
    memory: "256MiB",
  },
  async (event) => {
    const uid = String(event.params.uid || "").trim();
    const invoice = event.data?.data() || {};
    const sourceMessageId = String(invoice.sourceMessageId || "").trim();
    if (!uid || !sourceMessageId) return;

    const connection = await db.collection("gmailConnections").doc(uid).get();
    if (!connection.exists || connection.data()?.initialBackfillCompleted !== true) return;
    if (!await authenticatedAccountExists(uid)) {
      logger.info("Gmail invoice push suppressed for deleted account", { uid });
      return;
    }

    const eventId = `bill-detected:${sourceMessageId}`;
    const deliveryRef = db.collection("users").doc(uid)
      .collection("notificationEvents")
      .doc(deliveryDocumentId(eventId));
    const leaseOwner = String(event.id || crypto.randomUUID());
    const nowMs = Date.now();
    const claimed = await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(deliveryRef);
      const data = snapshot.data() || {};
      if (data.status === "SENT" || data.status === "NO_DEVICE" || data.status === "ACCOUNT_DELETED") return false;
      const activeLeaseUntilMs = Number(data.leaseUntilMs || 0);
      const activeLeaseOwner = String(data.leaseOwner || "");
      if (activeLeaseUntilMs > nowMs && activeLeaseOwner && activeLeaseOwner !== leaseOwner) return false;
      transaction.set(deliveryRef, {
        eventId,
        type: "NEW_INVOICE",
        sourceMessageId,
        status: "PROCESSING",
        leaseOwner,
        leaseUntilMs: nowMs + DELIVERY_LEASE_MS,
        createdAt: data.createdAt || FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      return true;
    });
    if (!claimed) return;

    try {
      const delivery = await sendPushToUser(uid, safeNotificationEnvelope(uid, "NEW_INVOICE", {
        sourceMessageId,
        eventId,
      }));
      await deliveryRef.set({
        status: delivery.attempted > 0 ? "SENT" : "NO_DEVICE",
        deliveredCount: Number(delivery.delivered || 0),
        leaseOwner: FieldValue.delete(),
        leaseUntilMs: FieldValue.delete(),
        completedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    } catch (error) {
      await deliveryRef.set({
        status: "RETRYABLE_FAILURE",
        leaseOwner: FieldValue.delete(),
        leaseUntilMs: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true }).catch(() => undefined);
      logger.error("Authoritative Gmail invoice push failed", {
        uid,
        errorName: error instanceof Error ? error.name : typeof error,
      });
      throw error;
    }
  }
);

Object.defineProperties(module.exports, {
  _deliveryDocumentId: { value: deliveryDocumentId, enumerable: false },
  _authenticatedAccountExists: { value: authenticatedAccountExists, enumerable: false },
});
