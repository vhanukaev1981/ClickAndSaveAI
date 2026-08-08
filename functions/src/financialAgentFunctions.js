"use strict";

const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const {
  buildFinancialContext,
  detectFinancialSignals,
} = require("./financialIntelligence");

const db = getFirestore();
const MAX_INVOICES_PER_AGENT_RUN = 500;

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

async function loadObservedInvoices(uid) {
  const snapshot = await db
    .collection("users")
    .doc(uid)
    .collection("gmailInvoices")
    .limit(MAX_INVOICES_PER_AGENT_RUN)
    .get();

  return snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}

async function persistFinancialState(uid, invoices) {
  const context = buildFinancialContext(invoices);
  const { insights, opportunities } = detectFinancialSignals(invoices);
  const userRef = db.collection("users").doc(uid);
  const batch = db.batch();

  batch.set(userRef.collection("financialContext").doc("current"), {
    ...context,
    generatedAt: FieldValue.serverTimestamp(),
    engineVersion: 1,
  }, { merge: true });

  for (const insight of insights) {
    batch.set(userRef.collection("financialInsights").doc(insight.id), {
      ...insight,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
      engineVersion: 1,
    }, { merge: true });
  }

  for (const opportunity of opportunities) {
    batch.set(userRef.collection("opportunities").doc(opportunity.id), {
      ...opportunity,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp(),
      engineVersion: 1,
    }, { merge: true });
  }

  await batch.commit();
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
