# WirSchiffenDas – Engine Quality Analysis

Proof-of-Concept einer Microservice-basierten Qualitätsanalyse für Diesel-Engine-Konfigurationen im Modul SEKA.

## Services

- `configuration-service` – Port 8081
- `analysis-management-service` – Port 8082
- `fluid-analysis-service` – Port 8083
- `thermal-analysis-service` – Port 8084
- `electrical-analysis-service` – Port 8085
- `engine-management-analysis-service` – Port 8086
- `web-ui` – React + Material UI + Vite, erreichbar über Port 3000

Die Analyse läuft choreographiert:

```text
Analysis Management -> Fluid -> Thermal -> Electrical -> Engine Management
```

Status und Ergebnisse werden von den Analyse-Services proaktiv an `analysis-management-service` zurückgemeldet. Service-zu-Service-Aufrufe sind mit Resilience4j Circuit Breakern abgesichert.

## Web UI

Das Frontend liegt unter `frontend/` und dient als kompakte Bedien- und Demonstrationsoberfläche für den PoC.

Funktionen:

- Engine-Konfiguration anlegen und persistent speichern
- vorhandene Konfiguration über `configurationId` laden
- Qualitätsanalyse starten
- `OverallResult` anzeigen
- Status und Resultat aller vier Analysealgorithmen live anzeigen
- Retry-Button für jeden fehlgeschlagenen Algorithmus
- Runtime-Erreichbarkeit aller sechs Backend-Services anzeigen
- Circuit-Breaker-Zustände `CLOSED`, `OPEN` und `HALF_OPEN` visualisieren

Die UI führt keine eigene Ablaufsteuerung durch. Sie verwendet ausschließlich die bestehenden REST-Endpunkte; die eigentliche Analyse bleibt choreographiert.

### Frontend lokal starten

Backend-Services auf Ports 8081–8086 starten und anschließend:

```bash
cd frontend
npm install
npm run dev
```

Vite läuft standardmäßig unter `http://localhost:5173` und proxyt die API-/Actuator-Anfragen an die Backend-Services.

## Lokal mit Maven prüfen

```bash
mvn clean verify
```

Damit werden unter anderem die Unit-/Service-Tests für `AnalysisRun` und den `configuration-service` ausgeführt.

## Docker-Versionierung

Alle sechs Backend-Images werden gemeinsam unter dem Docker-Hub-Repository `ysirin2s/seka-wirschiffendas` veröffentlicht. Der Service-Name und die Release-Version stehen im Tag, zum Beispiel:

```text
ysirin2s/seka-wirschiffendas:configuration-service-v0.1.0
ysirin2s/seka-wirschiffendas:fluid-analysis-service-v0.1.0
```

Die gemeinsame Version wird über `VERSION` gesetzt. Als Vorlage dient `.env.example`:

```bash
cp .env.example .env
```

Unter PowerShell kann die Datei alternativ manuell als `.env` kopiert werden. `.env` ist nicht versioniert.

## Docker-Variante 1 – versionierte Images aus Docker Hub

Die Standarddatei `docker-compose.yml` verwendet die veröffentlichten Backend-Images aus `ysirin2s/seka-wirschiffendas` und baut die Web-UI lokal.

Beispiel `.env`:

```env
VERSION=0.1.0
```

Danach:

```bash
docker compose pull
docker compose up --build -d
```

Das Dashboard ist anschließend erreichbar unter:

```text
http://localhost:3000
```

Status anzeigen:

```bash
docker compose ps
```

Logs verfolgen:

```bash
docker compose logs -f
```

Stoppen:

```bash
docker compose down
```

Persistierte H2-Daten ebenfalls löschen:

```bash
docker compose down -v
```

## Docker-Variante 2 – alle Images lokal aus dem Source Code bauen

Für Entwicklung und Prüfung des neuesten Source-Codes:

```bash
docker compose -f alternative_docker-compose.yml up --build -d
```

Diese Variante baut alle sechs Backend-Services und die Web-UI direkt aus dem Repository. Das Dashboard ist ebenfalls unter `http://localhost:3000` erreichbar.

## Versioniertes Release nach Docker Hub

Das PowerShell-Skript `scripts/release.ps1` baut und pusht alle sechs Backend-Images in einem Schritt. Manuelles `docker tag` und einzelne `docker push`-Befehle sind nicht nötig.

Vorher einmal bei Docker Hub anmelden:

```powershell
docker login
```

Release erstellen:

```powershell
.\scripts\release.ps1 0.2.0
```

Dadurch werden unter anderem folgende Tags erzeugt und gepusht:

```text
configuration-service-v0.2.0
analysis-management-service-v0.2.0
fluid-analysis-service-v0.2.0
thermal-analysis-service-v0.2.0
electrical-analysis-service-v0.2.0
engine-management-analysis-service-v0.2.0
```

