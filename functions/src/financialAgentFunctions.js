"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const {
  buildFinancialContext,
  detectFinancialSignals,
} = require("./financialIntelligence");

const db = getFirestore();
const MAX_SOURCE_DOCS_PER_AGENT_RUN = 500;
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

async function commitWrites(writes) {
  for (let index = 0; index < writes.length; index += MAX_WRITES_PER_BATCH) {
    const batch = db.batch();
    for (const write of writes.slice(index, index + MAX_WRITES_PER_BATCH)) {
      batch.set(write.ref, write.data, { merge: true });
    }
    await batch.commit();
  }
}

async function persistFinancialState(uid, invoices) {
  const context = buildFinancialContext(invoices);
  const { insights, opportunities } = detectFinancialSignals(invoices);
  const userRef = db.collection("users").doc(uid);
  const writes = [];

  writes.push({
    ref: userRef.collection("financialContext").doc("current"),
    data: {
      ...context,
      activeInsightIds: insights.map((item) => item.id),
      activeOpportunityIds: opportunities.map((item) => item.id),
      generatedAt: FieldValue.serverTimestamp(),
      engineVersion: 1,
    },
  });

  for (const insight of insights) {
    writes.push({
      ref: userRef.collection("financialInsights").doc(insight.id),
      data: {
        ...insight,
        engineGenerated: true,
        updatedAt: FieldValue.serverTimestamp(),
        engineVersion: 1,
      },
    });
  }

  for (const opportunity of opportunities) {
    writes.push({
      ref: userRef.collection("opportunities").doc(opportunity.id),
      data: {
        ...opportunity,
        engineGenerated: true,
        updatedAt: FieldValue.serverTimestamp(),
        engineVersion: 1,
      },
    });
  }

  await commitWrites(writes);
  return {
    context,
    insightCount: insights.length,
    opportunityCount: opportunities.length,
  };
}

async function runFinancialAgentForUser(uid) {
  const invoices = await loadObservedInvoices(uid);
  const result = await persistFinancialState(uid, invoices);
  logger.info("Financial agent evaluation completed", {
    uid,
    invoiceCount: invoices.length,
    recurringServiceCount: result.context.recurringServiceCount,
    insightCount: result.insightCount,
    opportunityCount: result.opportunityCount,
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
      sourceCoverage: result.context.sourceCoverage,
      isCompleteHouseholdSpend: result.context.isCompleteHouseholdSpend,
    };
  }
);

exports._runFinancialAgentForUser = runFinancialAgentForUser;
exports._persistFinancialState = persistFinancialState;
exports._loadObservedInvoices = loadObservedInvoices;
