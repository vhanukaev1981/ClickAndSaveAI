"use strict";

const crypto = require("node:crypto");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError } = require("firebase-functions/v2/https");

const db = getFirestore();
const BLOCKED_ACCOUNT_LIFECYCLE_STATES = new Set([
  "DELETING",
  "DELETE_RETRY_REQUIRED",
]);

function lifecycleDocumentId(uid) {
  return crypto.createHash("sha256").update(String(uid)).digest("hex");
}

function lifecycleRef(uid) {
  return db.collection("accountLifecycle").doc(lifecycleDocumentId(uid));
}

function authErrorCode(error) {
  return String(error?.code || error?.errorInfo?.code || "");
}

async function assertActiveAccount(uid) {
  const normalizedUid = String(uid || "").trim();
  if (!normalizedUid) {
    throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  }

  const lifecycle = await lifecycleRef(normalizedUid).get();
  const lifecycleState = String(lifecycle.data()?.state || "").toUpperCase();
  if (BLOCKED_ACCOUNT_LIFECYCLE_STATES.has(lifecycleState)) {
    throw new HttpsError(
      "permission-denied",
      lifecycleState === "DELETE_RETRY_REQUIRED"
        ? "This account deletion requires retry before account writes can resume."
        : "This account is being deleted."
    );
  }

  try {
    const user = await getAuth().getUser(normalizedUid);
    if (user.disabled === true) {
      throw new HttpsError("permission-denied", "This account is disabled.");
    }
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    if (authErrorCode(error) === "auth/user-not-found") {
      throw new HttpsError("permission-denied", "This account no longer exists.");
    }
    throw error;
  }
  return normalizedUid;
}

module.exports = {
  assertActiveAccount,
  lifecycleDocumentId,
  lifecycleRef,
  BLOCKED_ACCOUNT_LIFECYCLE_STATES,
};
