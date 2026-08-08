"use strict";

const coreFunctions = require("./index");
const pushFunctions = require("./pushFunctions");

module.exports = {
  ...coreFunctions,
  ...pushFunctions,
};
