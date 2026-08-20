"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { collectPdfAttachments, parseGmailMessage } = require("./gmailParser");
const { decryptToken } = require("./tokenCrypto");
const { BACKFILL_BATCH_MODE } = require("./agentTriggerPolicy");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const { gmailInvoiceDocumentId } = require("./gmailInvoiceSources");
const { _runFinancialAgentForUser: runFinancialAgentForUser } = require("./financialAgentFunctions");
const {
  analyzePdfCandidate,
  bodyCandidate,
  loadPdfAttachmentBase64,
  normalizeStoredCandidate,
  pdfSourceDocumentId,
  storedCandidates,
} = require("./gmailRecurringIngestionEngine");
const {
  pdfContentFingerprint,
  selectRecurringBills,
} = require("./gmailRecurringBillPolicy");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const INITIAL_GMAIL_LOOKBACK = "6m";
const GMAIL_LIST_PAGE_SIZE = 100;
const MAX_AUTHORITATIVE_INVOICES = 500;
const GMAIL_PARSER_VERSION = ACTIVE_GMAIL_PARSER_VERSION;

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function hasCompletedInitialBackfill(connection) {
  return connection?.initialBackfillCompleted === true || Boolean(connection?.initialBackfillCompletedAt);
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
    logger.error("Google token refresh failed for Gmail scan", { status: response.status });
    throw new HttpsError("failed-precondition", "Google authorization could not be refreshed.");
  }
  return String(payload.access_token);
}

async function listQueryMessageIds(accessToken, query) {
  const messageIds = [];
  let pageToken = "";
  let pageCount = 0;
  do {
    const params = new URLSearchParams({ q: query, maxResults: String(GMAIL_LIST_PAGE_SIZE) });
    if (pageToken) params.set("pageToken", pageToken);
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages?${params.toString()}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (!response.ok) {
      logger.error("Gmail messages.list failed", { status: response.status, pageCount });
      throw new HttpsError("unavailable", "Gmail could not be scanned right now.");
    }
    const payload = await response.json().catch(() => ({}));
    for (const item of Array.isArray(payload.messages) ? payload.messages : []) {
      const id = String(item?.id || "");
      if (id) messageIds.push(id);
    }
    pageToken = String(payload.nextPageToken || "");
    pageCount += 1;
  } while (pageToken);
  return { messageIds, pageCount };
}

