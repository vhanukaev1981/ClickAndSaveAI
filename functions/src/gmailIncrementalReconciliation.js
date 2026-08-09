"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { decryptToken } = require("./tokenCrypto");
const { gmailPushNotification } = require("./gmailWatchFunctions");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const INITIAL_GMAIL_LOOKBACK = "6m";
const MAX_CONNECTIONS_PER_SWEEP = 250;
const MAX_CONCURRENCY = 5;

function normalizeHistoryId(value) {
  const normalized = String(value || "").trim();
  return /^\d+$/.test(normalized) ? normalized : "";
}

function compareHistoryIds(left, right) {
  const a = normalizeHistoryId(left);
  const b = normalizeHistoryId(right);
  if (!a || !b) return null;
  const aBig = BigInt(a);
  const bBig = BigInt(b);
  return aBig === bBig ? 0 : (aBig > bBig ? 1 : -1);
}

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

async function historyCheckpointIsReadable(accessToken, startHistoryId) {
  const params = new URLSearchParams({
    startHistoryId: String(startHistoryId),
    historyTypes: "messageAdded",
    maxResults: "1",
  });
  const response = await fetch(
    `https://gmail.googleapis.com/gmail/v1/users/me/history?${params.toString()}`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (response.status === 404) return false;
  if (!response.ok) {
    throw new Error(`Gmail history.list preflight failed with ${response.status}`);
  }
  return true;
}

function syntheticPubSubEvent(emailAddress, historyId) {
  const payload = Buffer.from(JSON.stringify({ emailAddress, historyId }), "utf8").toString("base64");
  return {
    data: {
      message: {
        data: payload,
      },
    },
  };
}

async function invokeExistingIncrementalHandler(emailAddress, historyId) {
  const handler = typeof gmailPushNotification.run === "function"
    ? gmailPushNotification.run.bind(gmailPushNotification)
    : gmailPushNotification;
  if (typeof handler !== "function") {
    throw new Error("Gmail Pub/Sub handler is unavailable for reconciliation reuse.");
  }
  await handler(syntheticPubSubEvent(emailAddress, historyId));
}

function backfillCompletionUpdate(data) {
  if (data.initialBackfillCompleted === true) return null;
  if (!data.lastScanAt || String(data.initialBackfillLookback || "") !== INITIAL_GMAIL_LOOKBACK) {
    return null;
  }
  return {
    initialBackfillCompleted: true,
    initialBackfillCompletedAt: data.lastScanAt,
    updatedAt: FieldValue.serverTimestamp(),
  };
}

async function markBackfillCompleteIfObserved(doc) {
  const data = doc.data() || {};
  const update = backfillCompletionUpdate(data);
  if (!update) return data.initialBackfillCompleted === true;
  await doc.ref.set(update, { merge: true });
  return true;
}

async function reconcileConnection(doc) {
  const data = doc.data() || {};
  if (data.watchEnabled !== true || !data.encryptedRefreshToken) {
    return { status: "SKIPPED_NOT_WATCHING" };
  }

  const initialBackfillCompleted = await markBackfillCompleteIfObserved(doc);
  if (!initialBackfillCompleted) {
    return { status: "SKIPPED_BACKFILL_INCOMPLETE" };
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

  if (data.historyRecoveryRequired === true) {
    await doc.ref.set({
      lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { status: "RECOVERY_PENDING" };
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

  const readable = await historyCheckpointIsReadable(accessToken, startHistoryId);
  if (!readable) {
    await doc.ref.set({
      pendingHistoryId: currentHistoryId,
      historyRecoveryRequired: true,
      historyRecoveryReason: "HISTORY_ID_EXPIRED",
      historyRecoveryDetectedAt: FieldValue.serverTimestamp(),
      lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { status: "RECOVERY_REQUIRED" };
  }

  const emailAddress = String(data.email || "").trim().toLowerCase();
  if (!emailAddress) {
    throw new Error("Gmail connection is missing normalized email address.");
  }

  await invokeExistingIncrementalHandler(emailAddress, currentHistoryId);
  await doc.ref.set({
    lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
    lastReconciliationAt: FieldValue.serverTimestamp(),
    lastReconciliationHistoryId: currentHistoryId,
    parserVersion: ACTIVE_GMAIL_PARSER_VERSION,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  return { status: "RECONCILED" };
}

async function runWithConcurrency(items, concurrency, worker) {
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
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

exports.onGmailConnectionCheckpointChanged = onDocumentUpdated(
  {
    document: "gmailConnections/{uid}",
    region: "europe-west1",
  },
  async (event) => {
    const before = event.data?.before?.data() || {};
    const after = event.data?.after?.data() || {};
    const ref = event.data?.after?.ref;
    if (!ref) return;

    const update = {};
    const completion = backfillCompletionUpdate(after);
    if (completion) Object.assign(update, completion);

    const sameMailbox = String(before.email || "").trim().toLowerCase() ===
      String(after.email || "").trim().toLowerCase();
    const beforeHistoryId = normalizeHistoryId(before.watchHistoryId);
    const afterHistoryId = normalizeHistoryId(after.watchHistoryId);
    const ordering = sameMailbox && beforeHistoryId && afterHistoryId
      ? compareHistoryIds(afterHistoryId, beforeHistoryId)
      : null;

    if (ordering !== null && ordering < 0) {
      update.watchHistoryId = beforeHistoryId;
      update.historyCheckpointProtectedAt = FieldValue.serverTimestamp();
    }

    const recoveryAdvancedCheckpoint =
      sameMailbox &&
      after.historyRecoveryRequired === true &&
      beforeHistoryId &&
      afterHistoryId &&
      compareHistoryIds(afterHistoryId, beforeHistoryId) === 1 &&
      normalizeHistoryId(after.pendingHistoryId) === afterHistoryId;

    if (recoveryAdvancedCheckpoint) {
      update.watchHistoryId = beforeHistoryId;
      update.historyCheckpointProtectedAt = FieldValue.serverTimestamp();
      update.historyRecoveryReason = String(after.historyRecoveryReason || "HISTORY_ID_EXPIRED");
    }

    if (Object.keys(update).length > 0) {
      update.updatedAt = FieldValue.serverTimestamp();
      await ref.set(update, { merge: true });
    }
  }
);

Object.defineProperties(module.exports, {
  _normalizeHistoryId: { value: normalizeHistoryId, enumerable: false },
  _compareHistoryIds: { value: compareHistoryIds, enumerable: false },
  _backfillCompletionUpdate: { value: backfillCompletionUpdate, enumerable: false },
  _syntheticPubSubEvent: { value: syntheticPubSubEvent, enumerable: false },
});
