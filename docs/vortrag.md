# WirSchiffenDas: Vortrags- und Demoleitfaden

Die aktuelle Präsentation ist [WirSchiffenDas_Pruefungsvortrag_aktuell.pptx](WirSchiffenDas_Pruefungsvortrag_aktuell.pptx). Das [PDF-Handout](WirSchiffenDas_Pruefungsvortrag_aktuell.pdf) zeigt dieselben zwölf Folien. Die ursprüngliche Präsentation bleibt als ältere Fassung erhalten. Die Sprechernotizen im aktuellen Deck enthalten Erläuterungen und Quellen.

## Zeitrahmen und roter Faden

Annahme: Einzelvortrag mit 10 bis 13 Minuten nach Übung 8. Die folgende Planung ergibt **12:30 Minuten einschließlich Demo**. Bei 18 bis 20 Minuten für ein Zweierteam lassen sich die Entscheidungsalternativen und der Code-Walkthrough vertiefen. Beide Personen übernehmen ungefähr die Hälfte und sollten den ganzen Ablauf erklären können. Einen konkreten Prüfungstermin setzt dieser Leitfaden nicht voraus.

| Folie | Zeit | Kernaussage |
|---|---:|---|
| 1. WirSchiffenDas | 0:20 | Vier Services simulieren die Qualitätsanalyse einer Engine-Konfiguration. |
| 2. Fallstudie und Anforderungen | 0:50 | Der Prototyp adressiert Analyse, Rückmeldung und Wiederholung. |
| 3. Kontextsicht | 0:35 | Ingenieur und Testwerkzeuge nutzen eine klar begrenzte Anwendung. |
| 4. Bausteinsicht | 0:50 | Sechs Backend-Services, React und zwei getrennte Datenhaltungen. |
| 5. Laufzeitsicht | 0:55 | Der Gesamtauftrag läuft asynchron, einzelne HTTP-Aufrufe bleiben synchron. |
| 6. Verteilungssicht | 0:45 | Sieben Container laufen auf einem lokalen Docker-Host. |
| 7. Entscheidung 1 | 1:00 | Fachlicher Schnitt und Choreographie haben Nutzen und konkrete Kosten. |
| 8. Entscheidung 2 | 1:05 | Ein Timeout beweist keine Ablehnung. Zustandsregeln ergänzen den Breaker. |
| 9. Code-Walkthrough | 1:15 | Equipment-Ergebnisse, Versuchserkennung und Übergabefehler an drei Stellen zeigen. |
| 10. Live-Demo | 2:20 | Erfolg, ungültiges Öl, Thermal-Ausfall und Wiederanlauf. |
| 11. Literatur und Tests | 0:50 | Qualitätsprobleme mit Schirgi/Brenner einordnen und überprüfbare Eigenschaften nennen. |
| 12. Fazit und Grenzen | 0:45 | Der Prototyp zeigt die Entscheidungen, ohne dauerhafte Zustellung oder Hochverfügbarkeit zu versprechen. |

Die Zeitplanung ist knapp. Die Container vor dem Vortrag bauen und starten. Im Vortrag keine Installation oder erstmaligen Downloads durchführen. Bei Zeitverlust auf Folie 11 nur ein Beispiel aus Literatur und Tests erläutern. Die vier Sichten und die beiden Entscheidungen bleiben erhalten.

## Vorbereitung

Alle Befehle im Projektordner `WirSchiffenDas` ausführen. Bei einer separat benannten Compose-Instanz denselben `-p`-Wert bei jedem Compose-Befehl verwenden.

```powershell
docker compose up --build -d --wait
docker compose ps
```

