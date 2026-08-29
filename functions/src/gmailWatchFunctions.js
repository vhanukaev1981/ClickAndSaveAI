"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { collectPdfAttachments, parseGmailMessage } = require("./gmailParser");
const { decryptToken } = require("./tokenCrypto");
const { REALTIME_MODE } = require("./agentTriggerPolicy");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const { gmailInvoiceDocumentId } = require("./gmailInvoiceSources");
const { emitOperationalEvent } = require("./operationalTelemetry");
const {
  analyzePdfCandidate,
  bodyCandidate,
  loadPdfAttachmentBase64,
  normalizeStoredCandidate,
  pdfSourceDocumentId,
  storedCandidates,
} = require("./gmailRecurringIngestionEngine");
const { selectRecurringBills } = require("./gmailRecurringBillPolicy");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const GMAIL_PUBSUB_TOPIC = "gmail-notifications";
const GMAIL_PARSER_VERSION = ACTIVE_GMAIL_PARSER_VERSION;
const MAX_RECURRENCE_HISTORY_DOCS = 200;

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
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
    logger.error("Google token refresh failed for Gmail watch", { status: response.status });
    throw new HttpsError("failed-precondition", "Google authorization could not be refreshed.");
  }
  return String(payload.access_token);
}

function currentProjectId() {
  const projectId = String(process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || "").trim();
  if (!projectId) {
    throw new HttpsError("failed-precondition", "Google Cloud project id is unavailable.");
  }
  return projectId;
}

