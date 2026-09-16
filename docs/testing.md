# Tests und Demonstration

Die Tests prüfen die Ergebnisregeln sowie Fehler an den Grenzen zwischen Services. Ein erfolgreicher Build allein belegt keine zuverlässige verteilte Verarbeitung.

## Ausgeführter Stand vom 16. September 2026

Die folgenden Ergebnisse stammen aus dem endgültigen lokalen Stand auf Branch `devTheo`:

| Prüfung | Ergebnis |
|---|---|
| Java-21-Gesamtbuild mit `mvn verify` | 7/7 Maven-Module erfolgreich; 142 Tests bestanden; 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| Lokaler Compose-Aufbau mit `docker compose up --build -d --wait` | 7/7 Images gebaut; alle 7 Container gestartet und `healthy` |
| Kurzer PowerShell-E2E-Test | 11/11 Prüfungen bestanden |
| Vollständiger E2E-Störungstest mit `-IncludeFaults -IncludeWorkerLoss` | 44/44 Prüfungen bestanden |
| Frontend-Healthtests | 6/6 Tests bestanden |
| Frontend-Produktionsbuild | Erfolgreich in der reproduzierbaren Node-22-Docker-Umgebung |
| Manuelle Kontrolle der laufenden Weboberfläche | 6/6 Services `REACHABLE`; 7/7 Circuit Breaker `CLOSED`; keine Browserwarnungen oder Browserfehler |

Die E2E-Läufe deckten Erfolgsfall, fachlich ungültiges Equipment, Thermal-Ausfall, getrennte Circuit Breaker, Wiederherstellung, Retry, eine veraltete Rückmeldung und Worker-Verlust mit Inaktivitäts-Timeout ab. Die lokale globale npm-Installation war nicht verwendbar; deshalb erfolgten Frontend-Test und Build in der vom Projekt festgelegten Node-22-Docker-Umgebung.

## Backend

Mit Java 21 und Maven im Projektordner:

```powershell
mvn verify
```

Abgedeckte Bereiche:

- Vollständige Equipmentresultate und konsistente Status-/Ergebniskombinationen.
- Gesamt-OK nur bei vier vollständigen erfolgreichen Analysen.
- Genau die benötigten erfolgreichen Vorgänger für Engine Management.
- Atomarer Retry bei konkurrierenden Anfragen; alte und doppelte Rückmeldungen ändern keinen abgeschlossenen oder neueren Versuch.
- Rückmeldungen während des Startaufrufs und ein verlorener HTTP-Start-Response.
- Persistierte Inaktivitätsfrist; Timeout und anschließender Retry.
- Zielbezogene Circuit Breaker im Management.
- Worker-Duplikaterkennung sowie begrenzte Callback-Wiederholung bei vorübergehenden Fehlern.
- Klare Ablehnung eines Folgeschritts im Unterschied zu unklarer Annahme bei Antwortverlust oder HTTP 5xx.

Die Management-Integrationstests verwenden Spring und eine separate H2-Testdatenbank. HTTP-Clients werden für kontrollierte Störfälle ersetzt. Worker-Clienttests kontrollieren HTTP-Anfragen und Fehlertypen. Diese 142 bestandenen Backendtests ergänzen die unten dokumentierten echten Containerabläufe.

## Frontend

Node.js 22.18+ oder 24:

```powershell
cd frontend
npm ci
npm test
npm run build
```

Die sechs bestandenen Health-Tests unterscheiden echte Actuator-Antworten von Proxyfehlern und ordnen die Breaker ihren tatsächlichen Verbindungen zu. Der erfolgreiche Produktionsbuild prüft TypeScript und erstellt die Oberfläche aus dem Lockfile.

## Systemtest mit PowerShell

Zuerst im Projektordner starten:

```powershell
docker compose up --build -d --wait
.\scripts\e2e.ps1
```

Der kurze Test bestand mit 11 von 11 Prüfungen. Er erstellt und lädt eine Konfiguration, prüft einen vollständigen Lauf und zeigt einen negativen Ölcheck mit weiterhin positivem Kraftstoffcheck.

