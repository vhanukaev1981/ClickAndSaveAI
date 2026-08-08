"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  OPERATOR_STATES,
  deriveIntegrationStatus,
  buildPrivacySafePartnerFunnel,
} = require("../src/providerIntegrationStatus");

test("integration is never shown as connected before connection verification", () => {
  assert.equal(deriveIntegrationStatus({ enabled: false }).state, OPERATOR_STATES.DISABLED);
  assert.equal(deriveIntegrationStatus({ enabled: true }).state, OPERATOR_STATES.READY_FOR_ADAPTER);
  assert.equal(deriveIntegrationStatus({
    enabled: true,
    adapterImplemented: true,
    credentialsConfigured: true,
  }).state, OPERATOR_STATES.CONNECTION_UNVERIFIED);
});

test("verified healthy provider is ACTUALLY_CONNECTED", () => {
  const status = deriveIntegrationStatus({
    enabled: true,
    adapterImplemented: true,
    credentialsConfigured: true,
    connectionVerified: true,
    attemptedLast24h: 100,
    failedLast24h: 3,
    deadLetterCount: 0,
  });
  assert.equal(status.state, OPERATOR_STATES.ACTUALLY_CONNECTED);
  assert.equal(status.actionable, true);
  assert.equal(status.failureRate, 0.03);
});

test("dead letters or elevated failure rate mark integration degraded", () => {
  assert.equal(deriveIntegrationStatus({
    enabled: true,
    adapterImplemented: true,
    credentialsConfigured: true,
    connectionVerified: true,
    attemptedLast24h: 10,
    failedLast24h: 2,
  }).state, OPERATOR_STATES.DEGRADED);

  assert.equal(deriveIntegrationStatus({
    enabled: true,
    adapterImplemented: true,
    credentialsConfigured: true,
    connectionVerified: true,
    attemptedLast24h: 100,
    failedLast24h: 0,
    deadLetterCount: 1,
  }).state, OPERATOR_STATES.DEGRADED);
});

test("privacy-safe funnel contains aggregate counts only", () => {
  const funnel = buildPrivacySafePartnerFunnel({
    verifiedOpportunities: 100,
    consentedRequests: 25,
    acknowledgedDispatches: 20,
    activations: 5,
    confirmedCommission: 420.5,
    uid: "must-not-leak",
    gmailContent: "must-not-leak",
  });
  assert.deepEqual(funnel, {
    verifiedOpportunities: 100,
    consentedRequests: 25,
    acknowledgedDispatches: 20,
    activations: 5,
    confirmedCommission: 420.5,
    requestRate: 0.25,
    activationRate: 0.25,
  });
  assert.equal(Object.hasOwn(funnel, "uid"), false);
  assert.equal(Object.hasOwn(funnel, "gmailContent"), false);
});
