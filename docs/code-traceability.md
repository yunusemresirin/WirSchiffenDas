# WirSchiffenDas – Code-Traceability (TA, ADR, FR, QR)

Diese Datei verknüpft die in `requirements.md` und `arc42.md` beschriebenen
**technischen Anforderungen (TA)**, **Architecture Decision Records (ADR)**,
**funktionalen Anforderungen (FR)** und **Qualitätsanforderungen (QR)** mit
konkreten Code-Stellen im Repository.

Ziel: In der Präsentation kann zu jeder Anforderung ein Snippet gezeigt und der
Zusammenhang erklärt werden.

> **Hinweis zu Zeilennummern:** Die Angaben beziehen sich auf den Stand dieses
> Dokuments. Bei Änderungen am Code können sie sich verschieben – dann über die
> genannte Datei und den Suchbegriff (Symbol) navigieren.

---

## 1. Technische Anforderungen (TA)

### TA-01 – Spring Boot

**Anforderung:** Alle Microservices sind Spring-Boot-Anwendungen.

**Nachweis:** Jeder Service besitzt eine `@SpringBootApplication`-Klasse.

| Datei | Zeilen | Symbol |
|---|---|---|
| `services/fluid-analysis-service/src/main/java/de/hbrs/seka/wirschiffendas/fluid/FluidAnalysisServiceApplication.java` | 1–11 | `@SpringBootApplication`, `@EnableAsync` |
| `services/analysis-management-service/.../AnalysisManagementServiceApplication.java` | 1–11 | `@SpringBootApplication` |
| `services/configuration-service/.../ConfigurationServiceApplication.java` | 1–11 | `@SpringBootApplication` |

```java
@EnableAsync
@SpringBootApplication
public class FluidAnalysisServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FluidAnalysisServiceApplication.class, args);
    }
}
```

**Erläuterung:** `@SpringBootApplication` aktiviert Auto-Configuration, Component
Scan und die eingebettete Servlet-Engine. `@EnableAsync` ist die Grundlage für
die asynchrone Algorithmusausführung (siehe QR-08).

**Abhängigkeit:** `services/fluid-analysis-service/pom.xml` Zeile 7
(`spring-boot-starter-web`).

---

### TA-02 – Docker

**Anforderung:** Jeder Microservice läuft in einem eigenen Docker-Container.

**Nachweis:** Jeder Service besitzt ein eigenes `Dockerfile` (Multi-Stage-Build).

| Datei | Zeilen |
|---|---|
| `services/fluid-analysis-service/Dockerfile` | 1–11 |
| `services/thermal-analysis-service/Dockerfile` | 1–11 |
| `services/electrical-analysis-service/Dockerfile` | 1–11 |
| `services/engine-management-analysis-service/Dockerfile` | 1–11 |
| `services/analysis-management-service/Dockerfile` | 1–11 |
| `services/configuration-service/Dockerfile` | 1–11 |

```dockerfile
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services ./services
RUN mvn -q -pl services/fluid-analysis-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/fluid-analysis-service/target/fluid-analysis-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**Erläuterung:** Der Multi-Stage-Build trennt Build-Umgebung (Maven + JDK) vom
schlanken Runtime-Image (nur JRE). Jeder Service erhält ein eigenes Image und
einen eigenen Port → Grundlage für Independent Deployability (QR-03, ADR-05).

---

### TA-03 – Docker Compose

**Anforderung:** Das Gesamtsystem startet über `docker compose up --build`.

**Nachweis:** `docker-compose.yml` (Root) und `alternative_docker-compose.yml`.

```yaml
services:
  configuration-service:
    image: ysirin2s/seka-wirschiffendas:configuration-service-v${VERSION}
    ports:
      - "8081:8081"
    volumes:
      - configuration-data:/app/data

  analysis-management-service:
    image: ysirin2s/seka-wirschiffendas:analysis-management-service-v${VERSION}
    ports:
      - "8082:8082"
    environment:
      CONFIGURATION_SERVICE_URL: http://configuration-service:8081
      FLUID_ANALYSIS_SERVICE_URL: http://fluid-analysis-service:8083
      ...
    volumes:
      - analysis-data:/app/data
