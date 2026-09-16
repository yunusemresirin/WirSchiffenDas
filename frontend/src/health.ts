import type {
  CircuitBreakerName,
  CircuitBreakerSnapshot,
  CircuitBreakerState,
  ServiceHealth,
  ServiceKey,
} from './types';

export const serviceBreakers: Record<ServiceKey, CircuitBreakerName[]> = {
  configuration: [],
  'analysis-management': ['startFluid', 'startThermal', 'startElectrical', 'startEngineManagement'],
  fluid: ['nextService'],
  thermal: ['nextService'],
  electrical: ['nextService'],
  'engine-management': [],
};

const breakerStates = new Set<CircuitBreakerState>([
  'CLOSED', 'OPEN', 'HALF_OPEN', 'DISABLED', 'FORCED_OPEN', 'METRICS_ONLY',
]);
const actuatorStates = new Set(['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN']);

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

export function parseServiceHealth(
  key: ServiceKey,
  httpStatus: number,
  payload: unknown,
): ServiceHealth {
  const root = record(payload);
  const validActuator = typeof root.status === 'string' && actuatorStates.has(root.status);
  // A real Actuator response may be 503 when a breaker is OPEN. A proxy's
  // 502/504 (or an unrelated JSON/HTML response) says nothing about the service.
  const reachable = validActuator && (httpStatus === 200 || httpStatus === 503);
  const circuitBreakers: ServiceHealth['circuitBreakers'] = {};

  if (reachable) {
    const details = record(record(record(root.components).circuitBreakers).details);
    for (const name of serviceBreakers[key]) {
      const value = record(record(details[name]).details);
      if (typeof value.state !== 'string' || !breakerStates.has(value.state as CircuitBreakerState)) continue;
      const snapshot: CircuitBreakerSnapshot = { state: value.state as CircuitBreakerState };
      if (typeof value.failureRate === 'string' || typeof value.failureRate === 'number') snapshot.failureRate = value.failureRate;
      for (const metric of ['bufferedCalls', 'failedCalls', 'notPermittedCalls'] as const) {
        if (typeof value[metric] === 'number') snapshot[metric] = value[metric];
      }
      circuitBreakers[name] = snapshot;
    }
  }

  return {
    key,
    reachable,
    actuatorStatus: reachable ? root.status as string : 'UNREACHABLE',
    circuitBreakers,
    ...(reachable ? {} : { error: `Keine gültige Service-Antwort (HTTP ${httpStatus}).` }),
    checkedAt: new Date().toISOString(),
  };
}
