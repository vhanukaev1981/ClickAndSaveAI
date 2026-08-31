const NEXT_STAGE = new Map([
  [0, 5],
  [5, 20],
  [20, 50],
  [50, 100],
]);

export function decideProductionRollout({ currentPercent, requestedPercent, healthy, telemetryComplete }) {
  const current = Number(currentPercent);
  const requested = Number(requestedPercent);

  if (telemetryComplete !== true) {
    return { action: 'blocked', reason: 'missing-telemetry' };
  }
  if (healthy !== true) {
    return { action: 'halt', reason: 'unhealthy' };
  }
  if (!Number.isInteger(current) || !Number.isInteger(requested)) {
    return { action: 'blocked', reason: 'invalid-transition' };
  }
  if (current === 100 && requested === 100) {
    return { action: 'no-op', percent: 100 };
  }
  if (NEXT_STAGE.get(current) !== requested) {
    return { action: 'blocked', reason: 'invalid-transition' };
  }
  return { action: 'promote', percent: requested };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const [currentArg, requestedArg, healthyArg, telemetryArg] = process.argv.slice(2);
  const decision = decideProductionRollout({
    currentPercent: Number(currentArg),
    requestedPercent: Number(requestedArg),
    healthy: healthyArg === 'true',
    telemetryComplete: telemetryArg === 'true',
  });
  process.stdout.write(`${JSON.stringify(decision)}\n`);
}
