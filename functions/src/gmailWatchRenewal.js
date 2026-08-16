"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret, defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { decryptToken } = require("./tokenCrypto");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");

const GMAIL_PUBSUB_TOPIC = "gmail-notifications";

function currentProjectId() {
  return String(process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || "").trim();
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

async function renewOne(doc, topicName) {
  const data = doc.data() || {};
  if (!data.encryptedRefreshToken || data.watchEnabled !== true) return { skipped: true };

  const accessToken = await refreshAccessToken(data.encryptedRefreshToken);
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
    throw new Error(`Gmail users.watch failed with ${response.status}`);
  }

  const update = {
    watchEnabled: true,
    watchTopic: topicName,
    watchExpiration: String(payload.expiration || ""),
    watchRenewedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  };

  // Preserve the last processed history id so renewal cannot skip unprocessed Gmail events.
  if (!data.watchHistoryId) {
    update.watchHistoryId = String(payload.historyId);
  }

  await doc.ref.set(update, { merge: true });
  return { skipped: false };
}

exports.renewGmailWatches = onSchedule(
  {
    schedule: "0 3 * * *",
    timeZone: "Asia/Jerusalem",
    region: "europe-west1",
    timeoutSeconds: 540,
    memory: "256MiB",
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey],
  },
  async () => {
    const projectId = currentProjectId();
    if (!projectId) throw new Error("Google Cloud project id is unavailable.");
    const topicName = `projects/${projectId}/topics/${GMAIL_PUBSUB_TOPIC}`;
    const snapshot = await db.collection("gmailConnections")
      .where("watchEnabled", "==", true)
      .get();

    let renewed = 0;
    let failed = 0;
    for (const doc of snapshot.docs) {
      try {
        const result = await renewOne(doc, topicName);
        if (!result.skipped) renewed += 1;
      } catch (error) {
        failed += 1;
        logger.error("Gmail watch renewal failed", {
          uid: doc.id,
          errorName: error instanceof Error ? error.name : typeof error,
          errorMessage: error instanceof Error ? error.message : String(error),
        });
      }
    }

    logger.info("Gmail watch renewal completed", {
      candidates: snapshot.size,
      renewed,
      failed,
    });

    if (failed > 0) {
      throw new Error(`Failed to renew ${failed} Gmail watch(es)`);
    }
  }
);
