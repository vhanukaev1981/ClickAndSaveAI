"use strict";

const coreFunctions = require("./index");
const pushFunctions = require("./pushFunctions");
const gmailWatchFunctions = require("./gmailWatchFunctions");
const gmailWatchRenewal = require("./gmailWatchRenewal");
const financialAgentFunctions = require("./financialAgentFunctions");
const opportunityNotificationFunctions = require("./opportunityNotificationFunctions");
const opportunityActionFunctions = require("./opportunityActionFunctions");
const commerceOperationsFunctions = require("./commerceOperationsFunctions");
const providerOfferCatalogFunctions = require("./providerOfferCatalogFunctions");
const providerDispatchFunctions = require("./providerDispatchFunctions");
const gmailScanV5Functions = require("./gmailScanV5Functions");

module.exports = {
  ...coreFunctions,
  ...pushFunctions,
  ...gmailWatchFunctions,
  ...gmailWatchRenewal,
  ...financialAgentFunctions,
  ...opportunityNotificationFunctions,
  ...opportunityActionFunctions,
  ...commerceOperationsFunctions,
  ...providerOfferCatalogFunctions,
  ...providerDispatchFunctions,
  // Intentionally last: preserves the public callable name while replacing only
  // the legacy parser-v4 implementation. OAuth/connect/disconnect remain untouched.
  ...gmailScanV5Functions,
};
