"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");

const db = getFirestore();
const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

async function loadConnection(uid) {
  const snapshot = await db.collection("gmailConnections").doc(uid).get();
  return snapshot.exists ? (snapshot.data() || {}) : null;
}

async function executeGetGmailConnectionStatus(request, overrides = {}) {
  const uid = requireAuth(request);
  const deps = { loadConnection, ...overrides };
  let data;
  try {
    data = await deps.loadConnection(uid);
  } catch {
    throw new HttpsError("internal", "GMAIL_CONNECTION_STATUS_INTERNAL_LOAD_CONNECTION");
  }

  if (!data) return { connected: false, email: "", consentVersion: "" };

  const connected = Array.isArray(data.scopes) &&
    data.scopes.includes(GMAIL_READONLY_SCOPE) &&
    Boolean(data.encryptedRefreshToken);
  return {
    connected,
    email: connected ? String(data.email || request.auth?.token?.email || "") : "",
    consentVersion: connected ? String(data.consentVersion || "") : "",
  };
}

exports.getGmailConnectionStatus = onCall(
  { enforceAppCheck: true },
  executeGetGmailConnectionStatus
);

Object.defineProperty(module.exports, "_executeGetGmailConnectionStatus", {
  value: executeGetGmailConnectionStatus,
  enumerable: false,
});
