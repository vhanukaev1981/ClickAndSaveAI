"use strict";

const coreFunctions = require("./index");
const pushFunctions = require("./pushFunctions");
const gmailWatchFunctions = require("./gmailWatchFunctions");
const gmailWatchRenewal = require("./gmailWatchRenewal");
const financialAgentFunctions = require("./financialAgentFunctions");
const opportunityNotificationFunctions = require("./opportunityNotificationFunctions");
const opportunityActionFunctions = require("./opportunityActionFunctions");
const commerceOperationsFunctions = require("./commerceOperationsFunctions");

module.exports = {
  ...coreFunctions,
  ...pushFunctions,
  ...gmailWatchFunctions,
  ...gmailWatchRenewal,
  ...financialAgentFunctions,
  ...opportunityNotificationFunctions,
  ...opportunityActionFunctions,
  ...commerceOperationsFunctions,
};