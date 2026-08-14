"use strict";

function providerCleanupConfirmed(watchStopStatus, oauthRevocationStatus) {
  void watchStopStatus;
  if (["NO_CREDENTIAL", "NO_CONNECTION"].includes(oauthRevocationStatus)) return true;
  return ["CONFIRMED", "CONFIRMED_OR_ALREADY_INVALID"].includes(oauthRevocationStatus);
}

module.exports = { providerCleanupConfirmed };
