# Web UI – Engine Quality Dashboard

## Zweck

Die Web-UI ist eine zusätzliche Präsentationskomponente für den bestehenden Proof-of-Concept. Sie führt keine eigene fachliche Analyse oder Orchestration aus, sondern verwendet ausschließlich die bereits vorhandenen REST-Schnittstellen.

Technologien:

- React
- Material UI
- Vite
- TypeScript
- Nginx als Reverse Proxy im Container

## Funktionen

1. Engine-Konfiguration erstellen und persistent speichern.
2. Vorhandene Konfiguration über eine `configurationId` laden.
3. Qualitätsanalyse starten.
4. Status und Resultat der vier Analysealgorithmen anzeigen.
5. `OverallResult` anzeigen.
6. Retry-Button für einen Algorithmus im Zustand `FAILED` anbieten.
7. Runtime-Erreichbarkeit aller sechs Backend-Services anzeigen.
8. Circuit-Breaker-Zustände entlang der Choreographie visualisieren.

## Analyseansicht

Die UI zeigt für jeden Algorithmus:

- `PENDING`
- `RUNNING`
- `READY`
- `FAILED`
- Resultat `OK` / `FAILED`
- optionale Fehlermeldung
- Retry-Button bei `FAILED`

Der Analysezustand wird während der Verarbeitung regelmäßig über

```http
GET /api/analyses/{analysisId}
```

aktualisiert.

## Circuit-Breaker-Visualisierung

Visualisierte Kanten:

```text
Analysis Management --CB--> Fluid
Fluid              --CB--> Thermal
Thermal            --CB--> Electrical
Electrical         --CB--> Engine Management
```

Farben:

- `CLOSED`: grün
- `OPEN`: rot
- `HALF_OPEN`: orange
- unbekannt/nicht verfügbar: grau

Für die PoC-Demo werden Resilience4j Circuit Breaker zusätzlich als Spring-Boot-Actuator-Health-Indicator registriert. Die UI unterscheidet bewusst zwischen

- **Service REACHABLE / UNREACHABLE** und
- **Circuit-Breaker-/Actuator-Zustand**.

Das ist notwendig, weil Resilience4j einen `OPEN` Circuit Breaker im Actuator-Health-Modell als `DOWN` abbilden kann, obwohl der aufrufende Microservice selbst weiterhin erreichbar ist.

## Prototypische Parameter

Damit der Zustandswechsel in einer kurzen Prüfung sichtbar wird, gilt für die Breaker im PoC:

- erster fehlgeschlagener geschützter Aufruf kann `OPEN` auslösen,
- Wartezeit im Zustand `OPEN`: 10 Sekunden,
- automatischer Wechsel zu `HALF_OPEN`,
- ein Testaufruf ist in `HALF_OPEN` erlaubt.

Diese Parameter sind bewusst demonstrationsfreundlich und nicht als produktive Standardkonfiguration zu verstehen.

## Fehlerdemo

Beispiel:

```bash
docker compose -f alternative_docker-compose.yml stop thermal-analysis-service
```

Dann in der UI eine neue Analyse starten. Erwartung:

```text
FLUID   READY / OK
THERMAL FAILED / FAILED
OverallResult = FAILED
Fluid -> Thermal Circuit Breaker = OPEN
```

Nach ca. 10 Sekunden wird der Breaker `HALF_OPEN` angezeigt.

Danach:

```bash
docker compose -f alternative_docker-compose.yml start thermal-analysis-service
```

Über den Retry-Button wird `THERMAL` erneut gestartet. Die bereits erfolgreichen Vorgänger werden nicht wiederholt; die Choreographie wird ab dem Retry-Ziel fortgeführt.

## Reverse Proxy

Im Browser werden keine Backend-Ports direkt angesprochen. Vite übernimmt diese Aufgabe in der Entwicklungsumgebung; im Docker-Deployment übernimmt Nginx das Routing.

```text
Browser
  |
  v
Web UI / Nginx
  |-- /api/configurations* --> configuration-service:8081
  |-- /api/analyses* -------> analysis-management-service:8082
  `-- /monitor/... ----------> Actuator des jeweiligen Backend-Service
```

Dadurch bleibt die Browser-Konfiguration unabhängig von den internen Docker-Service-Adressen und es ist keine zusätzliche CORS-Konfiguration in allen Backends erforderlich.


## Automatische Recovery bei technischen Ausfällen

Der manuelle Retry bleibt als UI-Funktion erhalten. Für die Circuit-Breaker-Demonstration ist er jedoch nicht erforderlich:

1. Zielservice stoppen und eine Analyse starten.
2. Der aufrufende Circuit Breaker wechselt nach dem fehlgeschlagenen Aufruf nach `OPEN`.
3. Nach der Wartezeit folgt `HALF_OPEN`; ein geplanter Liveness-Probe testet den Zielservice.
4. Bleibt der Service unerreichbar, geht der Breaker wieder nach `OPEN`.
5. Nach dem Start des Zielservices schließt ein erfolgreicher Probe den Breaker.
6. Analysis Management setzt den betroffenen `AnalysisRun` automatisch am zuvor fehlgeschlagenen Algorithmus fort.

Die Web-UI pollt Runtime-Health alle 2 Sekunden ohne Browser-/Proxy-Cache und den aktiven bzw. technisch unterbrochenen `AnalysisRun` weiter, sodass die Zustandsänderungen ohne Benutzerinteraktion sichtbar werden.
