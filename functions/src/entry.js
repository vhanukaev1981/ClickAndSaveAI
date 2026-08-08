"use strict";

const coreFunctions = require("./index");
const pushFunctions = require("./pushFunctions");
const gmailWatchFunctions = require("./gmailWatchFunctions");
const gmailWatchRenewal = require("./gmailWatchRenewal");

module.exports = {
  ...coreFunctions,
  ...pushFunctions,
  ...gmailWatchFunctions,
  ...gmailWatchRenewal,
};
