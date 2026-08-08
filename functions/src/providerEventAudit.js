"use strict";

const crypto = require("node:crypto");

const AUDIT_EVENT_TYPES = Object.freeze({
  DISPATCH_PLANNED: "DISPATCH_PLANNED",
  DISPATCH_ACKNOWLEDGED: "DISPATCH_ACKNOWLEDGED",
  EVIDENCE_ACCEPTED: "EVIDENCE_ACCEPTED",
  LIFECYCLE_ADVANCED: "LIFECYCLE_ADVANCED",
  COMMISSION_CONFIRMED: "COMMISSION_CONFIRMED",
  SETTLEMENT_MATCHED: "SETTLEMENT_MATCHED",
  DEAD_LETTERED: "DEAD_LETTERED",
});

function requiredText(value, field, maxLength = 200) {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) throw new TypeError(`${field} is required`);
  if (text.length > maxLength) throw new TypeError(`${field} exceeds ${maxLength} characters`);
  return text;
}

function normalizeAuditEvent(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new TypeError("provider audit event must be an object");
  }
  const eventType = requiredText(input.eventType, "eventType", 64).toUpperCase();
  if (!Object.values(AUDIT_EVENT_TYPES).includes(eventType)) throw new TypeError(`unsupported provider audit event: ${eventType}`);

  const occurredAt = requiredText(input.occurredAt, "occurredAt", 64);
  if (!Number.isFinite(Date.parse(occurredAt))) throw new TypeError("occurredAt must be a valid timestamp");

  const event = {
    eventType,
    providerId: requiredText(input.providerId, "providerId", 128),
    contractId: requiredText(input.contractId, "contractId", 128),
    subjectId: requiredText(input.subjectId, "subjectId", 200),
    evidenceId: typeof input.evidenceId === "string" ? input.evidenceId.trim().slice(0, 200) : "",
    actor: typeof input.actor === "string" && input.actor.trim() ? input.actor.trim().slice(0, 80) : "SYSTEM",
    occurredAt: new Date(Date.parse(occurredAt)).toISOString(),
  };

  const fingerprintMaterial = [
    event.eventType,
    event.providerId,
    event.contractId,
    event.subjectId,
    event.evidenceId,
    event.actor,
    event.occurredAt,
  ].join(":");
  event.auditEventId = crypto.createHash("sha256").update(fingerprintMaterial).digest("hex");
  return event;
}

function appendAuditEvent(existingEvents, input) {
  if (!Array.isArray(existingEvents)) throw new TypeError("existing audit events must be an array");
  const normalized = normalizeAuditEvent(input);
  const seen = new Set(existingEvents.map((event) => normalizeAuditEvent(event).auditEventId));
  if (seen.has(normalized.auditEventId)) return existingEvents.map(normalizeAuditEvent);
  return [...existingEvents.map(normalizeAuditEvent), normalized];
}

module.exports = {
  AUDIT_EVENT_TYPES,
  normalizeAuditEvent,
  appendAuditEvent,
};
