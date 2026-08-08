"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { normalizeAuditEvent, appendAuditEvent } = require("../src/providerEventAudit");

function event(overrides = {}) {
  return {
    eventType: "EVIDENCE_ACCEPTED",
    providerId: "provider-a",
    contractId: "contract-1",
    subjectId: "crm-123",
    evidenceId: "evidence-1",
    actor: "SYSTEM",
    occurredAt: "2026-08-08T20:00:00Z",
    ...overrides,
  };
}

test("normalizes audit event and derives deterministic id", () => {
  const first = normalizeAuditEvent(event());
  const second = normalizeAuditEvent(event());
  assert.equal(first.auditEventId, second.auditEventId);
  assert.equal(first.auditEventId.length, 64);
});

test("duplicate audit event is idempotent", () => {
  const first = appendAuditEvent([], event());
  const second = appendAuditEvent(first, event());
  assert.equal(second.length, 1);
});

test("different lifecycle step yields another audit event", () => {
  const list = appendAuditEvent(appendAuditEvent([], event()), event({ eventType: "LIFECYCLE_ADVANCED" }));
  assert.equal(list.length, 2);
});

test("unsupported audit event is rejected", () => {
  assert.throws(() => normalizeAuditEvent(event({ eventType: "MAGIC_SUCCESS" })), /unsupported provider audit event/);
});
