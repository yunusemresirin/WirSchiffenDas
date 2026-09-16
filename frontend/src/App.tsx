import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  AppBar,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Divider,
  LinearProgress,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import type { ChipProps } from '@mui/material/Chip';
import AddCircleOutlineRoundedIcon from '@mui/icons-material/AddCircleOutlineRounded';
import AnalyticsRoundedIcon from '@mui/icons-material/AnalyticsRounded';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import CloudDoneRoundedIcon from '@mui/icons-material/CloudDoneRounded';
import CloudOffRoundedIcon from '@mui/icons-material/CloudOffRounded';
import ErrorRoundedIcon from '@mui/icons-material/ErrorRounded';
import HubRoundedIcon from '@mui/icons-material/HubRounded';
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import ReplayRoundedIcon from '@mui/icons-material/ReplayRounded';
import SaveRoundedIcon from '@mui/icons-material/SaveRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import SettingsInputComponentRoundedIcon from '@mui/icons-material/SettingsInputComponentRounded';
import {
  createConfiguration,
  loadAnalysis,
  loadConfiguration,
  loadSystemHealth,
  retryAlgorithm,
  startAnalysis,
} from './api';
import { serviceBreakers } from './health';
import type {
  AlgorithmExecution,
  AlgorithmName,
  AnalysisResponse,
  AnalysisStatus,
  CircuitBreakerState,
  CircuitBreakerName,
  CreateConfigurationRequest,
  EngineConfiguration,
  ServiceHealth,
  ServiceKey,
} from './types';

const emptyConfiguration: CreateConfigurationRequest = {
  oilSystem: 'STANDARD',
  fuelSystem: 'PREMIUM',
  coolingSystem: 'STANDARD',
  electricalSystem: 'PREMIUM',
  engineManagementSystem: 'ADVANCED',
};

const equipmentLabels: Record<keyof CreateConfigurationRequest, string> = {
  oilSystem: 'Ölsystem',
  fuelSystem: 'Kraftstoffsystem',
  coolingSystem: 'Kühlsystem',
  electricalSystem: 'Elektrisches System',
  engineManagementSystem: 'Motorsteuerung',
};

const managementBreakers: Array<{ name: CircuitBreakerName; target: ServiceKey; label: string }> = [
  { name: 'startFluid', target: 'fluid', label: 'Start / Retry Fluid' },
  { name: 'startThermal', target: 'thermal', label: 'Retry Thermal' },
  { name: 'startElectrical', target: 'electrical', label: 'Retry Electrical' },
  { name: 'startEngineManagement', target: 'engine-management', label: 'Retry Engine Management' },
];

function breakerLabel(key: ServiceKey, name: CircuitBreakerName): string {
  if (key === 'analysis-management') {
    return managementBreakers.find((item) => item.name === name)?.label ?? name;
  }
  const next: Partial<Record<ServiceKey, string>> = {
    fluid: 'Thermal', thermal: 'Electrical', electrical: 'Engine Management',
  };
  return `Weitergabe → ${next[key] ?? name}`;
}

const algorithmLabels: Record<AlgorithmName, string> = {
  FLUID: 'Fluid Analysis',
  THERMAL: 'Thermal Analysis',
  ELECTRICAL: 'Electrical Analysis',
  ENGINE_MANAGEMENT: 'Engine Management',
};

const serviceLabels: Record<ServiceKey, string> = {
  configuration: 'Configuration',
  'analysis-management': 'Analysis Management',
  fluid: 'Fluid Analysis',
  thermal: 'Thermal Analysis',
  electrical: 'Electrical Analysis',
  'engine-management': 'Engine Management',
};

const breakerEdges: Array<{
  source: ServiceKey;
  target: ServiceKey;
  label: string;
  breakerName: CircuitBreakerName;
}> = [
  {
    source: 'analysis-management',
    target: 'fluid',
    label: 'Start / Retry Fluid',
    breakerName: 'startFluid',
  },
  { source: 'fluid', target: 'thermal', label: 'Weitergabe', breakerName: 'nextService' },
  { source: 'thermal', target: 'electrical', label: 'Weitergabe', breakerName: 'nextService' },
  { source: 'electrical', target: 'engine-management', label: 'Weitergabe', breakerName: 'nextService' },
];