async function persistInvoiceDocuments(uid, recurringInvoices) {
  await Promise.all(recurringInvoices.map((invoice) => {
    const safeId = gmailInvoiceDocumentId(invoice.sourceMessageId);
    return db.collection("users").doc(uid).collection("gmailInvoices").doc(safeId).set({
      ...invoice,
      serviceType: invoice.serviceType || FieldValue.delete(),
      verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
      sourceType: "GMAIL_READONLY",
      parserVersion: GMAIL_PARSER_VERSION,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }));
}

async function deleteInvoiceDocuments(uid, sourceMessageIds) {
  const ids = [...new Set((Array.isArray(sourceMessageIds) ? sourceMessageIds : [])
    .map((value) => String(value || "").trim())
    .filter(Boolean))];
  await Promise.all(ids.map((sourceMessageId) =>
    db.collection("users").doc(uid).collection("gmailInvoices")
      .doc(gmailInvoiceDocumentId(sourceMessageId)).delete()
  ));
}

async function listHistoryMessageIds(accessToken, startHistoryId) {
  const ids = new Set();
  let pageToken = "";
  do {
    const params = new URLSearchParams({
      startHistoryId: String(startHistoryId),
      historyTypes: "messageAdded",
    });
    if (pageToken) params.set("pageToken", pageToken);
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/history?${params.toString()}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (response.status === 404) return { expired: true, messageIds: [] };
    if (!response.ok) throw new Error(`Gmail history.list failed with ${response.status}`);
    const payload = await response.json().catch(() => ({}));
    for (const history of Array.isArray(payload.history) ? payload.history : []) {
      for (const added of Array.isArray(history.messagesAdded) ? history.messagesAdded : []) {
        const id = String(added?.message?.id || "");
        if (id) ids.add(id);
      }
    }
    pageToken = String(payload.nextPageToken || "");
  } while (pageToken);
  return { expired: false, messageIds: [...ids] };
}

async function recentCandidateHistory(uid, currentMessageId) {
  const snapshot = await db.collection("users").doc(uid).collection("gmailMessageImports")
    .orderBy("importedAt", "desc")
    .limit(MAX_RECURRENCE_HISTORY_DOCS)
    .get();
  const candidates = [];
  for (const doc of snapshot.docs) {
    if (doc.id === currentMessageId) continue;
    candidates.push(...storedCandidates(doc.data() || {}));
  }
  return candidates;
}

function acceptedFromAudit(data) {
  const raw = Array.isArray(data?.acceptedInvoices) ? data.acceptedInvoices : [];
  return raw.map(normalizeStoredCandidate).filter(Boolean);
}

async function processMessage(uid, accessToken, messageId) {
  const auditRef = db.collection("users").doc(uid).collection("gmailMessageImports").doc(messageId);
  const existing = await auditRef.get();
  const existingData = existing.data() || {};
  if (Number(existingData.parserVersion || 0) >= GMAIL_PARSER_VERSION && existingData.pdfAnalysisComplete === true) {
    const acceptedInvoices = acceptedFromAudit(existingData);
    await persistInvoiceDocuments(uid, acceptedInvoices);
    return { invoices: acceptedInvoices, importedCount: 0, removedSourceMessageIds: [] };
  }

  const response = await fetch(
    `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}?format=full`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (!response.ok) return { invoices: [], importedCount: 0, removedSourceMessageIds: [] };

  const message = await response.json().catch(() => ({}));
  const pdfAttachments = collectPdfAttachments(message.payload);
  const pdfCandidates = [];
  let allPdfsAnalyzed = true;

  for (let index = 0; index < pdfAttachments.length; index += 1) {
    const attachment = pdfAttachments[index];
    try {
      const pdfBase64 = await loadPdfAttachmentBase64(accessToken, messageId, attachment);
      if (!pdfBase64) {
        allPdfsAnalyzed = false;
        continue;
      }
      const candidate = await analyzePdfCandidate(
        message,
        pdfBase64,
        attachment.filename,
        pdfSourceDocumentId(messageId, attachment, index),
        geminiApiKey.value()
      );
      if (candidate) pdfCandidates.push(candidate);
    } catch (error) {
      allPdfsAnalyzed = false;
      emitOperationalEvent({
        event: "gmail.watch.incremental",
        subsystem: "gmail",
        outcome: "degraded",
        severity: "WARNING",
        code: "GMAIL_INCREMENTAL_PDF_ANALYSIS_RETRY",
        uid,
        details: {
          attachmentIndex: index,
          errorName: error instanceof Error ? error.name : typeof error,
        },
      });
    }
  }

  const fallbackBody = bodyCandidate(parseGmailMessage(message));
  const candidates = pdfCandidates.length > 0 ? pdfCandidates : (fallbackBody ? [fallbackBody] : []);
  const historicalCandidates = await recentCandidateHistory(uid, messageId);
  const selected = selectRecurringBills([...historicalCandidates, ...candidates]);
  const currentSourceIds = new Set(candidates.map((candidate) => candidate.sourceMessageId));
  const recurringInvoices = selected
    .filter((candidate) => currentSourceIds.has(candidate.sourceMessageId))
    .map(normalizeStoredCandidate)
    .filter(Boolean);

  const previousAccepted = acceptedFromAudit(existingData);
  const previousIds = new Set(previousAccepted.map((invoice) => invoice.sourceMessageId));
  const acceptedIds = new Set(recurringInvoices.map((invoice) => invoice.sourceMessageId));
  const importedCount = recurringInvoices.filter((invoice) => !previousIds.has(invoice.sourceMessageId)).length;
  const removedSourceMessageIds = previousAccepted
    .map((invoice) => invoice.sourceMessageId)
    .filter((sourceId) => !acceptedIds.has(sourceId));

  await auditRef.set({
    sourceMessageId: messageId,
    candidates,
    acceptedInvoices: recurringInvoices,
    importedAt: FieldValue.serverTimestamp(),
    parserVersion: GMAIL_PARSER_VERSION,
    pdfAttachmentCount: pdfAttachments.length,
    pdfAnalysisComplete: allPdfsAnalyzed,
    agentTriggerMode: REALTIME_MODE,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  // The authoritative Firestore create trigger owns the user push. Therefore a
  // rejected one-off/refund/unknown document never reaches gmailInvoices and can
  // never emit a NEW_INVOICE notification.
  await Promise.all([
    persistInvoiceDocuments(uid, recurringInvoices),
    deleteInvoiceDocuments(uid, removedSourceMessageIds),
  ]);

  return { invoices: recurringInvoices, importedCount, removedSourceMessageIds };
}

async function processMailboxNotification(connectionDoc, notificationHistoryId) {
  const uid = connectionDoc.id;
  const connection = connectionDoc.data() || {};
  if (!connection.encryptedRefreshToken || connection.watchEnabled !== true) return;
  const startHistoryId = String(connection.watchHistoryId || "");
  if (!startHistoryId) return;

  try {
    const accessToken = await refreshAccessToken(connection.encryptedRefreshToken);
    const history = await listHistoryMessageIds(accessToken, startHistoryId);

    if (history.expired) {
      await connectionDoc.ref.set({
        pendingHistoryId: notificationHistoryId,
        historyRecoveryRequired: true,
        historyRecoveryReason: "HISTORY_ID_EXPIRED",
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      emitOperationalEvent({
        event: "gmail.watch.incremental",
        subsystem: "gmail",
        outcome: "degraded",
        severity: "WARNING",
        code: "GMAIL_HISTORY_RECOVERY_REQUIRED",
        uid,
        details: { parserVersion: GMAIL_PARSER_VERSION, historyRecoveryRequired: true },
      });
      return;
    }

    const removedSourceMessageIds = new Set();
    let importedCount = 0;
    for (const messageId of history.messageIds) {
      const result = await processMessage(uid, accessToken, messageId);
      importedCount += result.importedCount;
      for (const sourceMessageId of result.removedSourceMessageIds || []) {
        removedSourceMessageIds.add(sourceMessageId);
      }
    }

    await connectionDoc.ref.set({
      watchHistoryId: notificationHistoryId,
      pendingHistoryId: FieldValue.delete(),
      lastIncrementalScanAt: FieldValue.serverTimestamp(),
      historyRecoveryRequired: false,
      historyRecoveryReason: FieldValue.delete(),
      parserVersion: GMAIL_PARSER_VERSION,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    emitOperationalEvent({
      event: "gmail.watch.incremental",
      subsystem: "gmail",
      outcome: "success",
      severity: "INFO",
      code: "GMAIL_INCREMENTAL_PROCESSING_COMPLETED",
      uid,
      details: {
        parserVersion: GMAIL_PARSER_VERSION,
        messageCount: history.messageIds.length,
        importedCount,
        removedSourceCount: removedSourceMessageIds.size,
      },
    });
  } catch (error) {
    emitOperationalEvent({
      event: "gmail.watch.incremental",
      subsystem: "gmail",
      outcome: "failure",
      severity: "ERROR",
      code: "GMAIL_INCREMENTAL_PROCESSING_FAILED",
      uid,
      details: {
        errorName: error instanceof Error ? error.name : typeof error,
        errorCode: error?.code || "UNKNOWN",
      },
    });
    throw error;
  }
}

exports.startGmailWatch = onCall(
  {
    enforceAppCheck: true,
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey],
  },
  async (request) => {
    const uid = requireAuth(request);
    const ref = db.collection("gmailConnections").doc(uid);
    const snapshot = await ref.get();
    if (!snapshot.exists) throw new HttpsError("failed-precondition", "Gmail is not connected.");
    const connection = snapshot.data() || {};
    if (!Array.isArray(connection.scopes) || !connection.scopes.includes(GMAIL_READONLY_SCOPE)) {
      throw new HttpsError("permission-denied", "The stored connection lacks gmail.readonly.");
    }
    if (!connection.encryptedRefreshToken) {
      throw new HttpsError("failed-precondition", "No Gmail refresh token is stored.");
    }

    const accessToken = await refreshAccessToken(connection.encryptedRefreshToken);
    const topicName = `projects/${currentProjectId()}/topics/${GMAIL_PUBSUB_TOPIC}`;
    const response = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/watch", {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ topicName }),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || !payload.historyId) {
      emitOperationalEvent({
        event: "gmail.watch.incremental",
        subsystem: "gmail",
        outcome: "failure",
        severity: "ERROR",
        code: "GMAIL_WATCH_START_FAILED",
        uid,
        details: { status: response.status },
      });
      throw new HttpsError("failed-precondition", "Gmail push watch could not be started.");
    }

    await ref.set({
      watchEnabled: true,
      watchTopic: topicName,
      watchHistoryId: String(payload.historyId),
      watchExpiration: String(payload.expiration || ""),
      watchStartedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return {
      watching: true,
      historyId: String(payload.historyId),
      expiration: String(payload.expiration || ""),
    };
  }
);

exports.stopGmailWatch = onCall(
  {
    enforceAppCheck: true,
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey],
  },
  async (request) => {
    const uid = requireAuth(request);
    const ref = db.collection("gmailConnections").doc(uid);
    const snapshot = await ref.get();
    if (!snapshot.exists) return { watching: false };
    const connection = snapshot.data() || {};
    if (connection.encryptedRefreshToken) {
      const accessToken = await refreshAccessToken(connection.encryptedRefreshToken);
      const response = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/stop", {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!response.ok) {
        emitOperationalEvent({
          event: "gmail.watch.incremental",
          subsystem: "gmail",
          outcome: "degraded",
          severity: "WARNING",
          code: "GMAIL_WATCH_STOP_UNCONFIRMED",
          uid,
          details: { status: response.status },
        });
      }
    }
    await ref.set({
      watchEnabled: false,
      watchStoppedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    return { watching: false };
  }
);

exports.gmailPushNotification = onMessagePublished(
  {
    topic: GMAIL_PUBSUB_TOPIC,
    region: "europe-west1",
    retry: true,
    timeoutSeconds: 540,
    memory: "1GiB",
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey, geminiApiKey],
  },
  async (event) => {
    const encoded = event.data?.message?.data || "";
    if (!encoded) return;
    let payload;
    try {
      payload = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
    } catch {
      logger.warn("Gmail Pub/Sub event contains invalid JSON");
      return;
    }
    const emailAddress = String(payload.emailAddress || "").trim().toLowerCase();
    const historyId = String(payload.historyId || "").trim();
    if (!emailAddress || !historyId) return;

    const matches = await db.collection("gmailConnections")
      .where("email", "==", emailAddress)
      .limit(10)
      .get();
    if (matches.empty) return;

    await Promise.all(matches.docs.map(async (doc) => {
      await doc.ref.set({
        pendingHistoryId: historyId,
        lastPushNotificationAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      await processMailboxNotification(doc, historyId);
    }));
  }
);

Object.defineProperties(module.exports, {
  _listHistoryMessageIds: { value: listHistoryMessageIds, enumerable: false },
  _processMessage: { value: processMessage, enumerable: false },
  _processMailboxNotification: { value: processMailboxNotification, enumerable: false },
  _recentCandidateHistory: { value: recentCandidateHistory, enumerable: false },
});