Der vollständige Störungstest:

```powershell
.\scripts\e2e.ps1 -IncludeFaults -IncludeWorkerLoss
```

Er stoppt bzw. beendet gezielt Thermal und stellt den Container im `finally`-Block wieder her. Er wird deshalb auf dem lokalen Demo-System ausgeführt, wenn keine andere Analyse gleichzeitig wichtig ist.

Der vollständige Lauf bestand mit 44 von 44 Prüfungen.

| Szenario | Erwartung |
|---|---|
| Gültige Konfiguration | Vier READY/OK, fünf sichtbare Equipmentresultate |
| Öl INVALID, Kraftstoff gültig | Öl FAILED, Kraftstoff OK, Gesamt FAILED |
| Thermal nicht erreichbar | Betroffene Stufe FAILED; Fluid->Thermal-Breaker OPEN |
| Thermal-Retry bei weiter bestehendem Ausfall | Management->Thermal OPEN; Management->Fluid weiter CLOSED |
| Gültiger neuer Start während Thermal-Ausfall | Fluid kann weiterhin ausgeführt werden |
| Thermal wieder erreichbar und Retry | Erfolgreiche Vorgänger behalten ihre Versuchkennung; Ziel und Nachfolger bekommen eine neue |
| Alte Thermal-Rückmeldung nach erfolgreichem Retry | Erfolgreiches Ergebnis bleibt unverändert |
| Neuer Gesamtlauf nach Breaker-Wartezeit | Probe auf Fluid->Thermal schließt auch den ursprünglichen Breaker |
| Thermal-Prozess während RUNNING beendet | Nach Inaktivitätsfrist FAILED mit Timeoutgrund; Retry nach Neustart erfolgreich |

Standardwartefrist des Tests: 60 Sekunden; über `-TimeoutSeconds` anpassbar. Mit `-ProjectName` lässt sich eine explizite Compose-Projektinstanz auswählen. Ohne diesen Parameter wird die normale Projektinstanz verwendet.

Das ältere `scripts/e2e.sh` bleibt ein zusätzlicher kurzer Happy-Path-/Ausfalltest für Umgebungen mit Bash, curl und jq. Der PowerShell-Test ist der ausführlichere aktuelle Prüfpfad.

## Manuelle Oberfläche

- Gültige und negative Demokonfiguration auswählen, speichern und starten.
- Nach Formularänderungen muss die bisherige Konfiguration abgewählt sein; Start ist erst nach erfolgreichem Speichern wieder möglich.
- Fehler beim Speichern, Laden, Starten und Retry müssen als Meldung erscheinen.
- Während Start/Retry darf eine verspätete ältere Statusabfrage den neuen Vorgang nicht zurücksetzen.
- Equipmentdetails und technische Fehlermeldungen unterscheiden.
- Thermal stoppen: Ziel unerreichbar; Fluid selbst weiterhin erreichbar, auch wenn dessen globales Actuator-Health wegen des offenen Breakers HTTP 503 liefert.
- Management-Retry und Probeaufruf auf der ursprünglichen Fluid-Verbindung getrennt beobachten.

## Persistenz und bestehende Daten

Nach einem normalen Neustart mit erhaltenen Volumes muss eine gespeicherte Konfiguration wieder lesbar sein. Daten aus einem älteren Projektstand bleiben erhalten. Alte fertige Läufe besitzen noch keine Equipmentdetails und können eine leere Versuchkennung enthalten. Alte offene Läufe werden mit einem Hinweis auf einen neu anzulegenden Lauf abgeschlossen; vorhandene Resultate werden nicht nachträglich erfunden.

## Grenzen des Nachweises

Der Test ist kein Last-, Sicherheits- oder Mehrinstanznachweis. Worker-Deduplikation ist lokal und zeitlich begrenzt; Callbacks besitzen keine dauerhafte Outbox. Ein Neustart kann deshalb Arbeit verlieren. Der Management-Timeout macht den Verlust sichtbar und ermöglicht einen bewussten neuen Versuch, ersetzt aber keine garantierte Wiederzustellung.
