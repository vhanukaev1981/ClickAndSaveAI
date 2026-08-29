"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const entry = require("../src/entry");

test("production export surface excludes Staging Gmail recovery callable", () => {
  assert.equal(Object.prototype.hasOwnProperty.call(entry, "runGmailRecoveryDryRun"), false);
  assert.equal(entry.runGmailRecoveryDryRun, undefined);
});

test("production export surface includes Gmail connection status", () => {
  assert.equal(typeof entry.getGmailConnectionStatus, "function");
});