function statusColor(status: AnalysisStatus): ChipProps['color'] {
  switch (status) {
    case 'READY':
      return 'success';
    case 'RUNNING':
      return 'info';
    case 'FAILED':
      return 'error';
    default:
      return 'default';
  }
}

function breakerColor(state: CircuitBreakerState): ChipProps['color'] {
  switch (state) {
    case 'CLOSED':
      return 'success';
    case 'OPEN':
    case 'FORCED_OPEN':
      return 'error';
    case 'HALF_OPEN':
      return 'warning';
    default:
      return 'default';
  }
}

function ConfigurationSection({
  value,
  selected,
  busy,
  onChange,
  onSaved,
  onBusyChange,
}: {
  value: CreateConfigurationRequest;
  selected: EngineConfiguration | null;
  busy: boolean;
  onChange: (value: CreateConfigurationRequest) => void;
  onSaved: (configuration: EngineConfiguration) => void;
  onBusyChange: (busy: boolean) => void;
}) {
  const [loadId, setLoadId] = useState(
    () => localStorage.getItem('wirschiffendas.configurationId') ?? '',
  );
  const [localBusy, setLocalBusy] = useState(false);
  const [configurationError, setConfigurationError] = useState<string | null>(null);

  const setPending = (pending: boolean) => {
    setLocalBusy(pending);
    onBusyChange(pending);
  };

  const setField = (field: keyof CreateConfigurationRequest, next: string) => {
    setConfigurationError(null);
    onChange({ ...value, [field]: next });
  };

  const save = async () => {
    setPending(true);
    setConfigurationError(null);
    onChange(value);
    try {
      const configuration = await createConfiguration(value);
      localStorage.setItem(
        'wirschiffendas.configurationId',
        configuration.configurationId,
      );
      setLoadId(configuration.configurationId);
      onSaved(configuration);
    } catch (error) {
      setConfigurationError(`Speichern fehlgeschlagen: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setPending(false);
    }
  };

  const load = async () => {
    if (!loadId.trim()) return;
    setPending(true);
    setConfigurationError(null);
    onChange(value);
    try {
      const configuration = await loadConfiguration(loadId.trim());
      localStorage.setItem(
        'wirschiffendas.configurationId',
        configuration.configurationId,
      );
      onSaved(configuration);
    } catch (error) {
      setConfigurationError(`Laden fehlgeschlagen: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      setPending(false);
    }
  };

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2.5}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <AddCircleOutlineRoundedIcon color="primary" />
            <Box>
              <Typography variant="h6">1. Engine-Konfiguration</Typography>
              <Typography variant="body2" color="text.secondary">
                Neue Konfiguration speichern oder eine vorhandene ID laden.
              </Typography>
            </Box>
          </Stack>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button
              variant="outlined"
              disabled={busy || localBusy}
              onClick={() => { setConfigurationError(null); onChange({ ...emptyConfiguration }); }}
            >
              Demo: gültige Konfiguration
            </Button>
            <Button
              variant="outlined"
              color="warning"
              disabled={busy || localBusy}
              onClick={() => { setConfigurationError(null); onChange({ ...emptyConfiguration, oilSystem: 'INVALID' }); }}
            >
              Demo: ungültiges Ölsystem
            </Button>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Die Demos füllen das Formular. Bei der negativen Demo bleibt das Kraftstoffsystem gültig, damit beide Einzelresultate sichtbar werden.
          </Typography>

          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: {
                xs: '1fr',
                sm: 'repeat(2, minmax(0, 1fr))',
                lg: 'repeat(3, minmax(0, 1fr))',
              },
              gap: 2,
            }}
          >
            {(Object.keys(equipmentLabels) as Array<keyof CreateConfigurationRequest>).map((field) => (
              <TextField
                key={field}
                label={equipmentLabels[field]}
                value={value[field]}
                disabled={busy || localBusy}
                onChange={(event) => setField(field, event.target.value)}
              />
            ))}
          </Box>

          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
            <Button
              variant="contained"
              startIcon={<SaveRoundedIcon />}
              disabled={busy || localBusy}
              onClick={save}
            >
              Konfiguration speichern
            </Button>
            <TextField
              size="small"
              label="Configuration ID"
              value={loadId}
              disabled={busy || localBusy}
              onChange={(event) => {
                setLoadId(event.target.value);
                setConfigurationError(null);
                onChange(value);
              }}
              sx={{ minWidth: { md: 310 } }}
            />
            <Button
              variant="outlined"
              startIcon={<SearchRoundedIcon />}
              disabled={busy || localBusy || !loadId.trim()}
              onClick={load}
            >
              Laden
            </Button>
          </Stack>

          {configurationError && <Alert severity="error">{configurationError}</Alert>}

          {!selected && (
            <Alert severity="info">
              Vor dem Analysestart bitte die angezeigte Konfiguration speichern oder eine vorhandene ID laden. Änderungen im Formular heben die bisherige Auswahl auf.
            </Alert>
          )}

          {selected && (
            <Alert severity="success" icon={<CheckCircleRoundedIcon />}>
              Aktive Konfiguration: <strong>{selected.configurationId}</strong>
            </Alert>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

function AlgorithmCard({
  execution,
  onRetry,
  retrying,
}: {
  execution: AlgorithmExecution;
  onRetry: (algorithm: AlgorithmName) => void;
  retrying: AlgorithmName | null;
}) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Stack spacing={1.5}>
          <Stack direction="row" justifyContent="space-between" spacing={1}>
            <Typography variant="subtitle1" fontWeight={700}>
              {algorithmLabels[execution.algorithm]}
            </Typography>
            <Chip
              size="small"
              label={execution.status}
              color={statusColor(execution.status)}
            />
          </Stack>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="body2" color="text.secondary">
              Result
            </Typography>
            <Chip
              size="small"
              variant="outlined"
              color={execution.result === 'OK' ? 'success' : execution.result === 'FAILED' ? 'error' : 'default'}
              label={execution.result ?? '—'}
            />
          </Stack>
          {execution.message && (
            <Typography variant="body2" color="text.secondary">
              {execution.message}
            </Typography>
          )}
          {Object.keys(execution.equipmentResults ?? {}).length > 0 && (
            <Stack spacing={0.75}>
              <Divider />
              <Typography variant="caption" color="text.secondary">Ergebnisse je Equipment</Typography>
              {Object.entries(execution.equipmentResults).map(([equipment, result]) => (
                <Stack key={equipment} direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
                  <Typography variant="body2">
                    {equipmentLabels[equipment as keyof CreateConfigurationRequest] ?? equipment}
                  </Typography>
                  <Chip size="small" variant="outlined" label={result === 'OK' ? 'OK' : 'Fehler'} color={result === 'OK' ? 'success' : 'error'} />
                </Stack>
              ))}
            </Stack>
          )}
        </Stack>
      </CardContent>
      {execution.status === 'FAILED' && (
        <CardActions>
          <Button
            color="warning"
            startIcon={<ReplayRoundedIcon />}
            disabled={retrying !== null}
            onClick={() => onRetry(execution.algorithm)}
          >
            {retrying === execution.algorithm ? 'Retry läuft …' : 'Retry'}
          </Button>
        </CardActions>
      )}
    </Card>
  );
}

