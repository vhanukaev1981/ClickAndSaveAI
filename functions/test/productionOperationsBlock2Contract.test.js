"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.resolve(__dirname, "..", "..");

function rootPath(relativePath) {
  return path.join(ROOT, relativePath);
}

function readRoot(relativePath) {
  const absolute = rootPath(relativePath);
  assert.equal(fs.existsSync(absolute), true, `Missing required Block 2 artifact: ${relativePath}`);
  return fs.readFileSync(absolute, "utf8");
}

function jsonRoot(relativePath) {
  return JSON.parse(readRoot(relativePath));
}

function assertOperationalEvent(source, eventName) {
  assert.match(source, new RegExp(`event:\\s*["']${eventName.replaceAll(".", "\\.")}["']`));
}

test("critical production boundaries emit canonical privacy-safe operational events", () => {
  const requirements = [
    ["functions/src/gmailConnectFunctions.js", "gmail.oauth.connect"],
    ["functions/src/gmailDisconnectFunctions.js", "gmail.oauth.disconnect"],
    ["functions/src/gmailWatchFunctions.js", "gmail.watch.incremental"],
    ["functions/src/gmailReliableScanFunctions.js", "gmail.reconciliation.scan"],
    ["functions/src/pushFunctions.js", "push.delivery"],
    ["functions/src/providerDispatchFunctions.js", "provider.handoff.queue"],
    ["functions/src/privacyLifecycleFunctions.js", "privacy.imported_data.delete"],
    ["functions/src/privacyLifecycleFunctions.js", "privacy.account.delete"],
  ];

  for (const [relativePath, eventName] of requirements) {
    const source = readRoot(relativePath);
    assert.match(source, /operationalTelemetry/);
    assertOperationalEvent(source, eventName);
  }
});

test("known-good release identity is immutable, machine-readable and explicitly not production evidence", () => {
  const manifest = jsonRoot("operations/release/known-good-manifest.example.json");
  assert.equal(manifest.manifestVersion, 1);
  assert.equal(manifest.evidenceState, "EXAMPLE_NOT_PRODUCTION");
  assert.match(manifest.sourceSha, /^[0-9a-f]{40}$/);
  assert.equal(manifest.sourceSha, "4f674d27dfec148e10108274de23013ae73613df");
  assert.equal(manifest.surfaces.functions.sourceSha, manifest.sourceSha);
  assert.equal(manifest.surfaces.firestoreRules.sourceSha, manifest.sourceSha);
  assert.equal(manifest.surfaces.firestoreIndexes.sourceSha, manifest.sourceSha);
  assert.equal(manifest.surfaces.configuration.bindingState, "PENDING_PRODUCTION_BINDING");
  assert.equal(manifest.surfaces.android.versionCode, 1);
  assert.equal(manifest.surfaces.android.versionName, "1.0");
  assert.equal(manifest.schema.compatibilityEpoch, "v1");

  const schema = jsonRoot("operations/release/known-good-manifest.schema.json");
  assert.equal(schema.$schema, "https://json-schema.org/draft/2020-12/schema");
  assert.ok(Array.isArray(schema.required));
  assert.ok(schema.required.includes("sourceSha"));
  assert.ok(schema.required.includes("surfaces"));
});

