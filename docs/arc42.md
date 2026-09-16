# WirSchiffenDas – kompakte arc42

SEKA · Semesterprojekt · Stand 16. September 2026 · implementierter Analyseprototyp

## 1. Einführung und Ziele

Die Fallstudie beschreibt eine gewachsene Unternehmensplattform mit gemeinsamem Datenmodell, gekoppelten Komponenten und einer blockierenden, schwer beobachtbaren Motoranalyse. Dieser Prototyp realisiert den Analyseausschnitt: Ein Ingenieur speichert eine Konfiguration, startet vier Analysealgorithmen und sieht Fortschritt, Equipmentresultate und Gesamtergebnis. Ein fehlgeschlagener Schritt lässt sich nach Beseitigung der Ursache wiederholen.

Priorisiert werden fachlich verständliche Grenzen, nachvollziehbare Ergebnisse, begrenzte Wartezeiten und eine reproduzierbare Demo. Eine physikalische Simulation und die Migration von ERP, CRM oder Order Fulfillment liegen außerhalb dieses Ausschnitts.

## 2. Randbedingungen

Übung 5 verlangt persistente Konfiguration, mindestens vier Analyse-Microservices, REST, Choreographie, proaktive Rückmeldungen und Circuit-Breaker-Verhalten. Übung 8 verlangt vier UML-Sichten, Entscheidungen, Code-Walkthrough, Demo und Fazit. Spring Boot und Postman sind keine allgemeinen Pflichttechnologien. Docker/Compose erfüllt eine technische Alternative aus Übung 8; Resilience4j ergänzt sie.

Implementiert sind Java 21/Spring Boot, React/MUI, REST/JSON, zwei getrennte H2-Datenbanken, Resilience4j und Docker Compose. Die Algorithmen prüfen fünf Equipmentfelder: leer bzw. `INVALID` ist ungültig. Diese Demonstrationsregeln haben keine physikalische Aussagekraft.

## 3. Kontextabgrenzung

Der Ingenieur bedient die Weboberfläche im Browser: Konfiguration, Analyse, Status, Ergebnisse und Retry. Der Demo-Operator stoppt/startet Services über Docker. Postman ist eine zusätzliche Bedienmöglichkeit. ERP und CRM sind keine laufenden Nachbarsysteme des Prototyps.

**UML-Kontextsicht:** [context.puml](diagrams/context.puml)

## 4. Lösungsstrategie

Configuration besitzt Konfigurationsdaten, Analysis Management besitzt Analysezustände. Vier Worker prüfen Fluid, Thermal, Electrical und Engine Management. Der fachliche Schnitt ist eine Entwurfsannahme; kleine Worker beweisen keine sechs vollständig ausgeprägten Bounded Contexts.

Management startet Fluid; danach geben Worker selbst weiter. Management sammelt Rückmeldungen, berechnet das Gesamtergebnis, überwacht Stillstand und startet angeforderte Retries. Der Normalablauf ist eine sequenzielle REST-Choreographie. Strategische Beziehungen, acht IST-Statements und die konzeptionelle KI-Vision stehen in [ddd.md](ddd.md); alle 21 Literaturkriterien in [anti-patterns.md](anti-patterns.md).

## 5. Bausteinsicht

| Baustein | Verantwortung |
|---|---|
| Web UI / Nginx | Bedienung, Weiterleitung, Zustands-/Erreichbarkeitsanzeige |
| Configuration | Equipmentwerte speichern/lesen; eigene Configuration DB |
| Analysis Management | Analysezustand, Versuchserkennung, Retry, Timeout; eigene Analysis DB |
| Fluid | getrennte Resultate für Öl und Kraftstoff |
| Thermal / Electrical | Kühlung bzw. Elektrik prüfen |
| Engine Management | Motorsteuerung und vollständige erfolgreiche Vorgänger prüfen |

**UML-Bausteinsicht:** [building-blocks.puml](diagrams/building-blocks.puml)

Die zustandsführenden Services enthalten API, Anwendung, Domäne und Infrastruktur. Worker verwenden lokale Command-DTOs und Maps ohne reichhaltige Aggregate. Ein gemeinsames Fachmodell und direkte Zugriffe auf fremde Datenbanken existieren nicht.

## 6. Laufzeitsicht

1. Management lädt die Konfiguration, persistiert einen Lauf mit `attemptId` und startet Fluid. Der Client erhält `202 Accepted`; Berechnung erfolgt asynchron.
2. Jeder Worker meldet `RUNNING`, dann Equipmentresultate und `READY/OK` oder `FAILED/FAILED`. Nach bestätigtem Erfolg startet er den Nachfolger.
3. Engine Management verlangt genau ein `OK` von Fluid, Thermal und Electrical. Gesamt-OK erfordert vier vollständige erfolgreiche Ausführungen.
4. Eindeutig abgelehnte Starts werden als technische Fehler gemeldet. Bei unklarer Annahme bleiben Rückmeldungen möglich; der Watchdog beendet den ersten offenen Schritt nach 30 Sekunden ohne Fortschritt (Scan jede Sekunde).
5. Retry setzt Ziel und Nachfolger unter einer Datenbanksperre auf einen neuen Versuch; erfolgreiche Vorgänger bleiben. Alte oder doppelte terminale Rückmeldungen überschreiben den gültigen Stand nicht.

