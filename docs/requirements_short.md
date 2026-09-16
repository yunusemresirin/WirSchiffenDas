# WirSchiffenDas – Anforderungen im Überblick

Stand: 16. September 2026. Details und Herkunft: [requirements.md](requirements.md). Tatsächlich ausgeführte Prüfungen: [testing.md](testing.md).

| Bereich | Gefordertes Verhalten / gewählte Umsetzung |
|---|---|
| Konfiguration | fünf Equipmentwerte speichern und anhand ihrer ID laden |
| Analyse | vier getrennte Analyse-Services; Management startet Fluid, Worker geben sequenziell weiter |
| Fachliche Ergebnisse | fünf Equipmentresultate; Engine benötigt drei erfolgreiche Vorgänger; Gesamt-OK nur bei vollständigem Erfolg |
| Beobachtung | proaktive RUNNING/READY/FAILED-Meldungen, zusätzlich PENDING; React zeigt Resultate und technische Fehler |
| Wiederholung | FAILED-Schritt wiederholen; Vorgänger erhalten, Ziel und Nachfolger neuer Versuch |
| Ausfallbehandlung | Circuit Breaker, HTTP-Fristen, begrenzte Callbackversuche und Inaktivitätsprüfung |
| Architekturunterlagen | vier UML-Sichten, reduzierte arc42, begründete Entscheidungen, DDD und Übung-4-Auswertung |
| Vortrag | Anforderungen, Architektur, Entscheidungen, Code, Demo und Fazit |

Java/Spring Boot, React, H2 und Resilience4j sind unsere Technologiewahl. Docker/Compose erfüllt eine technische Alternative aus Übung 8; Kafka ist nicht zusätzlich erforderlich. Die Bedienung kann über UI oder REST-Client erfolgen; Postman ist keine allgemeine Pflicht.

Die fachlichen Kernfälle sind Erfolg, ungültiges Equipment, Serviceausfall und Retry. Zusätzliche Absicherung prüft Zeitgrenzen, Zieltrennung der Breaker und konkurrierende/veraltete Rückmeldungen. Vorhandene Implementierung und tatsächlicher Testnachweis werden unterschieden.

Offen bleiben unter anderem dauerhafte Zustellung, API-Versionierung, Authentifizierung, CI/CD, verteiltes Tracing und nachgewiesene horizontale Skalierung. Die [vollständige Smell-Bewertung](anti-patterns.md) nennt Maßnahmen und verbleibende Grenzen zu allen 21 Kriterien.
