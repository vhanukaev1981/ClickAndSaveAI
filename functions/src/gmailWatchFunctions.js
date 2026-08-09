"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const {
  collectPdfAttachments,
  firstHeader,
  normalizePdfInvoiceCandidate,
  parseGmailMessage,
} = require("./gmailParser");
const { decryptToken } = require("./tokenCrypto");
const { REALTIME_MODE } = require("./agentTriggerPolicy");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const {
  gmailInvoiceDocumentId,
  staleInvoiceSourceIds,
} = require("./gmailInvoiceSources");
const { _sendPushToUser: sendPushToUser } = require("./pushFunctions");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const GMAIL_PUBSUB_TOPIC = "gmail-notifications";
const MAX_PDF_BYTES = 10 * 1024 * 1024;
const GMAIL_PARSER_VERSION = ACTIVE_GMAIL_PARSER_VERSION;

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
  const projectId = String(
    process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || ""
  ).trim();
  if (!projectId) {
    throw new HttpsError("failed-precondition", "Google Cloud project id is unavailable.");
  }
  return projectId;
}

function base64UrlToBase64(value) {
  const normalized = String(value || "").replace(/-/g, "+").replace(/_/g, "/");
  const padding = normalized.length % 4;
  return padding === 0 ? normalized : normalized + "=".repeat(4 - padding);
}

