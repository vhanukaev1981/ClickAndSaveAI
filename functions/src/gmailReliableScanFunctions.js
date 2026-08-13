"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const stableScan = require("./gmailScanV5Functions");
const stableScanHandler = stableScan.scanGmailInvoices;
const gmailWatch = require("./gmailWatchFunctions");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const { normalizeHistoryId, syncMode } = require("./gmailHistoryPolicy");

const db = getFirestore();
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");
const MAX_AUTHORITATIVE_INVOICES = 500;

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function handlerRunner(handler, name) {
  const runner = typeof handler?.run === "function" ? handler.run.bind(handler) : handler;
  if (typeof runner !== "function") throw new Error(`${name} handler is unavailable.`);
  return runner;
}

async function authoritativeInvoiceSnapshot(uid, mode, connection) {
  const snapshot = await db.collection("users").doc(uid).collection("gmailInvoices")
    .limit(MAX_AUTHORITATIVE_INVOICES)
    .get();
  return {
    invoices: snapshot.docs.map((doc) => doc.data()),
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
    authoritativeSnapshotTruncated: snapshot.size >= MAX_AUTHORITATIVE_INVOICES,
    syncMode: mode,
  };
}

async function establishInitialBaseline(request, connectionRef, before) {
  const existing = normalizeHistoryId(before.watchHistoryId);
  if (existing) return existing;
  const result = await handlerRunner(gmailWatch.startGmailWatch, "Gmail watch")(request);
  return normalizeHistoryId(result?.historyId);
}

async function establishRecoveryBaseline(request, connectionRef, before) {
  const processedCheckpoint = normalizeHistoryId(before.watchHistoryId);
  const result = await handlerRunner(gmailWatch.startGmailWatch, "Gmail watch")(request);
  const recoveryBaseline = normalizeHistoryId(result?.historyId);
  if (!recoveryBaseline) {
    throw new HttpsError("unavailable", "A fresh Gmail History recovery baseline is unavailable.");
  }

  const update = {
    recoveryBaselineHistoryId: recoveryBaseline,
    historyRecoveryRequired: true,
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (processedCheckpoint) update.watchHistoryId = processedCheckpoint;
  await connectionRef.set(update, { merge: true });
  return recoveryBaseline;
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

    if (mode === "INCREMENTAL") {
      return authoritativeInvoiceSnapshot(uid, mode, before);
    }

    let baseline = normalizeHistoryId(before.watchHistoryId);
    if (mode === "INITIAL_BACKFILL") {
      baseline = await establishInitialBaseline(request, connectionRef, before);
    } else if (mode === "RECOVERY_REQUIRED") {
      baseline = await establishRecoveryBaseline(request, connectionRef, before);
    }

    const result = await handlerRunner(stableScanHandler, "Stable Gmail scan")(request);
    const afterSnapshot = await connectionRef.get();
    const after = afterSnapshot.data() || before;
    const update = {
      parserVersion: ACTIVE_GMAIL_PARSER_VERSION,
      lastSuccessfulProcessingAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    };

    if (mode === "INITIAL_BACKFILL") {
      update.initialBackfillCompleted = true;
      update.initialBackfillCompletedAt = FieldValue.serverTimestamp();
      if (baseline) {
        update.initialBackfillHistoryBaseline = baseline;
        update.watchHistoryId = baseline;
        update.historyRecoveryRequired = false;
        update.historyRecoveryReason = FieldValue.delete();
      } else {
        update.historyRecoveryRequired = true;
        update.historyRecoveryReason = "MISSING_HISTORY_BASELINE";
      }
    } else if (mode === "RECOVERY_REQUIRED") {
      if (!baseline) {
        throw new HttpsError("unavailable", "Gmail History recovery baseline is unavailable.");
      }
      update.watchHistoryId = baseline;
      update.initialBackfillCompleted = true;
      update.historyRecoveryRequired = false;
      update.historyRecoveryReason = FieldValue.delete();
      update.recoveryBaselineHistoryId = FieldValue.delete();
      update.lastHistoryRecoveryAt = FieldValue.serverTimestamp();
    } else if (mode === "PARSER_UPGRADE_BACKFILL") {
      update.initialBackfillCompleted = true;
      if (normalizeHistoryId(after.watchHistoryId || baseline)) {
        update.historyRecoveryRequired = false;
        update.historyRecoveryReason = FieldValue.delete();
      }
    }

    await connectionRef.set(update, { merge: true });

    return {
      ...result,
      initialBackfillCompleted: true,
      historyRecoveryRequired: update.historyRecoveryRequired === true,
      syncMode: mode,
    };
  }
);

Object.defineProperties(module.exports, {
  _authoritativeInvoiceSnapshot: { value: authoritativeInvoiceSnapshot, enumerable: false },
  _handlerRunner: { value: handlerRunner, enumerable: false },
  _stableScanHandler: { value: stableScanHandler, enumerable: false },
});
