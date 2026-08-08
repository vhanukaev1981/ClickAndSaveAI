"use strict";

const crypto = require("node:crypto");
const { initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const {
  collectPdfAttachments,
  firstHeader,
  normalizePdfInvoiceCandidate,
  parseGmailMessage,
} = require("./gmailParser");
const { decryptToken, encryptToken } = require("./tokenCrypto");
const { validateDealQuery, validateLeadInput } = require("./validation");

initializeApp();
setGlobalOptions({
  region: "europe-west1",
  maxInstances: 10,
  memory: "256MiB",
  timeoutSeconds: 60,
});

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const CONSENT_VERSION = "gmail-readonly-v1";
const MAX_PDF_BYTES = 10 * 1024 * 1024;
const INITIAL_GMAIL_LOOKBACK = "6m";
const GMAIL_LIST_PAGE_SIZE = 100;
const GMAIL_PARSER_VERSION = 4;

function requireAuth(request) {
  if (!request.auth?.uid) {
    throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  }
  return request.auth.uid;
}

function invalidArgument(error) {
  const message = error instanceof Error ? error.message : "Invalid request";
  return new HttpsError("invalid-argument", message);
}

function normalizeStoredInvoice(invoice) {
  if (!invoice || typeof invoice !== "object") return null;
  if (!invoice.sourceMessageId || !invoice.providerName || !invoice.category) return null;
  const monthlyCost = Number(invoice.monthlyCost);
  if (!Number.isFinite(monthlyCost) || monthlyCost <= 0) return null;
  return {
    sourceMessageId: String(invoice.sourceMessageId),
    providerName: String(invoice.providerName),
    category: String(invoice.category),
    monthlyCost,
    receivedDate: String(invoice.receivedDate || ""),
    verificationStatus: "UNVERIFIED_GMAIL_IMPORT",
  };
}

function storedInvoices(data) {
  const raw = Array.isArray(data?.invoices)
    ? data.invoices
    : (data?.invoice ? [data.invoice] : []);
  return raw.map(normalizeStoredInvoice).filter(Boolean);
}

async function postForm(url, values) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(values),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    logger.error("Google OAuth request failed", {
      status: response.status,
      error: payload.error,
      description: payload.error_description,
    });
    throw new HttpsError("failed-precondition", "Google authorization could not be completed.");
  }
  return payload;
}

async function fetchGmailProfileEmail(accessToken) {
  const response = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/profile", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    logger.error("Gmail profile lookup failed", { status: response.status });
    throw new HttpsError("failed-precondition", "The authorized Gmail account could not be verified.");
  }
  const payload = await response.json().catch(() => ({}));
  const email = String(payload.emailAddress || "").trim().toLowerCase();
  if (!email) {
    throw new HttpsError("failed-precondition", "Google did not return the Gmail account identity.");
  }
  return email;
}

async function bestEffortRevokeGoogleToken(token) {
  if (!token) return;
  try {
    await fetch(`https://oauth2.googleapis.com/revoke?token=${encodeURIComponent(token)}`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    });
  } catch {
    // Revocation is defensive cleanup only; never mask the original authorization error.
  }
}

async function refreshGoogleAccessToken(encryptedRefreshToken) {
  const refreshToken = decryptToken(
    encryptedRefreshToken,
    oauthTokenEncryptionKey.value()
  );
  const payload = await postForm("https://oauth2.googleapis.com/token", {
    client_id: googleOAuthClientId.value(),
    client_secret: googleOAuthClientSecret.value(),
    refresh_token: refreshToken,
    grant_type: "refresh_token",
  });
  if (!payload.access_token) {
    throw new HttpsError("failed-precondition", "Google did not return an access token.");
  }
  return { accessToken: payload.access_token };
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

  if (!attachment.attachmentId) return null;
  if (attachment.size > MAX_PDF_BYTES) return null;

  const url = `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}/attachments/${encodeURIComponent(attachment.attachmentId)}`;
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    logger.warn("Gmail attachment skipped", { messageId, status: response.status });
    return null;
  }

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
    "Return JSON only with keys: isInvoice, providerName, category, monthlyCost, receivedDate.",
    "Set isInvoice=true when the PDF itself is clearly an invoice, tax invoice, receipt, bill, charge statement or equivalent billing document.",
    "The document does not need to belong to a predefined household-service category.",
    "For category, use a short useful category when clear; otherwise use other.",
    "monthlyCost must be the actual document total, amount charged, amount paid or current amount due represented by this billing document, not a promotional price or savings figure.",
    "Do not invent an amount. If the PDF is not a billing document or no reliable monetary total can be extracted, set isInvoice=false.",
    "Extract providerName when visible. Do not return account numbers, addresses, IDs, phone numbers, payment details or other personal data.",
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
    config: {
      responseMimeType: "application/json",
    },
  });

  const candidate = JSON.parse(response.text || "{}");
  return normalizePdfInvoiceCandidate(candidate, message, sourceDocumentId);
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

    const listResponse = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages?${params.toString()}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (!listResponse.ok) {
      logger.error("Gmail messages.list failed", { status: listResponse.status, pageCount });
      throw new HttpsError("unavailable", "Gmail could not be scanned right now.");
    }

    const listPayload = await listResponse.json();
    for (const item of Array.isArray(listPayload.messages) ? listPayload.messages : []) {
      const id = String(item?.id || "");
      if (id && !seen.has(id)) {
        seen.add(id);
        messageIds.push(id);
      }
    }

    pageToken = String(listPayload.nextPageToken || "");
    pageCount += 1;
  } while (pageToken);

  return { messageIds, pageCount };
}

