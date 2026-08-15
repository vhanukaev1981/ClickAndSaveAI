"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const ROOT = path.resolve(__dirname, "..", "..");
const PLANNER = path.join(ROOT, "scripts", "production-recovery-plan.mjs");
const GUARD = path.join(ROOT, "scripts", "production-operations-guard.mjs");
const MANIFEST = path.join(ROOT, "operations", "release", "known-good-manifest.example.json");

function run(script, args = []) {
  return spawnSync(process.execPath, [script, ...args], {
    cwd: ROOT,
    encoding: "utf8",
  });
}

function planner(target, manifest = MANIFEST, mode = "plan") {
  return run(PLANNER, ["--manifest", manifest, "--target", target, "--mode", mode]);
}

test("recovery planner produces plan-only output for every supported surface", () => {
  for (const target of ["functions", "firestore-rules", "configuration", "android"]) {
    const result = planner(target);
    assert.equal(result.status, 0, `${target}: ${result.stderr}`);
    const payload = JSON.parse(result.stdout);
    assert.equal(payload.target, target);
    assert.equal(payload.mode, "plan");
    assert.equal(payload.executionState, "NOT_EXECUTED");
    assert.equal(payload.verificationState, "PRODUCTION_VERIFICATION_REQUIRED");
    assert.equal(payload.sourceSha, "4f674d27dfec148e10108274de23013ae73613df");
    assert.ok(Array.isArray(payload.steps) && payload.steps.length >= 4);
  }
});

test("recovery planner refuses execution mode and unknown targets", () => {
  const execution = planner("functions", MANIFEST, "execute");
  assert.notEqual(execution.status, 0);
  assert.match(execution.stderr, /Only --mode plan is supported/);

  const unknown = planner("database");
  assert.notEqual(unknown.status, 0);
  assert.match(unknown.stderr, /--target must be one of/);
});

test("recovery planner refuses malformed SHA and secret-bearing recovery metadata", () => {
  const base = JSON.parse(fs.readFileSync(MANIFEST, "utf8"));
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "clickandsaveai-recovery-"));
  try {
    const badShaPath = path.join(tempDir, "bad-sha.json");
    fs.writeFileSync(badShaPath, JSON.stringify({ ...base, sourceSha: "main" }));
    const badSha = planner("functions", badShaPath);
    assert.notEqual(badSha.status, 0);
    assert.match(badSha.stderr, /40-character lowercase Git SHA/);

    const secretPath = path.join(tempDir, "secret.json");
    fs.writeFileSync(secretPath, JSON.stringify({ ...base, productionSecretValue: "must-never-exist" }));
    const secret = planner("functions", secretPath);
    assert.notEqual(secret.status, 0);
    assert.match(secret.stderr, /Secret-bearing recovery metadata key is prohibited/);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test("production operations guard proves repository readiness without upgrading external truth states", () => {
  const result = run(GUARD);
  assert.equal(result.status, 0, result.stderr);
  const payload = JSON.parse(result.stdout);
  assert.deepEqual(payload, {
    verdict: "PASS",
    evidenceState: "REPOSITORY_READY",
    productionRecoveryVerified: false,
    monitoringActive: false,
    alertsActive: false,
    legalApproval: false,
  });
});