### Retention: nur aktuelle und vorherige Version behalten

Für die automatische Bereinigung alter Docker-Hub-Tags benötigt das Skript zusätzlich ein Docker-Hub Personal Access Token (PAT). Das Token wird nur als Umgebungsvariable verwendet und niemals in Git gespeichert.

Für die aktuelle PowerShell-Sitzung:

```powershell
$env:DOCKERHUB_PAT = "<dein Docker-Hub-PAT>"
```

Danach reicht weiterhin ein einzelner Befehl:

```powershell
.\scripts\release.ps1 0.3.0
```

Das Skript liest die vorhandenen versionierten Service-Tags aus Docker Hub, behält die zwei neuesten gemeinsamen Releases und versucht ältere Service-Tags zu entfernen. Falls die Docker-Hub-Tag-Löschung über die API in der aktuellen Hub-Version nicht akzeptiert wird, wird das Release nicht verworfen; das Skript gibt stattdessen eine Warnung aus und der betreffende alte Tag kann im Docker-Hub-Repository unter **Tags** gelöscht werden.

Ohne `DOCKERHUB_PAT` funktionieren Build und Push weiterhin vollständig; lediglich die automatische Remote-Bereinigung wird übersprungen.

## Circuit-Breaker- und Retry-Demo über die UI

Für die prototypische Visualisierung ist der Breaker bewusst so konfiguriert, dass bereits ein fehlgeschlagener geschützter Aufruf den Zustand `OPEN` auslösen kann. Nach 10 Sekunden wechselt er automatisch nach `HALF_OPEN`.

Beispiel Thermal-Ausfall:

```bash
docker compose -f alternative_docker-compose.yml stop thermal-analysis-service
```

Danach in der Web-UI:

1. Konfiguration speichern oder laden.
2. Neue Analyse starten.
3. Fluid läuft erfolgreich durch.
4. Der Aufruf Fluid → Thermal schlägt fehl.
5. `THERMAL = FAILED`, `OverallResult = FAILED` und der Breaker Fluid → Thermal wird `OPEN` angezeigt.
6. Nach ca. 10 Sekunden wird `HALF_OPEN` sichtbar.

Thermal wieder starten:

```bash
docker compose -f alternative_docker-compose.yml start thermal-analysis-service
```

Danach in der UI beim fehlgeschlagenen Thermal-Algorithmus auf **Retry** klicken. Die Analyse wird ab Thermal fortgesetzt; bereits erfolgreiche Vorgänger werden nicht erneut ausgeführt.

Hinweis: Der Retry wird vom `analysis-management-service` direkt an das Retry-Ziel geschickt. Der Breaker Fluid → Thermal wird deshalb erst bei einem späteren normalen Aufruf über genau diese Kante wieder vollständig geschlossen. Das ist gewollt und macht den Unterschied zwischen Choreographie-Pfad und Management-Retry sichtbar.

## Automatisierter End-to-End-Test

Voraussetzungen: Docker Compose, `curl` und `jq`.

Mit Docker-Hub-Images:

```bash
docker compose up -d
bash scripts/e2e.sh
```

Oder mit lokal gebauten Images:

```bash
docker compose -f alternative_docker-compose.yml up --build -d
COMPOSE_FILE=alternative_docker-compose.yml bash scripts/e2e.sh
```

Das Skript testet sowohl den Happy Path als auch den Ausfall von `thermal-analysis-service` mit anschließendem Retry.

Weitere Details: `docs/testing.md`.

## Postman-Demo

Collection importieren:

`postman/WirSchiffenDas.postman_collection.json`

Happy Path:

1. `01 Create Configuration`
2. `02 Get Configuration`
3. `03 Start Analysis`
4. einige Sekunden warten
5. `04 Get Analysis Status / Result`

Erwartetes Endergebnis: alle vier Algorithmen `READY / OK` und `overallResult = OK`.

## Dokumentation

- `docs/arc42.md` – kompakte Architekturdokumentation für das Semesterprojekt
- `docs/requirements.md` – vollständige Anforderungen
- `docs/requirements_short.md` – kompakte Requirements-Übersicht
- `docs/ddd.md` – Domänenmodell und Bounded Contexts
- `docs/architecture.md` – Architektur- und REST-Entscheidungen
- `docs/testing.md` – Teststrategie und E2E-Szenarien
- `docs/architecture-views.md` – Übersicht des 4-Sichten-Modells
- `docs/diagrams/context.puml` – Kontextsicht
- `docs/diagrams/building-blocks.svg` – Bausteinsicht
- `docs/diagrams/runtime.puml` – Laufzeitsicht
- `docs/diagrams/deployment.puml` – Verteilungssicht
