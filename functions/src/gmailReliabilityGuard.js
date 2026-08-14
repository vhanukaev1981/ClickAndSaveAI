"use strict";

const { getAuth } = require("firebase-admin/auth");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const reconciliation = require("./gmailIncrementalReconciliation");
const reliableScan = require("./gmailReliableScanFunctions");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const { syncMode } = require("./gmailHistoryPolicy");

const db = getFirestore();
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");
const GMAIL_PUBSUB_TOPIC = "gmail-notifications";
const MAX_CONNECTIONS_PER_SWEEP = 250;
const MAX_CONCURRENCY = 5;

function decodePayload(event) {
  const encoded = event?.data?.message?.data || "";
  if (!encoded) return null;
  try {
    const payload = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
    const emailAddress = String(payload.emailAddress || "").trim().toLowerCase();
    const historyId = String(payload.historyId || "").trim();
    return emailAddress && historyId ? { emailAddress, historyId } : null;
  } catch {
    return null;
  }
}

function handlerRunner(handler, name) {
  const runner = typeof handler?.run === "function" ? handler.run.bind(handler) : handler;
  if (typeof runner !== "function") throw new Error(`${name} handler is unavailable.`);
  return runner;
}

function isPermanentAuthorizationFailure(error) {
  const message = error instanceof Error ? error.message : String(error || "");
  return /Google token refresh failed with (400|401)/.test(message) ||
    /Gmail .* failed with (401|403)/.test(message);
}

async function accountExists(uid) {
  try {
    await getAuth().getUser(String(uid));
    return true;
  } catch (error) {
    if (error?.code === "auth/user-not-found") return false;
    throw error;
  }
}

