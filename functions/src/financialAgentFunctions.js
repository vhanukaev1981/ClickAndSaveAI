"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const {
  buildFinancialContext,
  detectFinancialSignals,
} = require("./financialIntelligence");
const { enrichOpportunityWithBestOffer } = require("./commerceEngine");

const db = getFirestore();
const MAX_SOURCE_DOCS_PER_AGENT_RUN = 500;
const MAX_PROVIDER_OFFERS_PER_RUN = 500;
const MAX_WRITES_PER_BATCH = 400;

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function collectInvoicesFromImportDoc(data) {
  const raw = Array.isArray(data?.invoices)
    ? data.invoices
    : (data?.invoice ? [data.invoice] : []);
  return raw.filter((invoice) => invoice && typeof invoice === "object");
}

async function loadObservedInvoices(uid) {
  const userRef = db.collection("users").doc(uid);
  const [invoiceSnapshot, importSnapshot] = await Promise.all([
    userRef.collection("gmailInvoices").limit(MAX_SOURCE_DOCS_PER_AGENT_RUN).get(),
    userRef.collection("gmailMessageImports").limit(MAX_SOURCE_DOCS_PER_AGENT_RUN).get(),
  ]);

  const bySourceId = new Map();
  for (const doc of importSnapshot.docs) {
    for (const invoice of collectInvoicesFromImportDoc(doc.data())) {
      const sourceMessageId = String(invoice.sourceMessageId || "").trim();
      if (sourceMessageId) bySourceId.set(sourceMessageId, invoice);
    }
  }
  for (const doc of invoiceSnapshot.docs) {
    const invoice = doc.data();
    const sourceMessageId = String(invoice.sourceMessageId || "").trim();
    if (sourceMessageId) bySourceId.set(sourceMessageId, invoice);
  }
  return [...bySourceId.values()];
}

async function loadProviderOffers() {
  const snapshot = await db.collection("providerOffers").limit(MAX_PROVIDER_OFFERS_PER_RUN).get();
  return snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}

async function commitWrites(writes) {
  for (let index = 0; index < writes.length; index += MAX_WRITES_PER_BATCH) {
    const batch = db.batch();
    for (const write of writes.slice(index, index + MAX_WRITES_PER_BATCH)) {
      batch.set(write.ref, write.data, { merge: true });
    }
    await batch.commit();
  }
}

function opportunityWithoutCommissionTerms(opportunity) {
  if (!opportunity?.matchedOffer) return opportunity;
  const { commercial, ...offerForUserState } = opportunity.matchedOffer;
  return {
    ...opportunity,
    matchedOffer: offerForUserState,
    commercial: {
      ...(opportunity.commercial || {}),
      partnerMatchStatus: commercial?.agreementActive ? "ACTIVE_PARTNER_MATCH" : "NO_ACTIVE_PARTNER_AGREEMENT",
      commissionStatus: commercial?.agreementActive ? "TRACKABLE" : "NOT_TRACKABLE",
    },
  };
}

