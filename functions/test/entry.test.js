"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

test("Firebase Functions entry point loads all push and Gmail watch handlers", () => {
  const handlers = require("../src/entry");
  const expected = [
    "getGmailConnectionStatus",
    "connectGmail",
    "scanGmailInvoices",
    "disconnectGmail",
    "registerPushToken",
    "unregisterPushToken",
    "sendTestPush",
    "startGmailWatch",
    "stopGmailWatch",
    "gmailPushNotification",
    "renewGmailWatches",
  ];

  for (const name of expected) {
    assert.ok(handlers[name], `Expected ${name} to be exported`);
  }
});
