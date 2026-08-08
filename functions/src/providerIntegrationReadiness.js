"use strict";

const READINESS_STATES = Object.freeze({
  NOT_READY: "NOT_READY",
  READY_FOR_SANDBOX: "READY_FOR_SANDBOX",
  READY_FOR_PRODUCTION: "READY_FOR_PRODUCTION",
});

function evaluateIntegrationReadiness(input = {}) {
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new TypeError("integration readiness input must be an object");

  const checks = {
    adapterImplemented: input.adapterImplemented === true,
    credentialsReferenced: input.credentialsReferenced === true,
    connectionVerified: input.connectionVerified === true,
    dispatchCapability: input.dispatchCapability === true,
    evidencePathVerified: input.evidencePathVerified === true,
    webhookOrReportVerified: input.webhookOrReportVerified === true,
    commercialContractActive: input.commercialContractActive === true,
    dataMinimizationVerified: input.dataMinimizationVerified === true,
  };

  const requiredSandbox = ["adapterImplemented", "credentialsReferenced", "dispatchCapability", "dataMinimizationVerified"];
  const requiredProduction = [...requiredSandbox, "connectionVerified", "evidencePathVerified", "webhookOrReportVerified", "commercialContractActive"];
  const missingSandbox = requiredSandbox.filter((key) => !checks[key]);
  const missingProduction = requiredProduction.filter((key) => !checks[key]);

  if (missingSandbox.length > 0) {
    return { state: READINESS_STATES.NOT_READY, missing: missingSandbox, checks };
  }
  if (missingProduction.length > 0) {
    return { state: READINESS_STATES.READY_FOR_SANDBOX, missing: missingProduction, checks };
  }
  return { state: READINESS_STATES.READY_FOR_PRODUCTION, missing: [], checks };
}

module.exports = {
  READINESS_STATES,
  evaluateIntegrationReadiness,
};
