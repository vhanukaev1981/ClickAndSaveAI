#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const SHA_PATTERN = /^[0-9a-f]{40}$/;
const VALID_DISPOSITIONS = new Set(["DELETE", "ANONYMIZE", "RETAIN"]);
const VALID_ALERT_SEVERITIES = new Set(["WARNING", "ERROR", "CRITICAL"]);
const EVENT_PATTERN = /^[a-z][a-z0-9_-]*(?:\.[a-z0-9_-]+)+$/;

function fail(message) {
  throw new Error(`Production operations guard failed: ${message}`);
}

function read(relativePath) {
  const absolute = path.join(ROOT, relativePath);
  if (!fs.existsSync(absolute)) fail(`missing ${relativePath}`);
  return fs.readFileSync(absolute, "utf8");
}

function json(relativePath) {
  try {
    return JSON.parse(read(relativePath));
  } catch (error) {
    fail(`${relativePath} is not valid JSON: ${error instanceof Error ? error.message : String(error)}`);
  }
}

function assert(condition, message) {
  if (!condition) fail(message);
}

function validateManifest() {
  const file = "operations/release/known-good-manifest.example.json";
  const manifest = json(file);
  assert(manifest.manifestVersion === 1, "known-good manifestVersion must be 1");
  assert(manifest.evidenceState === "EXAMPLE_NOT_PRODUCTION", "example manifest must never claim production evidence");
  assert(SHA_PATTERN.test(String(manifest.sourceSha || "")), "example sourceSha must be immutable");
  for (const surface of ["functions", "firestoreRules", "firestoreIndexes"]) {
    assert(manifest.surfaces?.[surface]?.sourceSha === manifest.sourceSha, `${surface} must bind to sourceSha`);
  }
  assert(manifest.surfaces?.configuration?.bindingState === "PENDING_PRODUCTION_BINDING", "configuration must remain pending production binding");
  assert(manifest.surfaces?.configuration?.secretValuesStored === false, "manifest must never store secret values");
  assert(Number.isInteger(manifest.surfaces?.android?.versionCode), "Android versionCode missing");
  assert(manifest.surfaces?.android?.signingState === "OWNER_ACTION_REQUIRED", "production signing must remain owner-controlled");
  assert(manifest.schema?.minimumSupportedReleaseWindow >= 2, "release compatibility window must be at least two releases");

  const schema = json("operations/release/known-good-manifest.schema.json");
  assert(schema.$schema === "https://json-schema.org/draft/2020-12/schema", "manifest schema dialect mismatch");
}

function validateRetention() {
  const file = "operations/retention/retention-policy.json";
  const policy = json(file);
  assert(policy.policyVersion === 1, "retention policyVersion must be 1");
  assert(policy.legalApprovalState === "LEGAL_APPROVAL_REQUIRED", "retention policy must preserve LEGAL_APPROVAL_REQUIRED");
  assert(policy.activationState === "REPOSITORY_POLICY_ONLY", "retention policy must not claim production activation");
  assert(Array.isArray(policy.dataFamilies) && policy.dataFamilies.length > 0, "retention data families missing");
  const ids = new Set();
  const dispositions = new Set();
  for (const entry of policy.dataFamilies) {
    assert(entry && typeof entry === "object", "invalid retention entry");
    assert(typeof entry.id === "string" && entry.id.length > 0, "retention id missing");
    assert(!ids.has(entry.id), `duplicate retention id ${entry.id}`);
    ids.add(entry.id);
    assert(VALID_DISPOSITIONS.has(entry.disposition), `invalid disposition for ${entry.id}`);
    dispositions.add(entry.disposition);
    assert(typeof entry.trigger === "string" && entry.trigger.length > 0, `trigger missing for ${entry.id}`);
    assert(typeof entry.rationale === "string" && entry.rationale.length > 0, `rationale missing for ${entry.id}`);
    assert(["REPOSITORY_READY", "OWNER_POLICY_REQUIRED"].includes(entry.policyState), `invalid policyState for ${entry.id}`);
  }
  for (const disposition of VALID_DISPOSITIONS) {
    assert(dispositions.has(disposition), `retention policy must contain ${disposition}`);
  }
}

