"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { buildProviderDispatchPayload } = require("./providerDispatch");
const { isTrackableCommercialOffer } = require("./commercialPolicy");
const { normalizeHandoffTruth } = require("./handoffTruth");

const db = getFirestore();

function buildDispatchQueueRecord(leadId, lead, commerceMatch) {
  if (!lead || typeof lead !== "object") return null;
  if (!commerceMatch || typeof commerceMatch !== "object") return null;
  if (String(lead.source || "") !== "AI_PROACTIVE_OPPORTUNITY") return null;
  if (String(lead.status || "").toUpperCase() !== "NEW") return null;

  const uid = String(lead.uid || "").trim();
  const opportunityId = String(lead.opportunityId || "").trim();
  const offerId = String(lead.offerId || "").trim();
  if (!uid || !opportunityId || !offerId) return null;
  if (String(commerceMatch.uid || "").trim() !== uid) return null;
  if (String(commerceMatch.opportunityId || "").trim() !== opportunityId) return null;
  if (String(commerceMatch.offerId || "").trim() !== offerId) return null;
  if (String(commerceMatch.leadId || "").trim() !== String(leadId || "").trim()) return null;
  if (!isTrackableCommercialOffer({
    commercialAgreementActive: commerceMatch.agreementActive === true,
    commissionType: commerceMatch.commissionType,
    commissionValue: commerceMatch.commissionValue,
  })) return null;

  const truth = normalizeHandoffTruth(lead);
  if (truth.consentState !== "CONSENTED" || truth.requestState !== "REQUEST_CREATED") return null;

  const payload = buildProviderDispatchPayload({ id: leadId, ...lead });
  if (!payload) return null;

  return {
    leadId: String(leadId),
    uid,
    opportunityId,
    offerId,
    providerName: String(lead.requestedProvider || ""),
    category: String(lead.category || ""),
    commerceMatchId: `${uid}_${opportunityId}`,
    payload,
    status: "PENDING",
    attempts: 0,
    consentState: truth.consentState,
    requestState: truth.requestState,
    deliveryAttemptState: truth.deliveryAttemptState,
    submissionState: truth.submissionState,
    deliveryState: truth.deliveryState,
    providerContactState: truth.providerContactState,
    completionState: truth.completionState,
    savingRealizationState: truth.savingRealizationState,
  };
}

exports.onAttributedProviderLeadCreated = onDocumentWritten(
  {
    document: "providerLeads/{leadId}",
    region: "europe-west1",
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;
    const leadId = String(event.params.leadId || "").trim();
    const lead = after.data() || {};
    if (!leadId || String(lead.status || "").toUpperCase() !== "NEW") return;
    if (String(lead.source || "") !== "AI_PROACTIVE_OPPORTUNITY") return;

    const uid = String(lead.uid || "").trim();
    const opportunityId = String(lead.opportunityId || "").trim();
    if (!uid || !opportunityId) return;

    const commerceRef = db.collection("commerceMatches").doc(`${uid}_${opportunityId}`);
    const queueRef = db.collection("providerDispatchQueue").doc(leadId);
    let queued = false;

    await db.runTransaction(async (transaction) => {
      const [commerceSnapshot, existingQueue] = await Promise.all([
        transaction.get(commerceRef),
        transaction.get(queueRef),
      ]);
      if (existingQueue.exists || !commerceSnapshot.exists) return;

      const record = buildDispatchQueueRecord(leadId, lead, commerceSnapshot.data() || {});
      if (!record) return;

      transaction.create(queueRef, {
        ...record,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
        nextAttemptAt: FieldValue.serverTimestamp(),
      });
      queued = true;
    });

    logger.info("Provider dispatch queue evaluation completed", {
      leadId,
      uid,
      opportunityId,
      queued,
      deliveryState: queued ? "NOT_CONFIRMED" : "NOT_QUEUED",
    });
  }
);

exports._buildDispatchQueueRecord = buildDispatchQueueRecord;
