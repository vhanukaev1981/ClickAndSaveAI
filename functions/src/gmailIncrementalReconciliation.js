"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { decryptToken } = require("./tokenCrypto");
const { _processMailboxNotification: processMailboxNotification } = require("./gmailWatchFunctions");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const {
  compareHistoryIds,
  normalizeHistoryId,
  syncMode,
} = require("./gmailHistoryPolicy");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const MAX_CONNECTIONS_PER_SWEEP = 250;
const MAX_CONCURRENCY = 5;

async function refreshAccessToken(encryptedRefreshToken) {
  const refreshToken = decryptToken(encryptedRefreshToken, oauthTokenEncryptionKey.value());
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: googleOAuthClientId.value(),
      client_secret: googleOAuthClientSecret.value(),
      refresh_token: refreshToken,
      grant_type: "refresh_token",
    }),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || !payload.access_token) {
    throw new Error(`Google token refresh failed with ${response.status}`);
  }
  return String(payload.access_token);
}

async function getCurrentMailboxHistoryId(accessToken) {
  const response = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/profile", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const payload = await response.json().catch(() => ({}));
  const historyId = normalizeHistoryId(payload.historyId);
  if (!response.ok || !historyId) {
    throw new Error(`Gmail users.getProfile failed with ${response.status}`);
  }
  return historyId;
}

async function reconcileConnection(doc) {
  const data = doc.data() || {};
  if (data.watchEnabled !== true || !data.encryptedRefreshToken) {
    return { status: "SKIPPED_NOT_WATCHING" };
  }

  const mode = syncMode(data, ACTIVE_GMAIL_PARSER_VERSION);
  if (mode === "INITIAL_BACKFILL") {
    return { status: "SKIPPED_BACKFILL_INCOMPLETE" };
  }
  if (mode === "PARSER_UPGRADE_BACKFILL") {
    return { status: "SKIPPED_PARSER_UPGRADE_REQUIRED" };
  }
  if (mode === "RECOVERY_REQUIRED") {
    await doc.ref.set({
      lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { status: "RECOVERY_PENDING" };
  }

  const startHistoryId = normalizeHistoryId(data.watchHistoryId);
  if (!startHistoryId) {
    await doc.ref.set({
      historyRecoveryRequired: true,
      historyRecoveryReason: "MISSING_HISTORY_BASELINE",
      lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { status: "RECOVERY_REQUIRED" };
  }

  const accessToken = await refreshAccessToken(data.encryptedRefreshToken);
  const currentHistoryId = await getCurrentMailboxHistoryId(accessToken);
  const ordering = compareHistoryIds(currentHistoryId, startHistoryId);

  if (ordering !== null && ordering <= 0) {
    await doc.ref.set({
      lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
      lastReconciliationAt: FieldValue.serverTimestamp(),
      lastReconciliationHistoryId: startHistoryId,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { status: "CURRENT" };
  }

  const result = await processMailboxNotification(doc, currentHistoryId);
  await doc.ref.set({
    lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
    lastReconciliationAt: result?.status === "RECONCILED"
      ? FieldValue.serverTimestamp()
      : (data.lastReconciliationAt || FieldValue.delete()),
    lastReconciliationHistoryId: result?.checkpoint || startHistoryId,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  if (result?.status === "RECOVERY_REQUIRED" || result?.status === "RECOVERY_PENDING") {
    return { status: result.status };
  }
  return { status: "RECONCILED" };
}

async function runWithConcurrency(items, concurrency, worker) {
  let nextIndex = 0;
  const workerCount = Math.min(concurrency, items.length);
  const workers = Array.from({ length: workerCount }, async () => {
    while (true) {
      const index = nextIndex;
      nextIndex += 1;
      if (index >= items.length) return;
      await worker(items[index]);
    }
  });
  await Promise.all(workers);
}

exports.gmailIncrementalReconciliation = onSchedule(
  {
    schedule: "0 */4 * * *",
    timeZone: "Asia/Jerusalem",
    region: "europe-west1",
    timeoutSeconds: 540,
    memory: "1GiB",
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey, geminiApiKey],
  },
  async () => {
    const snapshot = await db.collection("gmailConnections")
      .where("watchEnabled", "==", true)
      .limit(MAX_CONNECTIONS_PER_SWEEP)
      .get();

    const stats = {
      candidates: snapshot.size,
      reconciled: 0,
      current: 0,
      recoveryRequired: 0,
      recoveryPending: 0,
      skippedBackfillIncomplete: 0,
      skippedParserUpgradeRequired: 0,
      skippedNotWatching: 0,
      failed: 0,
    };

    await runWithConcurrency(snapshot.docs, MAX_CONCURRENCY, async (doc) => {
      try {
        const result = await reconcileConnection(doc);
        switch (result.status) {
          case "RECONCILED": stats.reconciled += 1; break;
          case "CURRENT": stats.current += 1; break;
          case "RECOVERY_REQUIRED": stats.recoveryRequired += 1; break;
          case "RECOVERY_PENDING": stats.recoveryPending += 1; break;
          case "SKIPPED_BACKFILL_INCOMPLETE": stats.skippedBackfillIncomplete += 1; break;
          case "SKIPPED_PARSER_UPGRADE_REQUIRED": stats.skippedParserUpgradeRequired += 1; break;
          case "SKIPPED_NOT_WATCHING": stats.skippedNotWatching += 1; break;
          default: break;
        }
      } catch (error) {
        stats.failed += 1;
        logger.error("Gmail incremental reconciliation failed", {
          uid: doc.id,
          errorName: error instanceof Error ? error.name : typeof error,
          errorMessage: error instanceof Error ? error.message : String(error),
        });
      }
    });

    logger.info("Gmail incremental reconciliation completed", stats);
    if (stats.failed > 0) {
      throw new Error(`Failed to reconcile ${stats.failed} Gmail connection(s)`);
    }
  }
);

Object.defineProperties(module.exports, {
  _reconcileConnection: { value: reconcileConnection, enumerable: false },
  _getCurrentMailboxHistoryId: { value: getCurrentMailboxHistoryId, enumerable: false },
});
