# WirSchiffenDas – Teststrategie

## Ausführung

Backend: Java 21 und Maven, `mvn clean verify` im Repository-Root.
Frontend: Node 22.18+ oder 24, `cd frontend && npm ci && npm test && npm run build`.
`npm test` verwendet den Node-Test-Runner; zusätzliche Testpakete sind nicht nötig.

## Automatisierte Nachweise

| Bereich | Tests und Grenzen |
|---|---|
| AnalysisRun | `AnalysisRunTest`: PENDING, FAILED, OK und Entfernen alter Resultate bei Retry |
| Konfiguration | `ConfigurationApplicationServiceTest`: Erzeugen/Speichern einschließlich INVALID, mit gemocktem Repository |
| Konfigurations-API | `ConfigurationControllerTest`: alle vier Katalogwerte einschließlich INVALID → 201; unbekannter Wert → 400 vor Persistenz |
| Worker | `AnalysisWorkerTest` in allen vier Analyse-Services: STANDARD/PREMIUM/ADVANCED → READY; INVALID je zuständigem Feld → FAILED und kein Folgeaufruf |
| Resume | `AnalysisApplicationServiceRecoveryTest`: Vorgänger erhalten, fachliche Fehler auslassen, fehlgeschlagenen Start nicht zählen, nach Konfigurationsausfall erneut versuchen, keine Wiederholung laufender Schritte |
| Zielisolation | `AnalysisServiceStarterTest`: Thermal-Fehler blockiert Fluid nicht; Thermal-Recovery verwendet Thermal-Liveness |
| Management-Recovery | `CircuitBreakerRecoveryProbeTest`: Wiederholung ohne neuen Zustandswechsel, getrennte Zielzustände |
| Choreographie-Recovery | Probe-Tests in Fluid/Thermal/Electrical: fehlgeschlagene Liveness → OPEN; Erfolg → CLOSED; regulärer HALF_OPEN-Aufruf löst Recovery aus; verlorene Benachrichtigung wird wiederholt |
| UI-Polling | `frontend/tests/polling.test.mjs`: fachlicher Abbruch trotz PENDING-Nachfolgern beendet Analysis-Polling; technische Fehler und laufende Schritte bleiben beobachtbar |

Die JVM-Tests verwenden für HTTP gezielt MockRestServiceServer bzw. MockMvc und
für externe Dienste Mockito. Sie ersetzen keinen Container-Test. Runtime-Polling
läuft unabhängig vom Analysis-Polling alle zwei Sekunden; Management → Fluid
liest explizit den Fluid-Breaker und nicht den ersten beliebigen Breaker.

## Docker-End-to-End-Test

Voraussetzungen: Docker Compose, curl, jq und Bash. Im Repository-Root starten.
Mit Docker-Hub-Images muss `VERSION` auf ein Release mit dem geprüften Source-Stand
zeigen; ein Merge allein aktualisiert die Images nicht (siehe README, OCI-Labels).

```bash
docker compose pull
docker compose up --build -d
bash scripts/e2e.sh
```

Für lokal gebaute Images:

```bash
VERSION=0.1.0 VCS_REF=$(git rev-parse HEAD) docker compose -f alternative_docker-compose.yml up --build -d
COMPOSE_FILE=alternative_docker-compose.yml bash scripts/e2e.sh
```

### E2E-01 – Happy Path

Konfiguration mit gültigen Varianten anlegen und Analyse starten. Alle vier
Algorithmen müssen READY/OK und das Gesamtergebnis OK erreichen.

### E2E-02 – Thermal-Ausfall, automatische Recovery und Resume

Thermal stoppen, neue Analyse starten, THERMAL/FAILED und Fluid→Thermal/OPEN
abwarten. Thermal wieder starten, ohne den Retry-Endpunkt aufzurufen.
Derselbe AnalysisRun muss vollständig READY/OK werden und der Breaker CLOSED.
Das Log `Starting FLUID analysis <analysisId>` muss genau einmal vorkommen.
HALF_OPEN wird in den Probe-Tests deterministisch nachgewiesen; ein kurzer
HALF_OPEN-Zustand muss nicht zwischen zwei UI-Polls sichtbar sein.

Management prüft technische Fehler zusätzlich regelmäßig mit eigenen Breakern
je Ziel. Dadurch bleibt ein vorübergehend gescheiterter Wiederanlauf nicht liegen;
Resume kann vor dem Schließen des Breakers des Vorgänger-Service beginnen.

### E2E-03 – Fachlich ungültige Konfiguration

`coolingSystem=INVALID` wird gespeichert. Fluid wird READY, Thermal FAILED,
Electrical und Engine Management bleiben PENDING, das Gesamtergebnis wird FAILED.
Nach weiteren Recovery-Ticks bleibt dieser fachliche Fehler unverändert.

## Manuelle Demo

Die Web-UI zeigt Runtime-Erreichbarkeit und Breaker-Zustände. Der Retry-Button
bleibt ein manueller Fallback. Die Postman-Collection unter
`postman/WirSchiffenDas.postman_collection.json` bleibt für einzelne REST-Aufrufe
verfügbar. Ein HTTP 202 vom Recovery-Hook ist kein Nachweis abgeschlossener Analyse;
dafür den Status desselben AnalysisRun prüfen.