Die Oberfläche unter [localhost:3000](http://localhost:3000) öffnen. Zwei Fenster vorbereiten: Browser und Terminal. Ein Editorfenster mit den drei unten genannten Codeankern öffnen. Die Docker-Volumes behalten, damit gespeicherte Konfigurationen und Läufe verfügbar bleiben.

Für eine technische Probe stehen die verbundenen Tests bereit:

```powershell
.\scripts\e2e.ps1 -IncludeFaults
```

Dieser Test stoppt den Thermal-Service vorübergehend und startet ihn wieder. `-IncludeWorkerLoss` ergänzt einen Worker-Ausfall für den Watchdog-Nachweis. Vor dem Vortrag den normalen Zustand mit `docker compose ps` prüfen. Die tatsächlich ausgeführten Prüfungen stehen in [testing.md](testing.md); vorhandene Tests sind allein kein Beleg für einen erfolgreichen aktuellen Lauf.

## Demo in vier Fällen

### 1. Gültige Konfiguration

1. **Demo: gültige Konfiguration** wählen.
2. Konfiguration speichern und Analyse starten.
3. Den Übergang von `RUNNING` zu `READY` zeigen.
4. Die vier erfolgreichen Algorithmen und fünf Equipment-Ergebnisse zeigen: Öl, Kraftstoff, Kühlung, Elektrik und Motorsteuerung.

Satz dazu: „Das Gesamtergebnis wird erst OK, wenn alle vier Analysen vollständige erfolgreiche Equipment-Ergebnisse geliefert haben.“ Die Simulation benötigt standardmäßig zwei Sekunden pro Worker. Fachliche Berechnungen eines realen Dieselmotors finden nicht statt.

### 2. Ungültiges Ölsystem

1. **Demo: ungültiges Ölsystem** wählen. Öl hat den Wert `INVALID`, Kraftstoff bleibt gültig.
2. Als neue Konfiguration speichern und einen neuen Lauf starten.
3. Beim Fluid-Ergebnis **Öl FAILED** und **Kraftstoff OK** zeigen.
4. Darauf hinweisen, dass der technische Service erreichbar ist, obwohl ein fachlicher Test fehlschlägt.

Der Ablauf gibt bei fachlichem Fehler nicht an den nächsten Worker weiter. Ein unveränderter Retry repariert ungültige Eingaben nicht. Für korrigierte Equipment-Werte eine neue Konfiguration und Analyse anlegen.

### 3. Thermal-Service ausfallen lassen

```powershell
docker compose stop thermal-analysis-service
```

1. Eine gültige Konfiguration auswählen oder neu speichern.
2. Einen neuen Lauf starten.
3. Fluid liefert `READY/OK`. Die klare Verbindungsablehnung zum Thermal-Service führt zu `THERMAL FAILED`.
4. In der Oberfläche die Weitergabe **Fluid an Thermal** und den zugehörigen `nextService`-Breaker ansehen.

Zwei unterschiedliche Beobachtungen erklären: Die Healthanzeige prüft den Servicezustand. Der Circuit Breaker gehört zu einer bestimmten Aufrufkante und bewertet deren vergangene Aufrufe. Bei offenem Breaker führt der Aufrufer weitere Übergabeversuche vorübergehend nicht aus. Nach zehn Sekunden kann er in `HALF_OPEN` wechseln. Bei langsamer Erläuterung ist daher bereits dieser Zustand statt `OPEN` sichtbar.

### 4. Wiederanlauf, Retry und ursprüngliche Kante

```powershell
docker compose start thermal-analysis-service
```

Die Readiness abwarten, beispielsweise über die grüne Erreichbarkeitsanzeige beziehungsweise `docker compose ps`.

1. Im fehlgeschlagenen Lauf **THERMAL erneut ausführen** wählen.
2. Fluid bleibt erfolgreich. Thermal und seine Nachfolger starten mit einer neuen `attemptId`.
3. Nach erfolgreichem Abschluss das Gesamtergebnis zeigen.
4. Anschließend einen **neuen vollständigen Lauf** starten. Bei Bedarf die zehnsekündige Breaker-Wartezeit abwarten und erneut starten.
5. Damit die ursprüngliche Kante Fluid an Thermal tatsächlich probeweise aufrufen und ihren Breaker bei Erfolg wieder schließen.

Wichtig für die Erklärung: Der Thermal-Retry erfolgt direkt durch Analysis Management über `startThermal`. Er benutzt den Fluid-Breaker nicht und schließt ihn daher auch nicht. Erst ein vollständiger neuer Lauf erprobt diese ursprüngliche Kante wieder. Ein offener Thermal-Retry-Breaker blockiert den separaten Start-Fluid-Breaker nicht.

## Drei Codeanker

Keine Dateien vollständig vorlesen. Pro Anker eine Regel erklären und die passende Stelle markieren.

1. [Fluid AnalysisWorker](../services/fluid-analysis-service/src/main/java/de/hbrs/seka/wirschiffendas/fluid/application/AnalysisWorker.java), `execute`: Die Schleife bewertet jedes Equipment. `reportResult` enthält Öl und Kraftstoff auch bei einem fachlichen Fehler. Nur ein erfolgreicher Schritt gibt weiter.
2. [AnalysisRun](../services/analysis-management-service/src/main/java/de/hbrs/seka/wirschiffendas/analysismanagement/domain/AnalysisRun.java), `updateResult` und `retry`, ergänzt durch [AlgorithmExecution](../services/analysis-management-service/src/main/java/de/hbrs/seka/wirschiffendas/analysismanagement/domain/AlgorithmExecution.java), `complete`: Zulässige Übergänge und vollständige Ergebnisse bestimmen den Gesamtstatus. Retry ersetzt Versuch-IDs für den betroffenen Kettenabschnitt. Alte Rückmeldungen können den neuen Versuch nicht überschreiben.
3. [Fluid NextServiceClient](../services/fluid-analysis-service/src/main/java/de/hbrs/seka/wirschiffendas/fluid/infrastructure/NextServiceClient.java), `startNext` und `fallback`: Nachfolgeraufruf mit Circuit Breaker. Verbindungsablehnung und unklarer Übertragungsausgang führen zu unterschiedlichen Zustandsentscheidungen.

Bei Rückfragen zur Nebenläufigkeit zusätzlich `AnalysisApplicationService` und das Repository zeigen: Management verwendet kurze Datenbanktransaktionen mit Sperren. HTTP-Aufrufe sollen diese Datenbanksperren nicht festhalten. Die Worker-Deduplizierung ist lokal und geht bei einem Prozessneustart verloren.

## Antworten auf naheliegende Rückfragen

**Warum Choreographie?** Die Übung fordert choreographierte Analyse-Services. Die Worker geben den Auftrag dezentral weiter. Management besitzt trotzdem den fachlichen Laufzustand und startet sowohl den Einstieg als auch manuell gewählte Wiederholungen. Bei komplexeren Verzweigungen wäre ein Orchestrator eine begründbare Alternative.

**Warum kein Kafka?** Übung 8 erlaubt technische Alternativen. Docker Compose und Resilience4j decken den gewählten Fokus ab. Dauerhafte Nachrichten könnten die Zustellung verbessern, würden aber weitere Zustell-, Deduplizierungs- und Betriebsregeln erfordern.

**Ist das vollständiges DDD?** Die Zerlegung und Datenhoheit folgen fachlichen Verantwortungen. Die beiden zustandsbehafteten Services besitzen lokale Domänenmodelle. Die Worker nutzen einfache DTOs und Maps. Sie besitzen keine umfassenden taktischen Modelle oder vollständigen Anti-Corruption Layer. Die Context Map unterscheidet den tatsächlichen Stand von einer späteren Zielarchitektur.

**Warum sind Timeouts und Circuit Breaker nötig?** HTTP-Zeitgrenzen beschränken einzelne Aufrufe. Ein Breaker unterbindet vorübergehend wiederholte Aufrufe eines problematischen Ziels. Der gespeicherte Inaktivitätszeitpunkt erkennt einen stecken gebliebenen Ablauf auch ohne neue HTTP-Fehlermeldung. Standardwerte sind zwei Sekunden für Verbindungsaufbau, fünf Sekunden für das Lesen einer Antwort und 30 Sekunden ohne Analysefortschritt.

**Ist Verarbeitung genau einmal garantiert?** Nein. Versuch-IDs, Zustandsregeln und lokale Deduplizierung verhindern viele doppelte oder alte Änderungen. Angenommene Arbeit und Callbacks liegen jedoch nicht in einer dauerhaften Queue. Ein Prozessabsturz kann Arbeit verlieren. Der Watchdog stellt dann einen sichtbaren Fehlerzustand für einen manuellen Retry her.

**Kann das horizontal skalieren?** Der aktuelle Ein-Host-Aufbau mit H2-Dateien, festen Hostports und lokaler Worker-Deduplizierung belegt das nicht. Containerisierung erleichtert die Paketierung. Mehrinstanzbetrieb braucht zusätzliche Entscheidungen zur Persistenz, Koordination und Zustellung.

## Quellen und Abgabe

- Übung 8, *Infos Semesterprojekt*, Fassung vom 13.07.2026, Seiten 1 bis 3: Vortragsteile, vier Sichten, Zeitrahmen und technische Alternativen.
- Übung 5, *Prototyp für WirSchiffenDas*, Seiten 1 bis 3: Choreographie, vier Analyse-Services, Equipment-Ergebnisse und Fehlerfall.
- Übung 4 und *Case Study Baustein-Sicht WirSchiffenDas v3.0*: Ausgangsarchitektur, fachlicher Schnitt und Architekturprobleme.
- Vorlesung Kapitel 3: Kontext-, Baustein-, Laufzeit- und Verteilungssicht.
- Schirgi und Brenner (2021), *Quality Assurance for Microservice Architectures*, Abschnitte IV.A und IV.B. Literaturbestand im [OOKA-Repository](https://git.fslab.de/salda2m/ooka). Die projektbezogene Bewertung steht in [anti-patterns.md](anti-patterns.md).
- Implementierungsstand und Risiken: [arc42.md](arc42.md), [architecture-views.md](architecture-views.md), [ddd.md](ddd.md), [testing.md](testing.md).

Die UML-Quellen in `docs/diagrams` enthalten mehr Details als die für den Vortrag vereinfachten, editierbaren Diagramme. Das lokal fehlende Übungsblatt 6 wird nicht als vollständig ausgewertet dargestellt. Abgabetermin und erforderliche Dateien mit den aktuellen Kursangaben abgleichen; der Leitfaden ersetzt keine konkrete Terminbestätigung.
