# WirSchiffenDas – Teststrategie

## Ziel

Die Tests konzentrieren sich auf die für den Proof-of-Concept wichtigsten Anforderungen: Konfiguration, Analysezustand, erfolgreicher Analyseablauf, Ausfall eines Analyse-Service und Retry.

## Testpyramide

| Ebene | Zweck | Umsetzung |
|---|---|---|
| Unit Test | Domänenlogik schnell prüfen | JUnit 5 / AssertJ |
| Service Test | Erzeugen und Speichern von Konfigurationen prüfen | JUnit 5 / Mockito |
| End-to-End | Zusammenspiel aller sechs Container prüfen | `scripts/e2e.sh` |
| Demo | Manuelle Präsentation der Use Cases | Postman Collection |

## Automatisierte JUnit-Tests

### Analysis Management

`AnalysisRunTest` prüft insbesondere:

- neue Analysen starten mit vier `PENDING`-Algorithmen,
- ein fehlgeschlagener Algorithmus setzt `overallResult = FAILED`,
- vier erfolgreiche Algorithmen ergeben `overallResult = OK`,
- ein Retry-Zustand `RUNNING` entfernt ein altes Fehlerresultat.

### Configuration Service

`ConfigurationApplicationServiceTest` prüft:

- beim Anlegen wird eine eindeutige `C-...`-ID erzeugt,
- die übergebenen Konfigurationswerte bleiben erhalten,
- die Konfiguration wird über das Repository gespeichert.

Ausführen:

```bash
mvn clean verify
```

## Docker-End-to-End-Test

Voraussetzungen:

- Docker mit `docker compose`
- `curl`
- `jq`

### Variante A – Docker-Hub-Images

```bash
docker compose pull
docker compose up -d
bash scripts/e2e.sh
```

### Variante B – lokal gebaute Images

```bash
docker compose -f alternative_docker-compose.yml up --build -d
COMPOSE_FILE=alternative_docker-compose.yml bash scripts/e2e.sh
```

Das Skript prüft drei Szenarien.

### Szenario E2E-01 – Happy Path

1. Engine-Konfiguration anlegen.
2. Analyse starten.
3. Auf Abschluss warten.
4. Prüfen, dass `overallResult = OK` ist.

### Szenario E2E-02 – Serviceausfall und automatische Recovery

1. `thermal-analysis-service` stoppen.
2. Neue Analyse starten.
3. Warten, bis `THERMAL = FAILED` gemeldet wird und der Fluid-Breaker den technischen Ausfall erkennt.
4. Thermal Service wieder starten.
5. **Keinen manuellen Retry auslösen.** Der Circuit Breaker prüft die Liveness automatisch und Analysis Management setzt den Lauf ab `THERMAL` fort.
6. Prüfen, dass die Choreographie am Ende `overallResult = OK` erreicht und bereits erfolgreiche Vorgänger nicht erneut ausgeführt werden.

Der Zustand `HALF_OPEN` ist ein Übergangszustand. Bei einem Polling-Intervall von zwei Sekunden und einer sofort erfolgreichen Probe kann er in der UI nur sehr kurz oder gar nicht sichtbar sein. Für den Nachweis sind deshalb vor allem `OPEN`, die automatische Recovery und das anschließende `CLOSED` relevant.

### Szenario E2E-03 – fachlich ungültige Konfiguration

1. Konfiguration mit `coolingSystem = INVALID` anlegen.
2. Analyse starten.
3. Prüfen, dass Fluid erfolgreich abgeschlossen wird und `THERMAL = FAILED` mit fachlicher Fehlermeldung endet.
4. Prüfen, dass `ELECTRICAL` und `ENGINE_MANAGEMENT` nicht gestartet werden und `PENDING` bleiben.
5. Prüfen, dass `overallResult = FAILED` ist.

Die kontrollierten Demo-Varianten sind `STANDARD`, `PREMIUM`, `ADVANCED` und `INVALID`. `INVALID` darf gespeichert werden, wird aber erst im jeweils zuständigen Analyse-Worker fachlich abgelehnt.

## Manuelle Prüfungsdemo

Für die mündliche Präsentation bleibt die Postman-Collection unter
`postman/WirSchiffenDas.postman_collection.json` bestehen. Sie erlaubt denselben Ablauf sichtbar und schrittweise zu demonstrieren.

## Abdeckung der Qualitätsanforderungen

| Qualitätsanforderung | Nachweis |
|---|---|
| QR-01 Resilience | E2E-02 mit gestopptem Thermal Service |
| QR-02 Monitorability | `GET /api/analyses/{analysisId}` |
| QR-03 Independent Deployability | einzelner Container wird gestoppt/gestartet |
| QR-04 Lose Kopplung | Kommunikation ausschließlich über REST |
| QR-07 Testbarkeit | Unit Tests + E2E-Skript + Postman |
| QR-08 Responsiveness | Analyse startet mit `202 Accepted`, Status bleibt währenddessen abrufbar |
