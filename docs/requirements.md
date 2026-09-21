# WirSchiffenDas – Requirements Specification

## 1. Überblick

### 1.1 Projekt

**Projektname:** WirSchiffenDas – Engine Quality Analysis  
**Thema:** Microservice-basierte Qualitätsanalyse von Schiffsmotor-Konfigurationen  
**Projektart:** Semesterprojekt im Modul SEKA

### 1.2 Ziel

Ziel des Projekts ist die prototypische Entwicklung einer
Microservice-basierten Analyse-Komponente für die Qualitätskontrolle
einer Diesel-Engine-Konfiguration.

Die bestehende Analyse soll in mehrere fachlich abgegrenzte
Analyse-Microservices zerlegt werden.

Die Microservices sollen:

- unabhängig voneinander ausführbar sein,
- über REST-Schnittstellen kommunizieren,
- choreographiert zusammenarbeiten,
- ihren aktuellen Bearbeitungsstatus melden,
- Ergebnisse bereitstellen und
- Fehler einzelner Services tolerieren können.

Es wird kein reales physikalisches Modell eines Schiffsmotors implementiert.
Die einzelnen Analysealgorithmen werden für den Proof-of-Concept simuliert.


---

# 2. Prioritäten

Für die Anforderungen wird eine vereinfachte MoSCoW-Priorisierung verwendet.

| Priorität | Bedeutung |
|---|---|
| MUST | Muss im finalen Prototyp vorhanden sein |
| SHOULD | Sollte umgesetzt werden, falls zeitlich möglich |
| COULD | Optionale Erweiterung |
| WON'T | Wird im aktuellen Projekt nicht umgesetzt |


---

# 3. Projektumfang

## 3.1 In Scope

Der Prototyp umfasst:

- Erfassung einer Diesel-Engine-Konfiguration
- persistente Speicherung der Konfiguration
- Start einer Qualitätsanalyse
- mindestens vier Analyse-Microservices
- Choreographie der Analyse-Microservices
- REST-Kommunikation
- Bearbeitungsstatus der einzelnen Analysen
- Einzelresultate der Analysen
- Gesamtergebnis einer Analyse
- Retry eines fehlgeschlagenen Algorithmus
- Behandlung eines nicht erreichbaren Microservices
- Circuit Breaker
- Docker und Docker Compose
- Test/Demonstration über Postman
- Architekturmodellierung für arc42


## 3.2 Out of Scope

Folgende Punkte werden im aktuellen Projekt nicht umgesetzt:

- reale physikalische Simulation eines Schiffsmotors
- vollständige Migration der bestehenden Unternehmenssoftware
- Order-Fulfillment-Prozess
- Camunda
- MCP
- Kubernetes
- Cloud-Deployment
- vollständige SAP-/Oracle-Integration
- komplexe grafische Benutzeroberfläche

Apache Kafka ist zunächst nicht Teil des Pflichtumfangs und kann bei
ausreichender Zeit als Erweiterung ergänzt werden.


---

# 4. Stakeholder

## ST-01 – Ingenieur

Der Ingenieur konfiguriert eine Diesel Engine und startet die Qualitätsanalyse.

Er möchte:

- eine Konfiguration anlegen,
- eine Analyse starten,
- den aktuellen Status sehen,
- Ergebnisse betrachten und
- fehlgeschlagene Analysen erneut starten können.


## ST-02 – Entwicklungsteam

Das Entwicklungsteam entwickelt und wartet die einzelnen Microservices.

Wichtig sind insbesondere:

- verständliche Service-Grenzen,
- geringe Kopplung,
- unabhängiges Deployment und
- nachvollziehbare Schnittstellen.


## ST-03 – Geschäftsleitung

Die Geschäftsleitung erwartet einen Proof-of-Concept, der zeigt,
wie die bisher komplexe Analyse-Komponente sinnvoll durch
Microservices aufgeteilt werden kann.


---

# 5. Funktionale Anforderungen

