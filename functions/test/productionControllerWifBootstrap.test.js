const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const canonicalPath = path.resolve(__dirname, '../../scripts/bootstrap-production-deploy-iam.sh');
const bootstrapPath = path.resolve(__dirname, '../../scripts/bootstrap-production-controller-wif.sh');
const verifyPath = path.resolve(__dirname, '../../scripts/verify-production-controller-wif.sh');
const workflowPath = path.resolve(__dirname, '../../.github/workflows/production-release.yml');

function read(file) {
  return fs.readFileSync(file, 'utf8');
}

test('canonical deploy bootstrap remains the existing least-privilege implementation', () => {
  const canonical = read(canonicalPath);
  assert.match(canonical, /CUSTOM_DEPLOY_ROLE_ID="clickandsaveaiFirebaseDeployIamPolicy"/);
  assert.match(canonical, /INTENDED_ROLES=\(/);
  assert.match(canonical, /APPROVED_PREEXISTING_ROLES=\(/);
  assert.match(canonical, /GOOGLE_APPLICATION_CREDENTIALS/);
  assert.doesNotMatch(canonical, /bootstrap-production-deploy-iam-base\.sh/);
});

test('controller WIF bootstrap creates separate exact-workflow trust boundaries', () => {
  const bootstrap = read(bootstrapPath);
  assert.match(bootstrap, /github-actions-firebase-health/);
  assert.match(bootstrap, /clickandsaveai-firebase-health/);
  assert.match(bootstrap, /firebase-production-health-controller\.yml@refs\/heads\/main/);
  assert.match(bootstrap, /github-actions-play-production/);
  assert.match(bootstrap, /clickandsaveai-play-production/);
  assert.match(bootstrap, /google-play-production-controller\.yml@refs\/heads\/main/);
  assert.match(bootstrap, /attribute\.repository_id/);
  assert.match(bootstrap, /attribute\.repository_owner_id/);
  assert.match(bootstrap, /attribute\.environment/);
  assert.match(bootstrap, /attribute\.ref/);
  assert.match(bootstrap, /attribute\.workflow_ref/);
  assert.match(bootstrap, /roles\/iam\.workloadIdentityUser/);
  assert.match(bootstrap, /clickandsaveai-github-deployer@click-save-ai-production\.iam\.gserviceaccount\.com/);
  assert.match(bootstrap, /clickandsaveai-play-publisher@click-save-ai-production\.iam\.gserviceaccount\.com/);
});

test('controller WIF verifier independently checks both isolated pools and providers', () => {
  const verify = read(verifyPath);
  assert.match(verify, /github-actions-firebase-health/);
  assert.match(verify, /clickandsaveai-firebase-health/);
  assert.match(verify, /github-actions-play-production/);
  assert.match(verify, /clickandsaveai-play-production/);
  assert.match(verify, /firebase-production-health-controller\.yml@refs\/heads\/main/);
  assert.match(verify, /google-play-production-controller\.yml@refs\/heads\/main/);
  assert.match(verify, /roles\/iam\.workloadIdentityUser|workloadIdentityUser/);
});

test('governed production bootstrap job provisions and independently verifies controller WIF boundaries', () => {
  const workflow = read(workflowPath);
  const marker = '  production-bootstrap-deploy-iam:';
  const start = workflow.indexOf(marker);
  assert.notEqual(start, -1, 'missing governed production bootstrap job');
  const tail = workflow.slice(start + marker.length);
  const nextJob = tail.match(/\n  [A-Za-z0-9_-]+:\n/);
  const bootstrapJob = nextJob ? tail.slice(0, nextJob.index) : tail;

  assert.match(bootstrapJob, /bash scripts\/bootstrap-production-deploy-iam\.sh/);
  assert.match(bootstrapJob, /bash scripts\/verify-production-deploy-iam\.sh/);
  assert.match(bootstrapJob, /bash scripts\/bootstrap-production-controller-wif\.sh/);
  assert.match(bootstrapJob, /bash scripts\/verify-production-controller-wif\.sh/);
});