**UML-Laufzeitsicht:** [runtime.puml](diagrams/runtime.puml); Fehler/Retry: [runtime-recovery.puml](diagrams/runtime-recovery.puml). HTTP-Aufrufe bleiben synchron; HTTP 202 ist keine dauerhafte Arbeitswarteschlange.

## 7. Verteilungssicht

Sieben Container laufen auf einem Docker-Host: Web UI `3000:80`, Configuration `8081`, Management `8082`, Worker `8083–8086`. Zwei getrennte Volumes speichern die H2-Dateien. URLs sind konfigurierbar; intern gelten Compose-Servicenamen.

`docker-compose.yml` baut den lokalen Quellcode. `compose.images.yml` nutzt vorhandene Registry-Images. Readiness-Checks ordnen den Start; ein offener Breaker macht einen bedienbaren Prozess nicht automatisch unbereit. Austauschbare Container beweisen noch keine unabhängigen Releases oder horizontale Skalierung.

**UML-Verteilungssicht:** [deployment.puml](diagrams/deployment.puml)

## 8. Querschnittliche Konzepte

Status: `PENDING → RUNNING → READY/FAILED`; technische Startfehler dürfen offene Schritte direkt beenden. Gesamt-OK setzt alle Equipmentresultate voraus. Fachliche Fehler enthalten Einzelresultate; technische Fehler besitzen eine Meldung ohne fingierte Equipmentresultate.

Management hat vier getrennte Ziel-Breaker. Fluid, Thermal und Electrical besitzen je einen `nextService`-Breaker. HTTP-Verbindungs-/Lesegrenzen: 2/5 Sekunden. Rückmeldungen: höchstens drei Versuche bei ausgewählten temporären Fehlern. Versuchserkennung, Datenbanksperren und lokale Worker-Deduplikation ergänzen sich; letztere überlebt keinen Prozessneustart.

## 9. Entwurfsentscheidungen

| Entscheidung | Alternative und Konsequenz |
|---|---|
| Fachlicher Schnitt, getrennte Daten | Modularer Monolith wäre betrieblich einfacher; vier Analyse-Services zeigen den Aufgabenfokus. Datenhoheit erfordert explizite Verträge. |
| REST-Choreographie | Zentraler Orchestrator vereinfacht Ablaufübersicht, Broker ermöglicht dauerhafte Zustellung. REST passt zum Auftrag, erzeugt aber Laufzeitabhängigkeiten. |
| Resilience4j plus Fristen/Versuche | Eigener Breaker erhöht Infrastrukturaufwand. Die Bibliothek ersetzt keine fachlichen Zustandsregeln. |
| Compose und H2 | Clusterplattform/Serverdatenbank ermöglichen andere Betriebsmodelle, erhöhen den Aufwand. Ziel ist eine lokale Demo auf einem Host. |

## 10. Qualitätsanforderungen und Nachweise

Abnahmefälle: Erfolg, ungültiges Equipment, Ausfall/Retry, fehlender Fortschritt, getrennte Breaker, konkurrierende und alte Rückmeldungen. Domain-/Service-Tests prüfen Regeln, HTTP-Clienttests die Verträge, E2E den verbundenen Ablauf. Ausgeführte Prüfungen und Grenzen stehen in [testing.md](testing.md); vorhandene Tests belegen nicht pauschal sämtliche Qualitätsziele.

## 11. Risiken und Retrospektive

Callbacks und angenommene Arbeit sind nicht dauerhaft gepuffert. Nach Management-Ausfall kann ein Worker aufgeben; der Watchdog ermöglicht später Fehleranzeige und manuellen Retry. Genau-einmal-Ausführung wird nicht garantiert. Der Lauf speichert keinen vollständigen Konfigurationssnapshot; gespeicherte Konfigurationen sind aktuell nur anlegbar/lesbar. Historische Läufe ohne `attemptId` bleiben lesbar, erhalten keine erfundenen Equipmentdetails und benötigen für Wiederholung einen neuen Lauf.

H2-Dateien, feste Hostports und lokale Deduplikation begrenzen Mehrinstanzbetrieb. Authentifizierung, API-Versionierung, CI/CD, zentrales Logging und Tracing fehlen. Lernerfahrung: Container und Breaker allein sichern weder fachliche Konsistenz noch Wiederanlauf. Dafür braucht es Ergebnisregeln, Versuchserkennung und beobachtbare Fehlerpfade.

## 12. Glossar und Quellen

`AnalysisRun`: Analyseauftrag; `attemptId`: Versuch des aktiven Kettenabschnitts; Choreographie: Weitergabe durch Worker; Bounded Context: Gültigkeitsgrenze eines fachlichen Modells.

Grundlagen: Übung 4/5/8, jeweils S. 1–3; Fallstudie WirSchiffenDas v3.0. Schirgi/Brenner (2021), *Quality Assurance for Microservice Architectures*, IV.A/B: Bewertungsrahmen. Literaturlinks und Einordnung: [anti-patterns.md](anti-patterns.md). Das lokal fehlende Blatt 6 wird nicht als vollständig geprüft ausgegeben.
