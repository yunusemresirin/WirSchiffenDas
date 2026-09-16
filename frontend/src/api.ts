import type {
  AnalysisResponse,
  AlgorithmName,
  CreateConfigurationRequest,
  EngineConfiguration,
  ServiceHealth,
  ServiceKey,
} from './types';
import { parseServiceHealth } from './health';

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (init?.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(url, {
    ...init,
    headers,
  });

  if (!response.ok) {
    const text = await response.text();
    let message = `Anfrage fehlgeschlagen (HTTP ${response.status}).`;
    if (response.status === 502 || response.status === 504) {
      message = 'Der Service ist gerade nicht erreichbar. Bitte später erneut versuchen.';
    } else {
      try {
        const body = JSON.parse(text) as Record<string, unknown>;
        const detail = body.detail ?? body.message ?? body.error;
        if (typeof detail === 'string') message = detail;
      } catch {
        if (text && !text.trimStart().startsWith('<')) message = text;
      }
    }
    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

export function createConfiguration(
  request: CreateConfigurationRequest,
): Promise<EngineConfiguration> {
  return requestJson('/api/configurations', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function loadConfiguration(
  configurationId: string,
): Promise<EngineConfiguration> {
  return requestJson(`/api/configurations/${encodeURIComponent(configurationId)}`);
}

export function startAnalysis(
  configurationId: string,
): Promise<AnalysisResponse> {
  return requestJson('/api/analyses', {
    method: 'POST',
    body: JSON.stringify({ configurationId }),
  });
}

export function loadAnalysis(analysisId: string): Promise<AnalysisResponse> {
  return requestJson(`/api/analyses/${encodeURIComponent(analysisId)}`);
}

export function retryAlgorithm(
  analysisId: string,
  algorithm: AlgorithmName,
): Promise<AnalysisResponse> {
  return requestJson(
    `/api/analyses/${encodeURIComponent(analysisId)}/algorithms/${algorithm}/retry`,
    { method: 'POST' },
  );
}

const serviceKeys: ServiceKey[] = [
  'configuration',
  'analysis-management',
  'fluid',
  'thermal',
  'electrical',
  'engine-management',
];

async function fetchHealth(key: ServiceKey): Promise<ServiceHealth> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 1500);

  try {
    const response = await fetch(`/monitor/${key}/actuator/health`, {
      signal: controller.signal,
      cache: 'no-store',
    });

    const text = await response.text();
    let payload: unknown = null;
    try { payload = JSON.parse(text); } catch { /* Proxy errors may return HTML. */ }
    return parseServiceHealth(key, response.status, payload);
  } catch {
    return {
      key,
      reachable: false,
      actuatorStatus: 'UNREACHABLE',
      circuitBreakers: {},
      error: 'Service antwortet nicht innerhalb der Prüfzeit.',
      checkedAt: new Date().toISOString(),
    };
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function loadSystemHealth(): Promise<ServiceHealth[]> {
  return Promise.all(serviceKeys.map(fetchHealth));
}
