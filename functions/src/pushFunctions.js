"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");

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

exports.registerPushToken = onCall(
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
