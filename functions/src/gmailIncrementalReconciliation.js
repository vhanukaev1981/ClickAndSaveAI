"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { decryptToken } = require("./tokenCrypto");
const gmailWatchFunctions = require("./gmailWatchFunctions");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const {
  compareHistoryIds,
  normalizeHistoryId,
  selectMonotonicCheckpoint,
  syncMode,
} = require("./gmailHistoryPolicy");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const GMAIL_PUBSUB_TOPIC = "gmail-notifications";
const MAX_CONNECTIONS_PER_SWEEP = 250;
const MAX_CONCURRENCY = 5;
const LEASE_TTL_MS = 10 * 60 * 1000;

function oldPushRunner() {
  const handler = gmailWatchFunctions.gmailPushNotification;
  const runner = typeof handler?.run === "function" ? handler.run.bind(handler) : handler;
  if (typeof runner !== "function") {
    throw new Error("Existing Gmail Pub/Sub handler is unavailable.");
  }
  return runner;
}

function decodePubSubPayload(event) {
  const encoded = event?.data?.message?.data || "";
  if (!encoded) return null;
  try {
    const payload = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
    const emailAddress = String(payload.emailAddress || "").trim().toLowerCase();
    const historyId = normalizeHistoryId(payload.historyId);
    if (!emailAddress || !historyId) return null;
    return { emailAddress, historyId };
  } catch {
    return null;
  }
}

