"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

test("account deletion cleanup is exported and removes notification registrations", () => {
  const cleanup = fs.readFileSync(path.join(__dirname, "..", "src", "pushAccountCleanup.js"), "utf8");
  const entry = fs.readFileSync(path.join(__dirname, "..", "src", "entry.js"), "utf8");
  assert.match(cleanup, /auth\.user\(\)\.onDelete/);
  assert.match(cleanup, /collection\("pushTokens"\)/);
  assert.match(cleanup, /batch\.delete\(doc\.ref\)/);
  assert.match(entry, /pushAccountCleanup/);
});
