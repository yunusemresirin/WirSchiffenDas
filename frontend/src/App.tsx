import { useCallback, useEffect, useMemo, useState } from 'react';
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
  MenuItem,
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
import type {
  AlgorithmExecution,
  AlgorithmName,
  AnalysisResponse,
  AnalysisStatus,
  CircuitBreakerState,
  ConfigurationVariant,
  CreateConfigurationRequest,
  EngineConfiguration,
  ServiceHealth,
  ServiceKey,
} from './types';

const configurationVariants: Array<{
  value: ConfigurationVariant;
  label: string;
  breakerName: string;
}> = [
  { value: 'STANDARD', label: 'STANDARD' },
  { value: 'PREMIUM', label: 'PREMIUM' },
  { value: 'ADVANCED', label: 'ADVANCED' },
  { value: 'INVALID', label: 'INVALID · Demo-Fehlerfall' },
];

const emptyConfiguration: CreateConfigurationRequest = {
  oilSystem: 'STANDARD',
  fuelSystem: 'PREMIUM',
  coolingSystem: 'STANDARD',
  electricalSystem: 'PREMIUM',
  engineManagementSystem: 'ADVANCED',
};

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
}> = [
  {
    source: 'analysis-management',
    target: 'fluid',
    label: 'Start / Retry',
    breakerName: 'analysisServiceStarterFluid',
  },
  { source: 'fluid', target: 'thermal', label: 'Next', breakerName: 'nextService' },
  { source: 'thermal', target: 'electrical', label: 'Next', breakerName: 'nextService' },
  { source: 'electrical', target: 'engine-management', label: 'Next', breakerName: 'nextService' },
];

/**
 * Ordnet einem Analyse-Status die passende Chip-Farbe zu.
 */
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

/**
 * Ordnet einem Circuit-Breaker-Zustand die passende Chip-Farbe zu.
 */
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

/**
 * Formular zum Anlegen und Laden einer Engine-Konfiguration.
 */
