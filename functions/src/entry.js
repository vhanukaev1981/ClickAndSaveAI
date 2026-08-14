"use strict";

const coreFunctions = require("./index");
const pushFunctions = require("./pushFunctions");
const { safeNotificationEnvelope } = require("./notificationEnvelope");

// Gmail ingestion and push notification delivery share the historical push module. Load the
// Gmail worker once with a muted delivery function so ingestion can never emit a generic or
// content-bearing push. Exact Gmail notifications are emitted later from the authoritative
// gmailInvoices create trigger.
const pushModulePath = require.resolve("./pushFunctions");
const pushCache = require.cache[pushModulePath];
const originalPushExports = pushCache.exports;
const rawSendPushToUser = originalPushExports._sendPushToUser;

function pushExportsWith(sendPushToUser) {
  const replacement = { ...originalPushExports };
  Object.defineProperty(replacement, "_sendPushToUser", {
    value: sendPushToUser,
    enumerable: false,
  });
  return replacement;
}

pushCache.exports = pushExportsWith(async () => ({
  attempted: 0,
  delivered: 0,
  removedInvalid: 0,
  suppressedLegacyGmailPush: 1,
}));
const gmailWatchFunctions = require("./gmailWatchFunctions");
pushCache.exports = originalPushExports;

const gmailWatchRenewal = require("./gmailWatchRenewal");
const financialAgentFunctions = require("./financialAgentFunctions");
const financialActivityFunctions = require("./financialActivityFunctions");

// Opportunity notifications are authoritative Firestore transitions. Sanitize display content
// and bind an accountScope before the producer captures the delivery function.
pushCache.exports = pushExportsWith((uid, { data = {} } = {}) =>
  rawSendPushToUser(
    uid,
    safeNotificationEnvelope(uid, "VERIFIED_SAVINGS_OPPORTUNITY", data)
  )
);
const opportunityNotificationFunctions = require("./opportunityNotificationFunctions");
pushCache.exports = originalPushExports;

const opportunityActionFunctions = require("./opportunityActionFunctions");
const opportunityEngagementFunctions = require("./opportunityEngagementFunctions");
const commerceOperationsFunctions = require("./commerceOperationsFunctions");
const providerOfferCatalogFunctions = require("./providerOfferCatalogFunctions");
const providerDispatchFunctions = require("./providerDispatchFunctions");
const commerceFunnelFunctions = require("./commerceFunnelFunctions");
const gmailScanV5Functions = require("./gmailScanV5Functions");
const gmailSyncStatusFunctions = require("./gmailSyncStatusFunctions");
const gmailIncrementalReconciliation = require("./gmailIncrementalReconciliation");
const gmailReliableScanFunctions = require("./gmailReliableScanFunctions");
const gmailReliabilityGuard = require("./gmailReliabilityGuard");
const gmailInvoiceNotificationFunctions = require("./gmailInvoiceNotificationFunctions");
const pushAccountCleanup = require("./pushAccountCleanup");
const guardedUserWriteFunctions = require("./guardedUserWriteFunctions");
const gmailConnectFunctions = require("./gmailConnectFunctions");
const gmailDisconnectFunctions = require("./gmailDisconnectFunctions");
const privacyLifecycleFunctions = require("./privacyLifecycleFunctions");

// Preserve the historical public-module identity contract while routing public scans through
// Block 3 reliability semantics. gmailReliableScanFunctions captured the original stable v6
// handler before this assignment, so backfill/recovery still execute that bounded engine.
gmailScanV5Functions.scanGmailInvoices = gmailReliableScanFunctions.scanGmailInvoices;

module.exports = {
  ...coreFunctions,
  ...pushFunctions,
  ...gmailWatchFunctions,
  ...gmailWatchRenewal,
  ...financialAgentFunctions,
  ...financialActivityFunctions,
  ...opportunityNotificationFunctions,
  ...opportunityActionFunctions,
  ...opportunityEngagementFunctions,
  ...commerceOperationsFunctions,
  ...providerOfferCatalogFunctions,
  ...providerDispatchFunctions,
  ...commerceFunnelFunctions,
  ...gmailSyncStatusFunctions,
  ...gmailScanV5Functions,
  ...gmailIncrementalReconciliation,
  // Public scan is reliability-aware: initial/recovery backfills are bounded and subsequent
  // calls return the authoritative server snapshot without rescanning the mailbox.
  ...gmailReliableScanFunctions,
  // Public Pub/Sub and reconciliation entry points are guarded last for account isolation,
  // checkpoint recovery and truthful reconnect semantics.
  ...gmailReliabilityGuard,
  ...gmailInvoiceNotificationFunctions,
  ...pushAccountCleanup,
  // Legacy top-level user writes must not recreate records from stale auth after account deletion.
  ...guardedUserWriteFunctions,
  // Block 5 overrides only endpoints that need distinct connection/privacy lifecycles. Financial
  // Home keeps the authoritative Block 3 function object and enforces active-account state inside it.
  ...gmailConnectFunctions,
  ...gmailDisconnectFunctions,
  ...privacyLifecycleFunctions,
};