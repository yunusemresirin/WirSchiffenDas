import type { AnalysisResponse } from './types';

/** Technische Fehler bleiben beobachtbar; fachlich abgebrochene Ketten sind beendet. */
export function shouldPollAnalysis(analysis: AnalysisResponse): boolean {
  const failed = analysis.algorithms.filter((item) => item.status === 'FAILED');
  if (failed.length > 0) {
    return failed.every((item) => item.message?.toLowerCase().includes('unavailable'));
  }
  return analysis.algorithms.some((item) =>
    item.status === 'PENDING' || item.status === 'RUNNING');
}
