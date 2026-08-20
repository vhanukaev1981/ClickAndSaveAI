"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { defineSecret, defineString } = require("firebase-functions/params");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { assertActiveAccount } = require("./accountAuthorization");
const { encryptToken } = require("./tokenCrypto");
const { emitOperationalEvent } = require("./operationalTelemetry");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const CONSENT_VERSION = "gmail-readonly-v1";

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

async function postForm(url, values) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(values),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    logger.warn("Google OAuth request failed during Gmail connection", { status: response.status });
    throw new HttpsError("failed-precondition", "Google authorization could not be completed.");
  }
  return payload;
}

async function revokeTokenBestEffort(token) {
  if (!token) return;
  try {
    await fetch(`https://oauth2.googleapis.com/revoke?token=${encodeURIComponent(token)}`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    });
  } catch {
    // Defensive cleanup only. The original connection error remains authoritative.
  }
}

async function fetchGmailProfileEmail(accessToken) {
  const response = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/profile", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    throw new HttpsError("failed-precondition", "The authorized Gmail account could not be verified.");
  }
  const payload = await response.json().catch(() => ({}));
  const email = String(payload.emailAddress || "").trim().toLowerCase();
  if (!email) throw new HttpsError("failed-precondition", "Google did not return the Gmail identity.");
  return email;
}

exports.connectGmail = onCall(
  {
    enforceAppCheck: true,
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey],
  },
  async (request) => {
    const uid = requireAuth(request);
    try {
      await assertActiveAccount(uid);
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

      const connectionRef = db.collection("gmailConnections").doc(uid);
      const existing = await connectionRef.get();
      if (existing.data()?.disconnectState === "RETRY_REQUIRED") {
        throw new HttpsError(
          "failed-precondition",
          "Previous Gmail provider cleanup is still pending. Retry Gmail disconnect before reconnecting."
        );
      }

      const tokenPayload = await postForm("https://oauth2.googleapis.com/token", {
        client_id: googleOAuthClientId.value(),
        client_secret: googleOAuthClientSecret.value(),
        code: serverAuthCode,
        grant_type: "authorization_code",
        redirect_uri: "",
      });
      const grantedScopes = String(tokenPayload.scope || "").split(/\s+/).filter(Boolean);
      if (!grantedScopes.includes(GMAIL_READONLY_SCOPE)) {
        await revokeTokenBestEffort(tokenPayload.refresh_token || tokenPayload.access_token);
        throw new HttpsError("permission-denied", "The gmail.readonly scope was not granted.");
      }

      const accessToken = String(tokenPayload.access_token || "");
      if (!accessToken) {
        throw new HttpsError("failed-precondition", "Google did not return an access token.");
      }
      const gmailEmail = await fetchGmailProfileEmail(accessToken);
      const firebaseEmail = String(request.auth.token.email || "").trim().toLowerCase();
      if (!firebaseEmail || gmailEmail !== firebaseEmail) {
        await revokeTokenBestEffort(tokenPayload.refresh_token || accessToken);
        throw new HttpsError(
          "permission-denied",
          "The Gmail account must match the Google account signed in to the app."
        );
      }

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
        disconnectState: FieldValue.delete(),
        authorizationState: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      emitOperationalEvent({
        event: "gmail.oauth.connect",
        subsystem: "gmail",
        outcome: "success",
        severity: "INFO",
        code: "GMAIL_OAUTH_CONNECTED",
        uid,
        details: { scope: "gmail.readonly", consentVersion: CONSENT_VERSION },
      });

      return { connected: true, email: gmailEmail, consentVersion: CONSENT_VERSION };
    } catch (error) {
      emitOperationalEvent({
        event: "gmail.oauth.connect",
        subsystem: "gmail",
        outcome: "failure",
        severity: "ERROR",
        code: "GMAIL_OAUTH_CONNECT_FAILED",
        uid,
        details: {
          errorName: error instanceof Error ? error.name : typeof error,
          errorCode: error?.code || "UNKNOWN",
        },
      });
      throw error;
    }
  }
);
