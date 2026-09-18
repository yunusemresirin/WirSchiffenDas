// Lebenszyklus-Status eines Algorithmus
export type AnalysisStatus = 'PENDING' | 'RUNNING' | 'READY' | 'FAILED';
// Fachliches Ergebnis eines Algorithmus
export type AnalysisResult = 'OK' | 'FAILED';
// Algorithmen der Analyse-Kette in Ausführungsreihenfolge
export type AlgorithmName =
  | 'FLUID'
  | 'THERMAL'
  | 'ELECTRICAL'
  | 'ENGINE_MANAGEMENT';

// Engine-Konfiguration mit den Eingabewerten für die Analyse
export interface EngineConfiguration {
  configurationId: string;
  oilSystem: string;
  fuelSystem: string;
  coolingSystem: string;
  electricalSystem: string;
  engineManagementSystem: string;
}

// Anfrage zum Anlegen einer Konfiguration (ohne generierte ID)
export type CreateConfigurationRequest = Omit<
  EngineConfiguration,
  'configurationId'
>;

// Ausführung eines einzelnen Algorithmus innerhalb einer Analyse
export interface AlgorithmExecution {
  algorithm: AlgorithmName;
  status: AnalysisStatus;
  result: AnalysisResult | null;
  message: string | null;
}

// API-Antwort mit dem Zustand eines Analyse-Laufs
export interface AnalysisResponse {
  analysisId: string;
  configurationId: string;
  overallResult: AnalysisResult | null;
  algorithms: AlgorithmExecution[];
}

// Circuit-Breaker-Zustände gemäß Resilience4j
export type CircuitBreakerState =
  | 'CLOSED'
  | 'OPEN'
  | 'HALF_OPEN'
  | 'DISABLED'
  | 'FORCED_OPEN'
  | 'METRICS_ONLY'
  | 'UNKNOWN';

// Momentaufnahme der Circuit-Breaker-Metriken eines Service
export interface CircuitBreakerSnapshot {
  state: CircuitBreakerState;
  failureRate?: string | number;
  bufferedCalls?: number;
  failedCalls?: number;
  notPermittedCalls?: number;
}

// Schlüssel der überwachten Services
export type ServiceKey =
  | 'configuration'
  | 'analysis-management'
  | 'fluid'
  | 'thermal'
  | 'electrical'
  | 'engine-management';

// Health-Status eines Service inkl. Circuit-Breaker-Zustand
export interface ServiceHealth {
  key: ServiceKey;
  reachable: boolean;
  actuatorStatus: string;
  circuitBreaker: CircuitBreakerSnapshot | null;
  checkedAt: string;
}
