"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  canUseStagingCredentialLossRecovery,
  STAGING_PROJECT_ID,
  MANUAL_PROVIDER_CLEANUP_CONFIRMATION,
} = require("../src/gmailCredentialLossRecoveryPolicy");

function pendingCredentialLossConnection(overrides = {}) {
  return {
    disconnectState: "RETRY_REQUIRED",
    authorizationState: "DISCONNECTED_PENDING_PROVIDER_CLEANUP",
    lastWatchStopStatus: "UNCONFIRMED_CREDENTIAL_ERROR",
    lastOauthRevocationStatus: "UNCONFIRMED_CREDENTIAL_ERROR",
    watchEnabled: false,
    scopes: [],
    ...overrides,
  };
}

test("credential-loss recovery is allowed only for exact staging/manual-confirmation state", () => {
  assert.equal(canUseStagingCredentialLossRecovery({
    projectId: STAGING_PROJECT_ID,
    confirmation: MANUAL_PROVIDER_CLEANUP_CONFIRMATION,
    connection: pendingCredentialLossConnection(),
  }), true);
});

test("credential-loss recovery rejects production and any incomplete cleanup precondition", () => {
  const valid = {
    projectId: STAGING_PROJECT_ID,
    confirmation: MANUAL_PROVIDER_CLEANUP_CONFIRMATION,
    connection: pendingCredentialLossConnection(),
  };
  assert.equal(canUseStagingCredentialLossRecovery({ ...valid, projectId: "click-save-ai-production" }), false);
  assert.equal(canUseStagingCredentialLossRecovery({ ...valid, confirmation: "" }), false);
  assert.equal(canUseStagingCredentialLossRecovery({ ...valid, connection: pendingCredentialLossConnection({ watchEnabled: true }) }), false);
  assert.equal(canUseStagingCredentialLossRecovery({ ...valid, connection: pendingCredentialLossConnection({ scopes: ["https://www.googleapis.com/auth/gmail.readonly"] }) }), false);
  assert.equal(canUseStagingCredentialLossRecovery({ ...valid, connection: pendingCredentialLossConnection({ lastOauthRevocationStatus: "UNCONFIRMED_EXTERNAL_ERROR" }) }), false);
});
