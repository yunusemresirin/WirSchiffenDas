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

// Kontrollierte Varianten der simulierten Optional-Equipment-Konfiguration.
export type ConfigurationVariant =
  | 'STANDARD'
  | 'PREMIUM'
  | 'ADVANCED'
  | 'INVALID';

// Engine-Konfiguration mit den Eingabewerten für die Analyse
export interface EngineConfiguration {
  configurationId: string;
  oilSystem: ConfigurationVariant;
  fuelSystem: ConfigurationVariant;
  coolingSystem: ConfigurationVariant;
  electricalSystem: ConfigurationVariant;
  engineManagementSystem: ConfigurationVariant;
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
  circuitBreakers: Record<string, CircuitBreakerSnapshot>;
  checkedAt: string;
}