## FR-01 – Konfiguration anlegen

**Priorität:** MUST

Das System muss die Konfiguration des Optional Equipments einer
Diesel Engine erfassen können.

Für den Proof-of-Concept dürfen vereinfachte bzw. simulierte
Konfigurationswerte verwendet werden. Der Prototyp verwendet dafür die
kontrollierten Varianten `STANDARD`, `PREMIUM`, `ADVANCED` und den
bewussten Demonstrationswert `INVALID`.

`INVALID` ist syntaktisch eine erlaubte Konfigurationsvariante und darf
persistiert werden. Fachlich ist sie jedoch ungültig: Der jeweils zuständige
Analyse-Service muss den Wert erkennen, den Algorithmus mit `FAILED`
beenden und darf die Choreographie nicht zum nächsten Analyse-Service
fortsetzen. Beliebige Werte außerhalb dieses Katalogs werden bereits beim
Anlegen der Konfiguration abgelehnt.

### Akzeptanzkriterien

- Eine Konfiguration kann nur mit `STANDARD`, `PREMIUM`, `ADVANCED` oder `INVALID` angelegt werden.
- Jede Konfiguration erhält eine eindeutige ID.
- Werte außerhalb des definierten Variantenkatalogs werden abgelehnt.
- `INVALID` bleibt für den Fehlerfall speicherbar und wird erst prozessbedingt in der Analyse als `FAILED` bewertet.


---

## FR-02 – Konfiguration speichern

**Priorität:** MUST

Eine angelegte Engine-Konfiguration muss persistent gespeichert werden.

### Akzeptanzkriterien

- Eine gespeicherte Konfiguration besitzt eine eindeutige ID.
- Die Konfiguration kann später erneut über ihre ID abgerufen werden.


---

## FR-03 – Analyse starten

**Priorität:** MUST

Für eine gespeicherte Konfiguration muss eine Qualitätsanalyse gestartet
werden können.

Der Start der Analyse stößt einen ersten Analysealgorithmus
("Anker-Algorithmus") an.

### Akzeptanzkriterien

- Eine Analyse kann für eine existierende Konfiguration gestartet werden.
- Die Analyse erhält eine eindeutige ID.
- Der Benutzer muss nicht warten, bis alle Analysealgorithmen beendet sind.


---

## FR-04 – Analyse auf mehrere Microservices verteilen

**Priorität:** MUST

Die Qualitätsanalyse muss auf mindestens vier eigenständige
Analyse-Microservices aufgeteilt werden.

Die Microservices sollen nach fachlichen Analyseaufgaben geschnitten werden.

Eine mögliche Aufteilung ist:

- Fluid Analysis
- Thermal Analysis
- Electrical Analysis
- Engine Management Analysis

Die endgültige fachliche Aufteilung wird während des Architekturentwurfs
festgelegt.

### Akzeptanzkriterien

- Mindestens vier getrennte Analyse-Microservices existieren.
- Jeder Microservice besitzt eine eigene Analyseaufgabe.
- Jeder Microservice kann unabhängig ausgeführt werden.


---

## FR-05 – Choreographie durchführen

**Priorität:** MUST

Die Analyse-Microservices müssen im Rahmen einer Choreographie
zusammenarbeiten.

Für die erste Version wird eine sequenzielle Choreographie verwendet.

Beispiel:

Fluid Analysis
    ->
Thermal Analysis
    ->
Electrical Analysis
    ->
Engine Management Analysis

Nach erfolgreicher Bearbeitung stößt ein Microservice den nächsten
Analyseschritt an.

### Akzeptanzkriterien

- Die gesamte Analyse kann nach einem einzelnen Startsignal durchlaufen.
- Die einzelnen Analyse-Microservices arbeiten ohne manuelle
  Zwischeninteraktion zusammen.


---

## FR-06 – Analyseergebnisse erzeugen

**Priorität:** MUST

