"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const source = fs.readFileSync(
  path.join(__dirname, "..", "src", "pushFunctions.js"),
  "utf8"
);

test("unregisterPushToken stays App Check authenticated and token-specific", () => {
  const section = source
    .split("exports.unregisterPushToken = onCall(")[1]
    ?.split("exports.sendTestPush = onCall(")[0] || "";

  assert.match(section, /enforceAppCheck:\s*true/);
  assert.match(section, /const uid = requireAuth\(request\)/);
  assert.match(section, /normalizeToken\(request\.data\?\.token\)/);
  assert.match(section, /tokenDocumentId\(token\)/);
  assert.match(section, /collection\("users"\)/);
  assert.match(section, /doc\(uid\)/);
  assert.match(section, /collection\("pushTokens"\)/);
  assert.match(section, /doc\(tokenId\)/);
  assert.match(section, /\.delete\(\)/);
  assert.doesNotMatch(section, /listCollections|recursiveDelete|batch\(\)/);
});