```

**Erläuterung:** Compose definiert sechs Backend-Services und web-ui, ihre Ports, Umgebungs-
variablen (Service-URLs) und getrennte Volumes. Die Services finden sich über
ihre **Service-Namen** im gemeinsamen Netzwerk `wirschiffendas-network` – keine
fest verdrahteten IPs. `depends_on` steuert die Startreihenfolge.

**Präsentations-Tipp:** `docker compose up --build` live zeigen, dann
`docker compose ps` für die sechs laufenden Container.

---

### TA-04 – Resilience4j (Circuit Breaker)

**Anforderung:** Nicht erreichbare Microservices werden über das
Circuit-Breaker-Pattern behandelt.

**Nachweis (3 Ebenen):**

**a) Zielservice-spezifischer Breaker (Analysis Management → Analyse-Service):**

`services/analysis-management-service/.../infrastructure/AnalysisServiceStarter.java`

```java
public void start(AlgorithmName algorithm, AnalysisCommand command) {
    CircuitBreaker circuitBreaker = circuitBreakers.get(algorithm);
    RestClient client = clients.get(algorithm);

    circuitBreaker.executeRunnable(() ->
            client.post()
                    .uri("/internal/analyses")
                    .body(command)
                    .retrieve()
                    .toBodilessEntity());
}
```

Analysis Management verwendet getrennte Breaker für `FLUID`, `THERMAL`,
`ELECTRICAL` und `ENGINE_MANAGEMENT`. Dadurch kann ein fehlgeschlagener
Retry zu einem Ziel nicht den Breaker eines anderen Zielservice öffnen oder schließen.

**b) Annotation + Fallback (Analyse-Service → nächster Service):**

`services/fluid-analysis-service/.../infrastructure/NextServiceClient.java` Zeilen 23–33

```java
@CircuitBreaker(name = "nextService", fallbackMethod = "fallback")
public void startNext(AnalysisCommand command, String currentResult) {
    var results = new ArrayList<>(command.previousResults());
    results.add(Map.of("algorithm", "FLUID", "result", currentResult));
    AnalysisCommand nextCommand = new AnalysisCommand(command.analysisId(), command.configuration(), results);
    client.post().uri("/internal/analyses").body(nextCommand).retrieve().toBodilessEntity();
}

private void fallback(AnalysisCommand command, String currentResult, Throwable throwable) {
    managementClient.reportStatus(command.analysisId(), "THERMAL", "FAILED", "thermal-analysis-service unavailable");
}
```

**c) Konfiguration:**

`services/fluid-analysis-service/src/main/resources/application.yml` (Abschnitt `resilience4j`)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      nextService:
        sliding-window-size: 2
        minimum-number-of-calls: 1
        permitted-number-of-calls-in-half-open-state: 1
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        automatic-transition-from-open-to-half-open-enabled: true
        register-health-indicator: true
```

**Erläuterung:** Der Circuit Breaker fängt Verbindungsfehler ab. Statt einer
unkontrollierten Fehlerkaskade wird der **Fallback** ausgeführt, der den
betroffenen Algorithmus als `FAILED` markiert. Der aufrufende Service bleibt
erreichbar. Nach `wait-duration-in-open-state` wechselt der Breaker automatisch
in den Half-Open-Zustand → ein Retry ist möglich.

**Abhängigkeit:** `services/fluid-analysis-service/pom.xml` Zeile 11
(`resilience4j-spring-boot3`).

**Präsentations-Tipp:** `docker compose stop thermal-analysis-service`, Analyse
starten und `THERMAL = FAILED` mit `OPEN` zeigen. Danach Thermal wieder starten;
die Recovery schließt den passenden Breaker und setzt den Lauf automatisch fort.

---

### TA-05 – Apache Kafka (COULD – bewusst nicht umgesetzt)

**Anforderung:** Kafka ist optional und nicht Teil des Pflichtumfangs.

