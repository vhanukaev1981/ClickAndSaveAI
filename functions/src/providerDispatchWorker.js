"use strict";

const {
  assertPrivacySafeProviderPayload,
  integrationStatus,
  nextDispatchState,
  validateAdapterContract,
  validateCommissionReconciliationEvidence,
} = require("./providerIntegrationFramework");

async function executeProviderDispatchAttempt({
  queueRecord,
  integrationConfig,
  adapter,
  nowMs = Date.now(),
}) {
  if (!queueRecord || typeof queueRecord !== "object") {
    throw new TypeError("provider dispatch queue record is required");
  }
  if (!integrationConfig || typeof integrationConfig !== "object") {
    throw new TypeError("provider integration config is required");
  }
  const runtimeAdapter = validateAdapterContract(adapter);
  const status = integrationStatus(integrationConfig, {
    adapterRegistered: true,
    healthVerified: integrationConfig.lastHealthVerified === true,
  });
  if (status.state !== "ACTUALLY_CONNECTED") {
    return {
      executed: false,
      state: status.state,
      reason: status.reason,
      transition: null,
    };
  }

  const payload = assertPrivacySafeProviderPayload(queueRecord.payload);
  const evidence = await runtimeAdapter.dispatch({
    leadId: String(queueRecord.leadId || ""),
    opportunityId: String(queueRecord.opportunityId || ""),
    offerId: String(queueRecord.offerId || ""),
    providerName: String(queueRecord.providerName || ""),
    payload,
  });

  return {
    executed: true,
    state: status.state,
    reason: "Provider dispatch adapter returned explicit evidence.",
    transition: nextDispatchState(
      { ...queueRecord, retryPolicy: integrationConfig.retryPolicy },
      evidence,
      nowMs
    ),
  };
}

async function executeCommissionReconciliation({
  lead,
  integrationConfig,
  adapter,
}) {
  if (!integrationConfig?.supportsCommissionReconciliation) {
    return {
      executed: false,
      reason: "Provider integration does not declare commission reconciliation support.",
      evidence: null,
    };
  }
  const runtimeAdapter = validateAdapterContract(adapter);
  if (typeof runtimeAdapter.reconcileCommission !== "function") {
    return {
      executed: false,
      reason: "Registered adapter does not implement commission reconciliation.",
      evidence: null,
    };
  }
  const status = integrationStatus(integrationConfig, {
    adapterRegistered: true,
    healthVerified: integrationConfig.lastHealthVerified === true,
  });
  if (status.state !== "ACTUALLY_CONNECTED") {
    return { executed: false, reason: status.reason, evidence: null };
  }
  const evidence = await runtimeAdapter.reconcileCommission({
    leadId: String(lead?.leadId || lead?.id || ""),
    providerName: String(lead?.requestedProvider || lead?.providerName || ""),
    externalReference: String(lead?.externalReference || ""),
  });
  return {
    executed: true,
    reason: "Provider adapter returned authoritative commission evidence.",
    evidence: validateCommissionReconciliationEvidence(evidence),
  };
}

module.exports = {
  executeProviderDispatchAttempt,
  executeCommissionReconciliation,
};
