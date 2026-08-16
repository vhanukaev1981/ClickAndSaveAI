"use strict";

const crypto = require("node:crypto");
const { getMessaging } = require("firebase-admin/messaging");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { assertActiveAccount } = require("./accountAuthorization");
const { emitOperationalEvent } = require("./operationalTelemetry");

const db = getFirestore();

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  }
  return uid;
}

function normalizeToken(value) {
  const token = typeof value === "string" ? value.trim() : "";
  if (token.length < 20 || token.length > 4096 || /\s/.test(token)) {
    throw new HttpsError("invalid-argument", "A valid FCM registration token is required.");
  }
  return token;
}

function tokenDocumentId(token) {
  return crypto.createHash("sha256").update(token).digest("hex");
}

async function sendPushToUser(uid, { title, body, data = {} }) {
  const snapshot = await db
    .collection("users")
    .doc(uid)
    .collection("pushTokens")
    .where("enabled", "==", true)
    .get();

  const tokenDocs = snapshot.docs
    .map((doc) => ({ ref: doc.ref, token: String(doc.data()?.token || "").trim() }))
    .filter((item) => item.token.length >= 20);

  if (tokenDocs.length === 0) {
    emitOperationalEvent({
      event: "push.delivery",
      subsystem: "push",
      outcome: "no_op",
      severity: "INFO",
      code: "PUSH_NO_REGISTERED_DEVICE",
      uid,
      details: { attempted: 0, delivered: 0, removedInvalid: 0 },
    });
    return { attempted: 0, delivered: 0, removedInvalid: 0 };
  }

  const response = await getMessaging().sendEachForMulticast({
    tokens: tokenDocs.map((item) => item.token),
    notification: { title, body },
    data: Object.fromEntries(
      Object.entries(data).map(([key, value]) => [key, String(value)])
    ),
    android: {
      priority: "high",
      notification: {
        channelId: "savings_opportunities",
      },
    },
  });

  const invalidIndexes = [];
  response.responses.forEach((result, index) => {
    if (result.success) return;
    const code = result.error?.code || "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      invalidIndexes.push(index);
    }
  });

  await Promise.all(
    invalidIndexes.map((index) => tokenDocs[index].ref.delete().catch(() => undefined))
  );

  emitOperationalEvent({
    event: "push.delivery",
    subsystem: "push",
    outcome: response.failureCount > 0 ? "not_delivered" : "delivered",
    severity: response.failureCount > 0 ? "WARNING" : "INFO",
    code: response.failureCount > 0 ? "PUSH_DELIVERY_FAILURES" : "PUSH_DELIVERY_COMPLETED",
    uid,
    details: {
      attempted: tokenDocs.length,
      delivered: response.successCount,
      failed: response.failureCount,
      removedInvalid: invalidIndexes.length,
    },
  });

  return {
    attempted: tokenDocs.length,
    delivered: response.successCount,
    removedInvalid: invalidIndexes.length,
  };
}

exports.registerPushToken = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    await assertActiveAccount(uid);
    const token = normalizeToken(request.data?.token);
    const tokenId = tokenDocumentId(token);

    await db
      .collection("users")
      .doc(uid)
      .collection("pushTokens")
      .doc(tokenId)
      .set({
        token,
        platform: "android",
        enabled: true,
        updatedAt: FieldValue.serverTimestamp(),
        createdAt: FieldValue.serverTimestamp(),
      }, { merge: true });

    return { registered: true };
  }
);

exports.unregisterPushToken = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const token = normalizeToken(request.data?.token);
    const tokenId = tokenDocumentId(token);

    await db
      .collection("users")
      .doc(uid)
      .collection("pushTokens")
      .doc(tokenId)
      .delete();

    return { registered: false };
  }
);

exports.sendTestPush = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const delivery = await sendPushToUser(uid, {
      title: "ClickAndSaveAI מחובר להתראות",
      body: "ההתראות פעילות. כשנמצא חיסכון מאומת, נציג לך כמה אפשר לחסוך.",
      data: { type: "PUSH_TEST" },
    });
    if (delivery.attempted === 0) {
      throw new HttpsError(
        "failed-precondition",
        "No registered notification device was found for this user."
      );
    }
    return delivery;
  }
);

Object.defineProperty(module.exports, "_sendPushToUser", {
  value: sendPushToUser,
  enumerable: false,
});
