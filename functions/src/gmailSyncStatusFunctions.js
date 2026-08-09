"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");

const db = getFirestore();
const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const INITIAL_GMAIL_LOOKBACK = "6m";

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function buildGmailSyncStatus(connection) {
  const data = connection && typeof connection === "object" ? connection : {};
  const connected = Array.isArray(data.scopes) &&
    data.scopes.includes(GMAIL_READONLY_SCOPE) &&
    Boolean(data.encryptedRefreshToken);
  const storedParserVersion = Math.max(0, Number(data.parserVersion || 0));
  return {
    connected,
    storedParserVersion,
    activeParserVersion: ACTIVE_GMAIL_PARSER_VERSION,
    upgradeRequired: connected && storedParserVersion < ACTIVE_GMAIL_PARSER_VERSION,
    lookback: INITIAL_GMAIL_LOOKBACK,
  };
}

exports.getGmailSyncStatus = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const snapshot = await db.collection("gmailConnections").doc(uid).get();
    return buildGmailSyncStatus(snapshot.exists ? snapshot.data() : null);
  }
);

exports._buildGmailSyncStatus = buildGmailSyncStatus;
