"use strict";

const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { assertActiveAccount } = require("./accountAuthorization");
const coreFunctions = require("./index");

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function handlerRunner(handler, name) {
  const runner = typeof handler?.run === "function" ? handler.run.bind(handler) : handler;
  if (typeof runner !== "function") throw new Error(`${name} handler is unavailable.`);
  return runner;
}

exports.createProviderLead = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    await assertActiveAccount(uid);
    return handlerRunner(coreFunctions.createProviderLead, "createProviderLead")(request);
  }
);

Object.defineProperty(module.exports, "_handlerRunner", {
  value: handlerRunner,
  enumerable: false,
});
