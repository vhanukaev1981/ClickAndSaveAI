"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const disconnectSource = fs.readFileSync(
  path.join(__dirname, "../src/gmailDisconnectFunctions.js"),
  "utf8"
);
const connectSource = fs.readFileSync(
  path.join(__dirname, "../src/gmailConnectFunctions.js"),
  "utf8"
);

test("Gmail disconnect preserves the structured encrypted token payload for decryption", () => {
  assert.match(connectSource, /encryptedRefreshToken\s*=\s*refreshToken[\s\S]*encryptToken\(/);
  assert.match(disconnectSource, /const encryptedRefreshToken = data\.encryptedRefreshToken \|\| null;/);
  assert.doesNotMatch(disconnectSource, /String\(data\.encryptedRefreshToken/);
  assert.match(disconnectSource, /decryptToken\(encryptedRefreshToken, oauthTokenEncryptionKey\.value\(\)\)/);
});
