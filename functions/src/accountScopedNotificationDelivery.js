"use strict";

const { getAuth } = require("firebase-admin/auth");
const logger = require("firebase-functions/logger");
const { safeNotificationEnvelope } = require("./notificationEnvelope");

async function accountExists(uid) {
  try {
    await getAuth().getUser(String(uid));
    return true;
  } catch (error) {
    if (error?.code === "auth/user-not-found") return false;
    throw error;
  }
}

function createAccountScopedDelivery(rawSendPushToUser, forcedType = "") {
  if (typeof rawSendPushToUser !== "function") {
    throw new TypeError("A push delivery function is required.");
  }
  return async (uid, message = {}) => {
    if (!await accountExists(uid)) {
      logger.info("Push suppressed because authenticated account no longer exists", { uid });
      return { attempted: 0, delivered: 0, removedInvalid: 0, accountDeleted: true };
    }
    const data = message && typeof message === "object" && message.data
      ? message.data
      : {};
    const type = forcedType || String(data.type || "UNKNOWN");
    return rawSendPushToUser(uid, safeNotificationEnvelope(uid, type, data));
  };
}

module.exports = { accountExists, createAccountScopedDelivery };
