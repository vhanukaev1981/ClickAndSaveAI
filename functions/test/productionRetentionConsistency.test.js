"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.resolve(__dirname, "..", "..");

function read(relativePath) {
  return fs.readFileSync(path.join(ROOT, relativePath), "utf8");
}

test("provider commerce retention classification matches the destructive account lifecycle", () => {
  const policy = JSON.parse(read("operations/retention/retention-policy.json"));
  const providerCommerce = policy.dataFamilies.find((entry) => entry.id === "provider_commerce_records");
  assert.ok(providerCommerce);
  assert.equal(providerCommerce.disposition, "DELETE");
  assert.equal(providerCommerce.trigger, "confirmed_account_deletion");

  const lifecycle = read("functions/src/privacyLifecycleFunctions.js");
  for (const collection of [
    "providerLeads",
    "providerDispatchQueue",
    "commerceMatches",
    "commerceEvents",
  ]) {
    assert.match(lifecycle, new RegExp(`ACCOUNT_TOP_LEVEL_UID_COLLECTIONS[\\s\\S]*["']${collection}["']`));
  }
});
