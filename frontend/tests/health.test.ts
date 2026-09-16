import assert from 'node:assert/strict';
import test from 'node:test';
import { parseServiceHealth } from '../src/health.ts';

const managementPayload = {
  status: 'DOWN',
  components: {
    unrelated: { details: { state: 'OPEN' } },
    circuitBreakers: {
      status: 'DOWN',
      details: {
        startThermal: { status: 'DOWN', details: { state: 'OPEN', failureRate: '100.0%', failedCalls: 2 } },
        startEngineManagement: { status: 'UP', details: { state: 'HALF_OPEN' } },
        startFluid: { status: 'UP', details: { state: 'CLOSED' } },
        startElectrical: { status: 'UP', details: { state: 'DISABLED' } },
      },
    },
  },
};

test('503 from Actuator is reachable and keeps all four management breakers distinct', () => {
  const health = parseServiceHealth('analysis-management', 503, managementPayload);
  assert.equal(health.reachable, true);
  assert.equal(health.actuatorStatus, 'DOWN');
  assert.deepEqual(health.circuitBreakers, {
    startFluid: { state: 'CLOSED' },
    startThermal: { state: 'OPEN', failureRate: '100.0%', failedCalls: 2 },
    startElectrical: { state: 'DISABLED' },
    startEngineManagement: { state: 'HALF_OPEN' },
  });
});

test('gateway errors never report the upstream service as reachable', () => {
  for (const status of [502, 504]) {
    for (const body of [null, '<html>Bad gateway</html>', { status: 'UP' }, managementPayload]) {
      const health = parseServiceHealth('analysis-management', status, body);
      assert.equal(health.reachable, false);
      assert.deepEqual(health.circuitBreakers, {});
    }
  }
});

test('HTML, an empty response and unrelated JSON are not valid health responses', () => {
  for (const status of [200, 503]) {
    for (const body of [null, '<html>Unavailable</html>', {}, { error: 'Unavailable' }, { status: 503 }]) {
      assert.equal(parseServiceHealth('configuration', status, body).reachable, false);
    }
  }
});

test('a worker reads nextService only, not another nested breaker state', () => {
  const health = parseServiceHealth('fluid', 200, {
    status: 'UP',
    components: {
      unrelated: { details: { state: 'OPEN' } },
      circuitBreakers: {
        details: {
          startFluid: { status: 'DOWN', details: { state: 'OPEN' } },
          nextService: { status: 'UP', details: { state: 'CLOSED' } },
        },
      },
    },
  });
  assert.deepEqual(health.circuitBreakers, { nextService: { state: 'CLOSED' } });
});

test('an absent or unknown breaker remains unknown instead of inheriting another state', () => {
  assert.deepEqual(parseServiceHealth('fluid', 200, { status: 'UP' }).circuitBreakers, {});
  assert.deepEqual(parseServiceHealth('fluid', 200, {
    status: 'UP',
    components: {
      circuitBreakers: { details: { nextService: { status: 'UNKNOWN', details: { state: 'NOT_A_STATE' } } } },
    },
  }).circuitBreakers, {});
});

test('Engine Management and Configuration have no outgoing breaker', () => {
  for (const key of ['configuration', 'engine-management'] as const) {
    const health = parseServiceHealth(key, 200, { ...managementPayload, status: 'UP' });
    assert.equal(health.reachable, true);
    assert.deepEqual(health.circuitBreakers, {});
  }
});
