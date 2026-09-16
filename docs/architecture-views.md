# WirSchiffenDas – vier UML-Sichten

Stand: 16. September 2026. Die PlantUML-Quellen beschreiben den implementierten Prototyp und lassen sich einzeln rendern. Die ergänzende Unternehmensvision ist ausdrücklich getrennt.

| Sicht | Diagramm | Aussage und Abgrenzung |
|---|---|---|
| Kontext | [context.puml](diagrams/context.puml) | System als Blackbox, Ingenieur/Demo-Operator und angebotene Funktionen; keine ERP-/CRM-Integration behauptet |
| Bausteine | [building-blocks.puml](diagrams/building-blocks.puml) | React/Nginx, sechs Backendservices, zwei Datenhaltungen und HTTP-Abhängigkeiten |
| Laufzeit | [runtime.puml](diagrams/runtime.puml) | erfolgreiche sequenzielle Choreographie mit Command, Versuch und proaktiven Rückmeldungen |
| Verteilung | [deployment.puml](diagrams/deployment.puml) | Browser, einzelner Docker-Host, sieben Container, Ports und getrennte Volumes |

Die Laufzeitsicht wird durch [runtime-recovery.puml](diagrams/runtime-recovery.puml) um eindeutig abgelehnten Start, unklare Annahme/Timeout und Retry ergänzt. Die beiden Laufzeitdiagramme sind Varianten derselben Sicht, keine zusätzlichen Pflichtsichten.

DDD-Beziehungen stehen in [context-map.puml](diagrams/context-map.puml). U/D bezeichnet dort die fachliche Abhängigkeit von veröffentlichten Daten/Verträgen, nicht den gesamten HTTP-Aufrufpfad. Die weiter gefasste [context-map-vision.puml](diagrams/context-map-vision.puml) aus Übung 4 ist ausschließlich konzeptionell.

Zuordnung zur [arc42](arc42.md): Kontext → Abschnitt 3, Bausteine → 5, Laufzeit → 6, Verteilung → 7. Schnittstellen-/Fehlerdetails: [architecture.md](architecture.md); Modelle und Grenzen: [ddd.md](ddd.md).
