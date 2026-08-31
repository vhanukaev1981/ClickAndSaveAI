const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflowPath = path.resolve(__dirname, '../../.github/workflows/production-release.yml');
const workflow = fs.readFileSync(workflowPath, 'utf8');
const agentDispatchWorkflowPath = path.resolve(__dirname, '../../.github/workflows/agent-internal-testing-dispatch.yml');

function jobBlock(name, nextName) {
  const start = workflow.indexOf(`  ${name}:`);
  assert.notEqual(start, -1, `missing job ${name}`);
  const end = nextName ? workflow.indexOf(`  ${nextName}:`, start + 1) : workflow.length;
  assert.notEqual(end, -1, `missing next job ${nextName}`);
  return workflow.slice(start, end);
}

test('production release workflow gates Google Play internal testing behind an explicit authorization phrase', () => {
  assert.match(workflow, /authorize_google_play_internal_testing:/);
  assert.match(workflow, /PUBLISH_GOOGLE_PLAY_INTERNAL_TESTING/);
  assert.match(workflow, /default:\s*NO_PLAY_UPLOAD/);
});

test('Google Play publishing uses WIF with the dedicated publisher service account', () => {
  assert.match(workflow, /google-play-internal-testing:/);
  assert.match(workflow, /id-token:\s*write/);
  assert.match(workflow, /google-github-actions\/auth@v3/);
  assert.match(workflow, /clickandsaveai-play-publisher@click-save-ai-production\.iam\.gserviceaccount\.com/);
  assert.match(workflow, /GCP_WORKLOAD_IDENTITY_PROVIDER/);
});

test('Google Play publishing uploads the AAB only to the internal track and commits the edit', () => {
  assert.match(workflow, /upload\.androidpublisher\.googleapis\.com\/upload\/androidpublisher\/v3\/applications/);
  assert.match(workflow, /tracks\/internal/);
  assert.match(workflow, /:commit/);
  assert.doesNotMatch(workflow, /tracks\/production/);
});

test('Play Internal Testing candidate and publisher jobs use the production environment so scoped secrets and variables are injected', () => {
  const candidate = jobBlock('production-candidate', 'production-wif-auth-proof');
  const play = jobBlock('google-play-internal-testing', 'deploy-firebase-production');
  assert.match(candidate, /^\s*environment:\s*production\s*$/m);
  assert.match(play, /^\s*environment:\s*production\s*$/m);
});

test('agent-controlled Internal Testing dispatch bridge is fail-closed and cannot authorize other production actions', () => {
  assert.equal(fs.existsSync(agentDispatchWorkflowPath), true, 'missing agent Internal Testing dispatch bridge workflow');
  const bridge = fs.readFileSync(agentDispatchWorkflowPath, 'utf8');

  assert.match(bridge, /issues:/);
  assert.match(bridge, /types:\s*\[opened\]/);
  assert.match(bridge, /Agent Internal Testing Release/);
  assert.match(bridge, /github\.event\.issue\.user\.login/);
  assert.match(bridge, /github\.repository_owner/);
  assert.match(bridge, /source_sha/);
  assert.match(bridge, /git\/ref\/heads\/main/);
  assert.match(bridge, /actions\/workflows\/production-release\.yml\/dispatches/);
  assert.match(bridge, /CLICKANDSAVEAI_PRODUCTION/);
  assert.match(bridge, /PUBLISH_GOOGLE_PLAY_INTERNAL_TESTING/);
  assert.match(bridge, /NO_DEPLOY/);
  assert.match(bridge, /NO_WIF_PROOF/);
  assert.match(bridge, /NO_BOOTSTRAP/);
  assert.match(bridge, /NO_3F_PROBE/);
  assert.match(bridge, /NO_3F_EXTERNAL_PROBE/);
  assert.match(bridge, /NO_3F_SERVICE_STATE_PROBE/);
  assert.match(bridge, /NO_3F_FIREBASE_IAM_PERMISSION_PROBE/);
  assert.doesNotMatch(bridge, /tracks\/production/);
  assert.doesNotMatch(bridge, /DEPLOY_FIREBASE_PRODUCTION/);
  assert.doesNotMatch(bridge, /BOOTSTRAP_PRODUCTION_IAM_ONCE/);
});
