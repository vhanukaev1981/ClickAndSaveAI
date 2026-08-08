"use strict";

const OFFBOARD_ACTIONS = Object.freeze({
  DISABLE_NEW_DISPATCH: "DISABLE_NEW_DISPATCH",
  DRAIN_IN_FLIGHT: "DRAIN_IN_FLIGHT",
  ROTATE_CREDENTIALS: "ROTATE_CREDENTIALS",
  ARCHIVE_CONTRACT: "ARCHIVE_CONTRACT",
  COMPLETE: "COMPLETE",
});

function planProviderOffboarding(input = {}) {
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new TypeError("provider offboarding input must be an object");
  const providerId = typeof input.providerId === "string" ? input.providerId.trim() : "";
  if (!providerId) throw new TypeError("providerId is required");

  if (input.newDispatchDisabled !== true) {
    return { action: OFFBOARD_ACTIONS.DISABLE_NEW_DISPATCH, providerId, safeToArchive: false };
  }
  if (Number(input.inFlightCount || 0) > 0) {
    return { action: OFFBOARD_ACTIONS.DRAIN_IN_FLIGHT, providerId, safeToArchive: false, inFlightCount: Number(input.inFlightCount) };
  }
  if (input.credentialsRotated !== true) {
    return { action: OFFBOARD_ACTIONS.ROTATE_CREDENTIALS, providerId, safeToArchive: false };
  }
  if (input.contractArchived !== true) {
    return { action: OFFBOARD_ACTIONS.ARCHIVE_CONTRACT, providerId, safeToArchive: false };
  }
  return { action: OFFBOARD_ACTIONS.COMPLETE, providerId, safeToArchive: true };
}

module.exports = {
  OFFBOARD_ACTIONS,
  planProviderOffboarding,
};