**Nachweis:** Kein Kafka im Code. Die Begründung steht in `arc42.md` §2.2
(Projektgrenzen) und ADR-02. Statusmeldungen laufen stattdessen synchron per
REST-Callback (siehe FR-09).

---

## 2. Architecture Decision Records (ADR)

### ADR-01 – Fachlicher Schnitt statt technischer Layer

**Entscheidung:** Services werden nach Business Capabilities geschnitten.

**Nachweis:** Jeder Service hat eine **eigene fachliche Domäne** und intern die
gleiche saubere Schichtung `api / application / domain / infrastructure`.

```text
services/
├── configuration-service/          → Engine-Konfiguration
├── analysis-management-service/    → AnalysisRun, Status, Resultate
├── fluid-analysis-service/         → Oil-/Fuel-Analyse
├── thermal-analysis-service/       → thermische Analyse
├── electrical-analysis-service/    → elektrische Analyse
└── engine-management-analysis-service/ → Engine-Management-Analyse
```

**Beispielhafte Paketstruktur** (`fluid-analysis-service`):

```text
fluid/
├── api/             AnalysisController, AnalysisCommand
├── application/     AnalysisWorker
├── infrastructure/  NextServiceClient, AnalysisManagementClient
└── FluidAnalysisServiceApplication.java
```

**Erläuterung:** Es gibt keinen technischen „Controller-Service“ oder
„Database-Service“. Jeder Service kapselt eine fachliche Analyseaufgabe →
Vermeidung des Anti-Patterns **Wrong Cut** (QR-06).

---

### ADR-02 – REST für die Kommunikation

**Entscheidung:** Version 1 nutzt synchrone REST-Kommunikation.

**Nachweis:**

- Externe Endpunkte: `services/analysis-management-service/.../api/AnalysisController.java`
- Interne Endpunkte: `services/fluid-analysis-service/.../api/AnalysisController.java`
- Ausgehende Aufrufe: `RestClient` in `AnalysisServiceStarter`, `NextServiceClient`, `ConfigurationServiceClient`

```java
@RestController
@RequestMapping("/internal/analyses")
public class AnalysisController {

    private final AnalysisWorker worker;

    @PostMapping
    public ResponseEntity<Void> start(@RequestBody AnalysisCommand command) {
        worker.execute(command);
        return ResponseEntity.accepted().build();
    }
}
```

**Erläuterung:** Alle Services kommunizieren ausschließlich über HTTP/REST mit
definierten DTOs. Kein Service greift auf interne Klassen eines anderen zu →
lose Kopplung (QR-04).

---

### ADR-03 – Choreographie statt zentraler Orchestration

**Entscheidung:** Analysis Management startet nur den Anchor-Algorithmus
`FLUID`; danach gibt jeder Service selbst weiter.

**Nachweis (Start nur des Anchors):**

`services/analysis-management-service/.../application/AnalysisApplicationService.java` Zeilen 29–48

```java
public AnalysisRun start(String configurationId) {
    ConfigurationSnapshot configuration = configurationClient.get(configurationId);
    AnalysisRun run = AnalysisRun.start("A-" + UUID.randomUUID(), configurationId);
    AlgorithmExecution fluid = run.execution(AlgorithmName.FLUID);
    fluid.updateStatus(AnalysisStatus.RUNNING, null);

    // Repository transaction is committed before the external HTTP call.
    // This avoids a race where an asynchronous callback arrives before AnalysisRun exists.
    run = repository.saveAndFlush(run);

    try {
        serviceStarter.start(
                AlgorithmName.FLUID,
                new AnalysisCommand(run.getAnalysisId(), configuration, List.of()));
    } catch (RuntimeException exception) {
        ...
    }
    return run;
}
```

**Nachweis (Weitergabe an den nächsten Service):**

`services/fluid-analysis-service/.../application/AnalysisWorker.java` Zeilen 23–40

