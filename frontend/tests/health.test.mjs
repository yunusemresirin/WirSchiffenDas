import test from 'node:test';
import assert from 'node:assert/strict';
import { loadSystemHealth } from '../src/api.ts';

test('Runtime-Polling unterscheidet Proxyfehler von einem erreichbaren Service mit OPEN-Breaker', async () => {
  const originalWindow = globalThis.window;
  const originalFetch = globalThis.fetch;
  globalThis.window = { setTimeout, clearTimeout };
  globalThis.fetch = async (url, options) => {
    assert.equal(options.cache, 'no-store');
    assert.match(url, /[?]_=/);
    if (url.includes('/thermal/')) return new Response('proxy failure', { status: 502 });
    if (url.includes('/analysis-management/')) return Response.json({ status: 'DOWN', components: {
      circuitBreakers: { details: {
        analysisServiceStarterThermal: { status: 'DOWN', details: { state: 'OPEN' } },
        analysisServiceStarterFluid: { status: 'UP', details: { state: 'CLOSED' } },
      } },
    } }, { status: 503 });
    return Response.json({ status: 'UP' });
  };
  try {
    const health = await loadSystemHealth();
    assert.equal(health.find(x => x.key === 'thermal').reachable, false);
    const management = health.find(x => x.key === 'analysis-management');
    assert.equal(management.reachable, true);
    assert.equal(management.circuitBreaker.state, 'CLOSED');
  } finally {
    globalThis.window = originalWindow;
    globalThis.fetch = originalFetch;
  }
});
