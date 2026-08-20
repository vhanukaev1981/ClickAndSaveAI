"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/gmailConnectFunctions.js"),
  "utf8"
);

test("Gmail token exchange sends the complete server-auth-code contract", () => {
  assert.match(source, /client_id:\s*googleOAuthClientId\.value\(\)/);
  assert.match(source, /client_secret:\s*googleOAuthClientSecret\.value\(\)/);
  assert.match(source, /code:\s*serverAuthCode/);
  assert.match(source, /grant_type:\s*"authorization_code"/);
  assert.match(source, /redirect_uri:\s*""/);
});

test("Gmail OAuth scope remains read-only", () => {
  assert.match(
    source,
    /const GMAIL_READONLY_SCOPE = "https:\/\/www\.googleapis\.com\/auth\/gmail\.readonly";/
  );
  assert.doesNotMatch(source, /gmail\.modify|mail\.google\.com|gmail\.compose|gmail\.send/);
});
