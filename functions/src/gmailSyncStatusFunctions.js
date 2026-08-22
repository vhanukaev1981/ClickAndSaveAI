"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { ACTIVE_GMAIL_PARSER_VERSION } = require("./gmailParserVersion");
const { assertActiveAccount } = require("./accountAuthorization");
const financialAgent = require("./financialAgentFunctions");

const db = getFirestore();
const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const INITIAL_GMAIL_LOOKBACK = "6m";
const STAGING_PROJECT_ID = "clickandsaveai-staging";
const MAX_HOME_ITEMS = 20;
const MAX_ACTIVITY_IMPORTS = 50;
const MAX_ACTIVITY_EVENTS = 100;
const MAX_RECOVERY_DIAGNOSTIC_IMPORTS = 1000;

function requireAuth(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  return uid;
}

function nullableFiniteNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function timestampToIso(value) {
  if (!value) return "";
  if (typeof value === "string") {
    const millis = Date.parse(value);
    return Number.isFinite(millis) ? new Date(millis).toISOString() : "";
  }
  if (value instanceof Date) return value.toISOString();
  if (typeof value.toDate === "function") return value.toDate().toISOString();
  return "";
}

function buildGmailSyncStatus(connection) {
  const data = connection && typeof connection === "object" ? connection : {};
  const connected = Array.isArray(data.scopes) &&
    data.scopes.includes(GMAIL_READONLY_SCOPE) &&
    Boolean(data.encryptedRefreshToken);
  const storedParserVersion = Math.max(0, Number(data.parserVersion || 0));
  const initialBackfillCompleted = data.initialBackfillCompleted === true ||
    Boolean(data.initialBackfillCompletedAt);
  return {
    connected,
    storedParserVersion,
    activeParserVersion: ACTIVE_GMAIL_PARSER_VERSION,
    // This backwards-compatible flag means "the one-time initial baseline is
    // still required". Parser upgrades never reopen the six-month mailbox.
    upgradeRequired: connected && !initialBackfillCompleted,
    lookback: INITIAL_GMAIL_LOOKBACK,
  };
}

function recoveryCandidateCount(data) {
  if (Array.isArray(data?.candidates)) return data.candidates.length;
  if (Array.isArray(data?.invoices)) return data.invoices.length;
  return data?.invoice && typeof data.invoice === "object" ? 1 : 0;
}

function buildGmailRecoveryState({
  connection = null,
  authoritativeInvoiceCount = 0,
  gmailMessageImportCount = null,
  importDocs = [],
  importsTruncated = false,
} = {}) {
  const data = connection && typeof connection === "object" ? connection : {};
  const safeImports = Array.isArray(importDocs) ? importDocs : [];
  const parserDistribution = {};
  let storedCandidateCount = 0;

  for (const item of safeImports) {
    const rawVersion = Number(item?.parserVersion || 0);
    const parserVersion = Number.isFinite(rawVersion) && rawVersion >= 0
      ? Math.floor(rawVersion)
      : 0;
    const key = String(parserVersion);
    parserDistribution[key] = (parserDistribution[key] || 0) + 1;
    storedCandidateCount += recoveryCandidateCount(item);
  }

  const hasExactImportCount = gmailMessageImportCount !== null &&
    gmailMessageImportCount !== undefined &&
    gmailMessageImportCount !== "";
  const exactImportCount = hasExactImportCount ? Number(gmailMessageImportCount) : NaN;
  return {
    initialBackfillCompleted: data.initialBackfillCompleted === true ||
      Boolean(data.initialBackfillCompletedAt),
    initialBackfillCompletedAt: timestampToIso(data.initialBackfillCompletedAt),
    storedParserVersion: Math.max(0, Number(data.parserVersion || 0)),
    activeParserVersion: ACTIVE_GMAIL_PARSER_VERSION,
    authoritativeInvoiceCount: Math.max(0, Number(authoritativeInvoiceCount || 0)),
    gmailMessageImportCount: Number.isFinite(exactImportCount) && exactImportCount >= 0
      ? Math.floor(exactImportCount)
      : safeImports.length,
    gmailMessageImportsParserVersionDistribution: parserDistribution,
    storedCandidateCount,
    importsTruncated: importsTruncated === true,
  };
}

