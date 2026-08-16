"use strict";

const crypto = require("node:crypto");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");

const db = getFirestore();

function text(value) {
  return String(value || "").trim();
}

function eventId(parts) {
  return crypto.createHash("sha256").update(parts.join("|")).digest("hex");
}

function baseEvent(eventType, source) {
  if (!source || typeof source !== "object") return null;
  const uid = text(source.uid);
  const opportunityId = text(source.opportunityId);
  const offerId = text(source.offerId);
  if (!uid || !opportunityId || !offerId) return null;
  return {
    eventType,
    uid,
    opportunityId,
    offerId,
    providerName: text(source.providerName || source.requestedProvider),
    category: text(source.category),
  };
}

function commerceMatchEvent(matchId, data) {
  if (text(data?.matchStatus) !== "VERIFIED_MATCH") return null;
  const event = baseEvent("OFFER_MATCHED", data);
  if (!event) return null;
  return {
    id: eventId(["OFFER_MATCHED", matchId, event.offerId]),
    data: {
      ...event,
      commerceMatchId: text(matchId),
      potentialMonthlySaving: Number(data.potentialMonthlySaving) || null,
      potentialAnnualSaving: Number(data.potentialAnnualSaving) || null,
      actionMode: text(data.actionMode || "VIEW_ONLY"),
    },
  };
}

function leadStatusEvent(leadId, data) {
  if (text(data?.source) !== "AI_PROACTIVE_OPPORTUNITY") return null;
  const status = text(data?.status).toUpperCase();
  if (!status) return null;
  const event = baseEvent(status === "NEW" ? "LEAD_CREATED" : `LEAD_${status}`, data);
  if (!event) return null;
  const actualCommissionAmount = Number(data.actualCommissionAmount);
  return {
    id: eventId([event.eventType, leadId, event.offerId]),
    data: {
      ...event,
      leadId: text(leadId),
      leadStatus: status,
      ...(status === "COMMISSION_CONFIRMED" && Number.isFinite(actualCommissionAmount)
        ? { actualCommissionAmount, commissionCurrency: text(data.commissionCurrency || "ILS") }
        : {}),
    },
  };
}

function dispatchQueuedEvent(leadId, data) {
  if (text(data?.status).toUpperCase() !== "PENDING") return null;
  const event = baseEvent("DISPATCH_QUEUED", data);
  if (!event) return null;
  return {
    id: eventId(["DISPATCH_QUEUED", leadId, event.offerId]),
    data: {
      ...event,
      leadId: text(leadId),
      commerceMatchId: text(data.commerceMatchId),
    },
  };
}

async function writeEventOnce(event) {
  if (!event?.id || !event?.data) return false;
  const ref = db.collection("commerceEvents").doc(event.id);
  let created = false;
  await db.runTransaction(async (transaction) => {
    const existing = await transaction.get(ref);
    if (existing.exists) return;
    transaction.create(ref, {
      ...event.data,
      createdAt: FieldValue.serverTimestamp(),
      schemaVersion: 1,
    });
    created = true;
  });
  return created;
}

exports.onCommerceMatchFunnelEvent = onDocumentWritten(
  {
    document: "commerceMatches/{matchId}",
    region: "europe-west1",
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;
    const current = commerceMatchEvent(String(event.params.matchId || ""), after.data() || {});
    if (!current) return;
    const created = await writeEventOnce(current);
    if (created) logger.info("Commerce funnel event recorded", { eventType: current.data.eventType });
  }
);

exports.onProviderLeadFunnelEvent = onDocumentWritten(
  {
    document: "providerLeads/{leadId}",
    region: "europe-west1",
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;
    const beforeStatus = text(event.data?.before?.data()?.status).toUpperCase();
    const afterStatus = text(after.data()?.status).toUpperCase();
    if (beforeStatus && beforeStatus === afterStatus) return;
    const current = leadStatusEvent(String(event.params.leadId || ""), after.data() || {});
    if (!current) return;
    const created = await writeEventOnce(current);
    if (created) logger.info("Commerce funnel event recorded", { eventType: current.data.eventType });
  }
);

exports.onProviderDispatchFunnelEvent = onDocumentWritten(
  {
    document: "providerDispatchQueue/{leadId}",
    region: "europe-west1",
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    if (event.data?.before?.exists || !event.data?.after?.exists) return;
    const current = dispatchQueuedEvent(
      String(event.params.leadId || ""),
      event.data.after.data() || {}
    );
    if (!current) return;
    const created = await writeEventOnce(current);
    if (created) logger.info("Commerce funnel event recorded", { eventType: current.data.eventType });
  }
);

exports._commerceMatchEvent = commerceMatchEvent;
exports._leadStatusEvent = leadStatusEvent;
exports._dispatchQueuedEvent = dispatchQueuedEvent;
exports._eventId = eventId;
