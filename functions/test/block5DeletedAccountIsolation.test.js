"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

function read(relativePath) {
  return fs.readFileSync(path.join(__dirname, "..", relativePath), "utf8");
}

test("state-creating public callables require an active Firebase account", () => {
  const push = read("src/pushFunctions.js");
  const financial = read("src/gmailSyncStatusFunctions.js");
  const connect = read("src/gmailConnectFunctions.js");
  const legacyWrites = read("src/guardedUserWriteFunctions.js");
  const entry = read("src/entry.js");

  assert.match(push, /registerPushToken[\s\S]*assertActiveAccount\(uid\)/);
  assert.match(financial, /getFinancialHome[\s\S]*assertActiveAccount\(uid\)/);
  assert.match(connect, /connectGmail[\s\S]*assertActiveAccount\(uid\)/);
  assert.match(legacyWrites, /createProviderLead[\s\S]*assertActiveAccount\(uid\)/);
  assert.match(entry, /\.\.\.gmailSyncStatusFunctions/);
  assert.doesNotMatch(entry, /\.\.\.financialHomeFunctions/);
  assert.match(entry, /\.\.\.guardedUserWriteFunctions/);
  assert.match(entry, /\.\.\.gmailConnectFunctions/);
});

test("active-account authorization rejects deleting, delete-retry, disabled and missing accounts", () => {
  const authorization = read("src/accountAuthorization.js");
  assert.match(authorization, /DELETING/);
  assert.match(authorization, /DELETE_RETRY_REQUIRED/);
  assert.match(authorization, /BLOCKED_ACCOUNT_LIFECYCLE_STATES/);
  assert.match(authorization, /user\.disabled === true/);
  assert.match(authorization, /auth\/user-not-found/);
  assert.match(authorization, /permission-denied/g);
});
