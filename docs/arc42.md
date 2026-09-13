# WirSchiffenDas – arc42 Architekturdokumentation

**Projekt:** Engine Quality Analysis  
**Modul:** Service-basierte Komponenten-Architekturen (SEKA)  
**Architekturstil:** Microservices  
**Dokumentationsform:** reduzierte arc42-Struktur für das Semesterprojekt

> Diese Dokumentation fasst den tatsächlich umgesetzten Proof-of-Concept zusammen. Sie basiert auf den Anforderungen der Übungsblätter 4, 5 und 6-1 sowie den finalen Hinweisen zum Semesterprojekt. Detailbeschreibungen befinden sich in `requirements.md`, `ddd.md`, `architecture.md` und `testing.md`.

---

## 1. Einführung und Ziele

### 1.1 Aufgabenstellung

Ausgangspunkt der Fallstudie ist eine problematische Analyse-Komponente für die Konfiguration einer Diesel Engine. Mehrere fachlich eigenständige Validierungs- und Simulationsalgorithmen waren ursprünglich stark gebündelt. Für den Proof-of-Concept wird dieser Bereich in eigenständig deploybare Microservices zerlegt.

Die neue Lösung soll eine Engine-Konfiguration persistent verwalten und anschließend eine Qualitätsanalyse ausführen. Die Analyse besteht aus mindestens vier fachlich getrennten Algorithmen, die choreographiert zusammenarbeiten. Während der Ausführung sollen Status und Ergebnisse nachvollziehbar sein. Ein fehlgeschlagener Algorithmus muss einzeln wiederholt werden können; der Ausfall eines Microservices darf nicht zum Ausfall des Gesamtsystems führen.

### 1.2 Architekturziele

Die wichtigsten Architekturziele sind:

| Ziel | Bedeutung für den PoC |
|---|---|
| **Fachliche Zerlegung** | Analysefunktionen werden nach Business Capabilities und nicht nach technischen Schichten getrennt. |
| **Lose Kopplung** | Microservices kommunizieren ausschließlich über definierte REST-Schnittstellen. |
| **Resilience** | Fehler einzelner Analyse-Services werden über Circuit Breaker kontrolliert behandelt. |
| **Independent Deployability** | Jeder Microservice läuft als eigener Docker-Container. |
| **Monitorability** | Status und Resultate jedes Analysealgorithmus sind über die Analyse-ID abrufbar. |
| **Responsiveness** | Das Starten einer Analyse blockiert nicht bis zum Ende aller Algorithmen. |
| **Testbarkeit** | Happy Path, Ausfall und Retry sind automatisiert und über Postman reproduzierbar. |

### 1.3 Wesentliche funktionale Anforderungen

Für den finalen Prototyp sind insbesondere folgende Anforderungen relevant:

- Engine-Konfiguration erfassen und persistent speichern,
- Analyse für eine bestehende Konfiguration starten,
- mindestens vier eigenständige Analyse-Microservices verwenden,
- Analyse choreographiert ausführen,
- Einzelresultate `OK` bzw. `FAILED` bereitstellen,
- Status `RUNNING`, `READY` und `FAILED` pro Algorithmus verwalten,
- Gesamtergebnis aus den Einzelresultaten bestimmen,
- Status und Resultate proaktiv an Analysis Management melden,
- einzelnen fehlgeschlagenen Algorithmus per Retry wiederholen,
- Ausfall eines Microservices über Circuit Breaker behandeln,
- REST-Schnittstellen verwenden und den PoC über Postman demonstrieren.

Die vollständige Traceability befindet sich in `requirements.md` und `ddd.md`.

---

## 2. Randbedingungen

### 2.1 Technische Randbedingungen

Der PoC verwendet folgende Technologien:

| Technologie | Verwendung |
|---|---|
| Java 21 | Implementierungssprache |
| Spring Boot | Basis aller sechs Microservices |
| REST/HTTP | Kommunikation zwischen den Services |
| H2 + Spring Data JPA | Persistenz in Configuration und Analysis Management |
| Resilience4j | Circuit-Breaker-Pattern |
| Docker | eigener Container pro Microservice |
| Docker Compose | Komposition und lokales Deployment |
| Postman | manuelle Bedienung und Prüfungsdemo |
| JUnit 5 / Mockito / AssertJ | automatisierte Tests |
| PlantUML | Dokumentation der Architektursichten |