async function loadGmailRecoveryState(uid, connection) {
  const userRef = db.collection("users").doc(uid);
  const invoiceRef = userRef.collection("gmailInvoices");
  const importRef = userRef.collection("gmailMessageImports");
  const [invoiceCountSnapshot, importCountSnapshot, importSnapshot] = await Promise.all([
    invoiceRef.count().get(),
    importRef.count().get(),
    importRef.limit(MAX_RECOVERY_DIAGNOSTIC_IMPORTS + 1).get(),
  ]);
  const importsTruncated = importSnapshot.size > MAX_RECOVERY_DIAGNOSTIC_IMPORTS;
  const importDocs = importSnapshot.docs
    .slice(0, MAX_RECOVERY_DIAGNOSTIC_IMPORTS)
    .map((doc) => doc.data());

  return buildGmailRecoveryState({
    connection,
    authoritativeInvoiceCount: invoiceCountSnapshot.data().count,
    gmailMessageImportCount: importCountSnapshot.data().count,
    importDocs,
    importsTruncated,
  });
}

function normalizeFinancialHomeContext(context) {
  const data = context && typeof context === "object" ? context : {};
  return {
    sourceCoverage: Array.isArray(data.sourceCoverage) ? data.sourceCoverage.map(String) : [],
    isCompleteHouseholdSpend: data.isCompleteHouseholdSpend === true,
    observedRecurringMonthlySpend: nullableFiniteNumber(data.observedRecurringMonthlySpend),
    recurringServiceCount: nullableFiniteNumber(data.recurringServiceCount),
    recurringServices: Array.isArray(data.recurringServices)
      ? data.recurringServices.slice(0, MAX_HOME_ITEMS)
      : [],
    categories: Array.isArray(data.categories) ? data.categories.slice(0, MAX_HOME_ITEMS)
      : [],
  };
}

async function loadDocsByIds(collectionRef, ids) {
  const safeIds = Array.isArray(ids) ? ids.slice(0, MAX_HOME_ITEMS) : [];
  const snapshots = await Promise.all(safeIds.map((id) => collectionRef.doc(String(id)).get()));
  return snapshots.filter((snapshot) => snapshot.exists).map((snapshot) => ({
    id: snapshot.id,
    ...snapshot.data(),
  }));
}

function hasFiniteRequiredNumbers(item, keys) {
  if (!item || typeof item !== "object") return false;
  return keys.every((key) => nullableFiniteNumber(item[key]) !== null);
}

function safeHomeOpportunity(item) {
  if (!String(item?.id || "").trim()) return null;
  if (!hasFiniteRequiredNumbers(item, [
    "currentMonthlyCost",
    "previousMonthlyCost",
    "monthlyIncrease",
    "percentIncrease",
  ])) return null;

  let safeItem = item;
  if (item.matchedOffer) {
    const offerValid = Boolean(String(item.matchedOffer.offerId || "").trim()) &&
      nullableFiniteNumber(item.matchedOffer.monthlyPrice) !== null &&
      nullableFiniteNumber(item.matchedOffer.userFitScore) !== null;
    if (!offerValid) safeItem = { ...item, matchedOffer: null };
  }
  return financialAgent._homeOpportunity(safeItem);
}

function safeHomeInsight(item) {
  if (!String(item?.id || "").trim()) return null;
  if (!hasFiniteRequiredNumbers(item, [
    "currentMonthlyCost",
    "previousMonthlyCost",
    "monthlyIncrease",
    "percentIncrease",
  ])) return null;
  return financialAgent._homeInsight(item);
}

function collectInvoicesFromImportDoc(data) {
  const raw = Array.isArray(data?.invoices)
    ? data.invoices
    : (data?.invoice ? [data.invoice] : []);
  return raw.filter((invoice) => invoice && typeof invoice === "object");
}

