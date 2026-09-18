"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const stableScan = require("./gmailScanV5Functions");
const stableScanHandler = stableScan.scanGmailInvoices;
const gmailWatch = require("./gmailWatchFunctions");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const { normalizeHistoryId, syncMode } = require("./gmailHistoryPolicy");
const { emitOperationalEvent } = require("./operationalTelemetry");
const { _runFinancialAgentForUser: runFinancialAgentForUser } = require("./financialAgentFunctions");

const db = getFirestore();
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");
const MAX_AUTHORITATIVE_INVOICES = 500;
const DISCONNECT_STATES = new Set(["DISCONNECTING", "RETRY_REQUIRED"]);
const RECOVERY_MAX_LOOKBACK_MS = 30 * 24 * 60 * 60 * 1000;
const RECOVERY_OVERLAP_MS = 5 * 60 * 1000;
const RECOVERY_LEASE_TTL_MS = 10 * 60 * 1000;
const RECOVERY_PAGE_SIZE = 100;
const RECOVERY_MAX_PAGES = 20;

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

function timestampMillis(value) {
  if (!value) return 0;
  if (typeof value.toMillis === "function") return Number(value.toMillis()) || 0;
  const seconds = Number(value.seconds ?? value._seconds ?? 0);
  if (Number.isFinite(seconds) && seconds > 0) return seconds * 1000;
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : 0;
}

function recoveryWindowStartMs(connection, nowMs = Date.now()) {
  const checkpoints = [
    connection.lastSuccessfulProcessingAt,
    connection.lastReconciliationAt,
    connection.initialBackfillCompletedAt,
    connection.lastScanAt,
  ].map(timestampMillis).filter((value) => value > 0);
  if (checkpoints.length === 0) {
    throw new HttpsError(
      "failed-precondition",
      "A bounded Gmail History recovery window cannot be proven from stored checkpoints."
    );
  }
  const latestSuccessfulMs = Math.max(...checkpoints);
  const startMs = Math.max(0, latestSuccessfulMs - RECOVERY_OVERLAP_MS);
  if (nowMs - startMs > RECOVERY_MAX_LOOKBACK_MS) {
    throw new HttpsError(
      "failed-precondition",
      "The Gmail History recovery gap exceeds the bounded automatic recovery window."
    );
  }
  return startMs;
}

