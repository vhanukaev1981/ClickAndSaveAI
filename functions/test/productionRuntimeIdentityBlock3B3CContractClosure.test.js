"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "..", "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const design = read("docs/superpowers/specs/2026-08-15-production-enablement-block3b3c-v2-runtime-identity-design.md");
const plan = read("docs/superpowers/plans/2026-08-15-production-enablement-block3b3c-v2-runtime-identity.md");
const verifier = read("scripts/verify-production-runtime-build-actas.sh");
const docs = `${design}\n${plan}`;

const serviceQuery = "gcloud services list";
const identityDiscovery = "gcloud builds get-default-service-account";
const deferred = "DEFERRED_UNTIL_BUILD_SERVICE_INITIALIZATION";

test("Block 3B.3C contract documents require service-state-before-build-identity discovery", () => {
  for (const source of [design, plan]) {
    assert.match(source, /cloudbuild\.googleapis\.com/);
    assert.match(source, /gcloud services list/);
    assert.match(source, /gcloud builds get-default-service-account/);
    assert.ok(
      source.indexOf(serviceQuery) < source.indexOf(identityDiscovery),
      "Cloud Build service-state query must be documented before identity discovery"
    );
  }
});

test("disabled Cloud Build service is deferred without invoking identity discovery", () => {
  assert.match(docs, /service (?:is )?not enabled/i);
  assert.match(docs, new RegExp(deferred));
  assert.match(docs, /discovery (?:is )?not attempted|must not be invoked|is not invoked/i);
  assert.match(docs, /productionCloudBuildServiceEnabled=false/);
  assert.match(docs, /productionBuildIdentityDiscoveryAttempted=false/);
});

test("enabled-service discovery failure is a hard failure independent of gcloud prose", () => {
  assert.match(docs, /enabled-service identity discovery command fails[^\n]*hard FAIL/i);
  assert.doesNotMatch(docs, /stderr clearly indicates/i);
  assert.doesNotMatch(docs, /SERVICE_DISABLED|has not been used in project/i);
  assert.doesNotMatch(docs, /fails specifically because Cloud Build is not initialized\/enabled/i);
});

test("verifier implementation and contract documents reject the retired prose classifier", () => {
  for (const source of [verifier, design, plan]) {
    assert.doesNotMatch(source, /is_build_service_uninitialized/);
  }
});
