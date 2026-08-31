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

test('production release gate keeps Google Play Production authorization out of the legacy gate', () => {
  assert.doesNotMatch(workflow, /authorize_google_play_production:/);
  assert.doesNotMatch(workflow, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
  assert.doesNotMatch(workflow, /production_rollout_percent:/);
});

test('Internal Testing dispatch bridge cannot authorize Google Play Production', () => {
  assert.doesNotMatch(internalBridge, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
  assert.doesNotMatch(internalBridge, /authorize_google_play_production/);
});

test('Firebase Production deployment remains bounded to Firebase and cannot authorize Play Production', () => {
  const firebaseJob = jobBlock('deploy-firebase-production');
  assert.match(firebaseJob, /DEPLOY_FIREBASE_PRODUCTION/);
  assert.match(firebaseJob, /google-github-actions\/auth@v3/);
  assert.match(firebaseJob, /firebase deploy/);
  assert.doesNotMatch(firebaseJob, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
  assert.doesNotMatch(firebaseJob, /tracks\/production/);
});
