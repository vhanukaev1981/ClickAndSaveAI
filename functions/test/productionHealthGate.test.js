const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/production-health-gate.mjs');
const policyPath = path.join(repoRoot, 'config/production-release-policy.json');

function runGate(telemetry, rolloutPercent = 5) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'production-health-gate-'));
  const telemetryPath = path.join(dir, 'telemetry.json');
  fs.writeFileSync(telemetryPath, JSON.stringify(telemetry));
  const result = spawnSync(process.execPath, [scriptPath, policyPath, telemetryPath, String(rolloutPercent)], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  fs.rmSync(dir, { recursive: true, force: true });
  return result;
}

function runFirebaseGate(telemetry) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'production-health-gate-firebase-'));
  const telemetryPath = path.join(dir, 'telemetry.json');
  fs.writeFileSync(telemetryPath, JSON.stringify(telemetry));
  const result = spawnSync(process.execPath, [scriptPath, policyPath, telemetryPath, 'firebase'], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  fs.rmSync(dir, { recursive: true, force: true });
  return result;
}

test('production health gate accepts healthy complete telemetry', () => {
  assert.equal(fs.existsSync(policyPath), true, 'missing production release policy');
  assert.equal(fs.existsSync(scriptPath), true, 'missing production health gate evaluator');
  const result = runGate({
    crash_rate: 0.005,
    anr_rate: 0.002,
    backend_error_rate: 0.005,
    smoke_ok: true,
    telemetry_complete: true,
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

test('production health gate fails closed when telemetry is incomplete', () => {
  const result = runGate({
    crash_rate: 0.005,
    anr_rate: 0.002,
    backend_error_rate: 0.005,
    smoke_ok: true,
    telemetry_complete: false,
  });
  assert.notEqual(result.status, 0);
});

test('production health gate rejects crash rate above policy threshold', () => {
  const result = runGate({
    crash_rate: 0.03,
    anr_rate: 0.002,
    backend_error_rate: 0.005,
    smoke_ok: true,
    telemetry_complete: true,
  });
  assert.notEqual(result.status, 0);
});

test('production health gate rejects rollout percentages outside staged policy', () => {
  const result = runGate({
    crash_rate: 0.005,
    anr_rate: 0.002,
    backend_error_rate: 0.005,
    smoke_ok: true,
    telemetry_complete: true,
  }, 10);
  assert.notEqual(result.status, 0);
});

test('firebase production health gate accepts complete backend smoke telemetry without mobile vitals', () => {
  const result = runFirebaseGate({
    firebase_inventory_ok: true,
    smoke_ok: true,
    telemetry_complete: true,
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

test('firebase production health gate fails closed when provider inventory or smoke validation fails', () => {
  const inventoryFailure = runFirebaseGate({
    firebase_inventory_ok: false,
    smoke_ok: true,
    telemetry_complete: true,
  });
  const smokeFailure = runFirebaseGate({
    firebase_inventory_ok: true,
    smoke_ok: false,
    telemetry_complete: true,
  });
  assert.notEqual(inventoryFailure.status, 0);
  assert.notEqual(smokeFailure.status, 0);
});
