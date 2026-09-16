# WirSchiffenDas – Architektur und Integrationsverträge

Stand: 16. September 2026. Kompakter Überblick: [arc42.md](arc42.md). Dieses Dokument beschreibt den implementierten Vertrag, keine geplante Zielarchitektur.

## Ablauf und Verantwortlichkeiten

Die sechs Backendservices bleiben erhalten. Configuration speichert Eingaben; Analysis Management speichert den Analysezustand und startet Fluid als Anker. Die normale Weitergabe erfolgt durch `Fluid → Thermal → Electrical → Engine Management`. Alle Worker melden an Management zurück. Ein angeforderter Retry startet direkt am fehlgeschlagenen Ziel; danach läuft dieselbe Choreographie weiter.

React zeigt Status und Einzelresultate an. Nginx liefert die Oberfläche und leitet deren API-/Monitoring-Anfragen weiter. Postman ist ein alternativer Client; die Oberfläche entscheidet nicht über Folgeschritte.

## REST-Schnittstellen

| Endpunkt | Bedeutung |
|---|---|
| `POST /api/configurations` | neue Konfiguration speichern |
| `GET /api/configurations/{configurationId}` | gespeicherte Konfiguration lesen |
| `POST /api/analyses` | Lauf starten; Body enthält `configurationId`, Antwort `202` |
| `GET /api/analyses/{analysisId}` | Zustand einschließlich Ergebnisdetails lesen |
| `POST /api/analyses/{analysisId}/algorithms/{algorithm}/retry` | fehlgeschlagenen Schritt erneut starten; `202` bzw. bei Zustandskonflikt `409` |
| `POST /internal/analyses` | asynchronen Workerauftrag annehmen |
| `PUT /internal/analyses/{analysisId}/algorithms/{algorithm}/status` | `RUNNING` oder technisches `FAILED` melden |
| `PUT /internal/analyses/{analysisId}/algorithms/{algorithm}/result` | terminales Ergebnis mit Equipmentdetails melden |

Interne Endpunkte sind im lokalen Demonstrator erreichbar, aber nicht authentifiziert. URLs sind unversioniert. Ein inkompatibler Vertragswechsel benötigt eine Versionierungs-/Migrationsstrategie.

Ein Worker-Command enthält `analysisId`, `attemptId`, `configuration` und `previousResults`. Die Konfiguration enthält ihre ID und fünf Equipmentfelder. Ein Vorgängerresultat besteht aus `algorithm` und `result`; Equipmentdetails liegen im Management. Worker ergänzen das eigene Algorithmusergebnis bei der Weitergabe.

Beispiel eines Fluid-Ergebnis-Callbacks:

```json
{
  "attemptId": "UUID-des-aktuellen-Versuchs",
  "status": "FAILED",
  "result": "FAILED",
  "message": "Invalid or missing equipment: fuelSystem",
  "equipmentResults": { "oilSystem": "OK", "fuelSystem": "FAILED" }
}
```

Ein technischer Fehler wird über den Statusvertrag gemeldet und erfindet keine Equipmentergebnisse. Callback-Antwort `204` bedeutet, dass die Meldung bearbeitet wurde; eine alte oder bereits terminale Ausführung kann dabei bewusst unverändert bleiben.

## Ergebnisregeln

| Algorithmus | Exakt erforderliche Equipment-Schlüssel |
|---|---|
| `FLUID` | `oilSystem`, `fuelSystem` |
| `THERMAL` | `coolingSystem` |
| `ELECTRICAL` | `electricalSystem` |
| `ENGINE_MANAGEMENT` | `engineManagementSystem` |

Worker prüfen nichtleere Werte ungleich `INVALID` (ohne Beachtung der Groß-/Kleinschreibung). Engine Management prüft zusätzlich genau ein erfolgreiches Ergebnis jedes der drei Vorgänger. Management akzeptiert Ergebnisse nur zu einem laufenden gültigen Versuch, mit vollständigem Schlüsselsatz und widerspruchsfreien Status-/Resultatwerten.

`READY` ohne Resultat genügt nicht. Gesamt-OK erfordert vier vollständige `READY/OK`-Ausführungen. Sobald ein Schritt fehlgeschlagen ist, gilt Gesamt-FAILED; ansonsten bleibt das Gesamtergebnis bis zum Abschluss `null`.

## Versuchskonsistenz und Retry

