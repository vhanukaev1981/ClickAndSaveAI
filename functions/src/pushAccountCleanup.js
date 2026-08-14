"use strict";

const functions = require("firebase-functions/v1");
const { getFirestore } = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

const db = getFirestore();
const BATCH_SIZE = 400;

async function deletePushRegistrations(uid) {
  const collection = db.collection("users").doc(uid).collection("pushTokens");
  let deleted = 0;
  while (true) {
    const snapshot = await collection.limit(BATCH_SIZE).get();
    if (snapshot.empty) break;
    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    deleted += snapshot.size;
    if (snapshot.size < BATCH_SIZE) break;
  }
  return deleted;
}

exports.onPushAccountDeleted = functions.auth.user().onDelete(async (user) => {
  const uid = String(user?.uid || "").trim();
  if (!uid) return;
  const deleted = await deletePushRegistrations(uid);
  logger.info("Deleted account push registrations cleaned", { uid, deleted });
});

Object.defineProperty(module.exports, "_deletePushRegistrations", {
  value: deletePushRegistrations,
  enumerable: false,
});