function RuntimeHealthCard({ health }: { health: ServiceHealth }) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5, minWidth: 180 }}>
      <Stack spacing={1}>
        <Typography variant="subtitle2">{serviceLabels[health.key]}</Typography>
        <Chip
          size="small"
          icon={health.reachable ? <CloudDoneRoundedIcon /> : <CloudOffRoundedIcon />}
          label={health.reachable ? 'REACHABLE' : 'UNREACHABLE'}
          color={health.reachable ? 'success' : 'error'}
          variant={health.reachable ? 'outlined' : 'filled'}
        />
        {serviceBreakers[health.key].map((name) => (
          <Tooltip key={name} title={`${name} · Actuator: ${health.actuatorStatus}`}>
            <Chip
              size="small"
              icon={<SettingsInputComponentRoundedIcon />}
              label={`${breakerLabel(health.key, name)}: ${health.circuitBreakers[name]?.state ?? 'UNKNOWN'}`}
              color={breakerColor(health.circuitBreakers[name]?.state ?? 'UNKNOWN')}
            />
          </Tooltip>
        ))}
        {health.error && <Typography variant="caption" color="text.secondary">{health.error}</Typography>}
      </Stack>
    </Paper>
  );
}

function BreakerEdge({
  sourceHealth,
  target,
  label,
  breakerName,
}: {
  sourceHealth?: ServiceHealth;
  target: ServiceKey;
  label: string;
  breakerName: CircuitBreakerName;
}) {
  const snapshot = sourceHealth?.circuitBreakers[breakerName];
  const state = snapshot?.state ?? 'UNKNOWN';

  return (
    <Stack
      spacing={0.5}
      alignItems="center"
      justifyContent="center"
      sx={{ minWidth: 126, px: 1 }}
    >
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', width: '100%' }}>
        <Divider sx={{ flex: 1 }} />
        <Tooltip
          title={
            <span>
              {breakerName}: schützt den Aufruf zu {serviceLabels[target]}
              {snapshot?.failureRate !== undefined
                ? ` · Failure rate: ${snapshot.failureRate}`
                : ''}
            </span>
          }
        >
          <Chip
            size="small"
            sx={{ mx: 0.75 }}
            label={state}
            color={breakerColor(state)}
          />
        </Tooltip>
        <Divider sx={{ flex: 1 }} />
      </Box>
    </Stack>
  );
}

