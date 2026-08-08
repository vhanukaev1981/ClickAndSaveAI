"use strict";

function planSandboxProbe(input = {}) {
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new TypeError("sandbox probe input must be an object");
  const providerId = typeof input.providerId === "string" ? input.providerId.trim() : "";
  const adapterKey = typeof input.adapterKey === "string" ? input.adapterKey.trim() : "";
  if (!providerId) throw new TypeError("providerId is required");
  if (!adapterKey) throw new TypeError("adapterKey is required");
  if (input.adapterImplemented !== true) return { allowed: false, reason: "adapter is not implemented", providerId, adapterKey };
  if (input.credentialsReferenced !== true) return { allowed: false, reason: "credentials are not referenced", providerId, adapterKey };
  if (input.statusLookupCapability !== true) return { allowed: false, reason: "adapter lacks non-destructive status lookup capability", providerId, adapterKey };
  if (input.productionTraffic === true) return { allowed: false, reason: "sandbox probe cannot use production traffic", providerId, adapterKey };

  return {
    allowed: true,
    reason: "non-destructive provider connectivity probe allowed",
    providerId,
    adapterKey,
    operation: "STATUS_LOOKUP",
    syntheticReference: true,
  };
}

module.exports = {
  planSandboxProbe,
};