Jeder Analyse-Microservice muss nach Beendigung seiner Analyse ein
Ergebnis bereitstellen.

Zulässige Ergebniswerte sind mindestens:

- OK
- FAILED

### Akzeptanzkriterien

- Für jeden ausgeführten Algorithmus wird ein Ergebnis gespeichert.
- Das Ergebnis kann über die Analyse-ID abgefragt werden.


---

## FR-07 – Gesamtergebnis bestimmen

**Priorität:** MUST

Das System muss aus den Einzelresultaten ein Gesamtergebnis bilden.

Für den Proof-of-Concept gilt:

- alle relevanten Ergebnisse `OK` -> Gesamtergebnis `OK`
- mindestens ein relevantes Ergebnis `FAILED` -> Gesamtergebnis `FAILED`

### Akzeptanzkriterien

- Einzelresultate bleiben sichtbar.
- Nach Abschluss der Analyse existiert ein Gesamtergebnis.


---

## FR-08 – Bearbeitungsstatus anzeigen

**Priorität:** MUST

Für jeden Analysealgorithmus muss der aktuelle Bearbeitungsstatus
gespeichert und abrufbar sein.

Mindestens folgende Statuswerte werden verwendet:

- RUNNING
- READY
- FAILED

Optional kann zusätzlich der Zustand `PENDING` verwendet werden.

### Akzeptanzkriterien

Während einer Analyse kann für jeden Algorithmus nachvollzogen werden,
ob dieser:

- noch nicht gestartet,
- aktuell in Bearbeitung,
- erfolgreich beendet oder
- fehlgeschlagen ist.


---

## FR-09 – Status und Resultate proaktiv melden

**Priorität:** MUST

Die Analyse-Microservices müssen Statusänderungen und Ergebnisse
selbstständig an die zuständige Verwaltungs-Komponente melden.

### Akzeptanzkriterien

- Ein Analyse-Service meldet den Start seiner Analyse.
- Nach Beendigung meldet er seinen Status und sein Ergebnis.
- Die Verwaltungs-Komponente muss nicht dauerhaft nach dem Ergebnis pollen.


---

## FR-10 – Fehlgeschlagenen Algorithmus erneut starten

**Priorität:** MUST

Ein fehlgeschlagener Analysealgorithmus muss einzeln erneut gestartet
werden können.

### Akzeptanzkriterien

Gegeben:

    Algorithmus = FAILED

Wenn ein Retry ausgeführt wird:

    Algorithmus = RUNNING

Nach erfolgreicher Wiederholung:

    Algorithmus = READY
    Result = OK

Bereits erfolgreiche Algorithmen müssen dabei nicht erneut gestartet werden.


---

## FR-11 – Nicht erreichbaren Microservice behandeln

**Priorität:** MUST

Das System muss den Ausfall eines Analyse-Microservices behandeln können.

Hierfür soll das Circuit-Breaker-Pattern eingesetzt werden.

### Akzeptanzkriterien

Wenn ein Analyse-Microservice nicht erreichbar ist:

- bleibt das restliche System erreichbar,
- der Fehler wird erkannt,
- der betreffende Analyseschritt wird als fehlgeschlagen markiert,
- es entsteht kein unkontrollierter Fehler über mehrere Services hinweg,
- nach Wiederherstellung des Services ist ein Retry möglich.


---

## FR-12 – REST-Schnittstellen

**Priorität:** MUST

Die Microservices müssen über definierte REST-Schnittstellen miteinander
kommunizieren.

### Akzeptanzkriterien

- Jeder relevante Microservice stellt REST-Endpunkte bereit.
- Requests und Responses verwenden definierte Datenstrukturen.
- Ein Microservice greift nicht direkt auf interne Klassen eines anderen
  Microservices zu.


---

## FR-13 – Bedienung und Demonstration

**Priorität:** MUST

Der Proof-of-Concept muss ohne komplexe grafische Oberfläche bedienbar
und demonstrierbar sein.