export default function App() {
  const [configurationDraft, setConfigurationDraft] =
    useState<CreateConfigurationRequest>(emptyConfiguration);
  const [configuration, setConfiguration] =
    useState<EngineConfiguration | null>(null);
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [health, setHealth] = useState<ServiceHealth[]>([]);
  const [busy, setBusy] = useState(false);
  const [configurationBusy, setConfigurationBusy] = useState(false);
  const [retrying, setRetrying] = useState<AlgorithmName | null>(null);
  const [error, setError] = useState<string | null>(null);
  const analysisRequests = useRef({ generation: 0, mutationPending: false });

  const changeConfiguration = (next: CreateConfigurationRequest) => {
    setConfigurationDraft(next);
    setConfiguration(null);
  };

  const acceptConfiguration = (saved: EngineConfiguration) => {
    setConfigurationDraft({
      oilSystem: saved.oilSystem,
      fuelSystem: saved.fuelSystem,
      coolingSystem: saved.coolingSystem,
      electricalSystem: saved.electricalSystem,
      engineManagementSystem: saved.engineManagementSystem,
    });
    setConfiguration(saved);
  };

  const healthByKey = useMemo(
    () => new Map(health.map((item) => [item.key, item])),
    [health],
  );

  const refreshHealth = useCallback(async () => {
    setHealth(await loadSystemHealth());
  }, []);

  const refreshAnalysis = useCallback(async () => {
    if (!analysis?.analysisId || analysisRequests.current.mutationPending) return;
    const requestedId = analysis.analysisId;
    const generation = ++analysisRequests.current.generation;
    try {
      const next = await loadAnalysis(requestedId);
      // An older poll must not overwrite a newer response or a successful retry.
      if (generation !== analysisRequests.current.generation) return;
      setAnalysis((current) => current?.analysisId === requestedId ? next : current);
    } catch (nextError) {
      if (generation !== analysisRequests.current.generation) return;
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    }
  }, [analysis?.analysisId]);

  useEffect(() => {
    void refreshHealth();
    const interval = window.setInterval(() => void refreshHealth(), 2000);
    return () => window.clearInterval(interval);
  }, [refreshHealth]);

  useEffect(() => {
    if (!analysis?.analysisId) return;
    const hasActiveAlgorithm = analysis.algorithms.some(
      (item) => item.status === 'PENDING' || item.status === 'RUNNING',
    );
    if (!hasActiveAlgorithm) return;

    const interval = window.setInterval(() => void refreshAnalysis(), 1000);
    return () => window.clearInterval(interval);
  }, [analysis, refreshAnalysis]);

  const runAnalysis = async () => {
    if (!configuration || configurationBusy || analysisRequests.current.mutationPending) return;
    analysisRequests.current.mutationPending = true;
    ++analysisRequests.current.generation;
    setBusy(true);
    try {
      const next = await startAnalysis(configuration.configurationId);
      localStorage.setItem('wirschiffendas.analysisId', next.analysisId);
      setAnalysis(next);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      analysisRequests.current.mutationPending = false;
      setBusy(false);
    }
  };

  const retry = async (algorithm: AlgorithmName) => {
    if (!analysis || analysisRequests.current.mutationPending) return;
    const requestedId = analysis.analysisId;
    analysisRequests.current.mutationPending = true;
    ++analysisRequests.current.generation;
    setRetrying(algorithm);
    try {
      const next = await retryAlgorithm(requestedId, algorithm);
      setAnalysis((current) => current?.analysisId === requestedId ? next : current);
      await refreshHealth();
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      analysisRequests.current.mutationPending = false;
      setRetrying(null);
    }
  };

  const overallLabel = analysis?.overallResult ?? (analysis ? 'RUNNING' : '—');
  const overallColor: ChipProps['color'] =
    analysis?.overallResult === 'OK'
      ? 'success'
      : analysis?.overallResult === 'FAILED'
        ? 'error'
        : analysis
          ? 'info'
          : 'default';

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="static" elevation={0}>
        <Toolbar>
          <HubRoundedIcon sx={{ mr: 1.5 }} />
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6">WirSchiffenDas</Typography>
            <Typography variant="caption" sx={{ opacity: 0.85 }}>
              Engine Quality Analysis · Microservice Dashboard
            </Typography>
          </Box>
          <Tooltip title="Service-Status aktualisieren">
            <Button color="inherit" startIcon={<RefreshRoundedIcon />} onClick={() => void refreshHealth()}>
              Refresh
            </Button>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Stack spacing={3}>
          <Box>
            <Typography variant="h4" gutterBottom>
              Quality Analysis Control Center
            </Typography>
            <Typography color="text.secondary">
              Konfiguration verwalten, Analyse beobachten und Fehlerfälle kontrolliert wiederholen.
            </Typography>
          </Box>

          <ConfigurationSection
            value={configurationDraft}
            selected={configuration}
            busy={busy}
            onChange={changeConfiguration}
            onSaved={acceptConfiguration}
            onBusyChange={setConfigurationBusy}
          />

          <Card variant="outlined">
            <CardContent>
              <Stack spacing={2.5}>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  justifyContent="space-between"
                  spacing={2}
                >
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <AnalyticsRoundedIcon color="primary" />
                    <Box>
                      <Typography variant="h6">2. Qualitätsanalyse</Typography>
                      <Typography variant="body2" color="text.secondary">
                        Die Analyse prüft die gespeicherte Konfiguration. Status und Ergebnisse werden während des Laufs aktualisiert.
                      </Typography>
                    </Box>
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="body2" color="text.secondary">
                      Overall Result
                    </Typography>
                    <Chip label={overallLabel} color={overallColor} />
                  </Stack>
                </Stack>

                <Button
                  variant="contained"
                  size="large"
                  disabled={!configuration || busy || configurationBusy || retrying !== null}
                  startIcon={busy ? <CircularProgress size={18} color="inherit" /> : <AnalyticsRoundedIcon />}
                  onClick={runAnalysis}
                  sx={{ alignSelf: 'flex-start' }}
                >
                  Analyse starten
                </Button>

                {analysis && (
                  <>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                      <Chip variant="outlined" label={`Analysis ID: ${analysis.analysisId}`} />
                      <Chip variant="outlined" label={`Configuration: ${analysis.configurationId}`} />
                    </Stack>

                    {analysis.algorithms.some((item) => item.status === 'RUNNING') && (
                      <LinearProgress />
                    )}

                    <Box
                      sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                          xs: '1fr',
                          sm: 'repeat(2, minmax(0, 1fr))',
                          xl: 'repeat(4, minmax(0, 1fr))',
                        },
                        gap: 2,
                      }}
                    >
                      {analysis.algorithms.map((execution) => (
                        <AlgorithmCard
                          key={execution.algorithm}
                          execution={execution}
                          onRetry={retry}
                          retrying={retrying}
                        />
                      ))}
                    </Box>
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Stack spacing={2.5}>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  justifyContent="space-between"
                  spacing={1}
                >
                  <Box>
                    <Typography variant="h6">3. Runtime-Status aller Services</Typography>
                    <Typography variant="body2" color="text.secondary">
                      REACHABLE bedeutet: der Service liefert eine gültige Zustandsantwort. Ein offener Circuit Breaker kann den Gesamtstatus auf DOWN setzen, obwohl der Service weiterläuft. Proxyfehler zählen als UNREACHABLE.
                    </Typography>
                  </Box>
                  <Chip size="small" label="Polling: 2 s" variant="outlined" />
                </Stack>

                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: {
                      xs: '1fr',
                      sm: 'repeat(2, minmax(0, 1fr))',
                      lg: 'repeat(3, minmax(0, 1fr))',
                    },
                    gap: 1.5,
                  }}
                >
                  {(
                    [
                      'configuration',
                      'analysis-management',
                      'fluid',
                      'thermal',
                      'electrical',
                      'engine-management',
                    ] as ServiceKey[]
                  ).map((key) => {
                    const item = healthByKey.get(key);
                    return item ? (
                      <RuntimeHealthCard key={key} health={item} />
                    ) : (
                      <Paper key={key} variant="outlined" sx={{ p: 1.5 }}>
                        <Typography variant="subtitle2">{serviceLabels[key]}</Typography>
                        <LinearProgress sx={{ mt: 1 }} />
                      </Paper>
                    );
                  })}
                </Box>
              </Stack>
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Stack spacing={2.5}>
                <Box>
                  <Typography variant="h6">4. Circuit-Breaker-Visualisierung</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Grün = CLOSED, Rot = OPEN, Orange = HALF_OPEN. Die Breaker gehören jeweils zum aufrufenden Service und schützen den nächsten REST-Aufruf.
                  </Typography>
                </Box>

                <Box sx={{ overflowX: 'auto', pb: 1 }}>
                  <Stack direction="row" alignItems="stretch" sx={{ minWidth: 980 }}>
                    {breakerEdges.map((edge, index) => (
                      <Box key={`${edge.source}-${edge.target}`} sx={{ display: 'flex', alignItems: 'stretch' }}>
                        {index === 0 && (
                          <Paper variant="outlined" sx={{ p: 2, width: 180 }}>
                            <Typography fontWeight={700}>{serviceLabels[edge.source]}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {healthByKey.get(edge.source)?.reachable ? 'reachable' : 'unreachable'}
                            </Typography>
                          </Paper>
                        )}
                        <BreakerEdge
                          sourceHealth={healthByKey.get(edge.source)}
                          target={edge.target}
                          label={edge.label}
                          breakerName={edge.breakerName}
                        />
                        <Paper variant="outlined" sx={{ p: 2, width: 180 }}>
                          <Typography fontWeight={700}>{serviceLabels[edge.target]}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {healthByKey.get(edge.target)?.reachable ? 'reachable' : 'unreachable'}
                          </Typography>
                        </Paper>
                      </Box>
                    ))}
                  </Stack>
                </Box>

                <Typography variant="subtitle2">Direkte Start- und Retry-Aufrufe aus Analysis Management</Typography>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' }, gap: 1.5 }}>
                  {managementBreakers.map((edge) => (
                    <Paper key={edge.name} variant="outlined" sx={{ p: 1.5 }}>
                      <Typography variant="body2" textAlign="center">Management → {serviceLabels[edge.target]}</Typography>
                      <BreakerEdge sourceHealth={healthByKey.get('analysis-management')} target={edge.target} label={edge.label} breakerName={edge.name} />
                    </Paper>
                  ))}
                </Box>

                <Alert severity="info" icon={<ErrorRoundedIcon />}>
                  <strong>Fehler und Erholung:</strong> Wird Thermal gestoppt, schlägt die Weitergabe von Fluid fehl. Der Breaker <strong>Fluid → Thermal</strong> öffnet, sobald seine Fehlerschwelle erreicht ist. Nach dem Neustart kann ein Thermal-Retry die Analyse über den separaten Management-Aufruf fortsetzen. Dieser Retry schließt den Breaker von Fluid jedoch nicht. Nach dessen Wartezeit einen <strong>neuen Gesamtlauf</strong> starten: Erst ein erfolgreicher Probeaufruf über Fluid → Thermal ermöglicht die Rückkehr zu CLOSED. HALF_OPEN ist eine Prüfphase, noch kein Nachweis der Erholung.
                </Alert>
              </Stack>
            </CardContent>
          </Card>
        </Stack>
      </Container>

      <Snackbar
        open={error !== null}
        autoHideDuration={7000}
        onClose={() => setError(null)}
        message={error}
      />
    </Box>
  );
}
