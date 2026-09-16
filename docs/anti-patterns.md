# WirSchiffenDas – Smells und Anti-Patterns

Stand: 16. September 2026. Bewertungsrahmen: Schirgi/Brenner (2021), *Quality Assurance for Microservice Architectures*, Abschnitte IV.A/IV.B, PDF-S. 3–5. Die folgende Anwendung auf Fallstudien-IST und implementierten Prototyp ist unsere Bewertung. Ein Smell ist ein Prüfhinweis; Containeranzahl oder ein Patternname allein beweisen keine Architekturqualität.

Die Übung-4-Tabelle enthält 20 Kriterien. Der Katalog enthält zusätzlich **Insufficient Monitoring**; hier werden alle 21 betrachtet. „IST“ meint die Fallstudie v3.0, nicht eine frühere Version dieses Git-Branches. Priorität A bezeichnet die im Analyseprototyp gezielt bearbeiteten Risiken; B bezeichnet weitere sinnvolle Maßnahmen. Daraus werden keine zusätzlichen offiziellen Pflichttechnologien abgeleitet.

## Vollständige Bewertung

| Nr. | Kriterium | Fallstudien-IST | Lösung im Prototyp und verbleibende Grenze |
|---:|---|---|---|
| 1 | Hard-Coded Endpoints | IP-basierte Endpunkte; Registry als unnötig betrachtet. | **A:** konfigurierbare URLs und Compose-DNS. Keine fest verdrahteten IPs erforderlich; dynamische Registry nicht implementiert. |
| 2 | Shared Persistence | Globale Produkt-/Kundendatenbank koppelt Komponenten. | **A:** eigene Configuration-/Analysis-Datenbanken; kein Fremdtabellenzugriff. Unternehmensweite Datenmigration nicht umgesetzt. |
| 3 | Independent Deployability | Gebündelte Anwendung auf einer VM und gemeinsames Deployment. | **A:** eigene Images/Container. Gemeinsamer Maven-Parent und Vertragsänderungen erfordern weiterhin Abstimmung; unabhängige Releasepipelines fehlen. |
| 4 | Horizontal Scalability | Gesamte VM wird mit Skalierung einzelner Funktionen gleichgesetzt. | **B:** getrennte Worker schaffen kleinere Einheiten. Feste Hostports, H2-Dateien und lokale Deduplikation verhindern einen behaupteten Mehrinstanznachweis. |
| 5 | Isolation of Failures | Hängende Analyse beeinträchtigt Manufacturing. | **A:** Breaker je Ziel, HTTP-Fristen, Callback-Retry und Inaktivitätsprüfung. Management bleibt zentrale Zustandsabhängigkeit; dauerhafte Zustellung fehlt. |
| 6 | Decentralization | Schichtenteams, zentrale GP-API und Datenhaltung konzentrieren Verantwortung. | **A:** Worker geben den Ablauf weiter und besitzen Prüfregeln; Management besitzt Auftragszustand. Reale Teamautonomie ist nicht aus Code beweisbar. |
| 7 | Wrong Cut | Technische Packages und Services pro Business-Methode. | **A:** fachliche Analysecluster und getrennte Datenhoheit. Kleine Dummyregeln erlauben nur eine vorläufige Validierung der Context-Grenzen. |
| 8 | Cyclic Dependency | Konkreter Aufrufzyklus nicht eindeutig belegt; starke Kopplung schafft Risiko. | **B:** Command-/Callback-Abhängigkeiten ausdrücklich dokumentiert. Sie sind wechselseitig, aber keine rekursive Endlosschleife; Rückkanalverfügbarkeit bleibt erforderlich. |
| 9 | Nano Service | 167 Business-Methoden als einzelne Merchandise-Services. | **B:** Öl und Kraftstoff bilden einen Cluster. Vier kleine Analyse-Services sind Aufgabenfokus; ohne diese Randbedingung wäre ein modularer Monolith eine ernsthafte Alternative. |
| 10 | Mega Service | Große Anwendungseinheit und überladene GP-API. | **A:** Fachregeln in Workern, Konfiguration getrennt, Nginx ohne Fachlogik. Management bündelt weiterhin Zustandsregeln/Retry und muss bei Wachstum überprüft werden; das macht es nicht automatisch zum Mega Service. |
| 11 | Shared Libraries | Zentrales Shared-Libraries-Repository kann gemeinsame Änderungen erzwingen. | **A:** kein gemeinsam verwendetes Domain-Modul. Technischer Maven-Parent und Frameworks sind vertretbar; mehrfach vorhandener Callback-Code verursacht dafür Pflegeaufwand. |
| 12 | Too Many Standards | REST, XML-RPC, MQ und unterschiedliche experimentelle Technik. | **B:** ein Java-/REST-Backendstack plus React-Frontend. Einheitliche Verträge reduzieren Vielfalt; gemeinsame Standards ersetzen keine Kompatibilitätsprüfung. |
| 13 | Too New Technology | Experimenteller Einsatz jeweils neuer Software ohne klare Bewertung. | **B:** bewusst kleiner Technikumfang mit dokumentierten Entscheidungen. Aktualität allein belegt weder Mangel noch Sicherheit; Wartung und Updates bleiben erforderlich. |
| 14 | Manual Anti-Pattern | Manuelles Order Fulfillment und finales Deployment. | **A:** Source-Build, Compose-Start und Testskripte automatisieren den Demonstrator. Fachprozessmigration und automatische Auslieferung sind nicht umgesetzt. |
| 15 | No CI/CD | Repository vorhanden, aber keine hinreichenden Quality Checks und manuelles Deployment. | **B/offen:** lokale Tests/Builds vorhanden, keine Pipeline im Projekt. Nächster Schritt wäre automatisiertes Prüfen jedes Branches mit klarer Releasefreigabe. |
| 16 | No API Gateway | GP-API vermischt Aufgaben; keine klar begrenzte Einstiegsschicht. | **B/teilweise:** Nginx routet UI-Anfragen, ohne zentrale Fachregeln. Authentifizierung, Rate Limiting und eine vollständige Gateway-Plattform fehlen; sie sind für die lokale Demo nicht nötig. |
| 17 | Timeouts | Fristen für Analyse wurden verworfen; blockierende Arbeit bleibt möglich. | **A:** HTTP 2/5 Sekunden, 30-Sekunden-Inaktivitätsfrist, begrenzte Rückmeldungsversuche. Werte müssen bei längeren echten Simulationen angepasst werden. |
| 18 | No API Versioning | REST-Endpunkte unversioniert. | **B/offen:** weiterhin unversionierte URLs. Vor unabhängiger Evolution sind Versionierung, Deprecation und Consumer-Vertragstests nötig. |
| 19 | No Health Checks | Monitoring als Wunsch, Healthchecks nicht belegt. | **A:** Actuator-Readiness und Compose-Healthchecks. Prozessbereitschaft ist bewusst von Nachbarfehlern/Breakerzustand getrennt; Healthchecks reparieren keine verlorenen Jobs. |
| 20 | Local Logging | Fehlersuche nur über lokale Logdateien, geringe Transparenz. | **B/offen:** Containerlogs enthalten Analyse-/Versuchsbezug; keine zentrale Suche oder Ende-zu-Ende-Traces. Aggregation und Korrelation wären eine nächste Betriebsverbesserung. |
| 21 | Insufficient Monitoring | Stillstände und Ursachen kaum sichtbar; Monitoring nur geplant. | **A/teilweise:** UI zeigt Fortschritt, Ergebnisse, technische Meldungen, Erreichbarkeit und Breaker. Zeitreihen, Alerting, SLOs und verteiltes Tracing fehlen. |

