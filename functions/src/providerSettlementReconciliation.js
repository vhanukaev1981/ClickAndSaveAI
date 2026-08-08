"use strict";

const { COMMISSION_STATES, normalizeCommissionRecord } = require("./providerReconciliation");
const { normalizeSettlementEvidence } = require("./providerSettlementEvidence");

const SETTLEMENT_RESULTS = Object.freeze({
  MATCHED: "MATCHED",
  PARTIAL: "PARTIAL",
  OVERPAID: "OVERPAID",
  MISMATCH: "MISMATCH",
});

function reconcileSettlement(commissionInput, evidenceInput) {
  const commission = normalizeCommissionRecord(commissionInput);
  const evidence = normalizeSettlementEvidence(evidenceInput);

  if (commission.state !== COMMISSION_STATES.CONFIRMED && commission.state !== COMMISSION_STATES.PAID) {
    throw new Error("settlement requires a confirmed commission");
  }
  if (commission.partnerId !== evidence.partnerId) {
    return { result: SETTLEMENT_RESULTS.MISMATCH, reason: "partner mismatch" };
  }
  if (commission.providerReference !== evidence.providerReference) {
    return { result: SETTLEMENT_RESULTS.MISMATCH, reason: "provider reference mismatch" };
  }
  if (commission.currency !== evidence.currency) {
    return { result: SETTLEMENT_RESULTS.MISMATCH, reason: "currency mismatch" };
  }

  const expected = commission.confirmedAmount || 0;
  const paid = evidence.amount;
  const delta = Math.round((paid - expected) * 100) / 100;
  const result = paid === expected
    ? SETTLEMENT_RESULTS.MATCHED
    : paid < expected
      ? SETTLEMENT_RESULTS.PARTIAL
      : SETTLEMENT_RESULTS.OVERPAID;

  return {
    result,
    reason: result === SETTLEMENT_RESULTS.MATCHED ? "settlement matches confirmed commission" : "settlement amount differs from confirmed commission",
    commissionId: commission.commissionId,
    providerReference: commission.providerReference,
    settlementEvidenceId: evidence.settlementEvidenceId,
    confirmedAmount: expected,
    paidAmount: paid,
    delta,
    currency: commission.currency,
    canMarkPaid: result === SETTLEMENT_RESULTS.MATCHED,
  };
}

module.exports = {
  SETTLEMENT_RESULTS,
  reconcileSettlement,
};