async function listGmailCandidateMessageIds(accessToken) {
  // Keep PDF discovery independent from subject/sender heuristics so no-subject mail
  // and application/octet-stream attachments with a .pdf filename are still reached.
  const broadBillingQuery = `newer_than:${INITIAL_GMAIL_LOOKBACK} {חשבונית קבלה "הודעת תשלום" "פירוט חיוב" "חשבון חודשי" invoice receipt bill statement subscription billing}`;
  const pdfFallbackQuery = `newer_than:${INITIAL_GMAIL_LOOKBACK} has:attachment filename:pdf`;
  const results = await Promise.all([
    listQueryMessageIds(accessToken, broadBillingQuery),
    listQueryMessageIds(accessToken, pdfFallbackQuery),
  ]);
  const seen = new Set();
  const messageIds = [];
  for (const result of results) {
    for (const id of result.messageIds) {
      if (seen.has(id)) continue;
      seen.add(id);
      messageIds.push(id);
    }
  }
  return {
    messageIds,
    pageCount: results.reduce((sum, result) => sum + result.pageCount, 0),
  };
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

async function currentAuthoritativeInvoices(uid) {
  const snapshot = await db.collection("users").doc(uid).collection("gmailInvoices")
    .limit(MAX_AUTHORITATIVE_INVOICES)
    .get();
  return snapshot.docs
    .map((doc) => normalizeStoredCandidate(doc.data() || {}))
    .filter(Boolean);
}

async function currentAuthoritativeSourceIds(uid) {
  const snapshot = await db.collection("users").doc(uid).collection("gmailInvoices")
    .limit(MAX_AUTHORITATIVE_INVOICES)
    .get();
  return snapshot.docs
    .map((doc) => String(doc.data()?.sourceMessageId || ""))
    .filter(Boolean);
}

async function processMessage(uid, accessToken, messageId) {
  const auditRef = db.collection("users").doc(uid).collection("gmailMessageImports").doc(messageId);
  const existing = await auditRef.get();
  const existingData = existing.data() || {};
  if (Number(existingData.parserVersion || 0) >= GMAIL_PARSER_VERSION && existingData.pdfAnalysisComplete === true) {
    return {
      candidates: storedCandidates(existingData),
      importedCount: 0,
      upgraded: false,
    };
  }

  const response = await fetch(
    `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}?format=full`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (!response.ok) return { candidates: [], importedCount: 0, upgraded: false };

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
      // Explicit call kept here as a regression-visible invariant: exact PDF bytes
      // drive cross-forward deduplication, never Gmail message ids alone.
      pdfContentFingerprint(pdfBase64);
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
      logger.warn("Gmail PDF analysis failed and will be retried", {
        uid,
        messageId,
        attachmentIndex: index,
        errorName: error instanceof Error ? error.name : typeof error,
      });
    }
  }

  const fallbackBody = bodyCandidate(parseGmailMessage(message));
  const candidates = pdfCandidates.length > 0 ? pdfCandidates : (fallbackBody ? [fallbackBody] : []);
  const previousCandidates = storedCandidates(existingData);
  const previousIds = new Set(previousCandidates.map((candidate) => candidate.sourceMessageId));
  const importedCount = candidates.filter((candidate) => !previousIds.has(candidate.sourceMessageId)).length;
  const upgraded = Number(existingData.parserVersion || 0) < GMAIL_PARSER_VERSION;

  await auditRef.set({
    sourceMessageId: messageId,
    candidates,
    importedAt: FieldValue.serverTimestamp(),
    parserVersion: GMAIL_PARSER_VERSION,
    pdfAttachmentCount: pdfAttachments.length,
    pdfAnalysisComplete: allPdfsAnalyzed,
    agentTriggerMode: BACKFILL_BATCH_MODE,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  return {
    candidates: candidates.map(normalizeStoredCandidate).filter(Boolean),
    importedCount,
    upgraded,
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
    const connectionSnapshot = await connectionRef.get();
    if (!connectionSnapshot.exists) {
      throw new HttpsError("failed-precondition", "Gmail is not connected.");
    }
    const connection = connectionSnapshot.data() || {};

    if (hasCompletedInitialBackfill(connection)) {
      const invoices = await currentAuthoritativeInvoices(uid);
      return {
        invoices,
        scannedMessages: 0,
        importedCount: 0,
        removedSourceMessageIds: [],
        scannedPages: 0,
        lookback: "incremental",
        parserVersion: GMAIL_PARSER_VERSION,
        upgradedMessages: 0,
        agentRefreshed: false,
        alreadyCompleted: true,
      };
    }
    if (!Array.isArray(connection.scopes) || !connection.scopes.includes(GMAIL_READONLY_SCOPE)) {
      throw new HttpsError("permission-denied", "The stored connection lacks gmail.readonly.");
    }
    if (!connection.encryptedRefreshToken) {
      throw new HttpsError("failed-precondition", "No Gmail refresh token is stored.");
    }

    const accessToken = await refreshAccessToken(connection.encryptedRefreshToken);
    const { messageIds, pageCount } = await listGmailCandidateMessageIds(accessToken);
    const candidates = [];
    let importedCount = 0;
    let upgradedMessages = 0;

    for (const messageId of messageIds) {
      const result = await processMessage(uid, accessToken, messageId);
      candidates.push(...result.candidates);
      importedCount += result.importedCount;
      if (result.upgraded) upgradedMessages += 1;
    }

    const recurringInvoices = selectRecurringBills(candidates)
      .map(normalizeStoredCandidate)
      .filter(Boolean);
    const selectedIds = new Set(recurringInvoices.map((invoice) => invoice.sourceMessageId));
    const previousSourceIds = await currentAuthoritativeSourceIds(uid);
    const removedSourceMessageIds = previousSourceIds.filter((sourceId) => !selectedIds.has(sourceId));

    await Promise.all([
      persistInvoiceDocuments(uid, recurringInvoices),
      deleteInvoiceDocuments(uid, removedSourceMessageIds),
    ]);

    // Completion is written only after every candidate has been classified and the
    // authoritative recurring-bill snapshot has been persisted successfully.
    await connectionRef.set({
      lastScanAt: FieldValue.serverTimestamp(),
      initialBackfillCompleted: true,
      initialBackfillCompletedAt: FieldValue.serverTimestamp(),
      initialBackfillLookback: INITIAL_GMAIL_LOOKBACK,
      initialBackfillPages: pageCount,
      parserVersion: GMAIL_PARSER_VERSION,
      lastParserUpgradeCount: upgradedMessages,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    let agentRefreshed = false;
    try {
      await runFinancialAgentForUser(uid);
      agentRefreshed = true;
    } catch (error) {
      logger.error("Financial agent refresh failed after Gmail backfill batch", {
        uid,
        errorName: error instanceof Error ? error.name : typeof error,
      });
    }

    logger.info("Initial Gmail recurring-bill backfill completed", {
      uid,
      parserVersion: GMAIL_PARSER_VERSION,
      lookback: INITIAL_GMAIL_LOOKBACK,
      pages: pageCount,
      candidates: candidates.length,
      accepted: recurringInvoices.length,
      scannedMessages: messageIds.length,
      removedSourceCount: removedSourceMessageIds.length,
    });

    return {
      invoices: recurringInvoices,
      scannedMessages: messageIds.length,
      importedCount: recurringInvoices.length,
      removedSourceMessageIds,
      scannedPages: pageCount,
      lookback: INITIAL_GMAIL_LOOKBACK,
      parserVersion: GMAIL_PARSER_VERSION,
      upgradedMessages,
      agentRefreshed,
      alreadyCompleted: false,
    };
  }
);

exports._v5NormalizeStoredInvoice = normalizeStoredCandidate;
exports._v5ProcessMessage = processMessage;
exports._hasCompletedInitialBackfill = hasCompletedInitialBackfill;
exports.GMAIL_PARSER_VERSION_V5 = GMAIL_PARSER_VERSION;
exports.GMAIL_PARSER_VERSION_ACTIVE = GMAIL_PARSER_VERSION;