Mit Docker/Docker Compose wird `MS_TA2` umgesetzt. Mit Resilience4j wird zusätzlich die für `MS_TA3` relevante Circuit-Breaker-Lösung realisiert.

### 2.2 Projektgrenzen

Bewusst **nicht** Bestandteil der Lösung sind:

- reale physikalische Motorsimulation,
- vollständige Migration der bestehenden Unternehmenssoftware,
- Kafka-basierte Kommunikation,
- Camunda oder MCP,
- Order Fulfillment,
- Kubernetes oder Cloud-Deployment,
- komplexe Web-Oberfläche.

Die Analysealgorithmen verwenden deshalb deterministische bzw. simulierte Prüfregeln. Für den PoC ist die Architektur und nicht die fachliche Genauigkeit eines realen Motormodells entscheidend.

---

## 3. Kontextabgrenzung

Der zentrale Benutzer ist der **Ingenieur** bzw. während der Demonstration der Demo-Operator. Er interagiert mit dem System über Postman.

Wesentliche Interaktionen:

```text
Ingenieur
   |
   +--> Engine-Konfiguration anlegen / lesen
   |
   +--> Qualitätsanalyse starten
   |
   +--> Status und Resultate abrufen
   |
   +--> fehlgeschlagenen Algorithmus wiederholen
```

Die vollständige Kontextsicht ist modelliert unter:

- `docs/diagrams/context.puml`

Damit wird die Systemgrenze bewusst eng um den PoC der Engine Quality Analysis gezogen. Externe Unternehmenssysteme wie SAP oder Oracle CRM sind nicht Bestandteil dieses Prototyps.

---

## 4. Lösungsstrategie und Domain-Driven Design

Die Anforderungen wurden mit Strategic Domain-Driven Design in fachliche Bounded Contexts überführt. Ziel war, das Anti-Pattern **Wrong Cut** zu vermeiden und Services nicht nach technischen Layern zu schneiden.

### 4.1 Bounded Contexts

| Bounded Context | Microservice | Verantwortung |
|---|---|---|
| Configuration Management | `configuration-service` | Engine-Konfiguration anlegen, validieren, speichern und lesen |
| Analysis Management | `analysis-management-service` | AnalysisRun, Status, Resultate, Overall Result und Retry verwalten |
| Fluid Analysis | `fluid-analysis-service` | Oil-/Fuel-bezogene Konfiguration analysieren |
| Thermal Analysis | `thermal-analysis-service` | thermischen Teil simuliert analysieren |
| Electrical Analysis | `electrical-analysis-service` | elektrischen Teil simuliert analysieren |
| Engine Management Analysis | `engine-management-analysis-service` | Engine Management unter Einbezug vorheriger Ergebnisse analysieren |

Die vier Analyse-Services bilden die geforderten eigenständigen Analysealgorithmen. `Fluid Analysis` und `Engine Management Analysis` sind direkt aus der Aufgabenstellung motiviert; `Thermal` und `Electrical` sind die für den PoC gewählte fachliche Clusterung.

### 4.2 Datenhoheit

Es existiert kein gemeinsames Domain-Modul und keine gemeinsame Datenbank für alle Services.

```text
configuration-service
        |
        +--> Configuration DB

analysis-management-service
        |
        +--> Analysis DB

vier Analysis Services
        |
        +--> stateless
```

Integration erfolgt über DTOs. Externe Daten werden an einer Context-Grenze in das lokale Modell übersetzt. Dadurch bleiben die Bounded Contexts voneinander getrennt und Shared Persistence wird vermieden.

Die vollständige DDD-Beschreibung befindet sich in `docs/ddd.md`.

---

## 5. Bausteinsicht

Die Architektur besteht aus sechs Spring-Boot-Microservices.

```text
                         Ingenieur / Postman
                          /              \
                         v                v
             configuration-service   analysis-management-service
                     |                       |
                     v                       v
            Configuration DB             Analysis DB
                                             |
                                             v
                                      fluid-analysis
                                             |
                                             v
                                     thermal-analysis
                                             |
                                             v
                                    electrical-analysis
                                             |
                                             v
                              engine-management-analysis
```

Alle vier Analyse-Services melden Status und Resultate proaktiv an `analysis-management-service` zurück.

