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

Das Skript prüft zwei Szenarien.

### Szenario E2E-01 – Happy Path

1. Engine-Konfiguration anlegen.
2. Analyse starten.
3. Auf Abschluss warten.
4. Prüfen, dass `overallResult = OK` ist.

### Szenario E2E-02 – Serviceausfall und Retry

1. `thermal-analysis-service` stoppen.
2. Neue Analyse starten.
3. Warten, bis `THERMAL = FAILED` gemeldet wird.
4. Thermal Service wieder starten.
5. Nur `THERMAL` über den Retry-Endpunkt erneut starten.
6. Prüfen, dass die Choreographie ab Thermal fortgesetzt wird und am Ende `overallResult = OK` ist.

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
