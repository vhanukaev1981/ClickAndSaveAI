import fs from 'node:fs';

function fail(message) {
  console.error(message);
  process.exit(1);
}

const [evidencePath, action, target, result, rolloutRaw] = process.argv.slice(2);
if (!evidencePath || !action || !target || !result) {
  fail('Usage: production-release-evidence.mjs <identity.txt> <action> <target> <result> [rollout_percent]');
}

let text;
try {
  text = fs.readFileSync(evidencePath, 'utf8');
} catch {
  fail('Production release evidence file is unreadable.');
}

const entries = new Map();
for (const rawLine of text.split(/\r?\n/)) {
  if (!rawLine) continue;
  const index = rawLine.indexOf('=');
  if (index <= 0) fail('Production release evidence contains a malformed line.');
  entries.set(rawLine.slice(0, index), rawLine.slice(index + 1));
}

const required = ['source_sha', 'application_id', 'version_code', 'version_name', 'aab_sha256', 'production_gate_run_id'];
for (const key of required) {
  if (!entries.has(key) || entries.get(key) === '') fail(`Missing production release identity field: ${key}.`);
}

if (!/^[0-9a-f]{40}$/.test(entries.get('source_sha'))) fail('Invalid source_sha in production release evidence.');
if (entries.get('application_id') !== 'com.aistudio.clickandsaveai.app') fail('Unexpected application_id in production release evidence.');
if (!/^[1-9][0-9]*$/.test(entries.get('version_code'))) fail('Invalid version_code in production release evidence.');
if (!/^[0-9a-f]{64}$/.test(entries.get('aab_sha256'))) fail('Invalid aab_sha256 in production release evidence.');
if (!/^[1-9][0-9]*$/.test(entries.get('production_gate_run_id'))) fail('Invalid production_gate_run_id in production release evidence.');

const allowedActions = new Set(['firebase-deploy', 'play-production']);
if (!allowedActions.has(action)) fail('Unsupported production action.');
if (action === 'firebase-deploy' && target !== 'firebase-production') fail('Firebase deployment target mismatch.');
if (action === 'play-production' && target !== 'production') fail('Google Play Production target mismatch.');
if (!new Set(['success', 'failed', 'halted']).has(result)) fail('Unsupported production result.');

if (rolloutRaw !== undefined) {
  const rollout = Number(rolloutRaw);
  if (!Number.isInteger(rollout) || ![5, 20, 50, 100].includes(rollout)) fail('Unsupported production rollout percentage.');
  entries.set('production_rollout_percent', String(rollout));
}

if (action === 'firebase-deploy' && result === 'success') {
  entries.set('firebase_deployed', 'true');
}
if (action === 'play-production' && result === 'success') {
  entries.set('google_play_published', 'true');
  entries.set('google_play_track', 'production');
}

entries.set('production_action', action);
entries.set('production_target', target);
entries.set('production_result', result);

const output = [...entries.entries()].map(([key, value]) => `${key}=${value}`).join('\n') + '\n';
fs.writeFileSync(evidencePath, output, { mode: 0o600 });
console.log('Production release evidence updated.');
