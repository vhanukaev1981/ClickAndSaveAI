"use strict";

const crypto = require("node:crypto");
const { initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { GoogleGenAI } = require("@google/genai");
const { setGlobalOptions } = require("firebase-functions/v2");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { parseGmailMessage } = require("./gmailParser");
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
  return { accessToken: payload.access_token, refreshToken };
}

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
      redirect_uri: "",
    });

    const grantedScopes = String(tokenPayload.scope || "")
      .split(/\s+/)
      .filter(Boolean);
    if (!grantedScopes.includes(GMAIL_READONLY_SCOPE)) {
      throw new HttpsError("permission-denied", "The gmail.readonly scope was not granted.");
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

    const email = String(request.auth.token.email || "").toLowerCase();
    await connectionRef.set({
      uid,
      email,
      encryptedRefreshToken,
      scopes: grantedScopes,
      consentVersion: CONSENT_VERSION,
      consentedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    logger.info("Gmail connection stored", { uid, scopeCount: grantedScopes.length });
    return { connected: true, email, consentVersion: CONSENT_VERSION };
  }
);

exports.scanGmailInvoices = onCall(
  {
    enforceAppCheck: true,
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey],
    timeoutSeconds: 120,
    memory: "512MiB",
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
    const query = encodeURIComponent(
      'newer_than:18m subject:(חשבונית OR קבלה OR "הודעת תשלום" OR invoice OR receipt)'
    );
    const listResponse = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages?q=${query}&maxResults=25`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (!listResponse.ok) {
      logger.error("Gmail messages.list failed", { uid, status: listResponse.status });
      throw new HttpsError("unavailable", "Gmail could not be scanned right now.");
    }

    const listPayload = await listResponse.json();
    const messageIds = Array.isArray(listPayload.messages)
      ? listPayload.messages.map((item) => item.id).filter(Boolean)
      : [];
    const importedInvoices = [];

    for (const messageId of messageIds) {
      const auditRef = db
        .collection("users")
        .doc(uid)
        .collection("gmailMessageImports")
        .doc(String(messageId));
      const alreadyImported = await auditRef.get();
      if (alreadyImported.exists) continue;

      const detailUrl = `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date`;
      const detailResponse = await fetch(detailUrl, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!detailResponse.ok) {
        logger.warn("Gmail messages.get skipped", { uid, messageId, status: detailResponse.status });
        continue;
      }

      const parsed = parseGmailMessage(await detailResponse.json());
      if (!parsed) continue;

      let wasCreated = false;
      await db.runTransaction(async (transaction) => {
        const current = await transaction.get(auditRef);
        if (current.exists) return;
        transaction.create(auditRef, {
          sourceMessageId: parsed.sourceMessageId,
          importedAt: FieldValue.serverTimestamp(),
          parserVersion: 1,
        });
        wasCreated = true;
      });
      if (wasCreated) importedInvoices.push(parsed);
    }

    await connectionRef.set({
      lastScanAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    logger.info("Gmail scan completed", {
      uid,
      candidates: messageIds.length,
      imported: importedInvoices.length,
    });
    return {
      invoices: importedInvoices,
      scannedMessages: messageIds.length,
      importedCount: importedInvoices.length,
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
        await fetch(`https://oauth2.googleapis.com/revoke?token=${encodeURIComponent(refreshToken)}`, {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
        });
      } catch (error) {
        logger.warn("Google token revocation failed; deleting local connection", {
          uid,
          message: error instanceof Error ? error.message : String(error),
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

    const ai = new GoogleGenAI({ apiKey: geminiApiKey.value() });
    const prompt = [
      "You are a cautious Israeli household-services comparison assistant.",
      "Return JSON only with keys: summary, risks, questions, requiresVerification.",
      "Do not invent current prices, providers, discounts, savings, legal terms or availability.",
      "State that every commercial claim requires a dated official source.",
      `User request: ${query}`,
    ].join("\n");

    try {
      const response = await ai.models.generateContent({
        model: "gemini-3.6-flash",
        contents: prompt,
        config: {
          temperature: 0.1,
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
      logger.error("Gemini analysis failed", {
        uid,
        message: error instanceof Error ? error.message : String(error),
      });
      throw new HttpsError("unavailable", "AI analysis is temporarily unavailable.");
    }
  }
);
