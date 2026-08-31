const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/production-release-evidence.mjs');

function identityText() {
  return [
    'source_sha=0123456789abcdef0123456789abcdef01234567',
    'application_id=com.aistudio.clickandsaveai.app',
    'version_code=7',
    'version_name=1.0.7',
    'aab_sha256=' + 'a'.repeat(64),
    'production_gate_run_id=12345',
    'firebase_deployed=false',
    'google_play_published=false',
    '',
  ].join('\n');
}

function runEvidence(args, initial = identityText()) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'production-release-evidence-'));
  const evidencePath = path.join(dir, 'identity.txt');
  fs.writeFileSync(evidencePath, initial);
  const result = spawnSync(process.execPath, [scriptPath, evidencePath, ...args], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  const output = fs.existsSync(evidencePath) ? fs.readFileSync(evidencePath, 'utf8') : '';
  fs.rmSync(dir, { recursive: true, force: true });
  return { result, output };
}

test('production release evidence records a successful Firebase production deployment without changing Play truth', () => {
  assert.equal(fs.existsSync(scriptPath), true, 'missing production release evidence script');
  const { result, output } = runEvidence(['firebase-deploy', 'firebase-production', 'success']);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(output, /firebase_deployed=true/);
  assert.match(output, /production_action=firebase-deploy/);
  assert.match(output, /production_target=firebase-production/);
  assert.match(output, /production_result=success/);
  assert.match(output, /google_play_published=false/);
});

test('production release evidence rejects malformed source identity', () => {
  const bad = identityText().replace('0123456789abcdef0123456789abcdef01234567', 'not-a-sha');
  const { result } = runEvidence(['firebase-deploy', 'firebase-production', 'success'], bad);
  assert.notEqual(result.status, 0);
});

test('production release evidence accepts only configured rollout percentages when one is supplied', () => {
  const allowed = runEvidence(['play-production', 'production', 'success', '20']);
  const rejected = runEvidence(['play-production', 'production', 'success', '10']);
  assert.equal(allowed.result.status, 0, allowed.result.stderr || allowed.result.stdout);
  assert.match(allowed.output, /production_rollout_percent=20/);
  assert.notEqual(rejected.result.status, 0);
});
