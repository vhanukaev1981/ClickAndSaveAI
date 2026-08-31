const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflowPath = path.resolve(__dirname, '../../.github/workflows/google-play-production-controller.yml');
const policyPath = path.resolve(__dirname, '../../scripts/google-play-production-policy.mjs');

function workflow() {
  return fs.readFileSync(workflowPath, 'utf8');
}

test('Play Production controller has a distinct exact-main staged authorization boundary', () => {
  const text = workflow();
  assert.match(text, /workflow_dispatch:/);
  assert.match(text, /source_sha:/);
  assert.match(text, /authorize_google_play_production:/);
  assert.match(text, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
  assert.match(text, /default:\s*NO_PRODUCTION_UPLOAD/);
  assert.match(text, /production_rollout_percent:/);
  assert.match(text, /EXPECTED_REPOSITORY:\s*vhanukaev1981\/ClickAndSaveAI/);
  assert.match(text, /EXPECTED_REPOSITORY_ID:\s*['"]1314210715['"]/);
  assert.match(text, /EXPECTED_REPOSITORY_OWNER_ID:\s*['"]64756523['"]/);
  assert.match(text, /EXPECTED_REF:\s*refs\/heads\/main/);
  assert.match(text, /google-play-production-controller\.yml@refs\/heads\/main/);
  assert.match(text, /\^\[0-9a-f\]\{40\}\$/);
  assert.match(text, /github\.sha/);
  assert.match(text, /github\.repository_owner_id/);
  assert.doesNotMatch(text, /PUBLISH_GOOGLE_PLAY_INTERNAL_TESTING/);
});

test('Play Production controller uses isolated publisher WIF and production track only', () => {
  const text = workflow();
  assert.match(text, /google-github-actions\/auth@v3/);
  assert.match(text, /projects\/991489557172\/locations\/global\/workloadIdentityPools\/github-actions-play-production\/providers\/clickandsaveai-play-production/);
  assert.match(text, /clickandsaveai-play-publisher@click-save-ai-production\.iam\.gserviceaccount\.com/);
  assert.match(text, /androidpublisher/);
  assert.match(text, /tracks\/production/);
  assert.doesNotMatch(text, /tracks\/internal/);
  assert.match(text, /internal.*evidence|evidence.*internal/i);
});

test('Play Production controller requires health evidence and records bounded rollout evidence', () => {
  const text = workflow();
  assert.match(text, /firebase-production-health-\$\{\{ env\.SOURCE_SHA \}\}/);
  assert.match(text, /telemetry_complete|health_status/);
  assert.match(text, /5/);
  assert.match(text, /20/);
  assert.match(text, /50/);
  assert.match(text, /100/);
  assert.match(text, /halt|blocked|no-op/i);
  assert.match(text, /play-production-rollout-\$\{\{ env\.SOURCE_SHA \}\}/);
});

test('Play Production policy enforces the exact staged sequence and fail-closed health semantics', async () => {
  assert.equal(fs.existsSync(policyPath), true, 'missing staged rollout policy module');
  const policy = await import(policyPath);
  assert.equal(typeof policy.decideProductionRollout, 'function');

  assert.deepEqual(policy.decideProductionRollout({ currentPercent: 0, requestedPercent: 5, healthy: true, telemetryComplete: true }), { action: 'promote', percent: 5 });
  assert.deepEqual(policy.decideProductionRollout({ currentPercent: 5, requestedPercent: 20, healthy: true, telemetryComplete: true }), { action: 'promote', percent: 20 });
  assert.deepEqual(policy.decideProductionRollout({ currentPercent: 20, requestedPercent: 50, healthy: true, telemetryComplete: true }), { action: 'promote', percent: 50 });
  assert.deepEqual(policy.decideProductionRollout({ currentPercent: 50, requestedPercent: 100, healthy: true, telemetryComplete: true }), { action: 'promote', percent: 100 });
  assert.deepEqual(policy.decideProductionRollout({ currentPercent: 20, requestedPercent: 100, healthy: true, telemetryComplete: true }), { action: 'blocked', reason: 'invalid-transition' });
  assert.deepEqual(policy.decideProductionRollout({ currentPercent: 20, requestedPercent: 50, healthy: false, telemetryComplete: true }), { action: 'halt', reason: 'unhealthy' });
  assert.deepEqual(policy.decideProductionRollout({ currentPercent: 20, requestedPercent: 50, healthy: true, telemetryComplete: false }), { action: 'blocked', reason: 'missing-telemetry' });
});