Hierfür wird Postman verwendet.

### Akzeptanzkriterien

Über Postman können mindestens folgende Aktionen ausgeführt werden:

1. Konfiguration erstellen
2. Konfiguration abrufen
3. Analyse starten
4. Analysestatus abrufen
5. Analyseergebnis abrufen
6. fehlgeschlagenen Algorithmus erneut starten


---

# 6. Qualitätsanforderungen

## QR-01 – Resilience

**Priorität:** MUST

Der Ausfall eines einzelnen Microservices darf nicht zum vollständigen
Ausfall des Systems führen.

### Umsetzung

Circuit Breaker mit Resilience4j.

### Akzeptanzkriterium

Wird ein Analyse-Service während der Demonstration gestoppt,
bleiben die übrigen Services erreichbar.


---

## QR-02 – Monitorability

**Priorität:** MUST

Der Zustand einer laufenden Analyse muss nachvollziehbar sein.

### Akzeptanzkriterium

Für jede Analyse können Status und Ergebnis der einzelnen
Analysealgorithmen abgefragt werden.


---

## QR-03 – Independent Deployability

**Priorität:** MUST

Jeder Microservice soll unabhängig bereitgestellt und ausgeführt werden können.

### Umsetzung

Jeder Microservice erhält einen eigenen Docker-Container.

### Akzeptanzkriterium

Ein einzelner Analyse-Service kann gestartet oder gestoppt werden,
ohne dass alle anderen Services ebenfalls beendet werden müssen.


---

## QR-04 – Lose Kopplung

**Priorität:** MUST

Microservices sollen ausschließlich über klar definierte Schnittstellen
miteinander kommunizieren.

### Akzeptanzkriterien

Nicht erlaubt:

- direkter Zugriff auf interne Klassen anderer Microservices
- direkte gemeinsame Nutzung interner Implementierungsdetails

Erlaubt:

- REST-Kommunikation über definierte Schnittstellen


---

## QR-05 – Datenhoheit

**Priorität:** SHOULD

Analyse-Microservices sollen nicht direkt auf gemeinsam genutzte
Datenbanktabellen zugreifen.

Benötigte Daten werden über definierte Schnittstellen übertragen.

### Ziel

Vermeidung von Shared Persistence und unnötiger Kopplung.


---

## QR-06 – Verständliche Service-Grenzen

**Priorität:** MUST

Microservices sollen nach fachlichen Aufgaben und nicht nach
technischen Schichten aufgeteilt werden.

Beispiel für eine nicht gewünschte Aufteilung:

- Controller Service
- Business Service
- Database Service

Beispiel für eine gewünschte Aufteilung:

- Fluid Analysis
- Electrical Analysis
- Engine Management Analysis

### Ziel

Vermeidung des Anti-Patterns "Wrong Cut".


---

## QR-07 – Testbarkeit

**Priorität:** SHOULD

Die wichtigsten Erfolgs- und Fehlerszenarien müssen reproduzierbar
getestet werden können.

### Mindestszenarien

- erfolgreiche Analyse
- fehlgeschlagene Analyse
- nicht erreichbarer Microservice
- erfolgreicher Retry

Für End-to-End-Tests kann die Postman Collection verwendet werden.


---

## QR-08 – Responsiveness

**Priorität:** MUST

Eine laufende Analyse darf die restliche Anwendung nicht blockieren.

### Akzeptanzkriterium

Während ein Analysealgorithmus simuliert längere Zeit arbeitet,
kann der aktuelle Status weiterhin abgefragt werden.


---

# 7. Technische Anforderungen

## TA-01 – Spring Boot

**Priorität:** MUST

Die Microservices werden als Spring-Boot-Anwendungen implementiert.


## TA-02 – Docker

**Priorität:** MUST

Jeder Microservice wird in einem eigenen Docker-Container betrieben.


## TA-03 – Docker Compose

**Priorität:** MUST

Das Gesamtsystem muss über Docker Compose gestartet werden können.