async function persistFinancialState(uid, invoices, providerOffers = []) {
  const context = buildFinancialContext(invoices);
  const { insights, opportunities: detectedOpportunities } = detectFinancialSignals(invoices);
  const enrichedOpportunities = detectedOpportunities.map((opportunity) =>
    enrichOpportunityWithBestOffer(opportunity, providerOffers, { country: "IL" })
  );
  const opportunities = enrichedOpportunities.map(opportunityWithoutCommissionTerms);
  const userRef = db.collection("users").doc(uid);
  const writes = [];

  writes.push({
    ref: userRef.collection("financialContext").doc("current"),
    data: {
      ...context,
      activeInsightIds: insights.map((item) => item.id),
      activeOpportunityIds: opportunities.map((item) => item.id),
      generatedAt: FieldValue.serverTimestamp(),
      engineVersion: 2,
    },
  });

  for (const insight of insights) {
    writes.push({
      ref: userRef.collection("financialInsights").doc(insight.id),
      data: {
        ...insight,
        engineGenerated: true,
        updatedAt: FieldValue.serverTimestamp(),
        engineVersion: 2,
      },
    });
  }

  for (let index = 0; index < opportunities.length; index += 1) {
    const opportunity = opportunities[index];
    const enriched = enrichedOpportunities[index];
    writes.push({
      ref: userRef.collection("opportunities").doc(opportunity.id),
      data: {
        ...opportunity,
        engineGenerated: true,
        updatedAt: FieldValue.serverTimestamp(),
        engineVersion: 2,
      },
    });

    const commission = enriched?.matchedOffer?.commercial;
    if (enriched?.matchedOffer?.offerId) {
      writes.push({
        ref: db.collection("commerceMatches").doc(`${uid}_${opportunity.id}`),
        data: {
          uid,
          opportunityId: opportunity.id,
          offerId: enriched.matchedOffer.offerId,
          providerName: enriched.matchedOffer.providerName,
          monthlyPrice: enriched.matchedOffer.monthlyPrice,
          potentialMonthlySaving: enriched.potentialMonthlySaving,
          potentialAnnualSaving: enriched.potentialAnnualSaving,
          agreementActive: commission?.agreementActive === true,
          commissionType: commission?.commissionType || "NONE",
          commissionValue: commission?.commissionValue ?? null,
          attributionStatus: "MATCHED_NOT_ACTED",
          updatedAt: FieldValue.serverTimestamp(),
          engineVersion: 2,
        },
      });
    }
  }

  await commitWrites(writes);
  return {
    context,
    insightCount: insights.length,
    opportunityCount: opportunities.length,
    matchedOfferCount: enrichedOpportunities.filter((item) => item.matchedOffer).length,
    trackableCommerceMatchCount: enrichedOpportunities.filter(
      (item) => item.matchedOffer?.commercial?.agreementActive === true
    ).length,
  };
}

async function runFinancialAgentForUser(uid) {
  const [invoices, providerOffers] = await Promise.all([
    loadObservedInvoices(uid),
    loadProviderOffers(),
  ]);
  const result = await persistFinancialState(uid, invoices, providerOffers);
  logger.info("Financial agent evaluation completed", {
    uid,
    invoiceCount: invoices.length,
    providerOfferCount: providerOffers.length,
    recurringServiceCount: result.context.recurringServiceCount,
    insightCount: result.insightCount,
    opportunityCount: result.opportunityCount,
    matchedOfferCount: result.matchedOfferCount,
    trackableCommerceMatchCount: result.trackableCommerceMatchCount,
  });
  return result;
}

exports.onGmailFinancialDataChanged = onDocumentWritten(
  {
    document: "users/{uid}/gmailMessageImports/{messageId}",
    region: "europe-west1",
    memory: "256MiB",
    timeoutSeconds: 120,
  },
  async (event) => {
    const uid = String(event.params.uid || "").trim();
    if (!uid) return;
    await runFinancialAgentForUser(uid);
  }
);

exports.refreshFinancialAgent = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const result = await runFinancialAgentForUser(uid);
    return {
      observedRecurringMonthlySpend: result.context.observedRecurringMonthlySpend,
      recurringServiceCount: result.context.recurringServiceCount,
      insightCount: result.insightCount,
      opportunityCount: result.opportunityCount,
      matchedOfferCount: result.matchedOfferCount,
      trackableCommerceMatchCount: result.trackableCommerceMatchCount,
      sourceCoverage: result.context.sourceCoverage,
      isCompleteHouseholdSpend: result.context.isCompleteHouseholdSpend,
    };
  }
);

exports._runFinancialAgentForUser = runFinancialAgentForUser;
exports._persistFinancialState = persistFinancialState;
exports._loadObservedInvoices = loadObservedInvoices;
exports._loadProviderOffers = loadProviderOffers;
