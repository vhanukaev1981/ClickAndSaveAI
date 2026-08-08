"use strict";

const coreFunctions = require("./index");
const pushFunctions = require("./pushFunctions");
const gmailWatchFunctions = require("./gmailWatchFunctions");

module.exports = {
  ...coreFunctions,
  ...pushFunctions,
  ...gmailWatchFunctions,
};
