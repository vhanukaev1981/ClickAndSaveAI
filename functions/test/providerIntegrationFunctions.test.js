"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { getApps, initializeApp } = require("firebase-admin/app");

if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const { _publicIntegrationStatus: publicIntegrationStatus } = require("../src/providerIntegrationFunctions");

test("configured provider is never reported connected without a registered runtime adapter", () => {
  const status = publicIntegrationStatus("provider-a", {
    providerName: "Provider A",
    adapterKey: "provider-a-v1",
    enabled: true,
    supportsDeliveryReceipts: true,
    supportsCommissionReconciliation: true,
    lastHealthVerified: true,
  });
  assert.equal(status.state, "READY_FOR_ADAPTER");
  assert.equal(status.lastHealthVerified, true);
});