Ziel:

    docker compose up --build


## TA-04 – Resilience4j

**Priorität:** MUST

Für die Behandlung nicht erreichbarer Microservices wird
Resilience4j zur Umsetzung des Circuit-Breaker-Patterns verwendet.


## TA-05 – Apache Kafka

**Priorität:** COULD

Kafka kann optional für die Übertragung von Statusnachrichten eingesetzt
werden.

Kafka gehört nicht zum notwendigen Funktionsumfang der ersten Version.


---

# 8. Use Cases

## UC-01 – Engine-Konfiguration erstellen

**Akteur:** Ingenieur

### Vorbedingung

Das System ist gestartet.

### Hauptablauf

1. Der Ingenieur übermittelt eine Engine-Konfiguration.
2. Das System validiert die Eingabe.
3. Das System speichert die Konfiguration.
4. Das System erzeugt eine Konfigurations-ID.

### Nachbedingung

Die Konfiguration ist gespeichert und kann erneut abgerufen werden.


---

## UC-02 – Qualitätsanalyse starten

**Akteur:** Ingenieur

### Vorbedingung

Eine gültige Engine-Konfiguration existiert.

### Hauptablauf

1. Der Ingenieur startet eine Analyse.
2. Das System erzeugt eine Analyse-ID.
3. Der erste Analyse-Microservice wird gestartet.
4. Die Choreographie beginnt.

### Nachbedingung

Die Analyse befindet sich in Bearbeitung.


---

## UC-03 – Analysestatus betrachten

**Akteur:** Ingenieur

### Vorbedingung

Eine Analyse wurde gestartet.

### Hauptablauf

1. Der Ingenieur fragt die Analyse über ihre ID ab.
2. Das System liefert die Zustände der Analysealgorithmen.
3. Bereits vorhandene Ergebnisse werden ebenfalls angezeigt.

### Beispiel

    Fluid Analysis:       READY    -> OK
    Thermal Analysis:     RUNNING  -> -
    Electrical Analysis:  PENDING  -> -
    Engine Management:    PENDING  -> -


---

## UC-04 – Analyseergebnis betrachten

**Akteur:** Ingenieur

### Vorbedingung

Eine Analyse wurde ausgeführt.

### Hauptablauf

1. Der Ingenieur fragt die Analyse ab.
2. Das System zeigt alle Einzelresultate.
3. Das System zeigt das Gesamtergebnis.

### Nachbedingung

Der Benutzer kann erkennen, welche Analyse erfolgreich oder
fehlgeschlagen ist.


---

## UC-05 – Fehlgeschlagenen Algorithmus wiederholen

**Akteur:** Ingenieur

### Vorbedingung

Mindestens ein Algorithmus besitzt den Status `FAILED`.

### Hauptablauf

1. Der Ingenieur startet einen Retry für den fehlgeschlagenen Algorithmus.
2. Der Status wechselt auf `RUNNING`.
3. Der Algorithmus wird erneut ausgeführt.
4. Das neue Ergebnis wird gespeichert.

### Nachbedingung

Der Algorithmus besitzt ein aktualisiertes Ergebnis.


---

## UC-06 – Ausfall eines Microservices behandeln

**Akteur:** Ingenieur / Demo-Operator

### Vorbedingung

Ein Analyse-Microservice ist nicht erreichbar.

### Hauptablauf

1. Ein anderer Microservice versucht den Service aufzurufen.
2. Der Aufruf schlägt fehl.
3. Der Circuit Breaker behandelt den Fehler.
4. Der Analyseschritt wird als fehlgeschlagen markiert.
5. Das restliche System bleibt erreichbar.

### Nachbedingung

Nach Wiederherstellung des Services kann der betroffene Algorithmus
erneut gestartet werden.


---

# 9. Architecture Smells und Anti-Patterns

Im Projekt werden insbesondere folgende vier Probleme berücksichtigt.

## 9.1 Isolation of Failures

