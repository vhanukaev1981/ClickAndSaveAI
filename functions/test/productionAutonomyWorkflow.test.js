const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflowPath = path.resolve(__dirname, '../../.github/workflows/production-release.yml');
const workflow = fs.readFileSync(workflowPath, 'utf8');
const internalBridgePath = path.resolve(__dirname, '../../.github/workflows/agent-internal-testing-dispatch.yml');
const internalBridge = fs.readFileSync(internalBridgePath, 'utf8');

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
