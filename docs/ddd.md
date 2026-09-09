# WirSchiffenDas – Domain-Driven Design

## 1. Ziel

Dieses Dokument leitet aus der bestehenden `requirements.md` das fachliche Domänenmodell und die Bounded Contexts für den Proof-of-Concept **Engine Quality Analysis** ab. Die Anforderungen werden nicht erweitert, sondern in fachlich sinnvolle Service-Grenzen überführt.

## 2. Ubiquitous Language

| Begriff | Bedeutung |
|---|---|
| Engine Configuration | Konfiguration einer Diesel Engine einschließlich Optional Equipment |
| Optional Equipment | Konfigurierbarer Bestandteil der Engine |
| Analysis | Vollständiger Qualitätsanalyse-Durchlauf |
| Analysis ID | Eindeutige Identifikation eines Analyse-Durchlaufs |
| Algorithm | Fachlich abgegrenzte Validierungs-/Simulationslogik |
| Analysis Result | Ergebnis eines Algorithmus (`OK` oder `FAILED`) |
| Overall Result | Zusammengefasstes Ergebnis des Analyse-Durchlaufs |
| Status | Bearbeitungszustand eines Algorithmus |
| Anchor Algorithm | Erster Algorithmus der Choreographie |
| Retry | Erneute Ausführung eines fehlgeschlagenen Algorithmus |
| Choreography | Dezentrale Weitergabe des Analyseablaufs zwischen Analyse-Services |

Statuswerte: `PENDING`, `RUNNING`, `READY`, `FAILED`.

## 3. Bounded Contexts

| ID | Bounded Context | Verantwortung | Microservice |
|---|---|---|---|
| BC-01 | Configuration Management | Engine-Konfiguration verwalten | `configuration-service` |
| BC-02 | Analysis Management | Analyse-Durchlauf, Status, Resultate und Retry verwalten | `analysis-management-service` |
| BC-03 | Fluid Analysis | Fluid-bezogene Konfiguration analysieren | `fluid-analysis-service` |
| BC-04 | Thermal Analysis | Thermische Konfiguration analysieren | `thermal-analysis-service` |
| BC-05 | Electrical Analysis | Elektrische Konfiguration analysieren | `electrical-analysis-service` |
| BC-06 | Engine Management Analysis | Engine Management unter Einbezug vorheriger Resultate analysieren | `engine-management-analysis-service` |

`Fluid Analysis` und `Engine Management Analysis` sind direkt aus Übungsblatt 5 motiviert. `Thermal Analysis` und `Electrical Analysis` bilden die für diesen PoC gewählte fachliche Clusterung.

## 4. Configuration Management

Der `configuration-service` besitzt die fachliche Hoheit über die Engine-Konfiguration.

Verantwortlichkeiten:

- Konfiguration anlegen
- Konfiguration validieren
- Konfiguration persistent speichern
- Konfiguration wieder bereitstellen

```text
EngineConfiguration  <<Aggregate Root>>
├── configurationId
└── optionalEquipment
    ├── equipmentType
    └── configuredValue
```

Nur dieser Service greift direkt auf seine Konfigurationsdaten zu.

## 5. Analysis Management

Der `analysis-management-service` verwaltet den Zustand eines Analyse-Durchlaufs.

Verantwortlichkeiten:

- `analysisId` erzeugen
- `configurationId` zuordnen
- Status und Resultate speichern
- Overall Result berechnen
- Analysefortschritt bereitstellen
- Retry anstoßen
- Anchor-Algorithmus starten

```text
AnalysisRun  <<Aggregate Root>>
├── analysisId
├── configurationId
├── executions[]
│   ├── algorithm
│   ├── status
│   └── result
└── overallResult
```

Wichtig: Dieser Service ist **kein zentraler Orchestrator**. Er startet nur den Anchor-Algorithmus. Die nachfolgenden Schritte werden von den Analyse-Services selbst weitergegeben.

## 6. Analyse-Bounded-Contexts

### Fluid Analysis

Validiert beispielhaft `Oil System` und `Fuel System`.

```text
FluidAnalysisInput -> Fluid Analysis -> FluidAnalysisResult
```

### Thermal Analysis

Validiert simuliert einen thermischen Teil der Engine-Konfiguration.

```text
ThermalAnalysisInput -> Thermal Analysis -> ThermalAnalysisResult
```

### Electrical Analysis

Validiert simuliert den elektrischen Bereich der Engine-Konfiguration.

```text
ElectricalAnalysisInput -> Electrical Analysis -> ElectricalAnalysisResult
```

### Engine Management Analysis

Berücksichtigt zusätzlich vorherige Analyseergebnisse.

```text
Engine Configuration + Previous Results
                 ↓
      Engine Management Analysis
                 ↓
               Result
```

## 7. Context Map

```text
Configuration Management
        │
        │ Configuration Data
        ▼
Analysis Management
        │
        │ Start Anchor
        ▼
Fluid Analysis
        │
        ▼
Thermal Analysis
        │
        ▼
Electrical Analysis
        │
        ▼
Engine Management Analysis

Alle Analyse-Services
        │
        │ Status / Result
        ▼
Analysis Management
```

An Context-Grenzen werden REST-DTOs in lokale Domänenobjekte übersetzt:

```text
External DTO -> Mapper / Adapter -> Local Domain Model
```

Damit bleibt jeder Bounded Context fachlich eigenständig.

## 8. Kein Shared Kernel

Es wird bewusst kein gemeinsames `common-domain`-Modul zwischen den Microservices eingeführt. Jeder Bounded Context besitzt sein eigenes kleines Domänenmodell. Gemeinsame Daten werden ausschließlich über Integrationsverträge ausgetauscht.

Das reduziert Kopplung und unterstützt Independent Deployability sowie die Vermeidung von Shared Persistence und Wrong Cut.

## 9. Datenhoheit

```text
configuration-service
        ↓
Configuration DB

analysis-management-service
        ↓
Analysis DB

fluid-analysis-service                 keine DB notwendig
thermal-analysis-service               keine DB notwendig
electrical-analysis-service            keine DB notwendig
engine-management-analysis-service     keine DB notwendig
```

Die vier Analyse-Services können damit stateless umgesetzt werden.

## 10. Architektur-Baseline

Für Version 1 gelten folgende sechs Services als festgelegte Basis:

```text
configuration-service
analysis-management-service
fluid-analysis-service
thermal-analysis-service
electrical-analysis-service
engine-management-analysis-service
```

Neue Services werden nur ergänzt, wenn eine bereits bestehende Anforderung dies notwendig macht.

## 11. Traceability zu den Requirements

| Requirement | Verantwortlicher Context |
|---|---|
| FR-01 Konfiguration erfassen | Configuration Management |
| FR-02 Konfiguration speichern | Configuration Management |
| FR-03 Analyse starten | Analysis Management |
| FR-04 mindestens vier Analyse-Microservices | Fluid, Thermal, Electrical, Engine Management |
| FR-05 Choreographie | vier Analysis Contexts |
| FR-06 Einzelresultate | jeweiliger Analysis Context |
| FR-07 Gesamtergebnis | Analysis Management |
| FR-08 Status | jeweiliger Analysis Context + Analysis Management |
| FR-09 proaktive Meldung | Analysis Context → Analysis Management |
| FR-10 Retry | Analysis Management + betroffener Analysis Context |
| FR-11 Serviceausfall | Service-zu-Service-Kommunikation / Circuit Breaker |
| FR-12 REST | alle Context-Grenzen |
| FR-13 Postman | äußere Schnittstelle des PoC |