## Priorisierte Entscheidungen und Nachweise

Für den Analyseausschnitt sind **Shared Persistence, Wrong Cut, Isolation of Failures und Timeouts** besonders relevant: getrennte Datenhoheit, fachlicher Schnitt, Ziel-Breaker und Wiederanlaufregeln setzen direkt an den Fallstudienproblemen an. Independent Deployability wird technisch unterstützt, aber nicht allein durch Docker als organisatorisch abgeschlossen erklärt.

Der Nachweis folgt Szenarien: vollständiges Resultat, fehlgeschlagenes Equipment, unerreichbarer Nachfolger, fehlender Fortschritt und konsistenter Retry. Domain-, HTTP- und E2E-Prüfungen stehen in [testing.md](testing.md). Eine erfolgreiche Einzelprüfung belegt weder Produktionsreife noch Lastfestigkeit.

## Literaturbezug und Quellen

- [Schirgi und Brenner (2021), Quality Assurance for Microservice Architectures](https://git.fslab.de/salda2m/ooka/-/blob/main/papers/Schirgi%20and%20Brenner%2C%202021.pdf): vollständiger Katalog, IV.A/B, PDF-S. 3–5. Die vierte Spalte oben ist unsere projektspezifische Bewertung.
- [Garriga (2018), Microservices: A Taxonomy](https://git.fslab.de/salda2m/ooka/-/blob/main/papers/Garriga%2C%202018%20MS%20Taxonomy.pdf): Entwurf, Entwicklung, Deployment, Betrieb und Organisation gemeinsam betrachten. Hieraus folgt für unsere Bewertung, Containergrenzen nicht als vollständigen Qualitätsnachweis zu verwenden.
- [Crnkovic et al. (2011), Classification Framework](https://git.fslab.de/salda2m/ooka/-/blob/main/papers/Crnkovic%202011b%20Classification%20Framework.pdf): Schnittstellen, Kontextabhängigkeiten, Komposition und Lebenszyklus unterscheiden. Im Projekt sind interne Java-Klassen und deploybare HTTP-Services unterschiedliche Betrachtungsebenen.
- [Unterauer (2017)](https://git.fslab.de/salda2m/ooka/-/blob/main/papers/Unterauer%2C%202017.pdf): Grundlage für die Abwägung von Schicht-, Komponenten- und Funktionsteams in [ddd.md](ddd.md).
- [Vuckovic (2020), You Are Not Netflix](https://git.fslab.de/salda2m/ooka/-/blob/main/papers/Vuckovic2020_Chapter_YouAreNotNetflix.pdf): Kontext für die bewusste Begrenzung des Betriebsaufwands. Unsere Schlussfolgerung ist die lokale Compose-Demo statt einer zusätzlichen Clusterplattform.

Lokale Aufgabenquellen im übergeordneten SEKA-Ordner: Übung 4 (19.05.2026), Fallstudie v3.0 und ausgefüllte `Aufgabe_4d_Bewertung Anti Pattern bei WirSchiffenDas - Lösung.xlsx`; Übung 5; Übung 8. Die in Übung 4 modellierte größere Sollarchitektur ist keine Behauptung, dass ERP-/CRM-Integration, KI, Registry, CI/CD oder Tracing bereits implementiert wären.
