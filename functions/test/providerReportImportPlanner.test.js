"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  IMPORT_RESULTS,
  deriveImportEventId,
  planProviderReportImport,
} = require("../src/providerReportImportPlanner");

function event(overrides = {}) {
  return {
    providerId: "provider-a",
    reportId: "report-1",
    externalEventId: "event-1",
    eventType: "COMMISSION",
    payload: { amount: 75.5 },
    ...overrides,
  };
}

test("accepts supported report event", () => {
  const [result] = planProviderReportImport([event()]);
  assert.equal(result.result, IMPORT_RESULTS.ACCEPTED);
  assert.equal(result.importEventId.length, 64);
});

test("same external event is idempotent", () => {
  const id = deriveImportEventId(event());
  const [result] = planProviderReportImport([event()], [id]);
  assert.equal(result.result, IMPORT_RESULTS.DUPLICATE);
});

test("unsupported report event is quarantined instead of applied", () => {
  const [result] = planProviderReportImport([event({ eventType: "MAGIC_SUCCESS" })]);
  assert.equal(result.result, IMPORT_RESULTS.QUARANTINED);
});

test("invalid event does not block valid event in same batch", () => {
  const results = planProviderReportImport([null, event({ externalEventId: "event-2" })]);
  assert.equal(results[0].result, IMPORT_RESULTS.QUARANTINED);
  assert.equal(results[1].result, IMPORT_RESULTS.ACCEPTED);
});
