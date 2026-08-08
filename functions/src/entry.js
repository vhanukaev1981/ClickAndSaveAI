"use strict";

const coreFunctions = require("./index");
const pushFunctions = require("./pushFunctions");
const gmailWatchFunctions = require("./gmailWatchFunctions");
const gmailWatchRenewal = require("./gmailWatchRenewal");
const financialAgentFunctions = require("./financialAgentFunctions");

module.exports = {
  ...coreFunctions,
  ...pushFunctions,
  ...gmailWatchFunctions,
  ...gmailWatchRenewal,
  ...financialAgentFunctions,
};