async function loadPdfAttachmentBase64(accessToken, messageId, attachment) {
  if (attachment.inlineData) {
    const data = base64UrlToBase64(attachment.inlineData);
    if (Buffer.byteLength(data, "base64") > MAX_PDF_BYTES) return null;
    return data;
  }
  if (!attachment.attachmentId || attachment.size > MAX_PDF_BYTES) return null;
  const response = await fetch(
    `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}/attachments/${encodeURIComponent(attachment.attachmentId)}`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (!response.ok) return null;
  const payload = await response.json().catch(() => ({}));
  const data = base64UrlToBase64(payload.data);
  if (!data || Buffer.byteLength(data, "base64") > MAX_PDF_BYTES) return null;
  return data;
}

function pdfSourceDocumentId(messageId, attachment, index) {
  const fingerprint = crypto
    .createHash("sha256")
    .update(`${messageId}:${attachment.attachmentId || attachment.filename || "inline"}:${index}`)
    .digest("hex")
    .slice(0, 20);
  return `${messageId}:pdf:${fingerprint}`;
}

async function analyzePdfInvoice(message, pdfBase64, filename, sourceDocumentId) {
  const headers = message?.payload?.headers || [];
  const subject = firstHeader(headers, "Subject").slice(0, 300);
  const from = firstHeader(headers, "From").slice(0, 300);
  const date = firstHeader(headers, "Date").slice(0, 120);
  const safeFilename = String(filename || "").slice(0, 180);
  const prompt = [
    "Analyze this PDF independently of the email body.",
    "Return JSON only with keys: isInvoice, providerName, category, serviceType, monthlyCost, receivedDate.",
    "Set isInvoice=true only when the PDF itself is clearly an invoice, tax invoice, receipt, bill, charge statement or equivalent billing document.",
    "The document does not need to belong to a predefined household-service category.",
    "For category, use a short useful category when clear; otherwise use other.",
    "For serviceType, return a concise service descriptor only when explicitly written in the PDF, for example internet speed (1 Gbps/500 Mbps), mobile line count plus data allowance, or an explicit insurance type. Otherwise return an empty string.",
    "Never infer serviceType from provider name, price, marketing language or assumptions.",
    "monthlyCost must be the actual document total, amount charged, amount paid or current amount due, never a promotional price or savings figure.",
    "Do not invent an amount. If no reliable monetary total can be extracted, set isInvoice=false.",
    "Do not return account numbers, addresses, IDs, phone numbers, payment details or other personal data.",
    `Email subject context only: ${subject}`,
    `Email sender context only: ${from}`,
    `Email date context only: ${date}`,
    `Attachment filename context only: ${safeFilename}`,
  ].join("\n");

  const { GoogleGenAI } = await import("@google/genai");
  const ai = new GoogleGenAI({ apiKey: geminiApiKey.value() });
  const response = await ai.models.generateContent({
    model: "gemini-3.6-flash",
    contents: [{
      role: "user",
      parts: [
        { inlineData: { mimeType: "application/pdf", data: pdfBase64 } },
        { text: prompt },
      ],
    }],
    config: { responseMimeType: "application/json" },
  });
  return normalizePdfInvoiceCandidate(
    JSON.parse(response.text || "{}"),
    message,
    sourceDocumentId
  );
}

function normalizeStoredInvoice(invoice) {
  if (!invoice || typeof invoice !== "object") return null;
  const monthlyCost = Number(invoice.monthlyCost);
  if (!invoice.sourceMessageId || !invoice.providerName || !Number.isFinite(monthlyCost) || monthlyCost <= 0) {
    return null;
  }
  const normalized = {
    sourceMessageId: String(invoice.sourceMessageId),
    providerName: String(invoice.providerName),
    category: String(invoice.category || "other"),
    monthlyCost,
    receivedDate: String(invoice.receivedDate || ""),
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  };
  const serviceType = String(invoice.serviceType || "").trim();
  return serviceType ? { ...normalized, serviceType } : normalized;
}

function storedInvoices(data) {
  const raw = Array.isArray(data?.invoices) ? data.invoices : [];
  return raw.map(normalizeStoredInvoice).filter(Boolean);
}

async function persistInvoiceDocuments(uid, invoices) {
  await Promise.all(invoices.map((invoice) => {
    const safeId = gmailInvoiceDocumentId(invoice.sourceMessageId);
    return db.collection("users").doc(uid).collection("gmailInvoices").doc(safeId).set({
      ...invoice,
      serviceType: invoice.serviceType || FieldValue.delete(),
      verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
      sourceType: "GMAIL_READONLY",
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }));
}

async function deleteInvoiceDocuments(uid, sourceMessageIds) {
  const ids = [...new Set((Array.isArray(sourceMessageIds) ? sourceMessageIds : [])
    .map((value) => String(value || "").trim())
    .filter(Boolean))];
  if (ids.length === 0) return;
  await Promise.all(ids.map((sourceMessageId) => {
    const safeId = gmailInvoiceDocumentId(sourceMessageId);
    return db.collection("users").doc(uid).collection("gmailInvoices").doc(safeId).delete();
  }));
}

async function listHistoryMessageIds(accessToken, startHistoryId) {
  const ids = new Set();
  let pageToken = "";
  do {
    const params = new URLSearchParams({ startHistoryId: String(startHistoryId), historyTypes: "messageAdded" });
    if (pageToken) params.set("pageToken", pageToken);
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/history?${params.toString()}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (response.status === 404) return { expired: true, messageIds: [] };
    if (!response.ok) {
      throw new Error(`Gmail history.list failed with ${response.status}`);
    }
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

async function processMessage(uid, accessToken, messageId) {
  const auditRef = db.collection("users").doc(uid).collection("gmailMessageImports").doc(messageId);
  const existing = await auditRef.get();
  const existingData = existing.data() || {};
  if (Number(existingData.parserVersion || 0) >= GMAIL_PARSER_VERSION && existingData.pdfAnalysisComplete === true) {
    const existingInvoices = storedInvoices(existingData);
    await persistInvoiceDocuments(uid, existingInvoices);
    return { invoices: existingInvoices, importedCount: 0, removedSourceMessageIds: [] };
  }

  const response = await fetch(
    `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}?format=full`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (!response.ok) return { invoices: [], importedCount: 0, removedSourceMessageIds: [] };
  const message = await response.json();
  const bodyInvoice = parseGmailMessage(message);
  const pdfAttachments = collectPdfAttachments(message.payload);
  const pdfInvoices = [];
  let allPdfsAnalyzed = true;

  for (let index = 0; index < pdfAttachments.length; index += 1) {
    const attachment = pdfAttachments[index];
    try {
      const pdfBase64 = await loadPdfAttachmentBase64(accessToken, messageId, attachment);
      if (!pdfBase64) {
        allPdfsAnalyzed = false;
        continue;
      }
      const invoice = await analyzePdfInvoice(
        message,
        pdfBase64,
        attachment.filename,
        pdfSourceDocumentId(messageId, attachment, index)
      );
      if (invoice) pdfInvoices.push(invoice);
    } catch (error) {
      allPdfsAnalyzed = false;
      logger.warn("Background PDF analysis failed and will be retried", {
        uid,
        messageId,
        attachmentIndex: index,
        errorName: error instanceof Error ? error.name : typeof error,
      });
    }
  }

  const parsedInvoices = pdfInvoices.length > 0 ? pdfInvoices : (bodyInvoice ? [bodyInvoice] : []);
  let importedCount = 0;
  let removedSourceMessageIds = [];
  await db.runTransaction(async (transaction) => {
    const current = await transaction.get(auditRef);
    const currentInvoices = storedInvoices(current.data() || {});
    const existingIds = new Set(currentInvoices.map((invoice) => invoice.sourceMessageId));
    importedCount = parsedInvoices.filter((invoice) => !existingIds.has(invoice.sourceMessageId)).length;
    removedSourceMessageIds = staleInvoiceSourceIds(currentInvoices, parsedInvoices);
    transaction.set(auditRef, {
      sourceMessageId: messageId,
      invoices: parsedInvoices,
      importedAt: FieldValue.serverTimestamp(),
      parserVersion: GMAIL_PARSER_VERSION,
      pdfAttachmentCount: pdfAttachments.length,
      pdfAnalysisComplete: allPdfsAnalyzed,
      agentTriggerMode: REALTIME_MODE,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });

  await Promise.all([
    persistInvoiceDocuments(uid, parsedInvoices),
    deleteInvoiceDocuments(uid, removedSourceMessageIds),
  ]);

  return {
    invoices: parsedInvoices.map(normalizeStoredInvoice).filter(Boolean),
    importedCount,
    removedSourceMessageIds,
  };
}

async function processMailboxNotification(connectionDoc, notificationHistoryId) {
  const uid = connectionDoc.id;
  const connection = connectionDoc.data() || {};
  if (!connection.encryptedRefreshToken || connection.watchEnabled !== true) return;
  const startHistoryId = String(connection.watchHistoryId || "");
  if (!startHistoryId) return;
  const accessToken = await refreshAccessToken(connection.encryptedRefreshToken);
  const history = await listHistoryMessageIds(accessToken, startHistoryId);

  if (history.expired) {
    await connectionDoc.ref.set({
      watchHistoryId: notificationHistoryId,
      historyRecoveryRequired: true,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    logger.warn("Gmail history id expired; full scan will recover missed invoices", { uid });
    return;
  }

  const newInvoices = [];
  const removedSourceMessageIds = new Set();
  let importedCount = 0;
  for (const messageId of history.messageIds) {
    const result = await processMessage(uid, accessToken, messageId);
    importedCount += result.importedCount;
    for (const sourceMessageId of result.removedSourceMessageIds || []) {
      removedSourceMessageIds.add(sourceMessageId);
    }
    if (result.importedCount > 0) newInvoices.push(...result.invoices);
  }

  await connectionDoc.ref.set({
    watchHistoryId: notificationHistoryId,
    pendingHistoryId: FieldValue.delete(),
    lastIncrementalScanAt: FieldValue.serverTimestamp(),
    historyRecoveryRequired: false,
    parserVersion: GMAIL_PARSER_VERSION,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  if (importedCount > 0 && newInvoices.length > 0) {
    const first = newInvoices[0];
    const body = importedCount === 1
      ? `${first.providerName}: ${first.monthlyCost.toFixed(2)} ₪. אנחנו בודקים עכשיו אם יש דרך לחסוך.`
      : `נקלטו ${importedCount} חשבוניות חדשות. אנחנו בודקים עכשיו כמה אפשר לחסוך.`;
    await sendPushToUser(uid, {
      title: importedCount === 1 ? "חשבונית חדשה נקלטה" : "חשבוניות חדשות נקלטו",
      body,
      data: { type: "NEW_INVOICE", importedCount },
    });
  }

  logger.info("Incremental Gmail processing completed", {
    uid,
    parserVersion: GMAIL_PARSER_VERSION,
    messageCount: history.messageIds.length,
    importedCount,
    removedSourceCount: removedSourceMessageIds.size,
  });
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
      logger.error("Gmail users.watch failed", {
        uid,
        status: response.status,
        error: payload.error?.message || "",
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
    return { watching: true, historyId: String(payload.historyId), expiration: String(payload.expiration || "") };
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
      if (!response.ok) logger.warn("Gmail users.stop failed", { uid, status: response.status });
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
