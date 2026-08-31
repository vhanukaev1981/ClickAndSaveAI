const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflowPath = path.resolve(__dirname, '../../.github/workflows/firebase-production-health-controller.yml');

function readWorkflow() {
  return fs.readFileSync(workflowPath, 'utf8');
}

test('Firebase production health controller is exact-SHA, owner-bound, WIF-authenticated, and Firebase-only', () => {
  const workflow = readWorkflow();

  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /source_sha:/);
  assert.match(workflow, /authorize_firebase_health_check:/);
  assert.match(workflow, /VERIFY_FIREBASE_PRODUCTION_HEALTH/);
  assert.match(workflow, /default:\s*NO_FIREBASE_HEALTH_CHECK/);

  assert.match(workflow, /EXPECTED_REPOSITORY:\s*vhanukaev1981\/ClickAndSaveAI/);
  assert.match(workflow, /EXPECTED_REPOSITORY_ID:\s*['"]1314210715['"]/);
  assert.match(workflow, /EXPECTED_REPOSITORY_OWNER_ID:\s*['"]64756523['"]/);
  assert.match(workflow, /EXPECTED_REF:\s*refs\/heads\/main/);
  assert.match(workflow, /firebase-production-health-controller\.yml@refs\/heads\/main/);
  assert.match(workflow, /\^\[0-9a-f\]\{40\}\$/);
  assert.match(workflow, /github\.sha/);
  assert.match(workflow, /github\.repository_owner_id/);

  assert.match(workflow, /google-github-actions\/auth@v3/);
  assert.match(workflow, /GCP_WORKLOAD_IDENTITY_PROVIDER/);
  assert.match(workflow, /GCP_DEPLOY_SERVICE_ACCOUNT/);
  assert.match(workflow, /click-save-ai-production/);
  assert.match(workflow, /clickandsaveai-firebase-production-evidence-\$\{\{ env\.SOURCE_SHA \}\}/);
  assert.match(workflow, /production-health-gate\.mjs/);
  assert.match(workflow, /firebase-production-health-\$\{\{ env\.SOURCE_SHA \}\}/);
  assert.match(workflow, /rollback-required|rollback_required/);

  assert.doesNotMatch(workflow, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
  assert.doesNotMatch(workflow, /tracks\/production/);
  assert.doesNotMatch(workflow, /androidpublisher/);
});

test('Firebase production health controller fails closed on missing deployment evidence', () => {
  const workflow = readWorkflow();
  assert.match(workflow, /if-no-files-found:\s*error|missing.*evidence|evidence.*missing/i);
  assert.match(workflow, /telemetry_complete/);
  assert.match(workflow, /blocked|rollback-required|rollback_required/);
});
