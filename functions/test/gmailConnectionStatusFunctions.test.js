"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

require("../src/index");
const status = require("../src/gmailConnectionStatusFunctions");

const READONLY = "https://www.googleapis.com/auth/gmail.readonly";

function request(overrides = {}) {
  return {
    auth: { uid: "synthetic-user", token: { email: "synthetic@example.invalid" } },
    data: {},
    ...overrides,
  };
}

test("connection status requires auth and never validates by reconnecting", async () => {
  await assert.rejects(
    status._executeGetGmailConnectionStatus(request({ auth: null }), {
      loadConnection: async () => null,
    }),
    (error) => error?.code === "unauthenticated"
  );

  const source = fs.readFileSync(
    path.resolve(__dirname, "..", "src", "gmailConnectionStatusFunctions.js"),
    "utf8"
  );
  assert.match(source, new RegExp(READONLY.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.equal(source.includes("connectGmail"), false);
  assert.equal(source.includes("refresh_token"), false);
  assert.doesNotMatch(source, /\.(?:set|update|delete|create)\s*\(/);
});

test("connection status reports only stored readonly connection truth", async () => {
  const disconnected = await status._executeGetGmailConnectionStatus(request(), {
    loadConnection: async () => null,
  });
  assert.deepEqual(disconnected, { connected: false, email: "", consentVersion: "" });

  const connected = await status._executeGetGmailConnectionStatus(request(), {
    loadConnection: async () => ({
      scopes: [READONLY],
      encryptedRefreshToken: "synthetic-encrypted-token",
      email: "synthetic@example.invalid",
      consentVersion: "gmail-readonly-v1",
    }),
  });
  assert.deepEqual(connected, {
    connected: true,
    email: "synthetic@example.invalid",
    consentVersion: "gmail-readonly-v1",
  });
});

test("connection status backend exceptions expose only a sanitized exact stage", async () => {
  const secret = "SECRET_FIRESTORE_DETAIL";
  await assert.rejects(
    status._executeGetGmailConnectionStatus(request(), {
      loadConnection: async () => { throw new Error(secret); },
    }),
    (error) => {
      assert.equal(error?.code, "internal");
      assert.equal(error?.message, "GMAIL_CONNECTION_STATUS_INTERNAL_LOAD_CONNECTION");
      assert.equal(String(error?.message).includes(secret), false);
      return true;
    }
  );
});
