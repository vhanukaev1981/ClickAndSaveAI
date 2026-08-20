"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const index = fs.readFileSync(path.join(root, "functions/src/index.js"), "utf8");

test("non-Production v2 runtime fallback is omitted instead of becoming a literal default service account", () => {
  assert.doesNotMatch(
    index,
    /thenElse\(\s*PRODUCTION_V2_SERVICE_ACCOUNT\s*,\s*["']default["']\s*\)/,
    "A parameter Expression resolving to literal 'default' bypasses Firebase's shorthand normalization and is treated as an actual service account by Secret Manager."
  );
  assert.match(
    index,
    /thenElse\(\s*PRODUCTION_V2_SERVICE_ACCOUNT\s*,\s*["']["']\s*\)/,
    "Non-Production must resolve to an empty service-account value so Firebase CLI falls back to the platform default runtime identity."
  );
});