Ein Start erzeugt eine neue `attemptId` für alle Schritte. Retry ist nur bei `FAILED` und vollständigen erfolgreichen Vorgängern erlaubt. Er setzt Ziel und sämtliche Nachfolger mit einer neuen Versuch-ID zurück, markiert das Ziel laufend und erhält erfolgreiche Vorgänger. Der neue Command enthält deren Ergebnisse.

Änderungen erfolgen in einer Transaktion mit pessimistisch gesperrtem `AnalysisRun`. Parallele Retry-Anfragen können denselben Fehlerzustand deshalb nicht beide erfolgreich verbrauchen. HTTP-Aufrufe liegen außerhalb der Sperre. Alte Versuche und Rückmeldungen an terminale Ausführungen überschreiben keine gültigen Ergebnisse. Wiederholte `RUNNING`-Meldungen verlängern nicht künstlich die Fortschrittsfrist.

Jeder Worker unterdrückt doppelte Commands anhand `(analysisId, attemptId)` in einem lokalen Register. Standard: höchstens 10.000 Einträge, abgeschlossene Einträge bis zu einer Stunde bzw. bis zur notwendigen Verdrängung. Laufende Einträge werden nicht verdrängt. Das Register ist flüchtig; keine clusterweite oder dauerhafte Genau-einmal-Ausführung wird zugesichert.

## Fehlerbehandlung und Grenzen

| Schutz | Standard und Bedeutung |
|---|---|
| HTTP-Client | 2 Sekunden Verbindungs-, 5 Sekunden Lesefrist |
| Callback-Retry | maximal drei Gesamtversuche bei Netzwerkfehlern bzw. HTTP 500/502/503/504; Backoff 200/400 ms |
| Inaktivitätsprüfung | 30 Sekunden ohne Fortschritt, Scan jede Sekunde; erster offener Schritt wird `FAILED` |
| Management-Breaker | `startFluid`, `startThermal`, `startElectrical`, `startEngineManagement` |
| Weitergabe-Breaker | je ein prozesslokaler `nextService` in Fluid, Thermal und Electrical |

Die Demo-Breaker verwenden ein Fenster von zwei Aufrufen, mindestens einen Aufruf, 50 % Fehlerschwelle, zehn Sekunden OPEN und einen Probeaufruf in HALF_OPEN. Ein Thermal-Fehler öffnet dadurch nicht den Management-Breaker für Fluid. Ein direkter Management-Retry prüft aber auch nicht automatisch den Fluid→Thermal-Breaker; dafür ist ein späterer Aufruf über diese Kante erforderlich.

Eindeutige Ablehnung (Verbindungsaufbau gescheitert, offener Breaker oder HTTP 4xx) führt zur technischen Fehlermeldung. Nach unklarer Annahme, etwa verlorener Antwort oder HTTP 5xx, werden keine widersprüchlichen Fehlschläge erzwungen: Rückmeldungen bleiben bis zur Inaktivitätsfrist möglich. Versuchserkennung verhindert das Überschreiben durch veraltete Arbeit, beendet aber keinen entfernten Prozess.

Nach Ausschöpfen der Callback-Versuche stoppt der Worker seine Weitergabe. Queue/Outbox oder dauerhafte Zustellgarantie existieren nicht. Nach Management-Neustart kann der Watchdog persistierte inaktive Läufe erkennen; verlorene Workerarbeit wird nicht automatisch fortgesetzt.

## Persistenz, Kompatibilität und Betrieb

Configuration und Management besitzen getrennte H2-Dateien in eigenen Volumes. Worker halten nur flüchtige Verarbeitung und Deduplikation. Management referenziert die Konfigurations-ID; `ConfigurationSnapshot` ist ein Transportobjekt, kein im Lauf persistierter vollständiger Snapshot. Die öffentliche Konfigurations-API bietet aktuell keine Änderung gespeicherter Datensätze an.

Ältere Läufe ohne Versuch-ID und Equipmentdetails bleiben historische Datensätze. Unfertige Altbestände werden mit einem Hinweis beendet; für erneute Prüfung derselben Konfiguration wird ein neuer Lauf gestartet. Sie werden weder gelöscht noch nachträglich mit erfundenen Einzelergebnissen vervollständigt.

`docker-compose.yml` baut lokal; `compose.images.yml` verwendet Registry-Images. Readiness-Checks betreffen die Prozessbereitschaft, nicht die fachliche Fehlerfreiheit aller Nachbarn. Ports, Volumes und Abhängigkeiten zeigt [deployment.puml](diagrams/deployment.puml). Start-/Störungsanleitung und aktuelle Testnachweise stehen in [README](../README.md) und [testing.md](testing.md).
