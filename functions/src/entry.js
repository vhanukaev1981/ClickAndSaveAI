"use strict";

const coreFunctions = require("./index");
const pushFunctions = require("./pushFunctions");
const gmailWatchFunctions = require("./gmailWatchFunctions");
const gmailWatchRenewal = require("./gmailWatchRenewal");
const gmailIncrementalReconciliation = require("./gmailIncrementalReconciliation");
const financialAgentFunctions = require("./financialAgentFunctions");
const opportunityNotificationFunctions = require("./opportunityNotificationFunctions");
const opportunityActionFunctions = require("./opportunityActionFunctions");
const opportunityEngagementFunctions = require("./opportunityEngagementFunctions");
const commerceOperationsFunctions = require("./commerceOperationsFunctions");
const providerOfferCatalogFunctions = require("./providerOfferCatalogFunctions");
const providerDispatchFunctions = require("./providerDispatchFunctions");
const commerceFunnelFunctions = require("./commerceFunnelFunctions");
const gmailScanV5Functions = require("./gmailScanV5Functions");
const gmailReliableScanFunctions = require("./gmailReliableScanFunctions");
const gmailSyncStatusFunctions = require("./gmailSyncStatusFunctions");
const observedBillsFunctions = require("./observedBillsFunctions");

module.exports = {
  ...coreFunctions,
  ...pushFunctions,
  ...gmailWatchFunctions,
  ...gmailWatchRenewal,
  // Overrides only gmailPushNotification with the serialized/monotonic wrapper and
  // adds the four-hour History reconciliation schedule.
  ...gmailIncrementalReconciliation,
  ...financialAgentFunctions,
  ...opportunityNotificationFunctions,
  ...opportunityActionFunctions,
  ...opportunityEngagementFunctions,
  ...commerceOperationsFunctions,
  ...providerOfferCatalogFunctions,
  ...providerDispatchFunctions,
  ...commerceFunnelFunctions,
  ...gmailSyncStatusFunctions,
  ...observedBillsFunctions,
  // Keep the stable parser implementation available internally, then gate its public
  // callable so six-month scanning can run only for first backfill/parser upgrades.
  ...gmailScanV5Functions,
  ...gmailReliableScanFunctions,
};
