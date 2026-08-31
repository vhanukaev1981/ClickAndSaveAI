const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflowPath = path.resolve(__dirname, '../../.github/workflows/agent-production-dispatch.yml');

function workflow() {
  return fs.readFileSync(workflowPath, 'utf8');
}

test('production dispatcher is owner-only, title-gated, and validates exact current main SHA', () => {
  const text = workflow();
  assert.match(text, /issues:/);
  assert.match(text, /types:\s*\[labeled\]/);
  assert.match(text, /vhanukaev1981/);
  assert.match(text, /1314210715/);
  assert.match(text, /64756523/);
  assert.match(text, /github\.event\.issue\.user\.login/);
  assert.match(text, /github\.event\.issue\.user\.id/);
  assert.match(text, /github\.event\.issue\.title/);
  assert.match(text, /Agent Production Release/);
  assert.match(text, /git\/ref\/heads\/main/);
  assert.match(text, /source_sha/);
  assert.match(text, /\^\[0-9a-f\]\{40\}\$/);
});

test('production dispatcher exposes separate bounded labels and downstream workflows', () => {
  const text = workflow();
  assert.match(text, /release:firebase-health/);
  assert.match(text, /release:production-staged/);
  assert.match(text, /firebase-production-health-controller\.yml/);
  assert.match(text, /google-play-production-controller\.yml/);
  assert.match(text, /VERIFY_FIREBASE_PRODUCTION_HEALTH/);
  assert.match(text, /PUBLISH_GOOGLE_PLAY_PRODUCTION_STAGED/);
});

test('production dispatcher cannot authorize Internal Testing or cross-authorize operations', () => {
  const text = workflow();
  assert.doesNotMatch(text, /PUBLISH_GOOGLE_PLAY_INTERNAL_TESTING/);
  assert.doesNotMatch(text, /agent-internal-testing-dispatch\.yml/);
  assert.doesNotMatch(text, /DEPLOY_FIREBASE_PRODUCTION/);
  assert.match(text, /operation.*firebase-health|firebase-health.*operation/i);
  assert.match(text, /operation.*production-staged|production-staged.*operation/i);
});
