"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { decryptToken } = require("./tokenCrypto");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");

const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const GMAIL_PUBSUB_TOPIC = "gmail-notifications";

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
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
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

    logger.info("Gmail watch started", { uid, topicName });
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
        logger.warn("Gmail users.stop failed", { uid, status: response.status });
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
  },
  async (event) => {
    const encoded = event.data?.message?.data || "";
    if (!encoded) {
      logger.warn("Gmail Pub/Sub event missing data");
      return;
    }

    let payload;
    try {
      payload = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
    } catch {
      logger.warn("Gmail Pub/Sub event contains invalid JSON");
      return;
    }

    const emailAddress = String(payload.emailAddress || "").trim().toLowerCase();
    const historyId = String(payload.historyId || "").trim();
    if (!emailAddress || !historyId) {
      logger.warn("Gmail Pub/Sub event missing mailbox identity/historyId");
      return;
    }

    const matches = await db
      .collection("gmailConnections")
      .where("email", "==", emailAddress)
      .limit(10)
      .get();

    if (matches.empty) {
      logger.warn("Gmail push received for unknown mailbox", { emailAddress });
      return;
    }

    await Promise.all(matches.docs.map((doc) => doc.ref.set({
      pendingHistoryId: historyId,
      lastPushNotificationAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true })));

    logger.info("Gmail push notification accepted", {
      emailAddress,
      historyId,
      matchedConnections: matches.size,
    });
  }
);
