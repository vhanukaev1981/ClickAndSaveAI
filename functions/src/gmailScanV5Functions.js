"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const {
  collectPdfAttachments,
  firstHeader,
  normalizePdfInvoiceCandidate,
  parseGmailMessage,
} = require("./gmailParser");
const { decryptToken } = require("./tokenCrypto");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const INITIAL_GMAIL_LOOKBACK = "6m";
const GMAIL_LIST_PAGE_SIZE = 100;
const MAX_PDF_BYTES = 10 * 1024 * 1024;
const GMAIL_PARSER_VERSION = 5;

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
    logger.error("Google token refresh failed for Gmail v5 scan", { status: response.status });
    throw new HttpsError("failed-precondition", "Google authorization could not be refreshed.");
  }
  return String(payload.access_token);
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
  const raw = Array.isArray(data?.invoices)
    ? data.invoices
    : (data?.invoice ? [data.invoice] : []);
  return raw.map(normalizeStoredInvoice).filter(Boolean);
}

async function listGmailCandidateMessageIds(accessToken) {
  const query = `newer_than:${INITIAL_GMAIL_LOOKBACK} {חשבונית קבלה "הודעת תשלום" "פירוט חיוב" "חשבון חודשי" invoice receipt bill statement סלקום cellcom פרטנר partner פלאפון pelephone בזק bezeq "חברת החשמל" HOT yes filename:pdf}`;
  const messageIds = [];
  const seen = new Set();
  let pageToken = "";
  let pageCount = 0;

  do {
    const params = new URLSearchParams({
      q: query,
      maxResults: String(GMAIL_LIST_PAGE_SIZE),
    });
    if (pageToken) params.set("pageToken", pageToken);
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages?${params.toString()}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (!response.ok) {
      logger.error("Gmail v5 messages.list failed", { status: response.status, pageCount });
      throw new HttpsError("unavailable", "Gmail could not be scanned right now.");
    }
    const payload = await response.json().catch(() => ({}));
    for (const item of Array.isArray(payload.messages) ? payload.messages : []) {
      const id = String(item?.id || "");
      if (id && !seen.has(id)) {
        seen.add(id);
        messageIds.push(id);
      }
    }
    pageToken = String(payload.nextPageToken || "");
    pageCount += 1;
  } while (pageToken);

  return { messageIds, pageCount };
}

async function persistInvoiceDocuments(uid, invoices) {
  await Promise.all(invoices.map((invoice) => {
    const safeId = crypto.createHash("sha256").update(invoice.sourceMessageId).digest("hex");
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

async function processMessage(uid, accessToken, messageId) {
  const auditRef = db.collection("users").doc(uid).collection("gmailMessageImports").doc(messageId);
  const existing = await auditRef.get();
  const existingData = existing.data() || {};
  if (Number(existingData.parserVersion || 0) >= GMAIL_PARSER_VERSION && existingData.pdfAnalysisComplete === true) {
    const existingInvoices = storedInvoices(existingData);
    await persistInvoiceDocuments(uid, existingInvoices);
    return { invoices: existingInvoices, importedCount: 0, upgraded: false };
  }

  const response = await fetch(
    `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}?format=full`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  if (!response.ok) return { invoices: [], importedCount: 0, upgraded: false };
  const message = await response.json().catch(() => ({}));
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
      logger.warn("Gmail v5 PDF analysis failed and will be retried", {
        uid,
        messageId,
        attachmentIndex: index,
        errorName: error instanceof Error ? error.name : typeof error,
      });
    }
  }

  const parsedInvoices = pdfInvoices.length > 0 ? pdfInvoices : (bodyInvoice ? [bodyInvoice] : []);
  const previousInvoices = storedInvoices(existingData);
  const previousIds = new Set(previousInvoices.map((invoice) => invoice.sourceMessageId));
  const importedCount = parsedInvoices.filter((invoice) => !previousIds.has(invoice.sourceMessageId)).length;
  const upgraded = Number(existingData.parserVersion || 0) < GMAIL_PARSER_VERSION;

  await auditRef.set({
    sourceMessageId: messageId,
    invoices: parsedInvoices,
    importedAt: FieldValue.serverTimestamp(),
    parserVersion: GMAIL_PARSER_VERSION,
    pdfAttachmentCount: pdfAttachments.length,
    pdfAnalysisComplete: allPdfsAnalyzed,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  await persistInvoiceDocuments(uid, parsedInvoices);

  return {
    invoices: parsedInvoices.map(normalizeStoredInvoice).filter(Boolean),
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
    if (!Array.isArray(connection.scopes) || !connection.scopes.includes(GMAIL_READONLY_SCOPE)) {
      throw new HttpsError("permission-denied", "The stored connection lacks gmail.readonly.");
    }
    if (!connection.encryptedRefreshToken) {
      throw new HttpsError("failed-precondition", "No Gmail refresh token is stored.");
    }

    const accessToken = await refreshAccessToken(connection.encryptedRefreshToken);
    const { messageIds, pageCount } = await listGmailCandidateMessageIds(accessToken);
    const invoices = [];
    let importedCount = 0;
    let upgradedMessages = 0;

    for (const messageId of messageIds) {
      const result = await processMessage(uid, accessToken, messageId);
      invoices.push(...result.invoices);
      importedCount += result.importedCount;
      if (result.upgraded) upgradedMessages += 1;
    }

    await connectionRef.set({
      lastScanAt: FieldValue.serverTimestamp(),
      initialBackfillLookback: INITIAL_GMAIL_LOOKBACK,
      initialBackfillPages: pageCount,
      parserVersion: GMAIL_PARSER_VERSION,
      lastParserUpgradeCount: upgradedMessages,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    logger.info("Gmail parser-v5 scan completed", {
      uid,
      lookback: INITIAL_GMAIL_LOOKBACK,
      pages: pageCount,
      candidates: messageIds.length,
      returned: invoices.length,
      importedCount,
      upgradedMessages,
    });

    return {
      invoices,
      scannedMessages: messageIds.length,
      importedCount,
      scannedPages: pageCount,
      lookback: INITIAL_GMAIL_LOOKBACK,
      parserVersion: GMAIL_PARSER_VERSION,
      upgradedMessages,
    };
  }
);

exports._v5NormalizeStoredInvoice = normalizeStoredInvoice;
exports._v5ProcessMessage = processMessage;
exports.GMAIL_PARSER_VERSION_V5 = GMAIL_PARSER_VERSION;