function syntheticPubSubEvent(emailAddress, historyId) {
  const data = Buffer.from(JSON.stringify({ emailAddress, historyId }), "utf8").toString("base64");
  return { data: { message: { data } } };
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

async function acquireMailboxLease(connectionDocs, targetHistoryId) {
  const owner = crypto.randomUUID();
  const nowMs = Date.now();
  const leaseUntilMs = nowMs + LEASE_TTL_MS;

  const result = await db.runTransaction(async (transaction) => {
    const snapshots = [];
    for (const doc of connectionDocs) {
      snapshots.push(await transaction.get(doc.ref));
    }

    for (const snapshot of snapshots) {
      const data = snapshot.data() || {};
      const activeLeaseUntil = Number(data.incrementalLeaseUntilMs || 0);
      const activeOwner = String(data.incrementalLeaseOwner || "");
      if (activeLeaseUntil > nowMs && activeOwner && activeOwner !== owner) {
        return { acquired: false, owner, states: [] };
      }
    }

    const states = [];
    for (const snapshot of snapshots) {
      const data = snapshot.data() || {};
      const pendingHistoryId = selectMonotonicCheckpoint(
        data.pendingHistoryId,
        targetHistoryId
      );
      transaction.set(snapshot.ref, {
        incrementalLeaseOwner: owner,
        incrementalLeaseUntilMs: leaseUntilMs,
        pendingHistoryId,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      states.push({
        ref: snapshot.ref,
        data,
        checkpoint: normalizeHistoryId(data.watchHistoryId),
      });
    }
    return { acquired: true, owner, states };
  });

  return result;
}

async function releaseMailboxLease(states, owner) {
  if (!states.length) return;
  await db.runTransaction(async (transaction) => {
    const snapshots = [];
    for (const state of states) snapshots.push(await transaction.get(state.ref));
    for (const snapshot of snapshots) {
      const data = snapshot.data() || {};
      if (String(data.incrementalLeaseOwner || "") !== owner) continue;
      transaction.set(snapshot.ref, {
        incrementalLeaseOwner: FieldValue.delete(),
        incrementalLeaseUntilMs: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    }
  });
}

async function markRecovery(state, owner, targetHistoryId, reason) {
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(state.ref);
    const data = snapshot.data() || {};
    if (String(data.incrementalLeaseOwner || "") !== owner) return;
    const update = {
      pendingHistoryId: selectMonotonicCheckpoint(data.pendingHistoryId, targetHistoryId),
      historyRecoveryRequired: true,
      historyRecoveryReason: reason,
      historyRecoveryDetectedAt: FieldValue.serverTimestamp(),
      incrementalLeaseOwner: FieldValue.delete(),
      incrementalLeaseUntilMs: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    };
    if (state.checkpoint) update.watchHistoryId = state.checkpoint;
    transaction.set(state.ref, update, { merge: true });
  });
}

async function finalizeMailboxLease(states, owner, targetHistoryId) {
  let firstCheckpoint = "";
  let recoveryRequired = false;

  await db.runTransaction(async (transaction) => {
    const snapshots = [];
    for (const state of states) snapshots.push(await transaction.get(state.ref));

    for (let index = 0; index < snapshots.length; index += 1) {
      const snapshot = snapshots[index];
      const state = states[index];
      const data = snapshot.data() || {};
      if (String(data.incrementalLeaseOwner || "") !== owner) continue;

      const update = {
        incrementalLeaseOwner: FieldValue.delete(),
        incrementalLeaseUntilMs: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
      };

      if (data.historyRecoveryRequired === true) {
        recoveryRequired = true;
        if (state.checkpoint) update.watchHistoryId = state.checkpoint;
        update.pendingHistoryId = selectMonotonicCheckpoint(
          data.pendingHistoryId,
          targetHistoryId
        );
      } else {
        const checkpoint = selectMonotonicCheckpoint(
          selectMonotonicCheckpoint(state.checkpoint, data.watchHistoryId),
          targetHistoryId
        );
        if (checkpoint) {
          update.watchHistoryId = checkpoint;
          if (!firstCheckpoint) firstCheckpoint = checkpoint;
        }
        const pending = normalizeHistoryId(data.pendingHistoryId);
        const pendingOrdering = pending && checkpoint
          ? compareHistoryIds(pending, checkpoint)
          : null;
        update.pendingHistoryId = pendingOrdering !== null && pendingOrdering > 0
          ? pending
          : FieldValue.delete();
      }
      transaction.set(snapshot.ref, update, { merge: true });
    }
  });

  return {
    status: recoveryRequired ? "RECOVERY_REQUIRED" : "RECONCILED",
    checkpoint: firstCheckpoint || targetHistoryId,
  };
}

async function processMailboxNotification(event) {
  const payload = decodePubSubPayload(event);
  if (!payload) return { status: "IGNORED" };

  const matches = await db.collection("gmailConnections")
    .where("email", "==", payload.emailAddress)
    .limit(10)
    .get();
  if (matches.empty) return { status: "NO_CONNECTION" };

  const activeDocs = matches.docs.filter((doc) => {
    const data = doc.data() || {};
    return data.watchEnabled === true && Boolean(data.encryptedRefreshToken);
  });
  if (activeDocs.length === 0) return { status: "NO_ACTIVE_CONNECTION" };

  const lease = await acquireMailboxLease(activeDocs, payload.historyId);
  if (!lease.acquired) {
    throw new Error("Gmail mailbox reconciliation is already in progress.");
  }

  try {
    let needsProcessing = false;
    for (const state of lease.states) {
      const data = state.data;
      const mode = syncMode(data, ACTIVE_GMAIL_PARSER_VERSION);

      if (mode === "INITIAL_BACKFILL" || mode === "PARSER_UPGRADE_BACKFILL") {
        await releaseMailboxLease(lease.states, lease.owner);
        return { status: "BACKFILL_PENDING", checkpoint: state.checkpoint };
      }

      if (data.historyRecoveryRequired === true) {
        await markRecovery(state, lease.owner, payload.historyId, String(
          data.historyRecoveryReason || "RECOVERY_PENDING"
        ));
        await releaseMailboxLease(lease.states, lease.owner);
        return { status: "RECOVERY_PENDING", checkpoint: state.checkpoint };
      }

      if (!state.checkpoint) {
        await markRecovery(state, lease.owner, payload.historyId, "MISSING_HISTORY_BASELINE");
        await releaseMailboxLease(lease.states, lease.owner);
        return { status: "RECOVERY_REQUIRED", checkpoint: "" };
      }

      const ordering = compareHistoryIds(payload.historyId, state.checkpoint);
      if (ordering !== null && ordering <= 0) continue;

      const accessToken = await refreshAccessToken(data.encryptedRefreshToken);
      const readable = await historyCheckpointIsReadable(accessToken, state.checkpoint);
      if (!readable) {
        await markRecovery(state, lease.owner, payload.historyId, "HISTORY_ID_EXPIRED");
        await releaseMailboxLease(lease.states, lease.owner);
        logger.warn("Gmail History expired; checkpoint preserved for explicit recovery", {
          uid: state.ref.id,
        });
        return { status: "RECOVERY_REQUIRED", checkpoint: state.checkpoint };
      }
      needsProcessing = true;
    }

    if (!needsProcessing) {
      const finalized = await finalizeMailboxLease(lease.states, lease.owner, payload.historyId);
      return { ...finalized, status: "CURRENT" };
    }

    await oldPushRunner()(event);
    return await finalizeMailboxLease(lease.states, lease.owner, payload.historyId);
  } catch (error) {
    await releaseMailboxLease(lease.states, lease.owner).catch(() => undefined);
    throw error;
  }
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
  processMailboxNotification
);

async function reconcileOneConnection(doc) {
  const data = doc.data() || {};
  if (data.watchEnabled !== true || !data.encryptedRefreshToken) {
    return { status: "SKIPPED_NOT_WATCHING" };
  }

  const mode = syncMode(data, ACTIVE_GMAIL_PARSER_VERSION);
  if (mode === "INITIAL_BACKFILL") return { status: "SKIPPED_BACKFILL_INCOMPLETE" };
  if (mode === "PARSER_UPGRADE_BACKFILL") return { status: "SKIPPED_PARSER_UPGRADE_REQUIRED" };
  if (mode === "RECOVERY_REQUIRED") return { status: "RECOVERY_PENDING" };

  const email = String(data.email || "").trim().toLowerCase();
  if (!email) return { status: "SKIPPED_MISSING_EMAIL" };

  const accessToken = await refreshAccessToken(data.encryptedRefreshToken);
  const currentHistoryId = await getCurrentMailboxHistoryId(accessToken);
  const checkpoint = normalizeHistoryId(data.watchHistoryId);
  if (checkpoint && compareHistoryIds(currentHistoryId, checkpoint) <= 0) {
    await doc.ref.set({
      lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
      lastReconciliationAt: FieldValue.serverTimestamp(),
      lastReconciliationHistoryId: checkpoint,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { status: "CURRENT" };
  }

  const result = await processMailboxNotification(
    syntheticPubSubEvent(email, currentHistoryId)
  );
  await doc.ref.set({
    lastReconciliationAttemptAt: FieldValue.serverTimestamp(),
    lastReconciliationAt: result.status === "RECONCILED" || result.status === "CURRENT"
      ? FieldValue.serverTimestamp()
      : (data.lastReconciliationAt || FieldValue.delete()),
    lastReconciliationHistoryId: result.checkpoint || checkpoint || "",
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  return result;
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

    const seenEmails = new Set();
    const candidates = snapshot.docs.filter((doc) => {
      const email = String(doc.data()?.email || "").trim().toLowerCase();
      if (!email || seenEmails.has(email)) return false;
      seenEmails.add(email);
      return true;
    });

    const stats = { candidates: candidates.length, reconciled: 0, current: 0, skipped: 0, failed: 0 };
    await runWithConcurrency(candidates, MAX_CONCURRENCY, async (doc) => {
      try {
        const result = await reconcileOneConnection(doc);
        if (result.status === "RECONCILED") stats.reconciled += 1;
        else if (result.status === "CURRENT") stats.current += 1;
        else stats.skipped += 1;
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
    if (stats.failed > 0) throw new Error(`Failed to reconcile ${stats.failed} Gmail mailbox(es)`);
  }
);

Object.defineProperties(module.exports, {
  _processMailboxNotification: { value: processMailboxNotification, enumerable: false },
  _reconcileOneConnection: { value: reconcileOneConnection, enumerable: false },
  _getCurrentMailboxHistoryId: { value: getCurrentMailboxHistoryId, enumerable: false },
  _historyCheckpointIsReadable: { value: historyCheckpointIsReadable, enumerable: false },
  _syntheticPubSubEvent: { value: syntheticPubSubEvent, enumerable: false },
});
