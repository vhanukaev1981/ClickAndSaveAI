"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const {
  buildFinancialContext,
  detectFinancialSignals,
} = require("./financialIntelligence");
const { enrichOpportunityWithBestOffer } = require("./commerceEngine");
const {
  engineOpportunityPayload,
  isOpportunityLifecycleLocked,
  shouldRefreshCommerceMatch,
} = require("./opportunityLifecycle");

const db = getFirestore();
const MAX_SOURCE_DOCS_PER_AGENT_RUN = 500;
const MAX_PROVIDER_OFFERS_PER_RUN = 500;
const MAX_EXISTING_OPPORTUNITIES_PER_RUN = 500;
const MAX_WRITES_PER_BATCH = 400;
const MAX_HOME_ITEMS = 20;
const MAX_USERS_PER_SWEEP = 250;
const SWEEP_CONCURRENCY = 5;
const ENGINE_VERSION = 4;

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

async function loadExistingOpportunityStates(userRef) {
  const snapshot = await userRef
    .collection("opportunities")
    .limit(MAX_EXISTING_OPPORTUNITIES_PER_RUN)
    .get();
  return new Map(snapshot.docs.map((doc) => [doc.id, { id: doc.id, ...doc.data() }]));
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
  const existingById = await loadExistingOpportunityStates(userRef);
  const lockedExistingIds = [...existingById.values()]
    .filter(isOpportunityLifecycleLocked)
    .map((item) => item.id);
  const activeOpportunityIds = [...new Set([
    ...opportunities.map((item) => item.id),
    ...lockedExistingIds,
  ])];
  const writes = [];

  writes.push({
    ref: userRef.collection("financialContext").doc("current"),
    data: {
      ...context,
      activeInsightIds: insights.map((item) => item.id),
      activeOpportunityIds,
      generatedAt: FieldValue.serverTimestamp(),
      engineVersion: ENGINE_VERSION,
    },
  });

  for (const insight of insights) {
    writes.push({
      ref: userRef.collection("financialInsights").doc(insight.id),
      data: {
        ...insight,
        engineGenerated: true,
        updatedAt: FieldValue.serverTimestamp(),
        engineVersion: ENGINE_VERSION,
      },
    });
  }

  for (let index = 0; index < opportunities.length; index += 1) {
    const opportunity = opportunities[index];
    const enriched = enrichedOpportunities[index];
    const existing = existingById.get(opportunity.id) || null;
    const locked = isOpportunityLifecycleLocked(existing);
    const enginePayload = engineOpportunityPayload(opportunity, existing);

    writes.push({
      ref: userRef.collection("opportunities").doc(opportunity.id),
      data: {
        ...enginePayload,
        engineGenerated: true,
        engineLastObservedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
        engineVersion: ENGINE_VERSION,
      },
    });

    const commission = enriched?.matchedOffer?.commercial;
    if (
      !locked &&
      shouldRefreshCommerceMatch(existing) &&
      enriched?.matchedOffer?.offerId
    ) {
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
          matchStatus: "VERIFIED_MATCH",
          lastMatchedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
          engineVersion: ENGINE_VERSION,
        },
      });
    }
  }

  await commitWrites(writes);
  return {
    context,
    insightCount: insights.length,
    opportunityCount: activeOpportunityIds.length,
    detectedOpportunityCount: opportunities.length,
    matchedOfferCount: enrichedOpportunities.filter((item) => item.matchedOffer).length,
    trackableCommerceMatchCount: enrichedOpportunities.filter(
      (item) => item.matchedOffer?.commercial?.agreementActive === true
    ).length,
  };
}

async function runFinancialAgentForUser(uid, providerOffersOverride = null) {
  const invoicesPromise = loadObservedInvoices(uid);
  const offersPromise = providerOffersOverride == null
    ? loadProviderOffers()
    : Promise.resolve(providerOffersOverride);
  const [invoices, providerOffers] = await Promise.all([invoicesPromise, offersPromise]);
  const result = await persistFinancialState(uid, invoices, providerOffers);
  logger.info("Financial agent evaluation completed", {
    uid,
    invoiceCount: invoices.length,
    providerOfferCount: providerOffers.length,
    recurringServiceCount: result.context.recurringServiceCount,
    insightCount: result.insightCount,
    opportunityCount: result.opportunityCount,
    detectedOpportunityCount: result.detectedOpportunityCount,
    matchedOfferCount: result.matchedOfferCount,
    trackableCommerceMatchCount: result.trackableCommerceMatchCount,
  });
  return result;
}

async function loadDocsByIds(collectionRef, ids) {
  const safeIds = Array.isArray(ids) ? ids.slice(0, MAX_HOME_ITEMS) : [];
  const snapshots = await Promise.all(safeIds.map((id) => collectionRef.doc(String(id)).get()));
  return snapshots.filter((snapshot) => snapshot.exists).map((snapshot) => ({
    id: snapshot.id,
    ...snapshot.data(),
  }));
}

function nullableFiniteNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function homeOpportunity(item) {
  return {
    id: String(item.id || ""),
    type: String(item.type || ""),
    status: String(item.status || "OPEN"),
    providerName: String(item.providerName || ""),
    category: String(item.category || ""),
    currentMonthlyCost: Number(item.currentMonthlyCost || 0),
    previousMonthlyCost: Number(item.previousMonthlyCost || 0),
    monthlyIncrease: Number(item.monthlyIncrease || 0),
    percentIncrease: Number(item.percentIncrease || 0),
    potentialMonthlySaving: nullableFiniteNumber(item.potentialMonthlySaving),
    potentialAnnualSaving: nullableFiniteNumber(item.potentialAnnualSaving),
    recommendationAction: String(item.recommendationAction || ""),
    matchedOffer: item.matchedOffer ? {
      offerId: String(item.matchedOffer.offerId || ""),
      providerName: String(item.matchedOffer.providerName || ""),
      monthlyPrice: Number(item.matchedOffer.monthlyPrice || 0),
      serviceType: String(item.matchedOffer.serviceType || ""),
      verifiedAt: String(item.matchedOffer.verifiedAt || ""),
      validUntil: String(item.matchedOffer.validUntil || ""),
      userFitScore: Number(item.matchedOffer.userFitScore || 0),
    } : null,
  };
}

function homeInsight(item) {
  return {
    id: String(item.id || ""),
    type: String(item.type || ""),
    providerName: String(item.providerName || ""),
    category: String(item.category || ""),
    currentMonthlyCost: Number(item.currentMonthlyCost || 0),
    previousMonthlyCost: Number(item.previousMonthlyCost || 0),
    monthlyIncrease: Number(item.monthlyIncrease || 0),
    percentIncrease: Number(item.percentIncrease || 0),
    severity: String(item.severity || "INFO"),
  };
}

async function runSweep() {
  const [connections, providerOffers] = await Promise.all([
    db.collection("gmailConnections").limit(MAX_USERS_PER_SWEEP).get(),
    loadProviderOffers(),
  ]);
  const userIds = connections.docs.map((doc) => doc.id);
  let successCount = 0;
  let failureCount = 0;

  for (let index = 0; index < userIds.length; index += SWEEP_CONCURRENCY) {
    const group = userIds.slice(index, index + SWEEP_CONCURRENCY);
    const results = await Promise.allSettled(
      group.map((uid) => runFinancialAgentForUser(uid, providerOffers))
    );
    for (const result of results) {
      if (result.status === "fulfilled") successCount += 1;
      else failureCount += 1;
    }
  }

  logger.info("Scheduled financial agent sweep completed", {
    userCount: userIds.length,
    providerOfferCount: providerOffers.length,
    successCount,
    failureCount,
  });
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

exports.financialAgentSweep = onSchedule(
  {
    schedule: "every 4 hours",
    timeZone: "Asia/Jerusalem",
    region: "europe-west1",
    memory: "512MiB",
    timeoutSeconds: 540,
  },
  runSweep
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

exports.getFinancialHome = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const userRef = db.collection("users").doc(uid);
    let contextSnapshot = await userRef.collection("financialContext").doc("current").get();
    if (!contextSnapshot.exists) {
      await runFinancialAgentForUser(uid);
      contextSnapshot = await userRef.collection("financialContext").doc("current").get();
    }

    const context = contextSnapshot.data() || {};
    const [insights, opportunities] = await Promise.all([
      loadDocsByIds(userRef.collection("financialInsights"), context.activeInsightIds),
      loadDocsByIds(userRef.collection("opportunities"), context.activeOpportunityIds),
    ]);

    return {
      context: {
        sourceCoverage: Array.isArray(context.sourceCoverage) ? context.sourceCoverage.map(String) : [],
        isCompleteHouseholdSpend: context.isCompleteHouseholdSpend === true,
        observedRecurringMonthlySpend: Number(context.observedRecurringMonthlySpend || 0),
        recurringServiceCount: Number(context.recurringServiceCount || 0),
        recurringServices: Array.isArray(context.recurringServices)
          ? context.recurringServices.slice(0, MAX_HOME_ITEMS)
          : [],
        categories: Array.isArray(context.categories) ? context.categories.slice(0, MAX_HOME_ITEMS) : [],
      },
      insights: insights.map(homeInsight),
      opportunities: opportunities.map(homeOpportunity),
    };
  }
);

exports._runFinancialAgentForUser = runFinancialAgentForUser;
exports._persistFinancialState = persistFinancialState;
exports._loadObservedInvoices = loadObservedInvoices;
exports._loadProviderOffers = loadProviderOffers;
exports._loadExistingOpportunityStates = loadExistingOpportunityStates;
exports._homeOpportunity = homeOpportunity;
exports._homeInsight = homeInsight;
exports._nullableFiniteNumber = nullableFiniteNumber;
exports._runSweep = runSweep;
