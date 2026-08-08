"use strict";

const { COMMISSION_STATES, normalizeCommissionRecord, reconcileCommission } = require("./providerReconciliation");
const { SETTLEMENT_RESULTS, reconcileSettlement } = require("./providerSettlementReconciliation");

function applyVerifiedSettlementToCommission(commissionInput, settlementEvidenceInput) {
  const commission = normalizeCommissionRecord(commissionInput);
  if (commission.state !== COMMISSION_STATES.CONFIRMED && commission.state !== COMMISSION_STATES.PAID) {
    throw new Error("paid transition requires confirmed commission");
  }

  const settlement = reconcileSettlement(commission, settlementEvidenceInput);
  if (settlement.result !== SETTLEMENT_RESULTS.MATCHED || settlement.canMarkPaid !== true) {
    return {
      applied: false,
      reason: "settlement does not exactly match confirmed commission",
      settlement,
      commission,
    };
  }

  if (commission.state === COMMISSION_STATES.PAID) {
    return {
      applied: false,
      reason: "commission is already paid",
      settlement,
      commission,
    };
  }

  const updated = reconcileCommission(commission, {
    state: COMMISSION_STATES.PAID,
    confirmedAmount: settlement.paidAmount,
    evidenceSource: "SETTLEMENT_EVIDENCE",
    evidenceObservedAt: settlementEvidenceInput.paidAt,
  });

  return {
    applied: true,
    reason: "verified settlement exactly matches confirmed commission",
    settlementEvidenceId: settlement.settlementEvidenceId,
    settlement,
    commission: updated,
  };
}

module.exports = {
  applyVerifiedSettlementToCommission,
};