function ConfigurationSection({
  value,
  selected,
  busy,
  onChange,
  onSaved,
}: {
  value: CreateConfigurationRequest;
  selected: EngineConfiguration | null;
  busy: boolean;
  onChange: (value: CreateConfigurationRequest) => void;
  onSaved: (configuration: EngineConfiguration) => void;
}) {
  const [loadId, setLoadId] = useState(
    () => localStorage.getItem('wirschiffendas.configurationId') ?? '',
  );
  const [localBusy, setLocalBusy] = useState(false);

  const setField = (
    field: keyof CreateConfigurationRequest,
    next: ConfigurationVariant,
  ) => {
    onChange({ ...value, [field]: next });
  };

  const save = async () => {
    setLocalBusy(true);
    try {
      const configuration = await createConfiguration(value);
      localStorage.setItem(
        'wirschiffendas.configurationId',
        configuration.configurationId,
      );
      setLoadId(configuration.configurationId);
      onSaved(configuration);
    } finally {
      setLocalBusy(false);
    }
  };

  const load = async () => {
    if (!loadId.trim()) return;
    setLocalBusy(true);
    try {
      const configuration = await loadConfiguration(loadId.trim());
      localStorage.setItem(
        'wirschiffendas.configurationId',
        configuration.configurationId,
      );
      onChange({
        oilSystem: configuration.oilSystem,
        fuelSystem: configuration.fuelSystem,
        coolingSystem: configuration.coolingSystem,
        electricalSystem: configuration.electricalSystem,
        engineManagementSystem: configuration.engineManagementSystem,
      });
      onSaved(configuration);
    } finally {
      setLocalBusy(false);
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
                Optional Equipment konfigurieren. STANDARD, PREMIUM und ADVANCED sind gültig; INVALID dient als demonstrierbarer fachlicher Fehlerfall.
              </Typography>
            </Box>
          </Stack>

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
            {(
              [
                ['oilSystem', 'Oil System'],
                ['fuelSystem', 'Fuel System'],
                ['coolingSystem', 'Cooling System'],
                ['electricalSystem', 'Electrical System'],
                ['engineManagementSystem', 'Engine Management'],
              ] as Array<[keyof CreateConfigurationRequest, string]>
            ).map(([field, label]) => (
              <TextField
                key={field}
                select
                label={label}
                value={value[field]}
                onChange={(event) =>
                  setField(field, event.target.value as ConfigurationVariant)
                }
                helperText={
                  value[field] === 'INVALID'
                    ? 'Absichtlicher Fehlerfall: zuständige Analyse endet mit FAILED.'
                    : 'Gültige Konfigurationsvariante'
                }
                error={value[field] === 'INVALID'}
              >
                {configurationVariants.map((variant) => (
                  <MenuItem key={variant.value} value={variant.value}>
                    {variant.label}
                  </MenuItem>
                ))}
              </TextField>
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
              onChange={(event) => setLoadId(event.target.value)}
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

/**
 * Karte mit Status und Ergebnis eines einzelnen Algorithmus inkl. Retry-Button.
 */
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

/**
 * Karte mit Erreichbarkeit und Circuit-Breaker-Zustand eines Service.
 */
function RuntimeHealthCard({ health }: { health: ServiceHealth }) {
  const breakerEntries = Object.entries(health.circuitBreakers);

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
        {breakerEntries.length === 1 && health.circuitBreaker && (
          <Tooltip title={`Actuator status: ${health.actuatorStatus}`}>
            <Chip
              size="small"
              icon={<SettingsInputComponentRoundedIcon />}
              label={`CB ${health.circuitBreaker.state}`}
              color={breakerColor(health.circuitBreaker.state)}
            />
          </Tooltip>
        )}
        {breakerEntries.length > 1 && (
          <Tooltip title="Zielservice-spezifische Breaker werden unten an den Kanten angezeigt.">
            <Chip
              size="small"
              icon={<SettingsInputComponentRoundedIcon />}
              label={`${breakerEntries.length} Circuit Breakers`}
              variant="outlined"
            />
          </Tooltip>
        )}
      </Stack>
    </Paper>
  );
}

/**
 * Visualisiert eine Kante der Analyse-Kette mit dem Circuit-Breaker-Zustand des Aufrufers.
 */
function BreakerEdge({
  sourceHealth,
  target,
  label,
  breakerName,
}: {
  sourceHealth?: ServiceHealth;
  target: ServiceKey;
  label: string;
  breakerName: string;
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
              schützt den Aufruf zu {serviceLabels[target]}
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

/**
 * Hauptkomponente: Konfiguration, Analyse-Steuerung und Runtime-Monitoring.
 */
export default function App() {
  const [configurationDraft, setConfigurationDraft] =
    useState<CreateConfigurationRequest>(emptyConfiguration);
  const [configuration, setConfiguration] =
    useState<EngineConfiguration | null>(null);
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [health, setHealth] = useState<ServiceHealth[]>([]);
  const [busy, setBusy] = useState(false);
  const [retrying, setRetrying] = useState<AlgorithmName | null>(null);
  const [error, setError] = useState<string | null>(null);

  const healthByKey = useMemo(
    () => new Map(health.map((item) => [item.key, item])),
    [health],
  );

  const refreshHealth = useCallback(async () => {
    setHealth(await loadSystemHealth());
  }, []);

  const refreshAnalysis = useCallback(async () => {
    if (!analysis?.analysisId) return;
    try {
      setAnalysis(await loadAnalysis(analysis.analysisId));
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    }
  }, [analysis?.analysisId]);

  useEffect(() => {
    void refreshHealth();
    // Health-Status aller Services alle 2 Sekunden neu abfragen
    const interval = window.setInterval(() => void refreshHealth(), 2000);
    return () => window.clearInterval(interval);
  }, [refreshHealth]);

  useEffect(() => {
    if (!analysis?.analysisId) return;
    // Während eines technischen Ausfalls weiter pollen, damit eine automatische
    // Circuit-Breaker-Recovery und das anschließende Resume sofort sichtbar werden.
    const shouldKeepPolling = analysis.algorithms.some(
      (item) =>
        item.status === 'PENDING' ||
        item.status === 'RUNNING' ||
        (item.status === 'FAILED' &&
          item.message?.toLowerCase().includes('unavailable')),
    );
    if (!shouldKeepPolling) return;

    const interval = window.setInterval(() => void refreshAnalysis(), 1000);
    return () => window.clearInterval(interval);
  }, [analysis, refreshAnalysis]);

  const runAnalysis = async () => {
    if (!configuration) return;
    setBusy(true);
    try {
      const next = await startAnalysis(configuration.configurationId);
      localStorage.setItem('wirschiffendas.analysisId', next.analysisId);
      setAnalysis(next);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      setBusy(false);
    }
  };

  const retry = async (algorithm: AlgorithmName) => {
    if (!analysis) return;
    setRetrying(algorithm);
    try {
      setAnalysis(await retryAlgorithm(analysis.analysisId, algorithm));
      await refreshHealth();
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
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
            onChange={setConfigurationDraft}
            onSaved={setConfiguration}
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
                        Die UI startet nur den bestehenden Analysis-Management-Endpunkt; die weitere Verarbeitung bleibt choreographiert.
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
                  disabled={!configuration || busy}
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
                      REACHABLE bedeutet: der Container antwortet. Ein OPEN Circuit Breaker kann den Actuator-Gesamtstatus absichtlich auf DOWN setzen, obwohl der Service selbst weiterläuft.
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

                <Alert severity="info" icon={<ErrorRoundedIcon />}>
                  <strong>Demo:</strong> `docker compose stop thermal-analysis-service` → neue Analyse starten. Der Breaker <strong>Fluid → Thermal</strong> öffnet nach dem fehlgeschlagenen Aufruf. Nach der Wartezeit wird <strong>HALF_OPEN</strong> sichtbar. Service wieder starten. Der HALF_OPEN-Probe schließt den Breaker automatisch und der fehlgeschlagene Analyse-Schritt wird ohne Benutzeraktion fortgesetzt.
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
