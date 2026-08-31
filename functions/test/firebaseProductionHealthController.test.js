const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflowPath = path.resolve(__dirname, '../../.github/workflows/firebase-production-health-controller.yml');

function readWorkflow() {
  return fs.readFileSync(workflowPath, 'utf8');
}

test('Firebase production health controller is exact-SHA, owner-bound, isolated-WIF authenticated, and Firebase-only', () => {
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
  assert.match(workflow, /projects\/991489557172\/locations\/global\/workloadIdentityPools\/github-actions-firebase-health\/providers\/clickandsaveai-firebase-health/);
  assert.match(workflow, /clickandsaveai-github-deployer@click-save-ai-production\.iam\.gserviceaccount\.com/);
  assert.match(workflow, /click-save-ai-production/);
  assert.match(workflow, /production-health-gate\.mjs/);
  assert.match(workflow, /firebase-production-health-\$\{\{ env\.SOURCE_SHA \}\}/);
  assert.match(workflow, /rollback-required|rollback_required/);

  assert.doesNotMatch(workflow, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
  assert.doesNotMatch(workflow, /tracks\/production/);
  assert.doesNotMatch(workflow, /androidpublisher/);
});

test('Firebase health controller proves the deployment job and consumes the candidate artifact that actually exists', () => {
  const workflow = readWorkflow();
  assert.match(workflow, /deploy-firebase-production/);
  assert.match(workflow, /clickandsaveai-production-candidate-\$\{\{ env\.SOURCE_SHA \}\}/);
  assert.match(workflow, /application_id=com\.aistudio\.clickandsaveai\.app|application_id=\$EXPECTED_PACKAGE|EXPECTED_PACKAGE/);
  assert.match(workflow, /firebase_project_id=click-save-ai-production|firebase_project_id=\$EXPECTED_PROJECT_ID|EXPECTED_PROJECT_ID/);
  assert.doesNotMatch(workflow, /clickandsaveai-firebase-production-evidence-/);
  assert.doesNotMatch(workflow, /firebase_deployed=true/);
});

test('Firebase production health controller fails closed on missing evidence or telemetry', () => {
  const workflow = readWorkflow();
  assert.match(workflow, /missing.*evidence|evidence.*missing/i);
  assert.match(workflow, /telemetry_complete/);
  assert.match(workflow, /blocked|rollback-required|rollback_required/);
});
