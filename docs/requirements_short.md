# WirSchiffenDas – Requirements Overview

> Kurzfassung der Anforderungen für das Semesterprojekt.
>
> Die ausführliche und verbindliche Beschreibung befindet sich in
> [`requirements.md`](./requirements.md).

---

## 1. Funktionale Anforderungen

| ID | Anforderung | Priorität | Kurz-Akzeptanzkriterium | Status |
|---|---|---|---|---|
| FR-01 | Engine-Konfiguration erfassen | MUST | Gültige Konfiguration kann angelegt werden | ✅ |
| FR-02 | Konfiguration persistent speichern | MUST | Konfiguration kann über ID gespeichert und erneut geladen werden | ✅ |
| FR-03 | Qualitätsanalyse starten | MUST | Analyse erhält eine ID und startet den ersten Algorithmus | ✅ |
| FR-04 | Mindestens vier Analyse-Microservices | MUST | Mindestens vier getrennte fachliche Analyse-Services existieren | ✅ |
| FR-05 | Choreographie der Analyse | MUST | Analyse läuft nach einmaligem Start ohne manuelle Zwischenschritte weiter | ✅ |
| FR-06 | Einzelresultate erzeugen | MUST | Jeder Algorithmus liefert `OK` oder `FAILED` | ✅ |
| FR-07 | Gesamtergebnis bestimmen | MUST | Gesamtergebnis wird aus den Einzelresultaten gebildet | ✅ |
| FR-08 | Bearbeitungsstatus bereitstellen | MUST | Status `RUNNING`, `READY` und `FAILED` sind abrufbar | ✅ |
| FR-09 | Status und Resultate proaktiv melden | MUST | Analyse-Service meldet Statusänderungen selbstständig | ✅ |
| FR-10 | Einzelnen Algorithmus erneut starten | MUST | Fehlgeschlagener Algorithmus kann separat wiederholt werden | ✅ |
| FR-11 | Serviceausfall behandeln | MUST | Ausfall eines Analyse-Service führt nicht zum Gesamtausfall | ✅ |
| FR-12 | REST-Schnittstellen verwenden | MUST | Microservices kommunizieren über definierte REST-Endpunkte | ✅ |
| FR-13 | Demonstration über Postman | MUST | Wesentliche Use Cases können über Postman ausgeführt werden | ✅ |

---

## 2. Qualitätsanforderungen

| ID | Qualitätsziel | Priorität | Umsetzung / Ziel | Status |
|---|---|---|---|---|
| QR-01 | Resilience | MUST | Circuit Breaker verhindert Fehlerkaskaden | ✅ |
| QR-02 | Monitorability | MUST | Status und Ergebnisse einer Analyse sind nachvollziehbar | ✅ |
| QR-03 | Independent Deployability | MUST | Jeder Microservice läuft in einem eigenen Container | ✅ |
| QR-04 | Lose Kopplung | MUST | Kommunikation nur über definierte Schnittstellen | ✅ |
| QR-05 | Datenhoheit / keine Shared Persistence | SHOULD | Analyse-Services greifen nicht direkt auf gemeinsame Tabellen zu | ✅ |
| QR-06 | Fachliche Service-Grenzen | MUST | Aufteilung nach Business Capabilities statt technischen Schichten | ✅ |
| QR-07 | Testbarkeit | SHOULD | Erfolgs-, Fehler- und Retry-Szenarien sind reproduzierbar | ✅ |
| QR-08 | Responsiveness | MUST | Laufende Analyse blockiert Statusabfragen nicht | ✅ |

---

## 3. Technische Anforderungen

| ID | Technologie / Anforderung | Priorität | Zweck | Status |
|---|---|---|---|---|
| TA-01 | Spring Boot | MUST | Implementierung der Microservices | ✅ |
| TA-02 | Docker | MUST | Eigener Container pro Microservice | ✅ |
| TA-03 | Docker Compose | MUST | Start und Komposition des Gesamtsystems | ✅ |
| TA-04 | Resilience4j | MUST | Circuit-Breaker-Pattern | ✅ |
| TA-05 | Apache Kafka | COULD | Optionale Übertragung von Statusnachrichten | ⬜ |

---

## 4. Use Cases

| ID | Use Case | Akteur | Priorität | Status |
|---|---|---|---|---|
| UC-01 | Engine-Konfiguration erstellen | Ingenieur | MUST | ✅ |
| UC-02 | Qualitätsanalyse starten | Ingenieur | MUST | ✅ |
| UC-03 | Analysestatus betrachten | Ingenieur | MUST | ✅ |
| UC-04 | Analyseergebnis betrachten | Ingenieur | MUST | ✅ |
| UC-05 | Fehlgeschlagenen Algorithmus wiederholen | Ingenieur | MUST | ✅ |
| UC-06 | Ausfall eines Microservices behandeln | Ingenieur / Demo-Operator | MUST | ✅ |

---

## 5. Priorisierte Architecture Smells / Anti-Patterns

| Rang | Problem | Gegenmaßnahme | Im Projekt |
|---:|---|---|---|
| 1 | Isolation of Failures | Circuit Breaker mit Resilience4j | ✅ |
| 2 | Independent Deployability | Eigener Docker-Container pro Service | ✅ |
| 3 | Wrong Cut | Zerlegung nach fachlichen Analyseaufgaben | ✅ |
| 4 | Shared Persistence | Datenhoheit beim verantwortlichen Service | ✅ |

---

## 6. Optional / Nicht Teil des Kernprojekts

| Thema | Entscheidung |
|---|---|
| Kafka | COULD |
| Parallele Analyseausführung | COULD |
| Eigene Web-UI | COULD |
| erweitertes Monitoring | COULD |
| Camunda | WON'T |
| MCP | WON'T |
| Order Fulfillment | WON'T |
| Kubernetes | WON'T |
| Cloud Deployment | WON'T |
| echte physikalische Motorsimulation | WON'T |

---

## 7. Definition of Done

Der Prototyp gilt als fertig, wenn folgende Punkte demonstriert werden können:

- [x] System kann über Docker Compose gestartet werden
- [x] Engine-Konfiguration kann erstellt werden
- [x] gespeicherte Konfiguration kann abgerufen werden
- [x] Qualitätsanalyse kann gestartet werden
- [x] mindestens vier Analyse-Microservices werden verwendet
- [x] Status der Analysealgorithmen ist sichtbar
- [x] Einzelresultate sind sichtbar
- [x] Gesamtergebnis wird berechnet
- [x] ein Microservice kann gezielt gestoppt werden
- [x] Circuit Breaker behandelt den Ausfall
- [x] fehlgeschlagener Algorithmus kann erneut gestartet werden
- [x] Postman Collection ist vorhanden
- [x] Dockerfiles sind vorhanden
- [x] Docker-Compose-Konfiguration ist vorhanden
- [x] automatisierte Tests und E2E-Skript sind vorhanden
- [x] UML-/PlantUML-Architekturmodelle sind vorhanden
- [x] arc42-Dokumentation ist vorhanden

---

## 8. Legende

| Status | Bedeutung |
|---|---|
| ⬜ | Noch offen / optional nicht umgesetzt |
| ✅ | Fertig |
| ❌ | Blockiert / Problem |

### Prioritäten

| Priorität | Bedeutung |
|---|---|
| MUST | Für den finalen Prototyp notwendig |
| SHOULD | Sollte umgesetzt werden |
| COULD | Optional bei ausreichender Zeit |
| WON'T | Nicht Teil des aktuellen Projekts |
