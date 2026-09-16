# WirSchiffenDas – Anforderungen und Nachweise

Stand: 16. September 2026. Grundlage: Übung 5, S. 1–3 (Analyse), Übung 4, S. 1–3 (Architekturreflexion) und Übung 8, S. 1–3 (Semesterprojekt). Die folgenden IDs sind projektspezifisch. „Auftrag“ bezeichnet Vorgaben bzw. fachliche Wünsche aus den Unterlagen; „Entscheidung“ und „Absicherung“ bezeichnen unsere Umsetzung. Vorhandener Code wird nicht pauschal als erfolgreich abgenommen markiert. Ausgeführte Prüfungen stehen in [testing.md](testing.md).

## Funktionale Anforderungen

| ID | Herkunft / Anforderung | Umsetzung und prüfbares Kriterium |
|---|---|---|
| FR-01 | Auftrag: Konfiguration erfassen | fünf Equipmentwerte über React oder REST anlegen; ungültige Form-/Requesteingaben werden sichtbar zurückgewiesen |
| FR-02 | Auftrag: persistent speichern | Configuration DB im eigenen Volume; gespeicherte ID nach normalem Neustart wieder lesbar |
| FR-03 | Auftrag: Qualitätsanalyse starten | vorhandene Konfigurations-ID erzeugt neuen Lauf, Management startet Anker FLUID; Status separat abrufbar |
| FR-04 | Auftrag: mindestens vier Analyse-Microservices | Fluid, Thermal, Electrical, Engine Management sind getrennte Services; Engine prüft drei erfolgreiche Vorgänger |
| FR-05 | Auftrag: Choreographie | nach Ankerstart geben erfolgreiche Worker selbst weiter; sequenziell ist zulässig |
| FR-06 | Auftrag: Einzelresultate je Equipment | Öl/Kraftstoff getrennt, außerdem Kühlung, Elektrik, Motorsteuerung; fehlende Resultate ergeben kein falsches OK |
| FR-07 | Auftrag: Gesamtergebnis | ausschließlich vier vollständige erfolgreiche Ausführungen ergeben OK; ein Fehler ergibt FAILED |
| FR-08 | Auftrag: Status darstellen | RUNNING/READY/FAILED; zusätzlich PENDING für noch nicht gestartete Schritte; technische und fachliche Fehler unterscheidbar |
| FR-09 | Auftrag: proaktive Meldungen | Worker melden Zustand und Resultate an Management; Benutzer muss keine Folgeschritte manuell starten |
| FR-10 | Fachlicher Wunsch: Wiederholung | FAILED-Schritt gezielt wiederholen; erfolgreiche Vorgänger erhalten, Ziel/Nachfolger gehören zum neuen Versuch |
| FR-11 | Auftrag: Serviceausfall behandeln | Ausfall bleibt sichtbar; gesunde APIs bleiben bedienbar; Wiederanlauf/Retry nach Behebung möglich |
| FR-12 | Auftrag: REST | explizite Command-, Abfrage- und Callback-Endpunkte; aktuelle Verträge in architecture.md |
| FR-13 | Entscheidung: Bedienung/Demo | React als Hauptoberfläche, Postman als Alternative; Postman ist keine allgemeine Pflicht |

Demoalgorithmen sind ausdrücklich Simulationen. Der Wert `INVALID` ermöglicht einen fachlichen Negativfall; ein unveränderter ungültiger Eingabestand wird auch durch Retry nicht korrekt. Dafür wird eine korrigierte Konfiguration und ein neuer Lauf angelegt.

## Qualitätsziele und zusätzliche Absicherung

