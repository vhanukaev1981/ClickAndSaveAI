"use strict";

const { getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret, defineString } = require("firebase-functions/params");
const { collectPdfAttachments, parseGmailMessage } = require("./gmailParser");
const { decryptToken } = require("./tokenCrypto");
const {
  analyzePdfCandidate,
  bodyCandidate,
  loadPdfAttachmentBase64,
  normalizeStoredCandidate,
  pdfSourceDocumentId,
  resolvePdfBodyCandidates,
} = require("./gmailRecurringIngestionEngine");
const {
  PDF_ANALYSIS_STATES,
  candidatePdfAnalysisState,
  pdfClassificationResults,
} = require("./gmailPdfAnalysisState");
const {
  pdfContentFingerprint,
  selectRecurringBills,
} = require("./gmailRecurringBillPolicy");

const db = getFirestore();
const googleOAuthClientId = defineString("GOOGLE_OAUTH_CLIENT_ID");
const googleOAuthClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
const oauthTokenEncryptionKey = defineSecret("OAUTH_TOKEN_ENCRYPTION_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const STAGING_PROJECT_ID = "clickandsaveai-staging";
const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const INITIAL_GMAIL_LOOKBACK = "6m";
const GMAIL_LIST_PAGE_SIZE = 100;
const RECOVERY_DRY_RUN_VERSION = "staging-controlled-gmail-recovery-dry-run-v1";
const DOCUMENT_CLASSES = new Set([
  "RECURRING_BILL",
  "ONE_OFF",
  "REFUND",
  "RECEIPT_ONLY",
  "CONTRACT",
  "UNKNOWN",
]);
const UNKNOWN_REASON_KEYS = [
  "BODY_FALLBACK_NO_PDF_CANDIDATE",
  "BODY_FALLBACK_PDF_ANALYSIS_FAILURE",
  "PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS",
  "NORMALIZED_UNSUPPORTED_DOCUMENT_CLASS",
];

function zeroUnknownReasonCounts() {
  return Object.fromEntries(UNKNOWN_REASON_KEYS.map((key) => [key, 0]));
}

function sanitizedUnknownReasonCounts(value) {
  return Object.fromEntries(
    UNKNOWN_REASON_KEYS.map((key) => [key, Number(value?.[key] || 0)])
  );
}

function zeroCounts() {
  return {
    messagesExamined: 0,
    candidateMessageCount: 0,
    pdfCandidateCount: 0,
    normalizedCandidateCount: 0,
    recurringBillCount: 0,
    uniqueRecurringSourceCount: 0,
    duplicateCount: 0,
    rejectedOneOffCount: 0,
    rejectedRefundCount: 0,
    rejectedReceiptOnlyCount: 0,
    rejectedContractCount: 0,
    unknownCount: 0,
    unknownReasonCounts: zeroUnknownReasonCounts(),
  };
}

function responseEnvelope(result, credentialPreflight, failureStage, counts = zeroCounts()) {
  return {
    result,
    credentialPreflight,
    failureStage,
    recoveryDryRunVersion: RECOVERY_DRY_RUN_VERSION,
    messagesExamined: Number(counts.messagesExamined || 0),
    candidateMessageCount: Number(counts.candidateMessageCount || 0),
    pdfCandidateCount: Number(counts.pdfCandidateCount || 0),
    normalizedCandidateCount: Number(counts.normalizedCandidateCount || 0),
    recurringBillCount: Number(counts.recurringBillCount || 0),
    uniqueRecurringSourceCount: Number(counts.uniqueRecurringSourceCount || 0),
    duplicateCount: Number(counts.duplicateCount || 0),
    rejectedOneOffCount: Number(counts.rejectedOneOffCount || 0),
    rejectedRefundCount: Number(counts.rejectedRefundCount || 0),
    rejectedReceiptOnlyCount: Number(counts.rejectedReceiptOnlyCount || 0),
    rejectedContractCount: Number(counts.rejectedContractCount || 0),
    unknownCount: Number(counts.unknownCount || 0),
    unknownReasonCounts: sanitizedUnknownReasonCounts(counts.unknownReasonCounts),
  };
}

function requireRecoveryGate(request, projectId) {
  if (!request.auth?.uid) {
    throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  }
  if (projectId !== STAGING_PROJECT_ID) {
    throw new HttpsError("failed-precondition", "Recovery dry-run is Staging-only.");
  }
  if (request.data?.recoveryDryRunVersion !== RECOVERY_DRY_RUN_VERSION) {
    throw new HttpsError("failed-precondition", "Recovery dry-run version mismatch.");
  }
  return request.auth.uid;
}

async function loadConnection(uid) {
  const snapshot = await db.collection("gmailConnections").doc(uid).get();
  if (!snapshot.exists) return null;
  return snapshot.data() || {};
}

function decryptStoredRefreshToken(encryptedRefreshToken) {
  return decryptToken(encryptedRefreshToken, oauthTokenEncryptionKey.value());
}

async function refreshAccessToken(refreshToken) {
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: googleOAuthClientId.value(),
      client_secret: googleOAuthClientSecret.value(),
      refresh_token: refreshToken,
      grant_type: "refresh_token",
    }),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || !payload.access_token) {
    throw new Error("TOKEN_REFRESH_FAILED");
  }
  return String(payload.access_token);
}

