"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { assertActiveAccount } = require("./accountAuthorization");

const db = getFirestore();
const MAX_ACTIVITY_ITEMS = 100;

function text(value) {
  return String(value || "").trim();
}

function nullableFiniteNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function timestampIso(value) {
  if (!value) return "";
  try {
    if (typeof value.toDate === "function") return value.toDate().toISOString();
    if (typeof value === "object" && Number.isFinite(Number(value.seconds))) {
      return new Date(Number(value.seconds) * 1000).toISOString();
    }
    const date = value instanceof Date ? value : new Date(String(value));
    return Number.isFinite(date.getTime()) ? date.toISOString() : "";
  } catch {
    return "";
  }
}

function connectionEvents(connection = {}) {
  const events = [];
  const connectedAt = timestampIso(connection.consentedAt || connection.createdAt || connection.updatedAt);
  const scopes = Array.isArray(connection.scopes) ? connection.scopes.map(String) : [];
  const readOnlyConnected = Boolean(connection.encryptedRefreshToken) &&
    scopes.includes("https://www.googleapis.com/auth/gmail.readonly");
  if (readOnlyConnected && connectedAt) {
    events.push({
      id: "gmail-connected",
      type: "GMAIL_CONNECTED",
      timestamp: connectedAt,
      status: "CONNECTED",
      destination: "GMAIL_READONLY",
      providerName: null,
      category: null,
      observedAmount: null,
      verificationStatus: "SERVER_AUTHORIZED",
    });
  }

  const scanAt = timestampIso(connection.lastScanAt);
  if (scanAt) {
    events.push({
      id: "gmail-last-scan",
      type: "SCAN_COMPLETED",
      timestamp: scanAt,
      status: "COMPLETED",
      destination: "SERVER_FINANCIAL_STATE",
      providerName: null,
      category: null,
      observedAmount: null,
      verificationStatus: "SERVER_RECORDED",
    });
  }
  return events;
}

function billEvent(doc) {
  const data = typeof doc?.data === "function" ? (doc.data() || {}) : (doc?.data || doc || {});
  const id = text(doc?.id || data.id || data.sourceMessageId);
  const timestamp = timestampIso(data.receivedDate || data.createdAt || data.updatedAt);
  const amount = nullableFiniteNumber(data.monthlyCost);
  if (!id || !timestamp || !(amount > 0)) return null;
  return {
    id: `bill:${id}`,
    type: "BILL_DETECTED",
    timestamp,
    status: "OBSERVED",
    destination: "FINANCIAL_CORE",
    providerName: text(data.providerName) || null,
    category: text(data.category) || null,
    observedAmount: amount,
    verificationStatus: text(data.verificationStatus) || "UNVERIFIED_GMAIL_IMPORT",
  };
}

function commerceEvent(doc) {
  const data = typeof doc?.data === "function" ? (doc.data() || {}) : (doc?.data || doc || {});
  const id = text(doc?.id || data.id);
  const type = text(data.eventType);
  const timestamp = timestampIso(data.createdAt || data.updatedAt);
  if (!id || !type || !timestamp) return null;
  const amount = nullableFiniteNumber(
    data.realizedMonthlySaving ?? data.potentialMonthlySaving ?? data.actualCommissionAmount
  );
  return {
    id: `commerce:${id}`,
    type,
    timestamp,
    status: text(data.leadStatus || data.status || "RECORDED"),
    destination: "COMMERCE_LEDGER",
    providerName: text(data.providerName) || null,
    category: text(data.category) || null,
    observedAmount: amount,
    verificationStatus: text(data.offerVerificationState || "SERVER_RECORDED"),
  };
}

function sortNewest(events) {
  return events
    .filter(Boolean)
    .sort((left, right) => String(right.timestamp).localeCompare(String(left.timestamp)))
    .slice(0, MAX_ACTIVITY_ITEMS);
}

exports.getFinancialActivity = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
    await assertActiveAccount(uid);

    const userRef = db.collection("users").doc(uid);
    const [connectionSnapshot, invoiceSnapshot, commerceSnapshot] = await Promise.all([
      db.collection("gmailConnections").doc(uid).get(),
      userRef.collection("gmailInvoices").limit(MAX_ACTIVITY_ITEMS).get(),
      db.collection("commerceEvents").where("uid", "==", uid).limit(MAX_ACTIVITY_ITEMS).get(),
    ]);

    const events = [];
    if (connectionSnapshot.exists) events.push(...connectionEvents(connectionSnapshot.data() || {}));
    for (const doc of invoiceSnapshot.docs) events.push(billEvent(doc));
    for (const doc of commerceSnapshot.docs) events.push(commerceEvent(doc));

    return {
      events: sortNewest(events),
      sourceCoverage: ["GMAIL_READONLY", "SERVER_FINANCIAL_STATE", "COMMERCE_LEDGER"],
      // This projection is deliberately bounded. An empty list must never be represented as proof
      // that the account has never had activity.
      isCompleteHistory: false,
    };
  }
);

exports._connectionEvents = connectionEvents;
exports._billEvent = billEvent;
exports._commerceEvent = commerceEvent;
exports._sortNewest = sortNewest;
exports._timestampIso = timestampIso;