```java
@Async
public void execute(AnalysisCommand command) {
    managementClient.reportStatus(command.analysisId(), ALGORITHM, "RUNNING", null);
    if (!pause(command.analysisId())) return;

    boolean ok = valid(command.configuration().get("oilSystem"))
            && valid(command.configuration().get("fuelSystem"));
    String result = ok ? "OK" : "FAILED";
    managementClient.reportResult(command.analysisId(), ALGORITHM, ok ? "READY" : "FAILED", result, null);

    if (ok) {
        nextServiceClient.startNext(command, result);
    }
}
```

**Erläuterung:** Analysis Management kennt nur den ersten Schritt. Jeder
erfolgreiche Service ruft selbst den nächsten auf (`startNext`) und reicht die
bisherigen Ergebnisse im `AnalysisCommand` mit. Das ist echte Choreographie –
kein zentraler Orchestrator.

---

### ADR-04 – Circuit Breaker mit Resilience4j

**Entscheidung:** Service-zu-Service-Aufrufe werden durch Circuit Breaker
geschützt.

**Nachweis:** Identisch zu **TA-04** (siehe oben). Zentrale Stellen:

- `AnalysisServiceStarter.java` Zeile 31 (`@CircuitBreaker(name = "analysisServiceStarter")`)
- `NextServiceClient.java` Zeile 23 (`@CircuitBreaker(name = "nextService", fallbackMethod = "fallback")`)
- `application.yml` (Resilience4j-Instanzen)

**Erläuterung:** Umsetzung von **Isolation of Failures**. Der Fehler bleibt lokal
im aufrufenden Service behandelbar.

---

### ADR-05 – Container pro Microservice

**Entscheidung:** Jeder Service besitzt ein eigenes Dockerfile und einen eigenen
Container.

**Nachweis:** Identisch zu **TA-02** und **TA-03**. Sechs Dockerfiles + Compose
mit sechs Backend-Services (8081–8086) und web-ui (Host 3000 → Container 80).

**Erläuterung:** Unterstützt Independent Deployability (QR-03). Ein einzelner
Container kann gestoppt/gestartet werden, ohne die anderen zu beeinflussen.

---

### ADR-06 – Keine Shared Persistence

**Entscheidung:** Configuration und Analysis Management besitzen getrennte
Datenhoheit; Analyse-Services sind stateless.

**Nachweis (getrennte Entities/Datenbanken):**

- `services/configuration-service/.../domain/EngineConfiguration.java` (`@Entity`, Tabelle `engine_configurations`)
- `services/analysis-management-service/.../domain/AnalysisRun.java` (`@Entity`, Tabelle `analysis_runs`)

```java
@Entity
@Table(name = "engine_configurations")
public class EngineConfiguration {
    @Id
    private String configurationId;
    private String oilSystem;
    private String fuelSystem;
    ...
}
```

**Nachweis (Analyse-Services ohne Datenbank):**
`services/fluid-analysis-service/src/main/resources/application.yml` enthält
**keinen** `spring.datasource`-Block → stateless.

**Nachweis (Integration über DTO statt gemeinsamer DB):**

`services/analysis-management-service/.../infrastructure/ConfigurationSnapshot.java`

```java
public record ConfigurationSnapshot(
        String configurationId,
        String oilSystem,
        String fuelSystem,
        String coolingSystem,
        String electricalSystem,
        String engineManagementSystem) {
}
```

**Erläuterung:** Externe Daten werden an der Context-Grenze in ein lokales DTO
(`ConfigurationSnapshot`) übersetzt. Kein Service liest fremde Tabellen →
Vermeidung von **Shared Persistence** (QR-05).

---

## 3. Funktionale Anforderungen (FR) – Kurz-Mapping