function buildFinancialActivityLedger({ connection = null, imports = [] } = {}) {
  const events = [];
  const consentedAt = timestampToIso(connection?.consentedAt);
  if (consentedAt) {
    events.push({
      id: `gmail-connected:${consentedAt}`,
      type: "GMAIL_CONNECTED",
      timestamp: consentedAt,
      status: "CONFIRMED",
      destination: "ME",
      providerName: null,
      category: null,
      observedAmount: null,
      verificationStatus: "READ_ONLY",
    });
  }

  const lastScanAt = timestampToIso(connection?.lastScanAt);
  if (lastScanAt) {
    events.push({
      id: `scan-completed:${lastScanAt}`,
      type: "SCAN_COMPLETED",
      timestamp: lastScanAt,
      status: "COMPLETED",
      destination: "ACTIVITY",
      providerName: null,
      category: null,
      observedAmount: null,
      verificationStatus: null,
    });
  }

  for (const item of Array.isArray(imports) ? imports : []) {
    const importedAt = timestampToIso(item?.importedAt);
    if (!importedAt) continue;
    collectInvoicesFromImportDoc(item).forEach((invoice, index) => {
      events.push({
        id: `bill-detected:${importedAt}:${index}`,
        type: "BILL_DETECTED",
        timestamp: importedAt,
        status: "OBSERVED",
        destination: "BILLS",
        providerName: String(invoice.providerName || "").trim() || null,
        category: String(invoice.category || "").trim() || null,
        observedAmount: nullableFiniteNumber(invoice.monthlyCost),
        verificationStatus: String(invoice.verificationStatus || "").trim() || null,
      });
    });
  }

  events.sort((left, right) => Date.parse(right.timestamp) - Date.parse(left.timestamp));
  return {
    events: events.slice(0, MAX_ACTIVITY_EVENTS),
    sourceCoverage: ["GMAIL_CONNECTION", "GMAIL_SCAN", "GMAIL_IMPORT"],
    isCompleteHistory: false,
  };
}

exports.getGmailSyncStatus = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const snapshot = await db.collection("gmailConnections").doc(uid).get();
    const connection = snapshot.exists ? snapshot.data() : null;
    const result = buildGmailSyncStatus(connection);

    if (request.data?.includeRecoveryDiagnostics === true) {
      if (process.env.GCLOUD_PROJECT !== STAGING_PROJECT_ID) {
        throw new HttpsError(
          "failed-precondition",
          "Recovery diagnostics are available only in Staging."
        );
      }
      result.recoveryState = await loadGmailRecoveryState(uid, connection);
    }

    return result;
  }
);

exports.getFinancialHome = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    await assertActiveAccount(uid);
    const userRef = db.collection("users").doc(uid);
    let contextSnapshot = await userRef.collection("financialContext").doc("current").get();
    if (!contextSnapshot.exists) {
      await financialAgent._runFinancialAgentForUser(uid);
      contextSnapshot = await userRef.collection("financialContext").doc("current").get();
    }
    if (!contextSnapshot.exists) {
      throw new HttpsError("unavailable", "Financial context is not available yet.");
    }

    const context = contextSnapshot.data();
    const [insights, opportunities] = await Promise.all([
      loadDocsByIds(userRef.collection("financialInsights"), context.activeInsightIds),
      loadDocsByIds(userRef.collection("opportunities"), context.activeOpportunityIds),
    ]);

    return {
      contextStatus: "READY",
      context: normalizeFinancialHomeContext(context),
      insights: insights.map(safeHomeInsight).filter(Boolean),
      opportunities: opportunities.map(safeHomeOpportunity).filter(Boolean),
    };
  }
);

exports.getFinancialActivity = onCall(
  { enforceAppCheck: true },
  async (request) => {
    const uid = requireAuth(request);
    const userRef = db.collection("users").doc(uid);
    const [connectionSnapshot, importSnapshot] = await Promise.all([
      db.collection("gmailConnections").doc(uid).get(),
      userRef.collection("gmailMessageImports")
        .orderBy("importedAt", "desc")
        .limit(MAX_ACTIVITY_IMPORTS)
        .get(),
    ]);
    return buildFinancialActivityLedger({
      connection: connectionSnapshot.exists ? connectionSnapshot.data() : null,
      imports: importSnapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() })),
    });
  }
);

exports._buildGmailSyncStatus = buildGmailSyncStatus;
exports._buildGmailRecoveryState = buildGmailRecoveryState;
exports._loadGmailRecoveryState = loadGmailRecoveryState;
exports._normalizeFinancialHomeContext = normalizeFinancialHomeContext;
exports._buildFinancialActivityLedger = buildFinancialActivityLedger;
