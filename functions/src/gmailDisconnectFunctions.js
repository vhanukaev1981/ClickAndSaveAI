"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { defineSecret, defineString } = require("firebase-functions/params");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const { assertActiveAccount } = require("./accountAuthorization");
const { providerCleanupConfirmed } = require("./gmailDisconnectPolicy");
const { decryptToken } = require("./tokenCrypto");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

async function refreshAccessToken(refreshToken) {
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
  if (!response.ok || !payload.access_token) throw new Error("Google access-token refresh failed.");
  return String(payload.access_token);
}

async function disconnectGmailForUid(uid) {
  const ref = db.collection("gmailConnections").doc(uid);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    return {
      connected: false,
      idempotent: true,
      ingestionStopped: true,
      watchStopStatus: "NO_CONNECTION",
      oauthRevocationStatus: "NO_CONNECTION",
      externalCleanupConfirmed: true,
    };
  }

  const data = snapshot.data() || {};
  const isRetry = data.disconnectState === "RETRY_REQUIRED";

  // Disable every server ingestion path first. The encrypted credential is retained only when
  // provider revocation is unconfirmed so an explicit retry can finish cleanup safely.
  await ref.set({
    watchEnabled: false,
    scopes: [],
    authorizationState: "DISCONNECTING",
    disconnectState: "DISCONNECTING",
    disconnectStartedAt: data.disconnectStartedAt || FieldValue.serverTimestamp(),
    pendingHistoryId: FieldValue.delete(),
    watchHistoryId: FieldValue.delete(),
    watchExpiration: FieldValue.delete(),
    historyRecoveryRequired: false,
    historyRecoveryReason: FieldValue.delete(),
    recoveryBaselineHistoryId: FieldValue.delete(),
    incrementalLeaseOwner: FieldValue.delete(),
    incrementalLeaseUntilMs: FieldValue.delete(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  let watchStopStatus = "NO_CREDENTIAL";
  let oauthRevocationStatus = "NO_CREDENTIAL";
  let refreshToken = "";
  const encryptedRefreshToken = String(data.encryptedRefreshToken || "").trim();
  if (encryptedRefreshToken) {
    try {
      refreshToken = decryptToken(encryptedRefreshToken, oauthTokenEncryptionKey.value());
    } catch (error) {
      watchStopStatus = "UNCONFIRMED_CREDENTIAL_ERROR";
      oauthRevocationStatus = "UNCONFIRMED_CREDENTIAL_ERROR";
      logger.warn("Stored Google credential could not be opened during disconnect", { uid });
    }
  }

  if (refreshToken) {
    try {
      const accessToken = await refreshAccessToken(refreshToken);
      const stopResponse = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/stop", {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      watchStopStatus = stopResponse.ok ? "CONFIRMED" : `UNCONFIRMED_HTTP_${stopResponse.status}`;
    } catch (error) {
      watchStopStatus = "UNCONFIRMED_EXTERNAL_ERROR";
      logger.warn("Gmail watch stop was not confirmed", { uid });
    }

    try {
      const revokeResponse = await fetch(
        `https://oauth2.googleapis.com/revoke?token=${encodeURIComponent(refreshToken)}`,
        { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" } }
      );
      oauthRevocationStatus = revokeResponse.ok
        ? "CONFIRMED"
        : (revokeResponse.status === 400
          ? "CONFIRMED_OR_ALREADY_INVALID"
          : `UNCONFIRMED_HTTP_${revokeResponse.status}`);
    } catch (error) {
      oauthRevocationStatus = "UNCONFIRMED_EXTERNAL_ERROR";
      logger.warn("Google OAuth revoke was not confirmed", { uid });
    }
  }

  const externalCleanupConfirmed = providerCleanupConfirmed(
    watchStopStatus,
    oauthRevocationStatus
  );

  if (externalCleanupConfirmed) {
    await ref.delete();
  } else {
    await ref.set({
      watchEnabled: false,
      scopes: [],
      authorizationState: "DISCONNECTED_PENDING_PROVIDER_CLEANUP",
      disconnectState: "RETRY_REQUIRED",
      lastWatchStopStatus: watchStopStatus,
      lastOauthRevocationStatus: oauthRevocationStatus,
      lastDisconnectAttemptAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }

  return {
    connected: false,
    idempotent: isRetry,
    ingestionStopped: true,
    watchStopStatus,
    oauthRevocationStatus,
    externalCleanupConfirmed,
  };
}

exports.disconnectGmail = onCall(
  { enforceAppCheck: true, secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey] },
  async (request) => {
    const uid = requireAuth(request);
    await assertActiveAccount(uid);
    return disconnectGmailForUid(uid);
  }
);

Object.defineProperty(module.exports, "_disconnectGmailForUid", {
  value: disconnectGmailForUid,
  enumerable: false,
});
