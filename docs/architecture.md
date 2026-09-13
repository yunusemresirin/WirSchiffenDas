# WirSchiffenDas – Architekturentwurf

## 1. Ziel

Dieses Dokument konkretisiert die in `requirements.md` und `ddd.md` festgelegten Anforderungen und Bounded Contexts in eine umsetzbare Microservice-Architektur. Es werden keine neuen fachlichen Anforderungen eingeführt.

## 2. Service-Baseline

- `configuration-service`
- `analysis-management-service`
- `fluid-analysis-service`
- `thermal-analysis-service`
- `electrical-analysis-service`
- `engine-management-analysis-service`

## 3. Choreographie

Version 1 verwendet eine sequenzielle Choreographie:

```text
Client -> Analysis Management -> Fluid -> Thermal -> Electrical -> Engine Management
```

`Analysis Management` erzeugt den Analyse-Durchlauf und startet nur den Anchor-Algorithmus `FLUID`. Anschließend gibt jeder erfolgreich abgeschlossene Analyse-Service die Verarbeitung selbst an den nächsten Service weiter.

Jeder Analyse-Service:

1. meldet `RUNNING` proaktiv an Analysis Management,
2. simuliert seine Analyse asynchron,
3. meldet `READY/OK` oder `FAILED/FAILED`,
4. startet bei Erfolg den nächsten Analyse-Service.

Der Engine-Management-Service berücksichtigt die vorherigen Resultate und bildet das Ende der Choreographie.

## 4. REST-Verträge

### Extern

```http
POST /api/configurations
GET  /api/configurations/{configurationId}
POST /api/analyses
GET  /api/analyses/{analysisId}
POST /api/analyses/{analysisId}/algorithms/{algorithm}/retry
```

### Intern

Jeder Analyse-Service:

```http
POST /internal/analyses
```

Callbacks an Analysis Management:

```http
PUT /internal/analyses/{analysisId}/algorithms/{algorithm}/status
PUT /internal/analyses/{analysisId}/algorithms/{algorithm}/result
```

## 5. Analysis Command

```text
AnalysisCommand
├── analysisId
├── configuration
└── previousResults[]
```

Der Command wird entlang der Choreographie weitergegeben. Jeder erfolgreiche Service ergänzt sein eigenes Resultat, sodass Engine Management die Resultate der vorherigen Algorithmen berücksichtigen kann.

## 6. Retry

Ein Retry ist nur für einen Algorithmus im Status `FAILED` erlaubt.

Bereits erfolgreiche Vorgänger werden nicht erneut ausgeführt. Wenn der wiederholte Algorithmus erfolgreich ist, wird die Choreographie ab diesem Punkt mit den noch ausstehenden nachfolgenden Analysen fortgesetzt.

## 7. Circuit Breaker

Service-zu-Service-Aufrufe werden mit Resilience4j abgesichert:

```text
Analysis Management --Circuit Breaker--> Fluid / Retry-Ziel
Fluid              --Circuit Breaker--> Thermal
Thermal            --Circuit Breaker--> Electrical
Electrical         --Circuit Breaker--> Engine Management
```

Ist der nächste Service nicht erreichbar, markiert der Fallback den betroffenen Algorithmus als `FAILED`. Der aufrufende Service bleibt erreichbar und die Analyse kann später per Retry fortgesetzt werden.

## 8. Overall Result

- Sobald ein Algorithmus `FAILED` ist: `overallResult = FAILED`.
- Solange kein Fehler vorliegt, aber ein Algorithmus `PENDING` oder `RUNNING` ist: `overallResult = null`.
- Wenn alle vier Algorithmen `READY/OK` sind: `overallResult = OK`.

## 9. Datenhoheit

- `configuration-service`: persistiert `EngineConfiguration`
- `analysis-management-service`: persistiert `AnalysisRun`, Status und Resultate
- vier Analyse-Services: stateless

Analyse-Services greifen nicht direkt auf fremde Datenbanken zu.

## 10. Implementierungsstruktur

```text
api/             REST-Endpunkte und DTOs
application/     fachlicher Ablauf / AnalysisWorker
domain/          lokales Domänenmodell
infrastructure/  REST-Clients, Persistenz, Circuit Breaker
```

## 11. Docker-Deployment

Jeder Microservice besitzt ein eigenes `Dockerfile`. Das Repository enthält eine gemeinsame `docker-compose.yml`.

```text
Docker Host
├── configuration-service:8081
├── analysis-management-service:8082
├── fluid-analysis-service:8083
├── thermal-analysis-service:8084
├── electrical-analysis-service:8085
└── engine-management-analysis-service:8086
```

Die Services kommunizieren im Compose-Netzwerk über ihre Service-Namen, z. B. `http://thermal-analysis-service:8084`. `Configuration DB` und `Analysis DB` liegen in getrennten Docker Volumes.

Start des Gesamtsystems:

```bash
docker compose up --build
```

Damit wird die technische Anforderung Docker/Docker Compose direkt im Prototyp umgesetzt und Independent Deployability demonstrierbar.

## 12. Postman-Demonstration

Die Collection `postman/WirSchiffenDas.postman_collection.json` deckt die wichtigsten Use Cases ab: Konfiguration anlegen/lesen, Analyse starten/abfragen und Retry eines fehlgeschlagenen Algorithmus.

Für die Fehlerdemo wird beispielsweise `thermal-analysis-service` gestoppt. Der Circuit Breaker macht den Fehler im Analysezustand sichtbar; nach Neustart wird nur `THERMAL` wiederholt und die Choreographie ab dort fortgeführt.

## 13. Zentrale Entwurfsentscheidungen

- fachliche Service-Grenzen statt technischer Layer (`Wrong Cut` vermeiden)
- REST/HTTP für Version 1
- Choreographie statt zentraler Ablauf-Orchestration
- asynchrone Algorithmusausführung für Responsiveness
- stateless Analyse-Services
- Circuit Breaker mit Resilience4j
- Docker/Docker Compose als Deployment-Ziel
