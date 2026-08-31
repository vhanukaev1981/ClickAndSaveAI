const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const wrapperPath = path.resolve(__dirname, '../../scripts/bootstrap-production-deploy-iam.sh');
const basePath = path.resolve(__dirname, '../../scripts/bootstrap-production-deploy-iam-base.sh');
const bootstrapPath = path.resolve(__dirname, '../../scripts/bootstrap-production-controller-wif.sh');
const verifyPath = path.resolve(__dirname, '../../scripts/verify-production-controller-wif.sh');

function read(file) {
  return fs.readFileSync(file, 'utf8');
}

test('canonical deploy bootstrap delegates old behavior and isolated controller WIF setup', () => {
  const wrapper = read(wrapperPath);
  assert.equal(fs.existsSync(basePath), true, 'missing preserved canonical deploy IAM bootstrap');
  assert.match(wrapper, /bootstrap-production-deploy-iam-base\.sh/);
  assert.match(wrapper, /bootstrap-production-controller-wif\.sh/);
  assert.match(wrapper, /verify-production-controller-wif\.sh/);
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