| ID | Ziel | Konkretes Szenario / Grenze |
|---|---|---|
| QR-01 | Fehler begrenzen | getrennte Ziel-Breaker, HTTP-Fristen und begrenzte Callbackversuche; keine dauerhafte Zustellgarantie |
| QR-02 | Zustand beobachten | Ergebnisdetails, Meldungen, Erreichbarkeit und Breakerzustand in der UI; keine vollständige Observability-Plattform |
| QR-03 | Deployment entkoppeln | eigene Images/Container und getrennte Daten; unabhängige Releaseprozesse müssen weiterhin organisatorisch abgesichert werden |
| QR-04 | Verträge statt interne Zugriffe | kein Zugriff auf fremde Tabellen; REST-Abhängigkeiten bleiben vorhanden |
| QR-05 | Datenhoheit | Konfigurations- und Analysedaten getrennt; kein gemeinsames Fachmodell |
| QR-06 | fachlicher Schnitt | vier Analysefähigkeiten statt technischer Schichtservices; DDD-Annahmen in ddd.md begründet |
| QR-07 | Testbarkeit | Regeln, Verträge, Ausfall/Retry und Rückmeldungsrandfälle gezielt testen; Ausführungsstand in testing.md |
| QR-08 | Responsiveness | Berechnung asynchron, Fortschritt abrufbar; initialer HTTP-Austausch bleibt zeitlich begrenzt synchron |
| QR-09 | Absicherung: keine hängenden Aufträge | nach 30 Sekunden ohne Fortschritt erster offener Schritt FAILED; Frist konfigurierbar |
| QR-10 | Absicherung: konsistente Wiederholung | Versuch-IDs, Zeilensperre und lokale Deduplikation schützen gegen alte Rückmeldungen/Doppelstarts; kein Exactly-once-Versprechen |

QR-09/10 und die konkreten Fristwerte sind Entwurfsentscheidungen zur Absicherung des Verhaltens, keine wörtlichen Vorgaben des Aufgabenblatts.

## Technologiewahl und Abgabe

Spring Boot/Java sind unsere Implementierungswahl; die Unterlagen erlauben andere Frameworks. Docker und Compose erfüllen die ausgewählte technische Alternative aus Übung 8. Resilience4j implementiert den verlangten Breaker; Kafka ist deshalb keine zusätzliche Pflicht. React ist bereits umgesetzt, nicht mehr eine offene Option. H2, JPA, Nginx und PlantUML sind eigene Entscheidungen.

Zum Vortrag gehören Anforderungen, vier UML-Sichten, wichtige Entscheidungen, kurzer Code-Walkthrough, Demo und Fazit. Die reduzierte arc42 dokumentiert den tatsächlichen Stand; DDD und die vollständige 21-Punkte-Auswertung ergänzen die Übung-4-Bezüge. Blatt 6 liegt lokal nicht vor; dessen Detailanforderungen werden hier nicht als vollständig geprüft ausgegeben.

## Abnahmeszenarien

1. Lokal bauen/starten; Konfiguration speichern und nach Neustart mit erhaltenen Volumes laden.
2. Gültige Konfiguration: vier READY/OK-Schritte, fünf positive Equipmentresultate, Gesamt-OK.
3. Ungültiger Kraftstoff: Öl bleibt OK, Kraftstoff FAILED; Gesamt-FAILED und verständliche Meldung.
4. Thermal stoppen: Fehler an der tatsächlich betroffenen Kante beobachten; neuer Fluid-Start bleibt durch einen anderen Management-Breaker unabhängig.
5. Thermal starten und gezielt wiederholen: Vorgänger erhalten, Restkette erfolgreich; ursprünglichen Weitergabe-Breaker separat mit einem passenden Probeaufruf schließen.
6. Fehlenden Fortschritt und verlorene/alte/doppelte Rückmeldungen prüfen; Zustand bleibt konsistent und Fehler wird wiederholbar.
7. Historische Läufe ohne Versuch-ID lesbar lassen; neue Analyse für erneute Ausführung anlegen.

Diese Liste ist der Abnahmemaßstab, keine vorweggenommene grüne Checkliste. Automatisierte und manuelle Nachweise sind in [testing.md](testing.md) getrennt aufgeführt.

## Nicht umgesetzt / mögliche Erweiterungen

Authentifizierung, API-Versionierung, persistente Queue/Outbox, gespeicherte Konfigurationssnapshots, CI/CD, zentrale Logs/Tracing, Mehrinstanzbetrieb und Abbruchfunktion sind offen. Parallelisierung, Kafka, mehr Equipmentvarianten und KI sind mögliche Weiterentwicklungen. Echte Motorsimulation und vollständige Unternehmensmigration gehören nicht zum aktuellen Umfang.
