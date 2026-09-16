# Weboberfläche

Die React-Oberfläche dient dem Ingenieur zur Konfiguration und Beobachtung. Nginx stellt die gebauten Dateien bereit und leitet API- und Monitoring-Aufrufe an die Backendservices weiter. Sie steuert keine Folgeschritte der Analyse.

## Konfiguration

Die fünf Felder betreffen Öl, Kraftstoff, Kühlung, Elektrik und Engine Management. Die Beispielauswahl stellt eine gültige Konfiguration und einen gezielten Ölfehler bereit. Beim Ölfehler bleibt Kraftstoff gültig, damit die getrennten Einzelergebnisse sichtbar werden.

Speichern liefert eine Konfigurations-ID. Laden übernimmt einen gespeicherten Stand. Jede Formularänderung entfernt die zuvor ausgewählte ID; ein ungespeicherter neuer Stand kann deshalb nicht versehentlich unter einer alten ID analysiert werden. Während Speicher-/Ladeaktionen bleibt der Start gesperrt. Fehler werden angezeigt.

## Analyseergebnisse

Die Oberfläche zeigt je Algorithmus Status, Resultat, Fehlermeldung und vorhandene Equipmentdetails. Ein Lauf ist nur insgesamt erfolgreich, wenn alle vier Analysen vollständig erfolgreich sind. Nach einem fachlichen Fehler bleiben bereits berechnete Einzelresultate sichtbar.

Ein technischer Fehler besitzt eine Erklärung, aber keine erfundenen fachlichen Teilresultate. Ältere gespeicherte Läufe können leere Details und eine fehlende Versuchkennung enthalten.

Fehlgeschlagene Schritte können über Retry erneut gestartet werden. Der Backendservice entscheidet, ob der Zustand einen Retry zulässt. Erfolgreiche Vorgänger bleiben erhalten; alte Rückmeldungen werden im Backend verworfen. Die Oberfläche verwirft auch veraltete Statusabfragen während eines Start-/Retry-Vorgangs.

## Erreichbarkeit und Circuit Breaker

Erreichbarkeit und Breaker-Zustand sind verschiedene Informationen:

- Ein gültiger Actuator-Response beweist, dass der angesprochene Service antwortet. Das gilt auch für HTTP 503 bei einem offenen Breaker.
- Proxyfehler wie HTTP 502/504 und HTML-Fehlerseiten gelten nicht als erfolgreicher Servicekontakt.
- Breaker werden anhand ihrer konkreten Instanznamen gelesen und der passenden Verbindung zugeordnet.

| Quelle | Ziel | Breaker |
|---|---|---|
| Analysis Management | Fluid | startFluid |
| Analysis Management | Thermal | startThermal |
| Analysis Management | Electrical | startElectrical |
| Analysis Management | Engine Management | startEngineManagement |
| Fluid | Thermal | nextService |
| Thermal | Electrical | nextService |
| Electrical | Engine Management | nextService |

Configuration und Engine Management besitzen keinen solchen ausgehenden Start-Breaker. Die Worker-Rückmeldungen verwenden begrenzte HTTP-Wiederholungen.

## Demo eines Ausfalls

Thermal stoppen und eine gültige Analyse starten. Fluid kann sein Ergebnis melden; die Weitergabe scheitert und öffnet seinen Breaker. Nach Wiederherstellung erlaubt ein Retry über Management die Fortsetzung ab Thermal.

Dieser Retry verwendet Management->Thermal. Der ursprüngliche Breaker Fluid->Thermal braucht einen eigenen erfolgreichen Probeaufruf: nach der Wartezeit eine neue Gesamtanalyse starten. Die Oberfläche erläutert diesen Unterschied.

## Entwicklung

```powershell
cd frontend
npm ci
npm run dev
```

Vite verwendet standardmäßig Port 5173 und leitet API-/Monitoring-Aufrufe an die lokalen Backendports weiter. Der normale Containerstart stellt die Oberfläche auf Port 3000 bereit.