Die vollständige Bausteinsicht liegt unter:

- `docs/diagrams/building-blocks.puml`

### 5.1 Externe Schnittstellen

```http
POST /api/configurations
GET  /api/configurations/{configurationId}
POST /api/analyses
GET  /api/analyses/{analysisId}
POST /api/analyses/{analysisId}/algorithms/{algorithm}/retry
```

### 5.2 Interne Schnittstellen

Jeder Analyse-Service akzeptiert einen Analyseauftrag über:

```http
POST /internal/analyses
```

Status und Resultate werden an Analysis Management gemeldet:

```http
PUT /internal/analyses/{analysisId}/algorithms/{algorithm}/status
PUT /internal/analyses/{analysisId}/algorithms/{algorithm}/result
```

Der Integrationsvertrag enthält `analysisId`, die benötigte Konfiguration und bereits vorhandene Ergebnisse. Er ist ein REST-Vertrag und kein gemeinsames Domain-Modell.

---

## 6. Laufzeitsicht

### 6.1 Erfolgreiche Analyse

Version 1 verwendet eine sequenzielle **Choreographie**.

```text
Client
  |
  | POST /api/analyses
  v
Analysis Management
  |
  | startet nur Anchor FLUID
  v
Fluid
  |
  | READY / OK + Callback
  v
Thermal
  |
  | READY / OK + Callback
  v
Electrical
  |
  | READY / OK + Callback
  v
Engine Management
  |
  | READY / OK + Callback
  v
Analysis Management
  |
  +--> overallResult = OK
```

`analysis-management-service` ist dabei kein zentraler Orchestrator. Nach dem Start des Anchor-Algorithmus gibt jeder erfolgreiche Analyse-Service die Verarbeitung selbst an den nächsten Service weiter. Die eigentliche Algorithmusausführung erfolgt asynchron, sodass der Start-Request mit `202 Accepted` beantwortet werden kann.

### 6.2 Serviceausfall und Retry

Der wichtigste Fehlerfall des PoC ist ein nicht erreichbarer `thermal-analysis-service`.

```text
Fluid --REST--> Thermal [DOWN]
        |
        v
 Circuit Breaker / Fallback
        |
        +--> THERMAL = FAILED
        +--> overallResult = FAILED
```

Nach Neustart von Thermal kann ausschließlich dieser Algorithmus wiederholt werden. Bereits erfolgreiche Vorgänger werden nicht erneut ausgeführt. Bei erfolgreichem Retry läuft die Choreographie ab Thermal weiter über Electrical bis Engine Management.

Die vollständige Laufzeitsicht mit Happy Path und Fehlerpfad befindet sich unter:

- `docs/diagrams/runtime.puml`

---

## 7. Verteilungssicht

Jeder Microservice wird als eigenständiger Docker-Container ausgeführt.

```text
Docker Host / wirschiffendas-network
|
|-- configuration-service               :8081
|-- analysis-management-service          :8082
|-- fluid-analysis-service               :8083
|-- thermal-analysis-service             :8084
|-- electrical-analysis-service          :8085
`-- engine-management-analysis-service   :8086
```

Für die zustandsbehafteten Services existieren getrennte persistente Volumes:

- `configuration-data`
- `analysis-data`

Die Container kommunizieren über die Service-Namen des Docker-Compose-Netzwerks. Dadurch sind keine fest verdrahteten IP-Adressen notwendig.

Das Repository enthält zwei Deployment-Varianten:

1. `docker-compose.yml` – Verwendung der veröffentlichten Docker-Hub-Images.
2. `alternative_docker-compose.yml` – lokaler Build aus dem aktuellen Source Code.

Die vollständige Verteilungssicht befindet sich unter:

- `docs/diagrams/deployment.puml`

---

## 8. Querschnittliche Konzepte

### 8.1 Status- und Ergebnismodell

Für jeden Algorithmus werden Status und Resultat zentral im `AnalysisRun` verwaltet.

Status:

```text
PENDING -> RUNNING -> READY
                  \-> FAILED
