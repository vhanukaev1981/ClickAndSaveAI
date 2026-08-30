const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflowPath = path.resolve(__dirname, '../../.github/workflows/production-release.yml');
const workflow = fs.readFileSync(workflowPath, 'utf8');

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