**Problem:**  
Der Ausfall eines Microservices kann Fehler in weiteren Services verursachen.

**Lösung:**  
Circuit Breaker mit Resilience4j.

**Priorität:** 1


## 9.2 Independent Deployability

**Problem:**  
Microservices dürfen nicht gemeinsam deployt werden müssen.

**Lösung:**  
Ein eigener Docker-Container je Microservice.

**Priorität:** 2


## 9.3 Wrong Cut

**Problem:**  
Eine Aufteilung der Microservices nach technischen Schichten führt zu
unnötiger Kopplung.

**Lösung:**  
Aufteilung nach fachlichen Analyseaufgaben bzw. Business Capabilities.

**Priorität:** 3


## 9.4 Shared Persistence

**Problem:**  
Mehrere Microservices greifen direkt auf dieselben Datenstrukturen einer
gemeinsamen Datenbank zu.

**Lösung:**  
Die Datenhoheit liegt bei dem jeweils verantwortlichen Service.
Andere Services erhalten benötigte Informationen über Schnittstellen.

**Priorität:** 4


---

# 10. Zusammenfassung der Prioritäten

## MUST

- Engine-Konfiguration erfassen
- Engine-Konfiguration speichern
- Analyse starten
- mindestens vier Analyse-Microservices
- Choreographie
- Einzelresultate
- Gesamtergebnis
- Statusverwaltung
- proaktive Status-/Ergebnismeldung
- Retry
- Behandlung eines Serviceausfalls
- REST-Kommunikation
- Postman-Demonstration
- Circuit Breaker
- Docker
- Docker Compose
- fachliche Service-Grenzen
- lose Kopplung
- Responsiveness


## SHOULD

- keine Shared Persistence
- automatisierte Tests der wichtigsten Szenarien


## COULD

- Apache Kafka für Statusnachrichten
- parallele Ausführung unabhängiger Analysealgorithmen
- eigene grafische Benutzeroberfläche
- erweitertes Monitoring


## WON'T

Im aktuellen Projekt nicht vorgesehen:

- Camunda
- MCP
- Order Fulfillment
- Kubernetes
- Cloud Deployment
- vollständige Integration bestehender Unternehmenssysteme
- echte physikalische Motoranalyse


---

# 11. Definition of Done

Der Prototyp gilt als funktional fertig, wenn folgende Demonstration möglich ist:

1. Gesamtsystem mit Docker Compose starten.
2. Engine-Konfiguration über Postman anlegen.
3. Gespeicherte Konfiguration abrufen.
4. Analyse starten.
5. Status der Analyse-Microservices beobachten.
6. Mindestens vier Analysealgorithmen ausführen.
7. Einzelresultate anzeigen.
8. Gesamtergebnis anzeigen.
9. Einen Analyse-Microservice stoppen.
10. Circuit-Breaker-Verhalten demonstrieren.
11. Fehlgeschlagenen Algorithmus erkennen.
12. Service wieder starten.
13. Retry durchführen.
14. Analyse erfolgreich abschließen.

Zusätzlich müssen vorhanden sein:

- Source Code
- Dockerfiles
- docker-compose.yml
- Postman Collection
- README
- UML-Architekturmodelle
- arc42-Dokumentation


---

# 12. Offene Architekturentscheidungen

Folgende Punkte werden bewusst erst während des Architekturentwurfs entschieden:

- genaue fachliche Aufteilung der vier Analyse-Microservices
- konkrete REST-Endpunkte
- interne Klassen- und Package-Struktur
- Datenbanktechnologie
- konkrete Persistenzstruktur
- Portnummern der Microservices
- konkrete Resilience4j-Konfiguration
- genaue Ablaufreihenfolge der Choreographie
- mögliche parallele Ausführung einzelner Analysealgorithmen

Diese Entscheidungen werden später als Architektur- bzw. Entwurfsentscheidungen
in der arc42-Dokumentation festgehalten.