test("recovery planner is plan-only, fail-closed and covers every required production surface", () => {
  const source = readRoot("scripts/production-recovery-plan.mjs");
  assert.match(source, /--manifest/);
  assert.match(source, /--target/);
  assert.match(source, /--mode/);
  assert.match(source, /functions/);
  assert.match(source, /firestore-rules/);
  assert.match(source, /configuration/);
  assert.match(source, /android/);
  assert.match(source, /higher versionCode/i);
  assert.match(source, /halt/i);
  assert.doesNotMatch(source, /execSync\s*\(\s*[`"']firebase deploy/);
  assert.doesNotMatch(source, /execSync\s*\(\s*[`"']gcloud/);
});

test("canonical retention policy classifies known data families without claiming legal approval", () => {
  const policy = jsonRoot("operations/retention/retention-policy.json");
  assert.equal(policy.policyVersion, 1);
  assert.equal(policy.legalApprovalState, "LEGAL_APPROVAL_REQUIRED");
  assert.equal(policy.activationState, "REPOSITORY_POLICY_ONLY");
  assert.ok(Array.isArray(policy.dataFamilies));

  const dispositions = new Set(policy.dataFamilies.map((entry) => entry.disposition));
  assert.deepEqual([...dispositions].sort(), ["ANONYMIZE", "DELETE", "RETAIN"]);

  const requiredFamilies = new Set([
    "firebase_auth_identity",
    "gmail_oauth_credentials",
    "gmail_imported_data",
    "derived_financial_state",
    "push_registration_tokens",
    "provider_commerce_records",
    "operational_logs_metrics",
    "release_recovery_evidence",
  ]);
  const present = new Set(policy.dataFamilies.map((entry) => entry.id));
  for (const id of requiredFamilies) assert.equal(present.has(id), true, `Missing retention family ${id}`);

  for (const entry of policy.dataFamilies) {
    assert.ok(["DELETE", "ANONYMIZE", "RETAIN"].includes(entry.disposition));
    assert.equal(typeof entry.trigger, "string");
    assert.equal(typeof entry.rationale, "string");
    assert.ok(["REPOSITORY_READY", "OWNER_POLICY_REQUIRED"].includes(entry.policyState));
  }
});

test("monitoring specification covers critical subsystems but never claims alerts are active", () => {
  const spec = jsonRoot("operations/monitoring/monitoring-spec.json");
  assert.equal(spec.specVersion, 1);
  assert.equal(spec.activationState, "SPECIFIED_NOT_ACTIVE");
  assert.equal(spec.notificationChannelsState, "OWNER_ACTION_REQUIRED");
  assert.ok(Array.isArray(spec.metrics));

  const coverage = new Set(spec.metrics.map((entry) => entry.coverage));
  for (const required of [
    "gmail_oauth",
    "gmail_watch_reconciliation",
    "push",
    "provider_handoff",
    "privacy_deletion",
    "deployment_recovery",
  ]) {
    assert.equal(coverage.has(required), true, `Missing monitoring coverage ${required}`);
  }

  for (const metric of spec.metrics) {
    assert.equal(metric.activationState, "SPECIFIED_NOT_ACTIVE");
    assert.match(metric.event, /^[a-z][a-z0-9_-]*(?:\.[a-z0-9_-]+)+$/);
    assert.equal(typeof metric.windowMinutes, "number");
    assert.equal(typeof metric.threshold, "number");
    assert.ok(["WARNING", "ERROR", "CRITICAL"].includes(metric.severity));
  }
});

test("schema compatibility policy is additive-first and fail-closed for destructive changes", () => {
  const policy = jsonRoot("operations/schema/schema-compatibility.json");
  assert.equal(policy.policyVersion, 1);
  assert.equal(policy.compatibilityEpoch, "v1");
  assert.equal(policy.minimumSupportedReleaseWindow, 2);
  assert.equal(policy.additiveFirst, true);
  assert.equal(policy.semanticFieldReuseAllowed, false);
  assert.equal(policy.destructiveChanges.requireDeprecationWindow, true);
  assert.equal(policy.destructiveChanges.requireMigrationBeforeCleanup, true);
  assert.equal(policy.destructiveChanges.requireExplicitCompatibilityReview, true);
  assert.equal(policy.forwardRecovery.mustReadDataWrittenByCandidateRelease, true);
});

test("incident and recovery runbooks preserve operational truth and explicit stop conditions", () => {
  const operations = readRoot("docs/PRODUCTION_OPERATIONS_RUNBOOK.md");
  const recovery = readRoot("docs/PRODUCTION_RECOVERY_RUNBOOK.md");

  for (const heading of [
    "Incident Severity",
    "Evidence Capture",
    "Containment",
    "Stop Conditions",
    "External Owner Actions",
  ]) {
    assert.match(operations, new RegExp(`## ${heading}`));
  }

  for (const heading of [
    "Known-Good Release Identity",
    "Firebase Functions Recovery",
    "Firestore Rules Recovery",
    "Configuration Recovery",
    "Android Recovery",
    "Data Compatibility Verification",
    "Stop Conditions",
  ]) {
    assert.match(recovery, new RegExp(`## ${heading}`));
  }

  assert.match(operations, /LOG EXISTS.*MONITORING READY/s);
  assert.match(operations, /METRIC.*ALERT ACTIVE/s);
  assert.match(recovery, /ROLLBACK PLAN EXISTS.*ROLLBACK VERIFIED/s);
  assert.match(recovery, /staging.*production evidence/is);
});

test("Block 2 repository guard validates all machine-readable operations artifacts", () => {
  const source = readRoot("scripts/production-operations-guard.mjs");
  for (const requiredPath of [
    "operations/release/known-good-manifest.example.json",
    "operations/retention/retention-policy.json",
    "operations/monitoring/monitoring-spec.json",
    "operations/schema/schema-compatibility.json",
  ]) {
    assert.ok(source.includes(requiredPath));
  }
  assert.match(source, /LEGAL_APPROVAL_REQUIRED/);
  assert.match(source, /SPECIFIED_NOT_ACTIVE/);
  assert.match(source, /OWNER_ACTION_REQUIRED/);
});