async function disableDeletedAccount(doc) {
  await doc.ref.set({
    watchEnabled: false,
    authorizationState: "ACCOUNT_DELETED",
    historyRecoveryRequired: false,
    pendingHistoryId: FieldValue.delete(),
    incrementalLeaseOwner: FieldValue.delete(),
    incrementalLeaseUntilMs: FieldValue.delete(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
}

async function transitionToReconnect(doc, reason) {
  await doc.ref.set({
    encryptedRefreshToken: FieldValue.delete(),
    watchEnabled: false,
    authorizationState: "RECONNECT_REQUIRED",
    watchFailureReason: reason,
    historyRecoveryRequired: true,
    historyRecoveryReason: reason,
    incrementalLeaseOwner: FieldValue.delete(),
    incrementalLeaseUntilMs: FieldValue.delete(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
}

async function markAccountConflict(docs) {
  await Promise.all(docs.map((doc) => doc.ref.set({
    watchEnabled: false,
    authorizationState: "RECONNECT_REQUIRED",
    historyRecoveryRequired: true,
    historyRecoveryReason: "AMBIGUOUS_MAILBOX_OWNER",
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true })));
}

async function activeConnectionsForEmail(emailAddress) {
  const matches = await db.collection("gmailConnections")
    .where("email", "==", emailAddress)
    .limit(10)
    .get();
  const active = [];
  for (const doc of matches.docs) {
    const data = doc.data() || {};
    if (data.watchEnabled !== true || !data.encryptedRefreshToken) continue;
    if (!await accountExists(doc.id)) {
      await disableDeletedAccount(doc);
      continue;
    }
    active.push(doc);
  }
  return active;
}

async function recoverConnection(doc) {
  const data = doc.data() || {};
  const request = {
    auth: {
      uid: doc.id,
      token: { email: String(data.email || "") },
    },
    data: {},
  };
  return handlerRunner(reliableScan.scanGmailInvoices, "Reliable Gmail scan")(request);
}

async function processGuardedEvent(event) {
  const payload = decodePayload(event);
  if (!payload) return { status: "IGNORED" };
  const activeDocs = await activeConnectionsForEmail(payload.emailAddress);
  if (activeDocs.length === 0) return { status: "NO_ACTIVE_CONNECTION" };
  if (activeDocs.length !== 1) {
    await markAccountConflict(activeDocs);
    logger.error("Gmail event suppressed because mailbox ownership is ambiguous", {
      activeConnectionCount: activeDocs.length,
    });
    return { status: "ACCOUNT_CONFLICT" };
  }

  const doc = activeDocs[0];
  try {
    let result = await reconciliation._processMailboxNotification(event);
    if (result.status === "RECOVERY_REQUIRED" || result.status === "RECOVERY_PENDING") {
      await recoverConnection(await doc.ref.get());
      const refreshed = await doc.ref.get();
      result = await reconciliation._reconcileOneConnection(refreshed);
    }
    return result;
  } catch (error) {
    if (isPermanentAuthorizationFailure(error)) {
      await transitionToReconnect(doc, "OAUTH_REVOKED_OR_INVALID");
      logger.warn("Gmail authorization requires reconnect", { uid: doc.id });
      return { status: "RECONNECT_REQUIRED" };
    }
    throw error;
  }
}

async function reconcileGuardedConnection(doc) {
  let current = doc;
  try {
    if (!await accountExists(current.id)) {
      await disableDeletedAccount(current);
      return { status: "ACCOUNT_DELETED" };
    }
    const mode = syncMode(current.data() || {}, ACTIVE_GMAIL_PARSER_VERSION);
    if (mode === "RECOVERY_REQUIRED") {
      await recoverConnection(current);
      current = await current.ref.get();
    }
    return await reconciliation._reconcileOneConnection(current);
  } catch (error) {
    if (isPermanentAuthorizationFailure(error)) {
      await transitionToReconnect(current, "OAUTH_REVOKED_OR_INVALID");
      return { status: "RECONNECT_REQUIRED" };
    }
    throw error;
  }
}

async function runWithConcurrency(items, concurrency, worker) {
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (true) {
      const index = nextIndex++;
      if (index >= items.length) return;
      await worker(items[index]);
    }
  });
  await Promise.all(workers);
}

exports.gmailPushNotification = onMessagePublished(
  {
    topic: GMAIL_PUBSUB_TOPIC,
    region: "europe-west1",
    retry: true,
    timeoutSeconds: 540,
    memory: "1GiB",
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey, geminiApiKey],
  },
  processGuardedEvent
);

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

    const byEmail = new Map();
    for (const doc of snapshot.docs) {
      const email = String(doc.data()?.email || "").trim().toLowerCase();
      if (!email) continue;
      const group = byEmail.get(email) || [];
      group.push(doc);
      byEmail.set(email, group);
    }

    const conflicts = [...byEmail.values()].filter((group) => group.length > 1);
    await Promise.all(conflicts.map(markAccountConflict));
    const candidates = [...byEmail.values()]
      .filter((group) => group.length === 1)
      .map((group) => group[0]);

    const stats = {
      candidates: candidates.length,
      reconciled: 0,
      current: 0,
      skipped: 0,
      reconnect: 0,
      deleted: 0,
      failed: 0,
    };
    await runWithConcurrency(candidates, MAX_CONCURRENCY, async (doc) => {
      try {
        const result = await reconcileGuardedConnection(doc);
        if (result.status === "RECONCILED") stats.reconciled += 1;
        else if (result.status === "CURRENT") stats.current += 1;
        else if (result.status === "RECONNECT_REQUIRED") stats.reconnect += 1;
        else if (result.status === "ACCOUNT_DELETED") stats.deleted += 1;
        else stats.skipped += 1;
      } catch (error) {
        stats.failed += 1;
        logger.error("Guarded Gmail reconciliation failed", {
          uid: doc.id,
          errorName: error instanceof Error ? error.name : typeof error,
        });
      }
    });
    logger.info("Guarded Gmail reconciliation completed", stats);
    if (stats.failed > 0) throw new Error(`Failed to reconcile ${stats.failed} Gmail mailbox(es)`);
  }
);

Object.defineProperties(module.exports, {
  _decodePayload: { value: decodePayload, enumerable: false },
  _isPermanentAuthorizationFailure: { value: isPermanentAuthorizationFailure, enumerable: false },
  _accountExists: { value: accountExists, enumerable: false },
  _processGuardedEvent: { value: processGuardedEvent, enumerable: false },
  _reconcileGuardedConnection: { value: reconcileGuardedConnection, enumerable: false },
});
