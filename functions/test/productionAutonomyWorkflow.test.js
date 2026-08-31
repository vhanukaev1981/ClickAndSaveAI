const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflowPath = path.resolve(__dirname, '../../.github/workflows/production-release.yml');
const workflow = fs.readFileSync(workflowPath, 'utf8');
const internalBridgePath = path.resolve(__dirname, '../../.github/workflows/agent-internal-testing-dispatch.yml');
const internalBridge = fs.readFileSync(internalBridgePath, 'utf8');

function jobBlock(name, nextName) {
  const start = workflow.indexOf(`  ${name}:`);
  assert.notEqual(start, -1, `missing job ${name}`);
  const end = nextName ? workflow.indexOf(`  ${nextName}:`, start + 1) : workflow.length;
  assert.notEqual(end, -1, `missing next job ${nextName}`);
  return workflow.slice(start, end);
}

test('production release workflow exposes a distinct staged Google Play Production authorization', () => {
  assert.match(workflow, /authorize_google_play_production:/);
  assert.match(workflow, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
  assert.match(workflow, /default:\s*NO_PRODUCTION_UPLOAD/);
  assert.match(workflow, /production_rollout_percent:/);
});

test('Internal Testing dispatch bridge cannot authorize Google Play Production', () => {
  assert.doesNotMatch(internalBridge, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
  assert.doesNotMatch(internalBridge, /authorize_google_play_production/);
});

test('Firebase Production deployment records post-deploy health evidence without authorizing Play Production', () => {
  const firebaseJob = jobBlock('deploy-firebase-production');
  assert.match(firebaseJob, /DEPLOY_FIREBASE_PRODUCTION/);
  assert.match(firebaseJob, /google-github-actions\/auth@v3/);
  assert.match(firebaseJob, /production-health-gate\.mjs/);
  assert.match(firebaseJob, /firebase_deployed=true/);
  assert.doesNotMatch(firebaseJob, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
});