| FR | Anforderung | Code-Nachweis |
|---|---|---|
| FR-01 | Konfiguration anlegen | `ConfigurationController.create` + `CreateConfigurationRequest` (`@NotBlank`) |
| FR-02 | Konfiguration speichern | `ConfigurationApplicationService.create` (`repository.save`), `EngineConfiguration` (`@Entity`) |
| FR-03 | Analyse starten | `AnalysisApplicationService.start`, `AnalysisController` (`ResponseEntity.accepted()`) |
| FR-04 | Vier Analyse-Services | `AlgorithmName` (FLUID, THERMAL, ELECTRICAL, ENGINE_MANAGEMENT) + vier Service-Module |
| FR-05 | Choreographie | `AnalysisWorker.execute` → `NextServiceClient.startNext` |
| FR-06 | Ergebnisse erzeugen | `AnalysisWorker.reportResult` (`OK`/`FAILED`) |
| FR-07 | Gesamtergebnis | `AnalysisRun.recalculateOverallResult` |
| FR-08 | Bearbeitungsstatus | `AlgorithmExecution.updateStatus` (`PENDING/RUNNING/READY/FAILED`) |
| FR-09 | Proaktive Meldung | `AnalysisManagementClient.reportStatus` / `reportResult` |
| FR-10 | Retry | `AnalysisApplicationService.retry` |
| FR-11 | Nicht erreichbarer Service | `NextServiceClient.fallback` + Circuit Breaker |
| FR-12 | REST-Schnittstellen | Alle `*Controller` + `RestClient`-Clients |
| FR-13 | Postman-Demo | `postman/WirSchaffenDas.postman_collection.json` |

### FR-07 – Gesamtergebnis (Detail)

`services/analysis-management-service/.../domain/AnalysisRun.java` Zeilen 45–58

```java
public void recalculateOverallResult() {
    boolean failed = executions.stream()
            .anyMatch(e -> e.getStatus() == AnalysisStatus.FAILED || e.getResult() == AnalysisResult.FAILED);

    if (failed) {
        overallResult = AnalysisResult.FAILED;
        return;
    }

    boolean unfinished = executions.stream()
            .anyMatch(e -> e.getStatus() == AnalysisStatus.PENDING || e.getStatus() == AnalysisStatus.RUNNING);

    overallResult = unfinished ? null : AnalysisResult.OK;
}
```

**Erläuterung:** Genau die in `architecture.md` §8 definierte Regel:
`FAILED` sobald ein Fehler vorliegt, `null` solange Schritte offen sind, `OK`
wenn alle vier fertig sind.

### FR-10 – Retry (Detail)

`services/analysis-management-service/.../application/AnalysisApplicationService.java` Zeilen 79–112

```java
public AnalysisRun retry(String analysisId, AlgorithmName algorithm) {
    AnalysisRun run = find(analysisId);
    AlgorithmExecution execution = run.execution(algorithm);

    if (execution.getStatus() != AnalysisStatus.FAILED) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed algorithms can be retried");
    }

    ConfigurationSnapshot configuration = configurationClient.get(run.getConfigurationId());

    // A retry gets only successful results from predecessor algorithms.
    // The failed result of the retried algorithm itself must not be propagated.
    List<PreviousResult> previousResults = run.getExecutions().stream()
            .filter(item -> item.getResult() != null)
            .filter(item -> item.getAlgorithm().ordinal() < algorithm.ordinal())
            .map(item -> new PreviousResult(item.getAlgorithm(), item.getResult()))
            .toList();

    execution.updateStatus(AnalysisStatus.RUNNING, null);
    run.recalculateOverallResult();
    run = repository.saveAndFlush(run);

    try {
        serviceStarter.start(algorithm, new AnalysisCommand(run.getAnalysisId(), configuration, previousResults));
    } catch (RuntimeException exception) {
        ...
    }
    return run;
}
```

**Erläuterung:** Retry nur bei `FAILED` (sonst `409 CONFLICT`). Es werden nur
Ergebnisse **vorheriger** Algorithmen mitgegeben – bereits erfolgreiche
Vorgänger laufen nicht erneut. Das entspricht `architecture.md` §6.

---

## 4. Qualitätsanforderungen (QR) – Kurz-Mapping

