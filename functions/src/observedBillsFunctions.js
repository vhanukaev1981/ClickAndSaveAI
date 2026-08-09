"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");

const db = getFirestore();
const REGION = "europe-west1";
const MAX_BILLS = 100;
const MAX_AUTHORITATIVE_SOURCE_IDS = 500;

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function normalizeObservedBill(data) {
  if (!data || typeof data !== "object") return null;
  const sourceMessageId = String(data.sourceMessageId || "").trim();
  const providerName = String(data.providerName || "").trim();
  const category = String(data.category || "other").trim() || "other";
  const monthlyCost = Number(data.monthlyCost);
  if (!sourceMessageId || !providerName || !Number.isFinite(monthlyCost) || monthlyCost <= 0) {
    return null;
  }
  return {
    sourceMessageId: sourceMessageId.slice(0, 300),
    providerName: providerName.slice(0, 160),
    category: category.slice(0, 80),
    monthlyCost,
    receivedDate: String(data.receivedDate || "").slice(0, 120),
    verificationStatus: String(data.verificationStatus || "UNVERIFIED_GMAIL_IMPORT").slice(0, 80),
  };
}

function documentData(item) {
  if (item && typeof item.data === "function") return item.data();
  return item;
}

function buildObservedBillsPayload(documents, now = new Date()) {
  const rawDocuments = Array.isArray(documents) ? documents : [];
  const sourceSetComplete = rawDocuments.length <= MAX_AUTHORITATIVE_SOURCE_IDS;
  const normalized = rawDocuments
    .slice(0, MAX_AUTHORITATIVE_SOURCE_IDS)
    .map(documentData)
    .map(normalizeObservedBill)
    .filter(Boolean);

  return {
    bills: normalized.slice(0, MAX_BILLS),
    sourceMessageIds: [...new Set(normalized.map((bill) => bill.sourceMessageId))],
    sourceSetComplete,
    sourceCount: normalized.length,
    generatedAt: now.toISOString(),
  };
}

exports.getObservedBills = onCall(
  { enforceAppCheck: true, region: REGION },
  async (request) => {
    const uid = requireAuth(request);
    const snapshot = await db
      .collection("users")
      .doc(uid)
      .collection("gmailInvoices")
      .orderBy("updatedAt", "desc")
      .limit(MAX_AUTHORITATIVE_SOURCE_IDS + 1)
      .get();
    return buildObservedBillsPayload(snapshot.docs);
  }
);

exports._normalizeObservedBill = normalizeObservedBill;
exports._buildObservedBillsPayload = buildObservedBillsPayload;
exports._REGION = REGION;
exports._MAX_BILLS = MAX_BILLS;
exports._MAX_AUTHORITATIVE_SOURCE_IDS = MAX_AUTHORITATIVE_SOURCE_IDS;