async function fetchMailboxIdentity(accessToken) {
  const response = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/profile", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || !String(payload.emailAddress || "").trim()) {
    throw new Error("MAILBOX_IDENTITY_UNAVAILABLE");
  }
  return String(payload.emailAddress);
}

async function listQueryMessageIds(accessToken, query) {
  const messageIds = [];
  let pageToken = "";
  do {
    const params = new URLSearchParams({
      q: query,
      maxResults: String(GMAIL_LIST_PAGE_SIZE),
      ...(pageToken ? { pageToken } : {}),
    });
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages?${params.toString()}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (!response.ok) throw new Error("GMAIL_LIST_FAILED");
    const payload = await response.json().catch(() => ({}));
    for (const item of Array.isArray(payload.messages) ? payload.messages : []) {
      const id = String(item?.id || "").trim();
      if (id) messageIds.push(id);
    }
    pageToken = String(payload.nextPageToken || "");
  } while (pageToken);
  return messageIds;
}

async function listGmailCandidateMessageIds(accessToken) {
  const broadBillingQuery = `newer_than:${INITIAL_GMAIL_LOOKBACK} {חשבונית קבלה "הודעת תשלום" "פירוט חיוב" "חשבון חודשי" invoice receipt bill statement subscription billing}`;
  const pdfFallbackQuery = `newer_than:${INITIAL_GMAIL_LOOKBACK} has:attachment filename:pdf`;
  const results = await Promise.all([
    listQueryMessageIds(accessToken, broadBillingQuery),
    listQueryMessageIds(accessToken, pdfFallbackQuery),
  ]);
  const seen = new Set();
  const messageIds = [];
  for (const result of results) {
    for (const id of result) {
      if (seen.has(id)) continue;
      seen.add(id);
      messageIds.push(id);
    }
  }
  return messageIds;
}

