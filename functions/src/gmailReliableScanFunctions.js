"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const stableScan = require("./gmailScanV5Functions");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const { normalizeHistoryId, syncMode } = require("./gmailHistoryPolicy");

const db = getFirestore();
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function stableScanRunner() {
  const handler = stableScan.scanGmailInvoices;
  const runner = typeof handler?.run === "function" ? handler.run.bind(handler) : handler;
  if (typeof runner !== "function") {
    throw new Error("Stable Gmail scan handler is unavailable.");
  }
  return runner;
}

function incrementalNoScanResult(mode, connection) {
  return {
    invoices: [],
    scannedMessages: 0,
    importedCount: 0,
    removedSourceMessageIds: [],
    scannedPages: 0,
    lookback: "incremental",
    parserVersion: ACTIVE_GMAIL_PARSER_VERSION,
    upgradedMessages: 0,
    agentRefreshed: false,
    initialBackfillCompleted: connection.initialBackfillCompleted === true,
    historyRecoveryRequired: connection.historyRecoveryRequired === true,
    syncMode: mode,
  };
}

exports.scanGmailInvoices = onCall(
  {
    enforceAppCheck: true,
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey, geminiApiKey],
    timeoutSeconds: 540,
    memory: "1GiB",
  },
  async (request) => {
    const uid = requireAuth(request);
    const connectionRef = db.collection("gmailConnections").doc(uid);
    const beforeSnapshot = await connectionRef.get();
    if (!beforeSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Gmail is not connected.");
    }
    const before = beforeSnapshot.data() || {};
    const mode = syncMode(before, ACTIVE_GMAIL_PARSER_VERSION);

    if (mode === "INCREMENTAL" || mode === "RECOVERY_REQUIRED") {
      return incrementalNoScanResult(mode, before);
    }

    const result = await stableScanRunner()(request);
    const afterSnapshot = await connectionRef.get();
    const after = afterSnapshot.data() || before;
    const checkpoint = normalizeHistoryId(after.watchHistoryId || before.watchHistoryId);

    const update = {
      parserVersion: ACTIVE_GMAIL_PARSER_VERSION,
      updatedAt: FieldValue.serverTimestamp(),
    };

    if (mode === "INITIAL_BACKFILL") {
      update.initialBackfillCompleted = true;
      update.initialBackfillCompletedAt = FieldValue.serverTimestamp();
      if (checkpoint) {
        update.initialBackfillHistoryBaseline = checkpoint;
        update.historyRecoveryRequired = false;
        update.historyRecoveryReason = FieldValue.delete();
      } else {
        update.historyRecoveryRequired = true;
        update.historyRecoveryReason = "MISSING_HISTORY_BASELINE";
      }
    }

    await connectionRef.set(update, { merge: true });

    return {
      ...result,
      initialBackfillCompleted: mode === "INITIAL_BACKFILL"
        ? true
        : before.initialBackfillCompleted === true,
      historyRecoveryRequired: mode === "INITIAL_BACKFILL" && !checkpoint,
      syncMode: mode,
    };
  }
);

Object.defineProperties(module.exports, {
  _incrementalNoScanResult: { value: incrementalNoScanResult, enumerable: false },
  _stableScanRunner: { value: stableScanRunner, enumerable: false },
});