| QR | Anforderung | Code-Nachweis |
|---|---|---|
| QR-01 | Resilience | Circuit Breaker (TA-04 / ADR-04) |
| QR-02 | Monitorability | `AnalysisResponse` (Status + Resultat pro Algorithmus) |
| QR-03 | Independent Deployability | Dockerfile pro Service + Compose |
| QR-04 | Lose Kopplung | `RestClient` + DTOs, keine geteilten Klassen |
| QR-05 | Datenhoheit | `ConfigurationSnapshot`, getrennte Entities |
| QR-06 | Verständliche Service-Grenzen | fachliche Module + `api/application/domain/infrastructure` |
| QR-07 | Testbarkeit | `AnalysisRunTest`, `scripts/e2e.sh`, Postman |
| QR-08 | Responsiveness | `@Async` + `ResponseEntity.accepted()` (202) |

### QR-02 – Monitorability (Detail)

`services/analysis-management-service/.../api/AnalysisResponse.java`

```java
public record AnalysisResponse(
        String analysisId,
        String configurationId,
        AnalysisResult overallResult,
        List<AlgorithmResponse> algorithms) {

    public static AnalysisResponse from(AnalysisRun run) {
        return new AnalysisResponse(
                run.getAnalysisId(),
                run.getConfigurationId(),
                run.getOverallResult(),
                run.getExecutions().stream().map(AlgorithmResponse::from).toList());
    }

    public record AlgorithmResponse(
            AlgorithmName algorithm,
            AnalysisStatus status,
            AnalysisResult result,
            String message) { ... }
}
```

**Erläuterung:** `GET /api/analyses/{analysisId}` liefert pro Algorithmus
Status, Resultat und Meldung → der Analysezustand ist vollständig
nachvollziehbar.

### QR-08 – Responsiveness (Detail)

`services/fluid-analysis-service/.../api/AnalysisController.java` Zeilen 18–21

```java
@PostMapping
public ResponseEntity<Void> start(@RequestBody AnalysisCommand command) {
    worker.execute(command);
    return ResponseEntity.accepted().build();
}
```

**Erläuterung:** Der Controller antwortet sofort mit `202 Accepted`, während
`worker.execute` dank `@Async` im Hintergrund läuft. Der Client blockiert nicht
bis zum Ende der Analyse.

### QR-07 – Testbarkeit (Detail)

**Unit-Test:** `services/analysis-management-service/src/test/java/.../domain/AnalysisRunTest.java`

```java
@Test
void failedAlgorithmMakesOverallResultFailed() {
    AnalysisRun run = AnalysisRun.start("A-1", "C-1");
    run.execution(AlgorithmName.THERMAL)
            .updateStatus(AnalysisStatus.FAILED, "service unavailable");

    run.recalculateOverallResult();

    assertThat(run.getOverallResult()).isEqualTo(AnalysisResult.FAILED);
}
```

**End-to-End:** `scripts/e2e.sh` deckt Happy Path und Thermal-Ausfall + Retry ab.

---

## 5. Vorschlag für die Präsentations-Reihenfolge

1. **Überblick** – `docker-compose.yml` zeigen, `docker compose up --build`.
2. **TA-01/ADR-01** – `@SpringBootApplication` + fachliche Modulstruktur.
3. **FR-01/02** – Konfiguration anlegen (`ConfigurationController`, `EngineConfiguration`).
4. **FR-03/ADR-03** – Analyse starten, nur Anchor `FLUID` (`AnalysisApplicationService.start`).
5. **FR-05/ADR-03** – Choreographie (`AnalysisWorker` → `NextServiceClient`).
6. **FR-07/08/09** – Status & Gesamtergebnis (`AnalysisRun`, `AnalysisResponse`).
7. **TA-04/ADR-04/FR-11** – Circuit Breaker live: Thermal stoppen → `FAILED`.
8. **FR-10** – Retry (`AnalysisApplicationService.retry`), Thermal starten → `OK`.
9. **TA-02/03/ADR-05** – Docker/Compose, Independent Deployability.
10. **ADR-06/QR-05** – Datenhoheit (`ConfigurationSnapshot`, stateless Services).
11. **QR-07** – Tests (`AnalysisRunTest`, `e2e.sh`, Postman).