```

Resultate:

```text
OK | FAILED
```

`PENDING` ist eine projektspezifische Ergänzung für noch nicht gestartete Schritte. Das Gesamtergebnis ist:

- `FAILED`, sobald ein Algorithmus fehlgeschlagen ist,
- `null`, solange noch kein Fehler vorliegt, aber Schritte offen sind,
- `OK`, sobald alle vier Algorithmen `READY/OK` sind.

### 8.2 Resilience

Ausgehende Analyse-Service-Aufrufe werden mit Resilience4j-Circuit-Breakern abgesichert. Dadurch führt ein nicht erreichbarer Service nicht zu einer unkontrollierten Fehlerkaskade. Der betroffene Algorithmus wird stattdessen sichtbar als `FAILED` markiert und kann später gezielt wiederholt werden.

### 8.3 Persistenz

Nur zwei Services besitzen langfristigen Zustand:

- Configuration Management besitzt die Engine-Konfigurationen.
- Analysis Management besitzt Analysezustände und Resultate.

Die Analyse-Services bleiben stateless. Dadurch kann ein einzelner Analyse-Service unabhängig gestartet oder gestoppt werden.

---

## 9. Zentrale Entwurfsentscheidungen

### ADR-01 – Fachlicher Schnitt statt technischer Layer

**Entscheidung:** Microservices werden nach Business Capabilities getrennt.  
**Begründung:** Vermeidung von `Wrong Cut`; fachliche Verantwortung und Deployment-Einheit bleiben zusammen.

### ADR-02 – REST für die Kommunikation

**Entscheidung:** Version 1 verwendet synchrone REST-Kommunikation.  
**Begründung:** Für den kleinen PoC einfach nachvollziehbar, testbar und mit den Aufgabenstellungen kompatibel. Kafka bleibt bewusst außerhalb des Kernumfangs.

### ADR-03 – Choreographie statt zentraler Orchestration

**Entscheidung:** Analysis Management startet nur den Anchor-Algorithmus; danach gibt jeder Analyse-Service die Verarbeitung weiter.  
**Begründung:** Die Aufgabe soll explizit die Zusammenarbeit choreographierter Microservices demonstrieren.

### ADR-04 – Circuit Breaker mit Resilience4j

**Entscheidung:** Service-zu-Service-Aufrufe werden durch Circuit Breaker geschützt.  
**Begründung:** Umsetzung von `Isolation of Failures`; der Ausfall eines einzelnen Services bleibt lokal behandelbar.

### ADR-05 – Container pro Microservice

**Entscheidung:** Jeder Service besitzt ein eigenes Dockerfile und einen eigenen Container.  
**Begründung:** Unterstützt Independent Deployability und erfüllt Docker/Docker-Compose als technische Semesterprojektanforderung.

### ADR-06 – Keine Shared Persistence

**Entscheidung:** Configuration und Analysis Management besitzen getrennte Datenhoheit; die Analyse-Services sind stateless.  
**Begründung:** Reduziert Kopplung zwischen Bounded Contexts und vermeidet eine gemeinsam genutzte Datenbank als Integrationsmechanismus.

---

## 10. Qualitätsanforderungen und Tests

Die vier im Projekt priorisierten Architecture Smells bzw. Anti-Patterns sind:

| Rang | Problem | Lösung im PoC |
|---:|---|---|
| 1 | Isolation of Failures | Resilience4j Circuit Breaker |
| 2 | Independent Deployability | eigener Docker-Container pro Service |
| 3 | Wrong Cut | fachliche Zerlegung nach Analysefähigkeiten |
| 4 | Shared Persistence | getrennte Datenhoheit, stateless Analyse-Services |

Damit fließen mehr als die geforderten zwei priorisierten Lösungen in Konzept bzw. Prototyp ein.

### 10.1 Teststrategie

Die Qualität wird auf mehreren Ebenen überprüft:

| Ebene | Beispiel |
|---|---|
| Unit Test | `AnalysisRunTest` für Status-, Resultat- und Overall-Result-Logik |
| Service Test | `ConfigurationApplicationServiceTest` für Erzeugung und Speicherung |
| End-to-End | `scripts/e2e.sh` gegen alle sechs Docker-Container |
| Manuelle Demo | Postman Collection |

Der automatisierte End-to-End-Test deckt zwei zentrale Szenarien ab:

1. vollständiger Happy Path bis `overallResult = OK`,
2. Ausfall von Thermal, sichtbares `FAILED`, Neustart, Retry und Fortsetzung bis `OK`.

Weitere Details befinden sich in `docs/testing.md`.

---

## 11. Risiken, technische Schulden und Lessons Learned

### 11.1 Aktuelle Restriktionen

Der PoC ist bewusst klein gehalten. Daraus ergeben sich folgende Grenzen:

- REST-Choreographie erzeugt direkte Laufzeitabhängigkeiten zwischen aufeinanderfolgenden Services.
- Fällt Analysis Management aus, können Status-/Result-Callbacks nicht persistiert werden; für den PoC existiert hierfür keine Message Queue.
- H2 eignet sich für den lokalen Demonstrator, ist aber keine Entscheidung für einen produktiven Einsatz.
- Es existieren keine Authentifizierung und Autorisierung, da Security nicht Teil des vereinbarten Projektumfangs ist.
- Service Discovery, verteiltes Tracing und erweitertes Monitoring wurden nicht umgesetzt.
- Die fachlichen Analysealgorithmen sind Simulationen und besitzen keine reale physikalische Aussagekraft.

### 11.2 Lessons Learned

Die wichtigste Erkenntnis ist, dass eine Microservice-Zerlegung nicht allein durch das Erzeugen vieler Services entsteht. Entscheidend sind fachliche Grenzen, eigene Datenhoheit und klar definierte Schnittstellen. DDD war deshalb hilfreich, um die Analyse nach fachlichen Fähigkeiten statt nach technischen Layern zu strukturieren.

Choreographie reduziert die zentrale Ablaufsteuerung, verschiebt aber Verantwortung in die beteiligten Services und macht Fehlerbehandlung wichtiger. Der Circuit-Breaker- und Retry-Fall zeigt, dass Resilience und beobachtbarer Zustand bei verteilten Systemen von Beginn an berücksichtigt werden müssen.

Docker Compose erleichtert die reproduzierbare Demonstration und macht Independent Deployability sichtbar. Gleichzeitig zeigt der PoC, dass Containerisierung allein noch keine vollständige Produktionsplattform mit Service Discovery, Security oder Observability ersetzt.

---

## 12. Glossar und Referenzen

| Begriff | Bedeutung |
|---|---|
| AnalysisRun | ein vollständiger Analyse-Durchlauf für eine Konfiguration |
| Anchor Algorithm | erster Algorithmus, der die Choreographie startet |
| Choreographie | dezentrale Ablaufweitergabe zwischen Services |
| Circuit Breaker | Pattern zur kontrollierten Behandlung nicht erreichbarer Services |
| Bounded Context | fachlich abgegrenzter Bereich mit eigenem Modell |
| Retry | erneute Ausführung eines fehlgeschlagenen Algorithmus |
| Overall Result | zusammengefasstes Ergebnis der vier Analysen |

### Projektinterne Referenzen

- `docs/requirements.md` – vollständige Requirements
- `docs/requirements_short.md` – kompakter Requirements-Überblick
- `docs/ddd.md` – Strategic DDD und Context Map
- `docs/architecture.md` – REST-Verträge und Architekturdetails
- `docs/architecture-views.md` – Erläuterung des 4-Sichten-Modells
- `docs/diagrams/context.puml` – Kontextsicht
- `docs/diagrams/building-blocks.puml` – Bausteinsicht
- `docs/diagrams/runtime.puml` – Laufzeitsicht
- `docs/diagrams/deployment.puml` – Verteilungssicht
- `docs/testing.md` – Teststrategie
- `postman/WirSchiffenDas.postman_collection.json` – Prüfungsdemo
- `scripts/e2e.sh` – automatisierter Systemtest

### Quellenbasis der Aufgabenstellung

- SEKA, Übungsblatt Nr. 4 – Microservice-Zielarchitektur, 4-Sichten-Modell, DDD/Context Map, Architecture Smells
- SEKA, Übungsblatt Nr. 5 – Choreographie der Analyse-Microservices, Status, Resultate, Retry, Circuit Breaker und REST
- SEKA, Übungsblatt Nr. 6, Aufgabe 1 – technische Anforderungen Docker/Docker Compose bzw. Resilience sowie priorisierte Architecture Smells
- SEKA, Infos zum Semesterprojekt / Übung Nr. 8 – reduzierte arc42-Dokumentation mit wesentlichen Architekturmodellen und Entwurfsentscheidungen
