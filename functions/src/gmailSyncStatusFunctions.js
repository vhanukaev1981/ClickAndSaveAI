"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const { normalizeHistoryId, syncMode } = require("./gmailHistoryPolicy");

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
  const mode = connected ? syncMode(data, ACTIVE_GMAIL_PARSER_VERSION) : "DISCONNECTED";
  const initialBackfillCompleted = data.initialBackfillCompleted === true;

  return {
    connected,
    storedParserVersion,
    activeParserVersion: ACTIVE_GMAIL_PARSER_VERSION,
    // The Android client already uses this flag as its one-time scan gate. Keep legacy
    // parser-v6 connections without the new completion marker on the migration path once.
    upgradeRequired: connected && (
      storedParserVersion < ACTIVE_GMAIL_PARSER_VERSION || !initialBackfillCompleted
    ),
    lookback: INITIAL_GMAIL_LOOKBACK,
    initialBackfillCompleted,
    initialBackfillCompletedAt: data.initialBackfillCompletedAt || null,
    initialBackfillHistoryBaseline: normalizeHistoryId(data.initialBackfillHistoryBaseline),
    incrementalCheckpointHistoryId: normalizeHistoryId(data.watchHistoryId),
    pendingHistoryId: normalizeHistoryId(data.pendingHistoryId),
    historyRecoveryRequired: data.historyRecoveryRequired === true,
    historyRecoveryReason: data.historyRecoveryRequired === true
      ? String(data.historyRecoveryReason || "RECOVERY_REQUIRED").slice(0, 80)
      : "",
    lastIncrementalScanAt: data.lastIncrementalScanAt || null,
    lastReconciliationAt: data.lastReconciliationAt || null,
    syncMode: mode,
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
