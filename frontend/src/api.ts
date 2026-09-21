import type {
  AnalysisResponse,
  AlgorithmName,
  CircuitBreakerSnapshot,
  CircuitBreakerState,
  CreateConfigurationRequest,
  EngineConfiguration,
  ServiceHealth,
  ServiceKey,
} from './types';

/**
 * Führt einen JSON-Request aus und wirft bei Fehlerstatus eine Error mit Servermeldung.
 */
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
    const message = await response.text();
    throw new Error(message || `${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<T>;
}

/**
 * Legt eine neue Engine-Konfiguration an.
 */
export function createConfiguration(
  request: CreateConfigurationRequest,
): Promise<EngineConfiguration> {
  return requestJson('/api/configurations', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

/**
 * Lädt eine Engine-Konfiguration anhand ihrer ID.
 */
export function loadConfiguration(
  configurationId: string,
): Promise<EngineConfiguration> {
  return requestJson(`/api/configurations/${encodeURIComponent(configurationId)}`);
}

/**
 * Startet eine Analyse für eine bestehende Konfiguration.
 */
export function startAnalysis(
  configurationId: string,
): Promise<AnalysisResponse> {
  return requestJson('/api/analyses', {
    method: 'POST',
    body: JSON.stringify({ configurationId }),
  });
}

/**
 * Lädt den aktuellen Zustand einer Analyse.
 */
export function loadAnalysis(analysisId: string): Promise<AnalysisResponse> {
  return requestJson(`/api/analyses/${encodeURIComponent(analysisId)}`);
}

/**
 * Wiederholt einen fehlgeschlagenen Algorithmus.
 */
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

const breakerStates = new Set<CircuitBreakerState>([
  'CLOSED',
  'OPEN',
  'HALF_OPEN',
  'DISABLED',
  'FORCED_OPEN',
  'METRICS_ONLY',
]);

/**
 * Sucht rekursiv nach einem Circuit-Breaker-Snapshot in der Actuator-Antwort.
 */
function findCircuitBreaker(value: unknown): CircuitBreakerSnapshot | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const record = value as Record<string, unknown>;
  const state = record.state;

  if (typeof state === 'string' && breakerStates.has(state as CircuitBreakerState)) {
    return {
      state: state as CircuitBreakerState,
      failureRate: record.failureRate as string | number | undefined,
      bufferedCalls:
        typeof record.bufferedCalls === 'number' ? record.bufferedCalls : undefined,
      failedCalls:
        typeof record.failedCalls === 'number' ? record.failedCalls : undefined,
      notPermittedCalls:
        typeof record.notPermittedCalls === 'number'
          ? record.notPermittedCalls
          : undefined,
    };
  }

  for (const child of Object.values(record)) {
    const result = findCircuitBreaker(child);
    if (result) {
      return result;
    }
  }

  return null;
}

/**
 * Fragt den Health-Endpunkt eines Service ab (mit Timeout) und liest den Circuit-Breaker-Zustand aus.
 */
async function fetchHealth(key: ServiceKey): Promise<ServiceHealth> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 1500);

  try {
    const response = await fetch(
      `/monitor/${key}/actuator/health?_=${Date.now()}`,
      {
        signal: controller.signal,
        cache: 'no-store',
        headers: {
          'Cache-Control': 'no-cache',
          Pragma: 'no-cache',
        },
      },
    );

    // 502/504 stammen beim Docker-Setup vom Nginx-Proxy, wenn der Zielcontainer
    // nicht erreichbar ist. Sie dürfen nicht als Antwort des Services gewertet werden.
    if (response.status === 502 || response.status === 504) {
      return {
        key,
        reachable: false,
        actuatorStatus: 'UNREACHABLE',
        circuitBreaker: null,
        checkedAt: new Date().toISOString(),
      };
    }

    const text = await response.text();
    let payload: Record<string, unknown> = {};
    if (text) {
      try {
        payload = JSON.parse(text) as Record<string, unknown>;
      } catch {
        payload = {};
      }
    }

    // Ein OPEN Circuit Breaker kann den Actuator-Status absichtlich auf DOWN/503
    // setzen. Solange die Antwort vom Zielservice kommt, ist der Container erreichbar.
    return {
      key,
      reachable: true,
      actuatorStatus:
        typeof payload.status === 'string'
          ? payload.status
          : response.ok
            ? 'UP'
            : 'DOWN',
      circuitBreaker: findCircuitBreaker(payload),
      checkedAt: new Date().toISOString(),
    };
  } catch {
    return {
      key,
      reachable: false,
      actuatorStatus: 'UNREACHABLE',
      circuitBreaker: null,
      checkedAt: new Date().toISOString(),
    };
  } finally {
    window.clearTimeout(timeout);
  }
}

/**
 * Fragt die Health-Endpunkte aller Services parallel ab.
 */
export async function loadSystemHealth(): Promise<ServiceHealth[]> {
  return Promise.all(serviceKeys.map(fetchHealth));
}
