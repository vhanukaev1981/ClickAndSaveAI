"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const entry = require("../src/entry");
const status = require("../src/gmailSyncStatusFunctions");

test("Gmail sync status requires one-time upgrade below active parser revision", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 5,
  });

  assert.equal(result.connected, true);
  assert.equal(result.activeParserVersion, 6);
  assert.equal(result.storedParserVersion, 5);
  assert.equal(result.upgradeRequired, true);
  assert.equal(result.lookback, "6m");
});

test("Gmail sync status is current after revision 6 backfill", () => {
  const result = status._buildGmailSyncStatus({
    scopes: ["https://www.googleapis.com/auth/gmail.readonly"],
    encryptedRefreshToken: "encrypted",
    parserVersion: 6,
  });

  assert.equal(result.upgradeRequired, false);
  assert.equal(entry.getGmailSyncStatus, status.getGmailSyncStatus);
});

test("disconnected account never requests parser backfill", () => {
  const result = status._buildGmailSyncStatus({ parserVersion: 0 });
  assert.equal(result.connected, false);
  assert.equal(result.upgradeRequired, false);
});
