# Bausteinsicht – Whitebox

![UML-Komponentendiagramm](building-blocks.png)

- [SVG](building-blocks.svg): skalierbare Vektorgrafik mit editierbarem Text.
- [PNG](building-blocks.png): 2880 × 3936 Pixel, direkt in Dokumente einfügbar.
- [Python-Generator](generate_building_blocks.py): reproduzierbare Quelle ohne PlantUML oder Graphviz.

Vom Repository-Verzeichnis aus:

```sh
python -m pip install 'matplotlib>=3.8,<4'
python docs/diagrams/generate_building_blocks.py
```

Optional: `--dpi 200` für eine höhere PNG-Auflösung oder `--output-dir <Verzeichnis>`.
Pillow wird als Abhängigkeit von Matplotlib installiert. Beide Ausgaben werden aus
demselben Modell erzeugt. PNG verwendet eine optimierte Palette; SVG bleibt eine
echte Vektorgrafik. DejaVu Sans ist die von Matplotlib mitgelieferte Schrift.

## Notation und Abstraktion

Die äußere Komponente ist die Whitebox von WirSchiffenDas. Darin liegen die
Web-UI und die sechs fachlichen Microservices; die Deployment-Zuordnung zu einem
Docker-Container ist jeweils annotiert. Die Port-Quadrate sind logische UML-Ports,
keine zusätzlichen TCP-Listener. Kreis = Provided Interface, Halbkreis = Required
Interface. Das zusammengefügte Paar ist ein Assembly-/Kompositionskonnektor.
Die Verbindung vom äußeren IWeb-System-Port zum inneren Web-UI-Port ist eine
Delegation. Ein Port ist nicht schon deshalb eine Delegation, weil er eine
Schnittstelle anbietet.

Die Namen IFluid, ICallback usw. benennen REST-Verträge im Diagramm; sie behaupten
keine gleichnamigen Java-Interface-Typen. Wiederholte Symbole repräsentieren
einzelne direkte HTTP-Verbindungen. Insbesondere gibt es weder einen Callback-Bus
noch einen zentralen Health-Service. Der Monitoring-Ausschnitt zeigt dieselben
sechs Komponenten und ihre je eigene Health-Schnittstelle.

Analysis Management benötigt die Configuration-API nur lesend. Beim normalen
Start wird ausschließlich Fluid aufgerufen; die drei weiteren direkten
Aufrufverbindungen dienen dem gezielten Retry. Anschließend wird dezentral
weitergegeben: Fluid → Thermal → Electrical → Engine Management. Die vier
Analyse-Services melden Status und Resultate an Analysis Management zurück.
Der AnalysisServiceStarter und die drei NextServiceClients verwenden Circuit
Breaker. Configuration-Zugriff und Callback-Clients sind nicht dadurch geschützt.

## Transport und externe Zugriffe

| Komponente / Compose-DNS-Name | Host-Port → Container-Port | Provided Interfaces |
| --- | --- | --- |
| web-ui | 3000 → 80 | IWeb; Proxy für IConfiguration, IAnalysis und Monitoring |
| configuration-service | 8081 → 8081 | IConfiguration, IHealth |
| analysis-management-service | 8082 → 8082 | IAnalysis, ICallback, IHealth |
| fluid-analysis-service | 8083 → 8083 | IFluid, IHealth |
| thermal-analysis-service | 8084 → 8084 | IThermal, IHealth |
| electrical-analysis-service | 8085 → 8085 | IElectrical, IHealth |
| engine-management-analysis-service | 8086 → 8086 | IEngine, IHealth |

Intern werden Service-DNS und Container-Port verwendet, z. B.
`http://thermal-analysis-service:8084/internal/analyses`. Extern werden Docker-Host
und Host-Port verwendet, z. B. `http://localhost:8082/api/analyses` mit Postman.
Ein ausgehender HTTP-Client benötigt keinen eigenen veröffentlichten Server-Port.
Die logischen Ports des Clients drücken seinen Schnittstellenbedarf aus.

Alle sechs Backend-Ports sind laut Compose am Host veröffentlicht. Daher sind
auch `/internal/*`-Endpunkte technisch von außen erreichbar; der Pfad ist keine
Zugriffsbeschränkung. Öffentliche API, Callback-/Analyse-API und Actuator teilen
sich je Service denselben HTTP-Port. Die Grafik zeigt die von der UI genutzte
Health-Schnittstelle; weitere Actuator-Endpunkte sind bewusst ausgeblendet.
H2 läuft eingebettet, ohne eigenen DB-Container oder Netzwerk-Port.

## Quellen und Pflege

Abgeglichen mit `docs/latex`, Quellstand
`c5fe67b0a8a220216524e487efde8d693958e0f4`:

- `docker-compose.yml` und `alternative_docker-compose.yml`
- `frontend/nginx.conf` und `frontend/src/api.ts`
- REST-Controller, HTTP-Clients und `application.yml` unter `services/`

Bei Änderungen an Ports, REST-Verträgen oder Service-Verbindungen den Generator
anpassen und beide Bilder neu erzeugen. Das Diagramm ersetzt `building-blocks.puml`.
