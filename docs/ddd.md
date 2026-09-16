# WirSchiffenDas – fachlicher Schnitt und Context Map

Stand: 16. September 2026. Die Fallstudie betrachtet eine Unternehmensplattform; der Code implementiert daraus den Analyseausschnitt. Eine Context Map beschreibt Modellgrenzen und Integrationsbeziehungen. Die Ausführungsreihenfolge steht getrennt in der [Laufzeitsicht](diagrams/runtime.puml).

## Gemeinsame Sprache

| Begriff | Bedeutung im Analyseprototyp |
|---|---|
| Engine Configuration | gespeicherte Konfiguration mit fünf Equipmentwerten |
| Optional Equipment | betrachteter konfigurierbarer Bestandteil, hier als einfacher Textwert |
| AnalysisRun | einmaliger Analyseauftrag für eine Konfigurations-ID |
| AlgorithmExecution | Zustand und Ergebnisse eines der vier Analyseschritte |
| Equipmentresultat | `OK` oder `FAILED` für genau ein Equipmentfeld |
| Overall Result | Gesamt-OK ausschließlich bei vier vollständigen erfolgreichen Ausführungen |
| attemptId | Versuch des aktiven Kettenabschnitts; bei Retry für Ziel und Nachfolger neu |
| Retry | Wiederholung ab einem fehlgeschlagenen Schritt mit erhaltenen erfolgreichen Vorgängern |

## Fachliche Grenzen im implementierten Ausschnitt

| Kontext / fachliche Fähigkeit | Modell und Verantwortung | Umsetzung |
|---|---|---|
| Configuration Management | Welche Konfiguration wurde gespeichert? | `EngineConfiguration` mit ID, `oilSystem`, `fuelSystem`, `coolingSystem`, `electricalSystem`, `engineManagementSystem`; eigene DB |
| Analysis Management | Welcher Auftrag ist in welchem gültigen Zustand? | `AnalysisRun` als Konsistenzgrenze mit `AlgorithmExecution`, Versuch, Status, Equipmentresultaten und Gesamtergebnis; eigene DB |
| Fluid Analysis | Bewertung von Öl und Kraftstoff | eigener Worker, zwei separate Equipmentresultate |
| Thermal Analysis | Bewertung der Kühlung | eigener Worker für `coolingSystem` |
| Electrical Analysis | Bewertung der Elektrik | eigener Worker für `electricalSystem` |
| Engine Management Analysis | Bewertung der Motorsteuerung unter Einbezug aller Voranalysen | eigener Worker für `engineManagementSystem`; exakt drei erfolgreiche Vorgänger |

Configuration und Analysis Management besitzen klar unterschiedliche Begriffe und Datenhoheit. Die vier Analysefähigkeiten sind für die Aufgabenstellung eigenständig deploybar; ihre Einordnung als weitere Bounded Contexts bleibt eine begründete Entwurfsannahme. Die einfachen Simulationsregeln liefern noch wenig Evidenz für stark unterschiedliche lokale Fachmodelle. Ein Microservice ist nicht allein durch seine Containergrenze ein Bounded Context.

## Context Map des Prototyps

**Diagramm:** [context-map.puml](diagrams/context-map.puml)

| Beziehung | Einordnung und konkreter Vertrag |
|---|---|
| Configuration → Analysis Management | Configuration ist Upstream für Konfigurationsdaten. Management übernimmt den veröffentlichten REST/JSON-Vertrag in ein eigenes Transport-DTO `ConfigurationSnapshot`. Eine ausgeprägte Anti-Corruption Layer ist nicht implementiert. |
| Management ↔ Analysefähigkeiten | Gemeinsam abgestimmter Command-/Callback-Vertrag entspricht einer Partnership im Entwurf. Management besitzt Auftragszustand, Worker besitzen Prüfregeln. Tatsächliche Teamorganisation kann aus dem Code nicht abgeleitet werden. |
| Fluid/Thermal/Electrical → Engine Management | Engine Management ist fachlicher Downstream der Vorgängerergebnisse; es kennt Algorithmusnamen und OK/FAILED-Semantik. Daten gelangen entlang der Kette bzw. über den Retry-Command dorthin. |
| Zwischen den Workern | Einheitliches lokales JSON-Vertragsformat als Published Language; keine gemeinsam eingebundene Domain-Bibliothek. Vertragsänderungen müssen trotzdem abgestimmt werden. |

Die Map verwendet U/D für die Richtung fachlicher Abhängigkeit, nicht als vollständige Liste physischer HTTP-Aufrufe. Insbesondere senden nicht alle Vorgänger direkt an Engine Management. Diese Unterscheidung verhindert, dass Ablaufkanten als DDD-Muster ausgegeben werden.

## Taktisches DDD und Datenhoheit

Management bündelt Statusübergänge, Ergebnisvollständigkeit, Vorgängerbedingungen und Retry in `AnalysisRun`/`AlgorithmExecution`. Eine Transaktion mit Zeilensperre schützt diese Konsistenzgrenze. Configuration hält eine eigene JPA-Entität. Das ist eine teilweise taktische DDD-Umsetzung, keine vollständige Hexagonal-Architecture-Musterimplementierung.

