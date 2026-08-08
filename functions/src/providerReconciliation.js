"use strict";

const COMMISSION_STATES = Object.freeze({
  EXPECTED: "EXPECTED",
  CONFIRMED: "CONFIRMED",
  PAID: "PAID",
  REJECTED: "REJECTED",
});

function text(value, field, maxLength = 200) {
  const normalized = typeof value === "string" ? value.trim() : "";
  if (!normalized) throw new TypeError(`${field} is required`);
  if (normalized.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return normalized;
}

function money(value, field) {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount < 0) throw new TypeError(`${field} must be a non-negative number`);
  return Math.round(amount * 100) / 100;
}

function normalizeEvidenceTimestamp(value) {
  if (value == null || value === "") return "";
  const normalized = String(value).trim().slice(0, 64);
  if (!Number.isFinite(Date.parse(normalized))) {
    throw new TypeError("evidenceObservedAt must be a valid timestamp");
  }
  return normalized;
}

function normalizeCommissionRecord(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("commission record must be an object");
  }
  const state = text(input.state, "state", 30);
  if (!Object.values(COMMISSION_STATES).includes(state)) throw new TypeError("commission state is unsupported");

  const record = {
    commissionId: text(input.commissionId, "commissionId", 128),
    partnerId: text(input.partnerId, "partnerId", 128),
    providerReference: text(input.providerReference, "providerReference", 200),
    state,
    expectedAmount: money(input.expectedAmount ?? 0, "expectedAmount"),
    confirmedAmount: input.confirmedAmount == null ? null : money(input.confirmedAmount, "confirmedAmount"),
    currency: typeof input.currency === "string" && input.currency.trim()
      ? input.currency.trim().toUpperCase().slice(0, 8)
      : "ILS",
    evidenceSource: typeof input.evidenceSource === "string"
      ? input.evidenceSource.trim().slice(0, 80)
      : "",
    evidenceObservedAt: normalizeEvidenceTimestamp(input.evidenceObservedAt),
  };

  if ([COMMISSION_STATES.CONFIRMED, COMMISSION_STATES.PAID, COMMISSION_STATES.REJECTED].includes(state)) {
    if (!record.evidenceSource || !record.evidenceObservedAt) {
      throw new TypeError("resolved commission state requires provider evidence");
    }
  }
  if ([COMMISSION_STATES.CONFIRMED, COMMISSION_STATES.PAID].includes(state)) {
    if (record.confirmedAmount == null || record.confirmedAmount <= 0) {
      throw new TypeError("confirmed or paid commission requires a positive confirmedAmount");
    }
  }
  return record;
}

function reconcileCommission(currentInput, evidenceInput) {
  const current = normalizeCommissionRecord(currentInput);
  if (!evidenceInput || typeof evidenceInput !== "object" || Array.isArray(evidenceInput)) {
    throw new TypeError("reconciliation evidence must be an object");
  }
  const nextState = text(evidenceInput.state, "state", 30);
  if (![COMMISSION_STATES.CONFIRMED, COMMISSION_STATES.PAID, COMMISSION_STATES.REJECTED].includes(nextState)) {
    throw new TypeError("reconciliation evidence state is unsupported");
  }
  if (current.state === COMMISSION_STATES.PAID && nextState !== COMMISSION_STATES.PAID) {
    throw new Error("paid commission cannot be downgraded");
  }

  return normalizeCommissionRecord({
    ...current,
    state: nextState,
    confirmedAmount: evidenceInput.confirmedAmount ?? current.confirmedAmount,
    evidenceSource: evidenceInput.evidenceSource,
    evidenceObservedAt: evidenceInput.evidenceObservedAt,
  });
}

function buildPartnerMetrics(records) {
  const normalized = (Array.isArray(records) ? records : []).map(normalizeCommissionRecord);
  const metrics = {
    records: normalized.length,
    expectedAmount: 0,
    confirmedAmount: 0,
    paidAmount: 0,
    rejectedCount: 0,
  };
  for (const record of normalized) {
    metrics.expectedAmount += record.expectedAmount;
    if ([COMMISSION_STATES.CONFIRMED, COMMISSION_STATES.PAID].includes(record.state)) {
      metrics.confirmedAmount += record.confirmedAmount || 0;
    }
    if (record.state === COMMISSION_STATES.PAID) metrics.paidAmount += record.confirmedAmount || 0;
    if (record.state === COMMISSION_STATES.REJECTED) metrics.rejectedCount += 1;
  }
  metrics.expectedAmount = Math.round(metrics.expectedAmount * 100) / 100;
  metrics.confirmedAmount = Math.round(metrics.confirmedAmount * 100) / 100;
  metrics.paidAmount = Math.round(metrics.paidAmount * 100) / 100;
  return metrics;
}

module.exports = {
  COMMISSION_STATES,
  normalizeCommissionRecord,
  reconcileCommission,
  buildPartnerMetrics,
};