async function acquireRecoveryLease(connectionRef) {
  const owner = crypto.randomUUID();
  const nowMs = Date.now();
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(connectionRef);
    const data = snapshot.data() || {};
    const activeUntil = Number(data.incrementalLeaseUntilMs || 0);
    const activeOwner = String(data.incrementalLeaseOwner || "");
    if (activeUntil > nowMs && activeOwner) {
      throw new HttpsError("aborted", "Gmail mailbox reconciliation is already in progress.");
    }
    transaction.set(connectionRef, {
      incrementalLeaseOwner: owner,
      incrementalLeaseUntilMs: nowMs + RECOVERY_LEASE_TTL_MS,
      recoveryAttemptStartedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
  return owner;
}

async function releaseRecoveryLease(connectionRef, owner) {
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(connectionRef);
    const data = snapshot.data() || {};
    if (String(data.incrementalLeaseOwner || "") !== owner) return;
    transaction.set(connectionRef, {
      incrementalLeaseOwner: FieldValue.delete(),
      incrementalLeaseUntilMs: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
}

async function listRecoveryMessageIds(accessToken) {
  const messageIds = [];
  let pageToken = "";
  let pageCount = 0;
  do {
    const params = new URLSearchParams({
      maxResults: String(RECOVERY_PAGE_SIZE),
    });
    if (pageToken) params.set("pageToken", pageToken);
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages?${params.toString()}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (!response.ok) {
      throw new HttpsError("unavailable", "Gmail recovery messages could not be listed.");
    }
    const payload = await response.json().catch(() => ({}));
    for (const item of Array.isArray(payload.messages) ? payload.messages : []) {
      const id = String(item?.id || "").trim();
      if (id) messageIds.push(id);
    }
    pageToken = String(payload.nextPageToken || "");
    pageCount += 1;
    if (pageToken && pageCount >= RECOVERY_MAX_PAGES) {
      throw new HttpsError(
        "resource-exhausted",
        "The Gmail History recovery window is too large for one bounded recovery pass."
      );
    }
  } while (pageToken);
  return messageIds;
}

async function runBoundedHistoryRecovery(uid, connection, nowMs = Date.now()) {
  if (!connection.encryptedRefreshToken) {
    throw new HttpsError("failed-precondition", "No Gmail refresh token is stored.");
  }
  const startMs = recoveryWindowStartMs(connection, nowMs);
  const accessToken = await gmailWatch._refreshAccessToken(connection.encryptedRefreshToken);
  const messageIds = await listRecoveryMessageIds(accessToken);
  let processedMessages = 0;

  for (const messageId of messageIds) {
    await gmailWatch._processMessage(uid, accessToken, messageId, {
      maintenance: true,
      suppressUserNotification: true,
      notificationSuppressedReason: "HISTORY_RECOVERY",
    });
    const audit = await db.collection("users").doc(uid)
      .collection("gmailMessageImports").doc(messageId).get();
    const auditData = audit.data() || {};
    if (auditData.pdfAnalysisComplete !== true) {
      throw new HttpsError(
        "unavailable",
        "Gmail recovery paused because one or more PDF attachments require retry."
      );
    }
    processedMessages += 1;
  }

  await runFinancialAgentForUser(uid);

  return {
    processedMessages,
    recoveryWindowStartMs: startMs,
    scannedMailboxMessages: messageIds.length,
    agentRefreshed: true,
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
    let stage = "LOAD_CONNECTION";
    let recoveryConnectionRef = null;
    let recoveryLeaseOwner = "";
    try {
      const connectionRef = db.collection("gmailConnections").doc(uid);
      const beforeSnapshot = await connectionRef.get();
      if (!beforeSnapshot.exists) {
        throw new HttpsError("failed-precondition", "Gmail is not connected.");
      }
      const before = beforeSnapshot.data() || {};
      if (DISCONNECT_STATES.has(String(before.disconnectState || ""))) {
        throw new HttpsError(
          "failed-precondition",
          "Gmail ingestion is disabled while provider disconnect cleanup is pending."
        );
      }

      stage = "RESOLVE_SYNC_MODE";
      const mode = syncMode(before, ACTIVE_GMAIL_PARSER_VERSION);

      if (mode === "INCREMENTAL") {
        stage = "LOAD_AUTHORITATIVE_SNAPSHOT";
        const snapshot = await authoritativeInvoiceSnapshot(uid, mode, before);
        stage = "EMIT_INCREMENTAL_TELEMETRY";
        emitOperationalEvent({
          event: "gmail.reconciliation.scan",
          subsystem: "gmail",
          outcome: snapshot.authoritativeSnapshotTruncated ? "degraded" : "success",
          severity: snapshot.authoritativeSnapshotTruncated ? "WARNING" : "INFO",
          code: snapshot.authoritativeSnapshotTruncated
            ? "GMAIL_RECONCILIATION_SNAPSHOT_TRUNCATED"
            : "GMAIL_RECONCILIATION_CURRENT",
          uid,
          details: {
            syncMode: mode,
            authoritativeSnapshotTruncated: snapshot.authoritativeSnapshotTruncated,
            historyRecoveryRequired: snapshot.historyRecoveryRequired,
          },
        });
        return snapshot;
      }

      let baseline = normalizeHistoryId(before.watchHistoryId);
      let result;
      if (mode === "INITIAL_BACKFILL") {
        stage = "ESTABLISH_INITIAL_BASELINE";
        baseline = await establishInitialBaseline(request, connectionRef, before);
        stage = "RUN_STABLE_SCAN";
        result = await handlerRunner(stableScanHandler, "Stable Gmail scan")(request);
      } else if (mode === "RECOVERY_REQUIRED") {
        stage = "ACQUIRE_RECOVERY_LEASE";
        recoveryConnectionRef = connectionRef;
        recoveryLeaseOwner = await acquireRecoveryLease(connectionRef);
        stage = "ESTABLISH_RECOVERY_BASELINE";
        baseline = await establishRecoveryBaseline(request, connectionRef, before);
        stage = "RUN_BOUNDED_HISTORY_RECOVERY";
        result = await runBoundedHistoryRecovery(uid, before);
      } else {
        stage = "RUN_STABLE_SCAN";
        result = await handlerRunner(stableScanHandler, "Stable Gmail scan")(request);
      }

      stage = "RELOAD_CONNECTION";
      const afterSnapshot = await connectionRef.get();
      const after = afterSnapshot.data() || before;
      const update = {
        parserVersion: ACTIVE_GMAIL_PARSER_VERSION,
        lastSuccessfulProcessingAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      };

      stage = "BUILD_RECONCILIATION_UPDATE";
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

      stage = "PERSIST_RECONCILIATION_UPDATE";
      await connectionRef.set(update, { merge: true });

      const historyRecoveryRequired = update.historyRecoveryRequired === true;
      stage = "EMIT_COMPLETION_TELEMETRY";
      emitOperationalEvent({
        event: "gmail.reconciliation.scan",
        subsystem: "gmail",
        outcome: historyRecoveryRequired ? "degraded" : "success",
        severity: historyRecoveryRequired ? "WARNING" : "INFO",
        code: historyRecoveryRequired
          ? "GMAIL_RECONCILIATION_RECOVERY_REQUIRED"
          : "GMAIL_RECONCILIATION_COMPLETED",
        uid,
        details: {
          syncMode: mode,
          historyRecoveryRequired,
          parserVersion: ACTIVE_GMAIL_PARSER_VERSION,
        },
      });

      return {
        ...result,
        initialBackfillCompleted: true,
        historyRecoveryRequired,
        syncMode: mode,
      };
    } catch (error) {
      try {
        emitOperationalEvent({
          event: "gmail.reconciliation.scan",
          subsystem: "gmail",
          outcome: "failure",
          severity: "ERROR",
          code: "GMAIL_RECONCILIATION_FAILED",
          uid,
          details: {
            stage,
            errorName: error instanceof Error ? error.name : typeof error,
            errorCode: error?.code || "UNKNOWN",
          },
        });
      } catch {
        // Diagnostic telemetry must never replace the original reconciliation failure.
      }
      if (error instanceof HttpsError) throw error;
      throw new HttpsError("internal", `GMAIL_RECONCILIATION_INTERNAL_${stage}`);
    } finally {
      if (recoveryConnectionRef && recoveryLeaseOwner) {
        await releaseRecoveryLease(recoveryConnectionRef, recoveryLeaseOwner).catch(() => undefined);
      }
    }
  }
);

Object.defineProperties(module.exports, {
  _authoritativeInvoiceSnapshot: { value: authoritativeInvoiceSnapshot, enumerable: false },
  _handlerRunner: { value: handlerRunner, enumerable: false },
  _stableScanHandler: { value: stableScanHandler, enumerable: false },
  _recoveryWindowStartMs: { value: recoveryWindowStartMs, enumerable: false },
  _listRecoveryMessageIds: { value: listRecoveryMessageIds, enumerable: false },
  _runBoundedHistoryRecovery: { value: runBoundedHistoryRecovery, enumerable: false },
});