Worker besitzen API-Records und verwenden `Map<String, String>` sowie Listen von Maps. Ein flächendeckender Mapper zu reichen lokalen Domänenmodellen existiert dort nicht. `ConfigurationSnapshot` ist nur eine Kopie für den Aufruf, kein persistierter Snapshot des Laufs. Integration erfolgt über HTTP, nie über fremde Tabellen. Der gemeinsame Maven-Parent teilt technische Buildentscheidungen, kein Fachmodell.

## Übung 4: acht IST-Statements und abgeleitete Entscheidungen

| Statement | Bewertung und Konsequenz |
|---|---|
| 1. Komponenten-/Schichtenteams übernehmen | Schichtenteams benötigen für fachliche Änderungen viele Übergaben. Für die Sollorganisation sind Teams mit Verantwortung für eine fachliche Fähigkeit samt Betrieb plausibler; der Demonstrator belegt diese Organisation nicht. |
| 2. Architekturänderung und KI ohne Geschäftsführung | Verteilung verändert Kosten, Betrieb, Verantwortlichkeiten und Risiken. Unternehmensweite Migration und KI-Einsatz brauchen deshalb fachliche Ziele und abgestimmte Verantwortung; sie sind keine reine Frameworkentscheidung. |
| 3. Eine VM bedeutet horizontal skalierbar | Das Auslagern einer gebündelten Instanz skaliert nicht automatisch einzelne Analysefunktionen. Separate Worker schaffen kleinere Einheiten, aber feste Ports, lokale Daten und Deduplikation begrenzen weiterhin die hier gezeigte Skalierung. |
| 4. GP-API wird komplex | Ein universeller Einstieg kann unterschiedliche Clientbedürfnisse, Aggregation und Fachregeln sammeln. Eine passende Aufteilung bzw. clientbezogene Einstiegsschicht kann helfen; im Prototyp erledigt Nginx nur Routing, kein umfassendes BFF-Fachmodell. |
| 5. Analyse ist problematisch | Die Fallstudie nennt lange/blockierende Arbeit und fehlende Transparenz bei Fehlern. Asynchrone Worker, Statusmeldungen, Zeitgrenzen und gezielter Retry adressieren genau diesen Ausschnitt. |
| 6. Universales globales Datenmodell | Ein gemeinsames Unternehmensmodell koppelt Teams und Bestandsysteme an dieselben Änderungen. Getrennte Modelle und explizite Übersetzungen an ERP-/CRM-Grenzen sind die Sollrichtung; implementiert sind nur die lokalen Konfigurations-/Analysemodule. |
| 7. Moderne CI/CD vorhanden | Automatisierte Veröffentlichung allein beweist keine unabhängigen Releases oder geprüfte Verträge. Der Prototyp automatisiert Build und lokalen Start; eine CI/CD-Pipeline ist weiterhin offen. |
| 8. Order Fulfillment solide | Manuelle Übergaben und Kopplung der Fallstudie benötigen explizite Zuständigkeit, Fortschrittsmodell und Fehlerbehandlung. Ein eigener Auftragskontext ist eine Solloption, wurde im Analyseprototyp aber nicht implementiert. |

Diese Einordnung basiert auf Übung 4, S. 1–3 und der Fallstudie v3.0. Unterauer (2017) unterstützt die Diskussion von Teamzuschnitt; technische Modulgrenzen allein verändern keine reale Organisation.

## Größere Sollvision aus Übung 4

Außerhalb des implementierten Ausschnitts können Product/Configuration, Customer/Sales und Order Fulfillment eigene Modellgrenzen bilden. SAP ERP und Oracle CRM bleiben Upstream-Systeme mit ihren jeweiligen Begriffen. Fachliche Adapter bzw. Anti-Corruption Layers würden diese Modelle an den Grenzen übersetzen. Zusammenarbeit zwischen Produktentwicklung und Analyse verlangt abgestimmte Verträge; Registry, Broker, zentrale Logs und CI/CD sind ergänzende Betriebsentscheidungen, keine bereits vorhandenen Bestandteile.

**Konzeptionelle Map:** [context-map-vision.puml](diagrams/context-map-vision.puml). Sie ergänzt die Unternehmensperspektive; nur der markierte Analyseausschnitt existiert im Code.

## Kurze KI-Vision

Ein Assistenzdienst könnte Ingenieuren dokumentierte Equipmentregeln erklären, ähnliche frühere Fehler mit Quellen auffinden und eine Analysezusammenfassung entwerfen. Ein LLM sollte dabei überprüfbare Konfigurations-/Resultatdaten lesen; die deterministische Freigaberegel bleibt im Analysesystem und beim verantwortlichen Ingenieur. Ein späterer MCP-Adapter wäre lediglich ein möglicher Werkzeugzugang, keine Voraussetzung.

Voraussetzungen wären freigegebene Wissensquellen, Zugriffsschutz, nachprüfbare Quellenangaben und Evaluation falscher Empfehlungen. Eine KI-Komponente, ein Vektorspeicher und MCP sind im vorliegenden Code nicht implementiert.

## Weiterführende Dokumente

[arc42](arc42.md) · [Anforderungen](requirements.md) · [Integrationsverträge](architecture.md) · [21 Smells/Anti-Patterns samt Literatur](anti-patterns.md)