function validateMonitoring() {
  const file = "operations/monitoring/monitoring-spec.json";
  const spec = json(file);
  assert(spec.specVersion === 1, "monitoring specVersion must be 1");
  assert(spec.activationState === "SPECIFIED_NOT_ACTIVE", "monitoring must remain SPECIFIED_NOT_ACTIVE");
  assert(spec.notificationChannelsState === "OWNER_ACTION_REQUIRED", "notification channels must remain OWNER_ACTION_REQUIRED");
  assert(Array.isArray(spec.metrics) && spec.metrics.length > 0, "monitoring metrics missing");
  const coverage = new Set();
  for (const metric of spec.metrics) {
    coverage.add(metric.coverage);
    assert(metric.activationState === "SPECIFIED_NOT_ACTIVE", `${metric.id} must not claim active alerting`);
    assert(EVENT_PATTERN.test(String(metric.event || "")), `${metric.id} has invalid event`);
    assert(Number.isFinite(metric.windowMinutes) && metric.windowMinutes > 0, `${metric.id} window invalid`);
    assert(Number.isFinite(metric.threshold) && metric.threshold >= 0, `${metric.id} threshold invalid`);
    assert(VALID_ALERT_SEVERITIES.has(metric.severity), `${metric.id} severity invalid`);
  }
  for (const required of ["gmail_oauth", "gmail_watch_reconciliation", "push", "provider_handoff", "privacy_deletion", "deployment_recovery"]) {
    assert(coverage.has(required), `monitoring coverage missing ${required}`);
  }
}

function validateSchemaCompatibility() {
  const file = "operations/schema/schema-compatibility.json";
  const policy = json(file);
  assert(policy.policyVersion === 1, "schema policyVersion must be 1");
  assert(policy.additiveFirst === true, "schema evolution must be additive-first");
  assert(policy.semanticFieldReuseAllowed === false, "semantic field reuse must remain prohibited");
  assert(policy.minimumSupportedReleaseWindow >= 2, "minimum supported release window must be at least two");
  assert(policy.destructiveChanges?.defaultAllowed === false, "destructive schema changes must fail closed");
  assert(policy.destructiveChanges?.requireDeprecationWindow === true, "destructive changes need deprecation window");
  assert(policy.destructiveChanges?.requireMigrationBeforeCleanup === true, "migration must precede cleanup");
  assert(policy.destructiveChanges?.requireExplicitCompatibilityReview === true, "compatibility review required");
  assert(policy.forwardRecovery?.mustReadDataWrittenByCandidateRelease === true, "forward recovery data compatibility missing");
}

function validateRunbooksAndPlanner() {
  const operations = read("docs/PRODUCTION_OPERATIONS_RUNBOOK.md");
  const recovery = read("docs/PRODUCTION_RECOVERY_RUNBOOK.md");
  const planner = read("scripts/production-recovery-plan.mjs");
  const telemetry = read("functions/src/operationalTelemetry.js");
  assert(operations.includes("LOG EXISTS") && operations.includes("MONITORING READY"), "operations truth rule missing");
  assert(operations.includes("METRIC SPECIFIED") && operations.includes("ALERT ACTIVE"), "alert truth rule missing");
  assert(recovery.includes("ROLLBACK PLAN EXISTS") && recovery.includes("ROLLBACK VERIFIED"), "recovery truth rule missing");
  assert(recovery.includes("PRODUCTION_VERIFICATION_REQUIRED"), "recovery evidence state missing");
  assert(planner.includes('mode !== "plan"'), "recovery planner must remain plan-only");
  assert(telemetry.includes("actorRef") && telemetry.includes("sanitizeOperationalDetails"), "privacy-safe telemetry contract missing");
}

export function runProductionOperationsGuard() {
  validateManifest();
  validateRetention();
  validateMonitoring();
  validateSchemaCompatibility();
  validateRunbooksAndPlanner();
  return {
    verdict: "PASS",
    evidenceState: "REPOSITORY_READY",
    productionRecoveryVerified: false,
    monitoringActive: false,
    alertsActive: false,
    legalApproval: false,
  };
}

try {
  const result = runProductionOperationsGuard();
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
} catch (error) {
  process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 1;
}
