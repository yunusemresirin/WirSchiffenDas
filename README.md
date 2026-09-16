# WirSchiffenDas - Engine Quality Analysis

Ein SEKA-Prototyp zur Qualitätsanalyse einer Motorkonfiguration. Vier Analyse-Microservices arbeiten in einer REST-Choreographie zusammen. Die Weboberfläche zeigt Equipmentresultate, Status, Wiederholungen und die Erreichbarkeit der Services.

## Lokal starten

Voraussetzung: Docker Desktop mit Linux-Containern und Docker Compose v2.

Im Projektordner:

```powershell
docker compose up --build -d --wait
```

Die Oberfläche ist unter [localhost:3000](http://localhost:3000) erreichbar. Alle sechs Backends und die Oberfläche werden aus dem lokalen Quellcode gebaut. Eine `.env` ist nicht nötig; der lokale Image-Tag ist standardmäßig `dev`.

```powershell
docker compose ps
docker compose logs -f analysis-management-service
docker compose stop
docker compose start --wait
```

Ein normaler Stopp oder `docker compose down` erhält die Konfigurationen und Analyseläufe in zwei getrennten Docker-Volumes. `down -v` löscht diese Daten und gehört nicht zum normalen Demoablauf.

`alternative_docker-compose.yml` bleibt als kompatibler Verweis auf die Standarddatei erhalten. Für bereits veröffentlichte Backend-Images gibt es die getrennte Variante `compose.images.yml`; dafür sind `IMAGE_REPOSITORY` und `VERSION` explizit zu setzen. Die Weboberfläche wird auch dort lokal gebaut.

## Services und Ablauf

| Service | Host-Port | Verantwortung |
|---|---:|---|
| configuration-service | 8081 | Konfigurationen und eigene H2-Datenbank |
| analysis-management-service | 8082 | Analyseläufe, Ergebnisse, Versuche, Timeout und eigene H2-Datenbank |
| fluid-analysis-service | 8083 | Öl- und Kraftstoffsystem getrennt prüfen |
| thermal-analysis-service | 8084 | Kühlsystem prüfen |
| electrical-analysis-service | 8085 | Elektrisches System prüfen |
| engine-management-analysis-service | 8086 | Engine Management und erforderliche Vorgängerresultate prüfen |
| web-ui | 3000 | React-Oberfläche und Nginx-Reverse-Proxy |

```text
Analysis Management -> Fluid -> Thermal -> Electrical -> Engine Management
                         |         |           |               |
                         +---------+-----------+---------------+
                                    Status / Resultate
                                   an Analysis Management
```

Management startet den Anker Fluid oder bei einem Retry den betroffenen Schritt. Danach geben die Worker selbst weiter. Der Start antwortet mit HTTP 202; die simulierte Berechnung läuft asynchron. Die einzelnen HTTP-Aufrufe bleiben synchrone Kommunikationsabhängigkeiten.

## Ergebnisse und Fehlerbehandlung

- Jedes modellierte Equipmentteil bekommt ein eigenes `OK` oder `FAILED`. Fluid prüft Öl und Kraftstoff separat.
- Gesamt-`OK` setzt vier vollständige `READY/OK`-Analysen voraus. Engine Management verlangt drei eindeutige erfolgreiche Vorgänger.
- Fachlich ungültiges Equipment wird mit Einzelergebnissen erläutert. Technische Fehler haben eine Fehlermeldung und keine erfundenen Equipmentresultate.
- Jeder Ausführungsversuch hat eine `attemptId`. Retry erhält erfolgreiche Vorgänger und erneuert den betroffenen Schritt samt Nachfolgern. Alte Rückmeldungen ändern den neuen Stand nicht.
- Datenbanktransaktionen mit einer Sperre auf den Lauf verhindern konkurrierende Retry-Starts und verlorene Statusänderungen. HTTP-Aufrufe erfolgen außerhalb dieser Sperre.
- Management schützt jedes Start-/Retry-Ziel durch einen eigenen Circuit Breaker. Jeder weitergebende Worker besitzt seinen eigenen `nextService`-Breaker.
- Ohne Fortschritt wird ein noch offener Lauf nach der konfigurierten Frist an der ersten offenen Stufe als fehlgeschlagen markiert und kann wiederholt werden.
- Worker wiederholen Rückmeldungen begrenzt bei vorübergehenden Übertragungsfehlern. Lokale Duplikaterkennung verhindert doppelte Verarbeitung desselben Versuchs innerhalb ihres Aufbewahrungsfensters.

Die Readiness-Prüfung des Containers ist unabhängig vom globalen Circuit-Breaker-Healthstatus: Ein offener Breaker bedeutet nicht, dass sein aufrufender Service selbst unerreichbar ist.

## Vorbereitete Demo

1. In der Oberfläche die gültige Beispielkonfiguration auswählen, speichern und analysieren.
2. Die Öl-Fehlerkonfiguration auswählen: Öl muss `FAILED`, Kraftstoff weiterhin `OK` liefern.
3. Thermal stoppen und eine gültige Analyse starten:

```powershell
docker compose stop thermal-analysis-service
```

4. Fehlermeldung und den Breaker **Fluid -> Thermal** ansehen.
5. Thermal starten und den fehlgeschlagenen Schritt in der Oberfläche wiederholen:

```powershell
docker compose start thermal-analysis-service
```

Ein Retry von Management nach Thermal nutzt eine andere Verbindung als Fluid nach Thermal. Um auch den ursprünglichen Breaker zu schließen, nach dessen Wartezeit eine neue Gesamtanalyse starten. Dieser Aufruf prüft die ursprüngliche Verbindung.

## Tests

Automatisierte Backend-Tests benötigen **Java 21** und Maven:

```powershell
mvn verify
```

Frontend, Node.js 22.18+ oder 24:

```powershell
cd frontend
npm ci
npm test
npm run build
```

Systemtests gegen die gestarteten Container, aus dem Projektordner:

```powershell
.\scripts\e2e.ps1
.\scripts\e2e.ps1 -IncludeFaults -IncludeWorkerLoss
```

Der zweite Lauf stoppt bzw. beendet gezielt den Thermal-Container und startet ihn anschließend wieder. Er prüft Breaker-Isolation, Retry, alte Rückmeldungen, die Wiederherstellung der ursprünglichen Verbindung und einen verlorenen Worker nach Annahme des Auftrags. Details stehen in [docs/testing.md](docs/testing.md).

## Einstellungen

| Eigenschaft | Standard | Zweck |
|---|---|---|
| `http.client.connect-timeout` | 2 s | Verbindungsaufbau begrenzen |
| `http.client.read-timeout` | 5 s | Warten auf HTTP-Antwort begrenzen |
| `analysis.timeout.inactivity` | 30 s | Fehlenden Fortschritt erkennen |
| `analysis.timeout.scan-interval-ms` | 1000 | Intervall der Timeout-Prüfung |
| `worker.processing-delay` | 2 s | Sichtbare simulierte Berechnungsdauer |
| Breaker-Wartezeit | 10 s | Wartezeit vor einem Probeaufruf |

Spring-Konfiguration lässt sich bei Bedarf über entsprechende Umgebungsvariablen überschreiben. Für die Demo sind die Standardwerte vorgesehen.

## Gespeicherte Läufe älterer Projektstände

Ältere fertige Läufe bleiben lesbar. Sie enthalten noch keine Versuchkennung und keine Equipmentdetails; diese werden nicht nachträglich erfunden. Alte offene Läufe erhalten einen Hinweis, für ihre gespeicherte Konfiguration einen neuen Lauf anzulegen. Die Daten bleiben erhalten.

## Architektur und Vortragsunterlagen

- [arc42](docs/arc42.md): Kontext, Strategie, vier Sichten, Entscheidungen und Grenzen
- [DDD](docs/ddd.md): fachlicher Schnitt und Context Map
- [Anforderungen](docs/requirements.md): Aufgabenbezug und Nachweise
- [Architekturverträge](docs/architecture.md): Commands, Callbacks und Zustandsregeln
- [Tests und Demo](docs/testing.md)
- [Oberfläche](docs/ui.md)
- [Diagrammquellen](docs/diagrams)

Die Algorithmen sind Simulationen. Lokale H2-Dateien, begrenzte Worker-Deduplizierung und Rückmeldeversuche ersetzen keine dauerhaft zuverlässige Nachrichteninfrastruktur. Horizontale Skalierung, Authentifizierung und verteiltes Tracing werden nicht als umgesetzt behauptet.

## Eigene Backend-Images veröffentlichen

Nach dem eigenen Docker-Login kann das Releaseskript explizit ausgeführt werden:

```powershell
.\scripts\release.ps1 -Version 0.1.0 -ImageRepository deinname/dein-repository
```

Es baut lokal und veröffentlicht die sechs Backend-Images unter dem angegebenen Ziel. Bestehende Remote-Tags werden nicht automatisch gelöscht.