async function scanMailbox(accessToken) {
  const messageIds = await listGmailCandidateMessageIds(accessToken);
  const candidates = [];
  let candidateMessageCount = 0;
  let pdfCandidateCount = 0;

  for (const messageId of messageIds) {
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}?format=full`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (!response.ok) continue;
    const message = await response.json().catch(() => ({}));
    const pdfAttachments = collectPdfAttachments(message.payload);

    for (let index = 0; index < pdfAttachments.length; index += 1) {
      const attachment = pdfAttachments[index];
      try {
        const pdfBase64 = await loadPdfAttachmentBase64(accessToken, messageId, attachment);
        if (!pdfBase64) continue;
        pdfContentFingerprint(pdfBase64);
        const candidate = await analyzePdfCandidate(
          message,
          pdfBase64,
          attachment.filename,
          pdfSourceDocumentId(messageId, attachment, index),
          geminiApiKey.value()
        );
        if (candidate) pdfCandidateCount += 1;
      } catch {
        // Count-only dry-run intentionally records no message/attachment detail.
        // The absence of a semantic result is resolved below as PDF_ANALYSIS_FAILURE.
      }
    }

    const fallbackBody = bodyCandidate(parseGmailMessage(message));
    const { candidates: messageCandidates } = resolvePdfBodyCandidates({
      pdfAttachmentCount: pdfAttachments.length,
      pdfOutcomes: pdfClassificationResults(message),
      fallbackBody,
    });
    if (messageCandidates.length > 0) candidateMessageCount += 1;
    candidates.push(...messageCandidates);
  }

  return {
    messagesExamined: messageIds.length,
    candidateMessageCount,
    pdfCandidateCount,
    candidates,
  };
}

function normalizeText(value) {
  return String(value || "").replace(/\s+/g, " ").trim().toLowerCase();
}

function normalizedAmount(value) {
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0 ? amount.toFixed(2) : "";
}

function contentDedupeKey(item) {
  const fingerprint = String(item?.contentFingerprint || "").trim().toLowerCase();
  return /^sha256:[a-f0-9]{64}$/.test(fingerprint) ? fingerprint : "";
}

function transactionDedupeKey(item) {
  const provider = normalizeText(item?.providerName);
  const amount = normalizedAmount(item?.monthlyCost);
  const date = String(item?.receivedDate || "").trim().slice(0, 10);
  if (!provider || !amount || !date) return "";
  return [provider, normalizeText(item?.category), normalizeText(item?.serviceType), amount, date].join("|");
}

function classRank(documentClass) {
  if (documentClass === "RECURRING_BILL") return 3;
  if (documentClass === "RECEIPT_ONLY") return 2;
  return 1;
}

function policyEquivalentUnique(items) {
  const contentSeen = new Set();
  const contentUnique = [];
  for (const item of items) {
    const key = contentDedupeKey(item);
    if (key && contentSeen.has(key)) continue;
    if (key) contentSeen.add(key);
    contentUnique.push(item);
  }

  const byTransaction = new Map();
  const noTransactionKey = [];
  for (const item of contentUnique) {
    const key = transactionDedupeKey(item);
    if (!key) {
      noTransactionKey.push(item);
      continue;
    }
    const current = byTransaction.get(key);
    if (!current || classRank(item.documentClass) > classRank(current.documentClass)) {
      byTransaction.set(key, item);
    }
  }
  return [...byTransaction.values(), ...noTransactionKey];
}

function recurringSourceKey(item) {
  return [
    normalizeText(item?.providerName),
    normalizeText(item?.category),
    normalizeText(item?.serviceType),
  ].join("|");
}

function rawDocumentClass(candidate) {
  return String(candidate?.documentClass || "").trim().toUpperCase();
}

function unknownReasonFor(rawCandidate, normalizedCandidate) {
  if (normalizedCandidate?.documentClass !== "UNKNOWN") return "";
  const rawClass = rawDocumentClass(rawCandidate);
  if (!DOCUMENT_CLASSES.has(rawClass)) {
    return "NORMALIZED_UNSUPPORTED_DOCUMENT_CLASS";
  }

  const pdfState = candidatePdfAnalysisState(rawCandidate);
  if (pdfState === PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT) {
    return "PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS";
  }
  if (pdfState === PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE) {
    return "BODY_FALLBACK_PDF_ANALYSIS_FAILURE";
  }
  if (pdfState === PDF_ANALYSIS_STATES.NO_PDF) {
    return "BODY_FALLBACK_NO_PDF_CANDIDATE";
  }

  // Preserve truthful compatibility for synthetic/legacy diagnostic candidates
  // that predate the internal runtime-state marker.
  if (contentDedupeKey(normalizedCandidate)) {
    return "PDF_CLASSIFIER_UNKNOWN_OR_UNSUPPORTED_CLASS";
  }
  return "BODY_FALLBACK_NO_PDF_CANDIDATE";
}

function summarizeSelection(rawCandidates, scanCounts) {
  const normalizedPairs = (Array.isArray(rawCandidates) ? rawCandidates : [])
    .map((raw) => ({ raw, normalized: normalizeStoredCandidate(raw) }))
    .filter((pair) => Boolean(pair.normalized));
  const normalized = normalizedPairs.map((pair) => pair.normalized);
  const rawByNormalized = new WeakMap(
    normalizedPairs.map((pair) => [pair.normalized, pair.raw])
  );
  const policyUnique = policyEquivalentUnique(normalized);
  const recurring = selectRecurringBills(normalized)
    .map(normalizeStoredCandidate)
    .filter(Boolean);
  const selectedSources = new Set(recurring.map((item) => String(item.sourceMessageId || "")));
  const uniqueRecurringSources = new Set(
    recurring.map(recurringSourceKey).filter((key) => key && key !== "||")
  );

  const rejected = policyUnique.filter((item) => !selectedSources.has(String(item.sourceMessageId || "")));
  const classCount = (documentClass) => rejected
    .filter((item) => item.documentClass === documentClass).length;
  const unknownReasonCounts = zeroUnknownReasonCounts();
  for (const item of rejected) {
    if (item.documentClass !== "UNKNOWN") continue;
    const reason = unknownReasonFor(rawByNormalized.get(item), item);
    if (reason) unknownReasonCounts[reason] += 1;
  }

  return {
    messagesExamined: scanCounts.messagesExamined,
    candidateMessageCount: scanCounts.candidateMessageCount,
    pdfCandidateCount: scanCounts.pdfCandidateCount,
    normalizedCandidateCount: normalized.length,
    recurringBillCount: recurring.length,
    uniqueRecurringSourceCount: uniqueRecurringSources.size,
    duplicateCount: Math.max(0, normalized.length - policyUnique.length),
    rejectedOneOffCount: classCount("ONE_OFF"),
    rejectedRefundCount: classCount("REFUND"),
    rejectedReceiptOnlyCount: classCount("RECEIPT_ONLY"),
    rejectedContractCount: classCount("CONTRACT"),
    unknownCount: classCount("UNKNOWN"),
    unknownReasonCounts,
  };
}

function defaultDependencies() {
  return {
    projectId: process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || "",
    loadConnection,
    decryptRefreshToken: decryptStoredRefreshToken,
    refreshAccessToken,
    fetchMailboxIdentity,
    scanMailbox,
  };
}

async function executeRecoveryDryRun(request, overrides = {}) {
  const deps = { ...defaultDependencies(), ...overrides };
  const uid = requireRecoveryGate(request, deps.projectId);
  let connection;
  let refreshToken;
  let accessToken;

  try {
    connection = await deps.loadConnection(uid);
  } catch {
    return responseEnvelope("BLOCKED", "FAIL", "LOAD_CONNECTION");
  }
  if (!connection) return responseEnvelope("BLOCKED", "FAIL", "LOAD_CONNECTION");

  if (!Array.isArray(connection.scopes) || !connection.scopes.includes(GMAIL_READONLY_SCOPE)) {
    return responseEnvelope("BLOCKED", "FAIL", "SCOPE_VALIDATION");
  }
  if (!connection.encryptedRefreshToken) {
    return responseEnvelope("BLOCKED", "FAIL", "REFRESH_TOKEN_MISSING");
  }

  try {
    refreshToken = deps.decryptRefreshToken(connection.encryptedRefreshToken);
    if (!String(refreshToken || "").trim()) throw new Error("EMPTY_REFRESH_TOKEN");
  } catch {
    return responseEnvelope("BLOCKED", "FAIL", "DECRYPT_REFRESH_TOKEN");
  }

  try {
    accessToken = await deps.refreshAccessToken(refreshToken);
    if (!String(accessToken || "").trim()) throw new Error("EMPTY_ACCESS_TOKEN");
  } catch {
    return responseEnvelope("BLOCKED", "FAIL", "REFRESH_ACCESS_TOKEN");
  }

  try {
    const mailboxIdentity = await deps.fetchMailboxIdentity(accessToken);
    if (!String(mailboxIdentity || "").trim()) throw new Error("EMPTY_MAILBOX_IDENTITY");
  } catch {
    return responseEnvelope("BLOCKED", "FAIL", "MAILBOX_IDENTITY");
  }

  let scanResult;
  try {
    scanResult = await deps.scanMailbox(accessToken);
  } catch {
    return responseEnvelope("FAIL", "PASS", "RECOVERY_SCAN");
  }

  const counts = summarizeSelection(scanResult?.candidates, {
    messagesExamined: Number(scanResult?.messagesExamined || 0),
    candidateMessageCount: Number(scanResult?.candidateMessageCount || 0),
    pdfCandidateCount: Number(scanResult?.pdfCandidateCount || 0),
  });
  const result = counts.recurringBillCount > 0
    ? "CONTROLLED_RECOVERY_VIABLE"
    : "CONTROLLED_RECOVERY_NOT_VIABLE";
  return responseEnvelope(result, "PASS", "", counts);
}

exports.runGmailRecoveryDryRun = onCall(
  {
    enforceAppCheck: true,
    secrets: [googleOAuthClientSecret, oauthTokenEncryptionKey, geminiApiKey],
    timeoutSeconds: 540,
    memory: "1GiB",
  },
  executeRecoveryDryRun
);

Object.defineProperties(module.exports, {
  _executeRecoveryDryRun: { value: executeRecoveryDryRun, enumerable: false },
  _scanMailbox: { value: scanMailbox, enumerable: false },
  _summarizeSelection: { value: summarizeSelection, enumerable: false },
  RECOVERY_DRY_RUN_VERSION: { value: RECOVERY_DRY_RUN_VERSION, enumerable: false },
});
