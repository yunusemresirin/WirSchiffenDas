import test from 'node:test';
import assert from 'node:assert/strict';
import { shouldPollAnalysis } from '../src/polling.ts';
const run = (status, message) => ({ algorithms: [
  { status: 'READY' }, { status, message }, { status: 'PENDING' }, { status: 'PENDING' },
] });
test('fachliches INVALID beendet Analysis-Polling trotz PENDING-Nachfolgern', () => {
  assert.equal(shouldPollAnalysis(run('FAILED', 'Invalid thermal configuration')), false);
});
test('technischer Fehler und anschließendes Resume werden weiter beobachtet', () => {
  assert.equal(shouldPollAnalysis(run('FAILED', 'thermal-analysis-service unavailable')), true);
  assert.equal(shouldPollAnalysis(run('RUNNING')), true);
});
test('abgeschlossene Analyse beendet Analysis-Polling', () => {
  assert.equal(shouldPollAnalysis({ algorithms: Array(4).fill({ status: 'READY' }) }), false);
});