exports.getGmailConnectionStatus = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const snapshot = await db.collection("gmailConnections").doc(uid).get();
    if (!snapshot.exists) {
      return { connected: false, email: "", consentVersion: "" };
    }
    const data = snapshot.data();
    const connected = Array.isArray(data?.scopes) &&
      data.scopes.includes(GMAIL_READONLY_SCOPE) &&
      Boolean(data.encryptedRefreshToken);
    return {
      connected,
      email: connected ? String(data.email || request.auth.token.email || "") : "",
      consentVersion: connected ? String(data.consentVersion || "") : "",
    };
  }
);

exports.connectGmail = onCall(
  {
    enforceAppCheck: true,
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey],
  },
  async (request) => {
    const uid = requireAuth(request);
    const data = request.data || {};
    const serverAuthCode = typeof data.serverAuthCode === "string"
      ? data.serverAuthCode.trim()
      : "";

    if (!serverAuthCode || serverAuthCode.length > 2048) {
      throw new HttpsError("invalid-argument", "A valid Google server authorization code is required.");
    }
    if (data.consentAccepted !== true || data.consentVersion !== CONSENT_VERSION) {
      throw new HttpsError("failed-precondition", "Explicit Gmail read-only consent is required.");
    }

    const tokenPayload = await postForm("https://oauth2.googleapis.com/token", {
      client_id: googleOAuthClientId.value(),
      client_secret: googleOAuthClientSecret.value(),
      code: serverAuthCode,
      grant_type: "authorization_code",
    });

    const grantedScopes = String(tokenPayload.scope || "")
      .split(/\s+/)
      .filter(Boolean);
    if (!grantedScopes.includes(GMAIL_READONLY_SCOPE)) {
      await bestEffortRevokeGoogleToken(tokenPayload.refresh_token || tokenPayload.access_token);
      throw new HttpsError("permission-denied", "The gmail.readonly scope was not granted.");
    }

    const accessToken = String(tokenPayload.access_token || "");
    if (!accessToken) {
      throw new HttpsError("failed-precondition", "Google did not return an access token.");
    }

    const gmailEmail = await fetchGmailProfileEmail(accessToken);
    const firebaseEmail = String(request.auth.token.email || "").trim().toLowerCase();
    if (!firebaseEmail || gmailEmail !== firebaseEmail) {
      await bestEffortRevokeGoogleToken(tokenPayload.refresh_token || accessToken);
      logger.warn("Gmail/Firebase account mismatch rejected", { uid });
      throw new HttpsError(
        "permission-denied",
        "The Gmail account must match the Google account signed in to the app."
      );
    }

    const connectionRef = db.collection("gmailConnections").doc(uid);
    const existing = await connectionRef.get();
    const refreshToken = tokenPayload.refresh_token || null;
    const encryptedRefreshToken = refreshToken
      ? encryptToken(refreshToken, oauthTokenEncryptionKey.value())
      : existing.data()?.encryptedRefreshToken;

    if (!encryptedRefreshToken) {
      throw new HttpsError(
        "failed-precondition",
        "No refresh token was returned. Disconnect Google access and approve it again."
      );
    }

    await connectionRef.set({
      uid,
      email: gmailEmail,
      encryptedRefreshToken,
      scopes: grantedScopes,
      consentVersion: CONSENT_VERSION,
      consentedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    logger.info("Gmail connection stored", { uid, scopeCount: grantedScopes.length });
    return { connected: true, email: gmailEmail, consentVersion: CONSENT_VERSION };
  }
);

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
    const connection = await connectionRef.get();
    if (!connection.exists) {
      throw new HttpsError("failed-precondition", "Gmail is not connected.");
    }

    const connectionData = connection.data();
    if (!connectionData?.scopes?.includes(GMAIL_READONLY_SCOPE)) {
      throw new HttpsError("permission-denied", "The stored connection lacks gmail.readonly.");
    }

    const { accessToken } = await refreshGoogleAccessToken(connectionData.encryptedRefreshToken);
    const { messageIds, pageCount } = await listGmailCandidateMessageIds(accessToken);
    const invoices = [];
    let importedCount = 0;
    let pdfCandidates = 0;
    let pdfAnalyzed = 0;
    let pdfIncomplete = 0;

    for (const messageId of messageIds) {
      const auditRef = db
        .collection("users")
        .doc(uid)
        .collection("gmailMessageImports")
        .doc(String(messageId));
      const existingSnapshot = await auditRef.get();
      const existingData = existingSnapshot.data() || {};
      const existingInvoices = storedInvoices(existingData);
      const alreadyFullyAnalyzed = Number(existingData.parserVersion || 0) >= GMAIL_PARSER_VERSION &&
        existingData.pdfAnalysisComplete === true;
      if (alreadyFullyAnalyzed) {
        invoices.push(...existingInvoices);
        continue;
      }

      const detailUrl = `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}?format=full`;
      const detailResponse = await fetch(detailUrl, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!detailResponse.ok) {
        logger.warn("Gmail messages.get skipped", { uid, messageId, status: detailResponse.status });
        continue;
      }

      const message = await detailResponse.json();
      const bodyInvoice = parseGmailMessage(message);
      const pdfAttachments = collectPdfAttachments(message.payload);
      pdfCandidates += pdfAttachments.length;
      const pdfInvoices = [];
      let allPdfsAnalyzed = true;

      for (let index = 0; index < pdfAttachments.length; index += 1) {
        const attachment = pdfAttachments[index];
        try {
          const pdfBase64 = await loadPdfAttachmentBase64(accessToken, messageId, attachment);
          if (!pdfBase64) {
            allPdfsAnalyzed = false;
            pdfIncomplete += 1;
            continue;
          }
          pdfAnalyzed += 1;
          const sourceDocumentId = pdfSourceDocumentId(messageId, attachment, index);
          const pdfInvoice = await analyzePdfInvoice(
            message,
            pdfBase64,
            attachment.filename,
            sourceDocumentId
          );
          if (pdfInvoice) pdfInvoices.push(pdfInvoice);
        } catch (error) {
          allPdfsAnalyzed = false;
          pdfIncomplete += 1;
          logger.warn("PDF invoice analysis failed and will be retried", {
            uid,
            messageId,
            attachmentIndex: index,
            errorName: error instanceof Error ? error.name : typeof error,
          });
        }
      }

      // Every PDF is authoritative as its own document. If at least one PDF is a billing
      // document, do not also add the email-body representation of the same bill.
      const parsedInvoices = pdfInvoices.length > 0
        ? pdfInvoices
        : (bodyInvoice ? [bodyInvoice] : []);

      let resolvedInvoices = parsedInvoices;
      let newlyImportedCount = 0;
      await db.runTransaction(async (transaction) => {
        const current = await transaction.get(auditRef);
        const currentData = current.data() || {};
        const currentInvoices = storedInvoices(currentData);
        const currentComplete = Number(currentData.parserVersion || 0) >= GMAIL_PARSER_VERSION &&
          currentData.pdfAnalysisComplete === true;
        if (currentComplete) {
          resolvedInvoices = currentInvoices;
          return;
        }

        const existingIds = new Set(currentInvoices.map((invoice) => invoice.sourceMessageId));
        newlyImportedCount = parsedInvoices.filter(
          (invoice) => !existingIds.has(invoice.sourceMessageId)
        ).length;

        transaction.set(auditRef, {
          sourceMessageId: String(messageId),
          invoices: parsedInvoices,
          importedAt: FieldValue.serverTimestamp(),
          parserVersion: GMAIL_PARSER_VERSION,
          pdfAttachmentCount: pdfAttachments.length,
          pdfAnalysisComplete: allPdfsAnalyzed,
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      });

      importedCount += newlyImportedCount;
      invoices.push(...resolvedInvoices);
    }

    await connectionRef.set({
      lastScanAt: FieldValue.serverTimestamp(),
      initialBackfillLookback: INITIAL_GMAIL_LOOKBACK,
      initialBackfillPages: pageCount,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    logger.info("Gmail scan completed", {
      uid,
      lookback: INITIAL_GMAIL_LOOKBACK,
      pages: pageCount,
      candidates: messageIds.length,
      returned: invoices.length,
      imported: importedCount,
      pdfCandidates,
      pdfAnalyzed,
      pdfIncomplete,
    });
    return {
      invoices,
      scannedMessages: messageIds.length,
      importedCount,
      scannedPages: pageCount,
      lookback: INITIAL_GMAIL_LOOKBACK,
      pdfCandidates,
      pdfAnalyzed,
      pdfIncomplete,
    };
  }
);

exports.disconnectGmail = onCall(
  {
    enforceAppCheck: true,
    secrets: [oauthTokenEncryptionKey],
  },
  async (request) => {
    const uid = requireAuth(request);
    const connectionRef = db.collection("gmailConnections").doc(uid);
    const connection = await connectionRef.get();

    if (connection.exists && connection.data()?.encryptedRefreshToken) {
      try {
        const refreshToken = decryptToken(
          connection.data().encryptedRefreshToken,
          oauthTokenEncryptionKey.value()
        );
        await bestEffortRevokeGoogleToken(refreshToken);
      } catch (error) {
        logger.warn("Google token revocation failed; deleting local connection", {
          uid,
          errorMessage: error instanceof Error ? error.message : String(error),
        });
      }
    }

    await connectionRef.delete();
    return { connected: false };
  }
);

exports.createProviderLead = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    let input;
    try {
      input = validateLeadInput(request.data);
    } catch (error) {
      throw invalidArgument(error);
    }

    const leadId = crypto
      .createHash("sha256")
      .update(`${uid}:${input.idempotencyKey}`)
      .digest("hex");
    const leadRef = db.collection("providerLeads").doc(leadId);
    let duplicate = false;

    await db.runTransaction(async (transaction) => {
      const existing = await transaction.get(leadRef);
      if (existing.exists) {
        duplicate = true;
        return;
      }
      transaction.create(leadRef, {
        ...input,
        uid,
        authenticatedEmail: String(request.auth.token.email || "").toLowerCase(),
        status: "NEW",
        source: "ANDROID_APP",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    logger.info("Provider lead accepted", { uid, leadId, duplicate, category: input.category });
    return { leadId, status: "NEW", duplicate };
  }
);

exports.analyzeDeal = onCall(
  {
    enforceAppCheck: true,
    secrets: [geminiApiKey],
    timeoutSeconds: 90,
    memory: "512MiB",
  },
  async (request) => {
    const uid = requireAuth(request);
    let query;
    try {
      query = validateDealQuery(request.data);
    } catch (error) {
      throw invalidArgument(error);
    }

    const prompt = [
      "You are a cautious Israeli household-services comparison assistant.",
      "Return JSON only with keys: summary, risks, questions, requiresVerification.",
      "Do not invent current prices, providers, discounts, savings, legal terms or availability.",
      "State that every commercial claim requires a dated official source.",
      `User request: ${query}`,
    ].join("\n");

    try {
      const { GoogleGenAI } = await import("@google/genai");
      const ai = new GoogleGenAI({ apiKey: geminiApiKey.value() });
      const response = await ai.models.generateContent({
        model: "gemini-3.6-flash",
        contents: prompt,
        config: {
          responseMimeType: "application/json",
        },
      });
      const text = response.text || "{}";
      const parsed = JSON.parse(text);
      logger.info("Deal analysis completed", { uid });
      return {
        summary: String(parsed.summary || "לא הופק סיכום."),
        risks: Array.isArray(parsed.risks) ? parsed.risks.map(String).slice(0, 10) : [],
        questions: Array.isArray(parsed.questions) ? parsed.questions.map(String).slice(0, 10) : [],
        requiresVerification: true,
      };
    } catch (error) {
      const errorObject = error && typeof error === "object" ? error : {};
      logger.error("Gemini analysis failed", {
        uid,
        errorName: error instanceof Error ? error.name : typeof error,
        errorMessage: error instanceof Error ? error.message : String(error),
        errorCode: typeof errorObject.code === "string" || typeof errorObject.code === "number"
          ? String(errorObject.code)
          : "",
        httpStatus: typeof errorObject.status === "number" ? errorObject.status : null,
      });
      throw new HttpsError("unavailable", "AI analysis is temporarily unavailable.");
    }
  }
);
