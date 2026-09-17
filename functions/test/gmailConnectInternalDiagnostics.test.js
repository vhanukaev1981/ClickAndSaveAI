"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const source = fs.readFileSync(
  path.join(root, "functions/src/gmailConnectFunctions.js"),
  "utf8"
);

test("connectGmail converts unknown internal failures into a safe stage-specific callable error", () => {
  for (const stage of [
    "ACCOUNT_CHECK",
    "LOAD_CONNECTION",
    "TOKEN_EXCHANGE",
    "GMAIL_IDENTITY",
    "ENCRYPT_TOKEN",
    "STORE_CONNECTION",
  ]) {
    assert.match(source, new RegExp(`stage\\s*=\\s*["']${stage}["']`));
  }

  assert.match(
    source,
    /if\s*\(\s*error\s+instanceof\s+HttpsError\s*\)\s*throw\s+error\s*;/
  );
  assert.match(
    source,
    /throw\s+new\s+HttpsError\(\s*["']internal["']\s*,\s*`GMAIL_CONNECT_INTERNAL_\$\{stage\}`\s*\)/
  );
});
