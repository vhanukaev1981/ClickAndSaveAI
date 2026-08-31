import fs from 'node:fs';

function fail(message) {
  console.error(message);
  process.exit(1);
}

const [policyPath, telemetryPath, modeRaw] = process.argv.slice(2);
if (!policyPath || !telemetryPath || !modeRaw) {
  fail('Usage: production-health-gate.mjs <policy.json> <telemetry.json> <rollout_percent|firebase>');
}

let policy;
let telemetry;
try {
  policy = JSON.parse(fs.readFileSync(policyPath, 'utf8'));
  telemetry = JSON.parse(fs.readFileSync(telemetryPath, 'utf8'));
} catch {
  fail('Production health gate input is unreadable or invalid JSON.');
}

if (modeRaw === 'firebase') {
  for (const key of ['firebase_inventory_ok', 'smoke_ok', 'telemetry_complete']) {
    if (typeof telemetry[key] !== 'boolean') {
      fail(`Invalid or missing Firebase production health signal: ${key}.`);
    }
  }
  if (policy.require_complete_telemetry === true && telemetry.telemetry_complete !== true) {
    fail('Firebase production health gate blocked because required telemetry is incomplete.');
  }
  if (telemetry.firebase_inventory_ok !== true) {
    fail('Firebase production health gate blocked because deployed inventory validation failed.');
  }
  if (policy.require_smoke_ok === true && telemetry.smoke_ok !== true) {
    fail('Firebase production health gate blocked because smoke validation failed.');
  }
  console.log('Firebase production health gate passed.');
  process.exit(0);
}

const rollout = Number(modeRaw);
if (!Number.isInteger(rollout) || !Array.isArray(policy.rollout_percentages) || !policy.rollout_percentages.includes(rollout)) {
  fail('Requested rollout percentage is outside the production release policy.');
}

for (const key of ['crash_rate', 'anr_rate', 'backend_error_rate']) {
  const value = telemetry[key];
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    fail(`Invalid or missing production health metric: ${key}.`);
  }
}

if (typeof telemetry.smoke_ok !== 'boolean' || typeof telemetry.telemetry_complete !== 'boolean') {
  fail('Production health telemetry booleans are missing or invalid.');
}

if (policy.require_complete_telemetry === true && telemetry.telemetry_complete !== true) {
  fail('Production promotion blocked because required telemetry is incomplete.');
}
if (policy.require_smoke_ok === true && telemetry.smoke_ok !== true) {
  fail('Production promotion blocked because smoke validation failed.');
}

const thresholds = [
  ['crash_rate', 'max_crash_rate'],
  ['anr_rate', 'max_anr_rate'],
  ['backend_error_rate', 'max_backend_error_rate'],
];
for (const [metric, threshold] of thresholds) {
  const max = policy[threshold];
  if (typeof max !== 'number' || !Number.isFinite(max) || max < 0) {
    fail(`Invalid production release policy threshold: ${threshold}.`);
  }
  if (telemetry[metric] > max) {
    fail(`Production promotion blocked because ${metric} exceeds policy threshold.`);
  }
}

console.log(`Production health gate passed for rollout ${rollout}%.`);
