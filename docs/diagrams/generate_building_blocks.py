#!/usr/bin/env python3
"""Render the UML white-box view as PNG and SVG using only Matplotlib.

Run: python docs/diagrams/generate_building_blocks.py
Dependency: matplotlib>=3.8,<4
The model reflects docker-compose.yml, frontend/nginx.conf and the REST
controllers on docs/latex. UML ports are logical interaction points; they
are not extra TCP listeners. Repeated interface symbols are separate usages
of the same contract, never a shared runtime bus.
"""
from pathlib import Path
import argparse
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, Circle, Arc
from PIL import Image

INK = '#18324a'
BLUE = '#24669a'
GREEN = '#237864'
ORANGE = '#a56522'
GRAY = '#556574'
WIDTH, HEIGHT = 1800, 2460


def render(output_dir: Path, dpi: int = 160):
    plt.rcParams.update({'font.family': 'DejaVu Sans', 'svg.fonttype': 'none',
                         'svg.hashsalt': 'wirschiffendas-whitebox-v2'})
    fig, ax = plt.subplots(figsize=(18, 24.6), dpi=100)
    fig.subplots_adjust(0, 0, 1, 1)
    ax.set(xlim=(0, WIDTH), ylim=(HEIGHT, 0), aspect='equal')
    ax.axis('off')
    fig.patch.set_facecolor('white')

    def text(x, y, value, size=18, color=INK, weight='normal', ha='left', **kw):
        return ax.text(x, y, value, fontsize=size*.72, color=color,
                       weight=weight, ha=ha, va='top', linespacing=1.5, **kw)

    def line(points, color=INK, width=1.5, style='-'):
        ax.plot(*zip(*points), color=color, lw=width, ls=style, zorder=2)

    def rect(x, y, w, h, fill='white', edge=INK, lw=1.6):
        ax.add_patch(Rectangle((x, y), w, h, facecolor=fill, edgecolor=edge,
                               lw=lw, zorder=1))

    def port(x, y, color=INK):
        ax.add_patch(Rectangle((x-6, y-6), 12, 12, facecolor='white',
                               edgecolor=color, lw=1.5, zorder=5))

    def ball(x, y, color=INK):
        ax.add_patch(Circle((x, y), 8, facecolor='white', edgecolor=color,
                            lw=1.6, zorder=4))

    def socket(x, y, opening, color=INK):
        # Angles use screen coordinates because the y-axis is inverted.
        start = {'right': 90, 'left': -90, 'down': 180, 'up': 0}[opening]
        ax.add_patch(Arc((x, y), 28, 28, theta1=start, theta2=start+180,
                         color=color, lw=1.6, zorder=4))

    def assembly(x1, x2, y, provider='right', label='', color=BLUE):
        """Horizontal assembly connector, ball on provider / socket on user."""
        middle = (x1+x2)/2
        if provider == 'right':
            line([(x1, y), (middle-14, y)], color)
            line([(middle+8, y), (x2, y)], color)
            socket(middle, y, 'right', color)
        else:
            line([(x1, y), (middle-8, y)], color)
            line([(middle+14, y), (x2, y)], color)
            socket(middle, y, 'left', color)
        ball(middle, y, color)
        port(x1, y, color); port(x2, y, color)
        if label:
            text(middle, y-31, label, 17, color, ha='center')

    def vertical(x, y1, y2, label, color=BLUE):
        mid=(y1+y2)/2
        line([(x,y1),(x,mid-14)],color)
        line([(x,mid+8),(x,y2)],color)
        socket(x,mid,'down',color);ball(x,mid,color)
        port(x,y1,color);port(x,y2,color)
        text(x+25,mid-12,label,16,color)

    def component(x,y,w,h,title,subtitle,fill):
        rect(x,y,w,h,fill)
        text(x+20,y+15,'«component»  ·  Docker-Container',15,GRAY)
        text(x+20,y+44,title,23,weight='bold')
        text(x+20,y+78,subtitle,16,GRAY)
        # UML component glyph
        rect(x+w-43,y+16,24,24,fill)
        rect(x+w-49,y+20,10,6,fill)
        rect(x+w-49,y+31,10,6,fill)

    text(70,35,'WirSchiffenDas',38,weight='bold')
    text(70,86,'Bausteinsicht · UML-Komponenten · Whitebox des Gesamtsystems',24,GRAY)
    text(1730,48,'SEKA / docs/latex',18,GRAY,ha='right')

    # External web consumer and system port. Delegation preserves the contract.
    text(365,147,'Browser / Ingenieur',19,weight='bold',ha='center')
    line([(365,177),(365,185)],BLUE)
    socket(365,199,'down',BLUE);ball(365,199,BLUE)
    line([(365,207),(365,280)],BLUE)
    text(390,183,'IWeb · HTTP :3000 → :80',17,BLUE)
    rect(70,235,1660,1265,'#fcfdfe')
    text(90,250,'«component» Gesamtsystem',18,weight='bold')
    text(1710,251,'intern: wirschiffendas-network · HTTP / JSON',17,GRAY,ha='right')
    # Redraw boundary delegation over the system rectangle.
    line([(365,207),(365,290)],BLUE)
    port(365,235,BLUE);port(365,290,BLUE)
    text(390,262,'«delegate»',14,BLUE)

    component(140,290,480,160,'web-ui','Nginx :80 · Host :3000\nReact-Dateien ausliefern; REST & Health weiterleiten','#eaf3fa')
    component(1110,290,520,160,'configuration-service','HTTP :8081 · Konfiguration validieren / speichern\nEigene H2-Datei → Volume configuration-data','#edf6f1')
    assembly(620,1110,365,label='IConfiguration · :8081')
    vertical(365,450,570,'IAnalysis · :8082')

    # Read configuration: AM client -> configuration provider.
    line([(580,570),(580,520),(960,520),(960,421),(1020,421)],BLUE)
    socket(1034,421,'right',BLUE);ball(1034,421,BLUE)
    line([(1042,421),(1110,421)],BLUE)
    port(580,570,BLUE);port(1110,421,BLUE)
    text(730,494,'IConfiguration · GET · :8081',16,BLUE,ha='center')

    component(140,570,480,815,'analysis-management-service',
              'HTTP :8082 · Zustand und Gesamtergebnis verwalten','#edf6f1')
    text(163,704,'Analyse starten / laden / wiederholen',19,weight='bold')
    text(163,749,'AnalysisController\nAnalysisApplicationService\nAnalysisRun + Overall Result',18)
    text(163,866,'Ausgehende HTTP-Clients',19,weight='bold')
    text(163,910,'ConfigurationServiceClient\nAnalysisServiceStarter + Circuit Breaker',17)
    text(163,1035,'Callbacks annehmen',19,weight='bold')
    text(163,1080,'Status und Einzelresultate speichern.\nKein zentraler Ablauf-Orchestrator:\nNormaler Start nur bei FLUID;\nRetry adressiert den gewählten Service.',17)
    rect(163,1230,360,110,'white',GREEN)
    text(181,1246,'Eigene Persistenz',18,GREEN,weight='bold')
    text(181,1281,'H2-Datei → Volume analysis-data',17)

    names=[('fluid-analysis-service','Öl- / Kraftstoffanalyse',8083,'IFluid'),
           ('thermal-analysis-service','Thermische Analyse',8084,'IThermal'),
           ('electrical-analysis-service','Elektrische Analyse',8085,'IElectrical'),
           ('engine-management-','Finale EMS-Analyse',8086,'IEngine')]
    for i,(name,purpose,number,interface) in enumerate(names):
        y=570+i*220
        title=name if i<3 else name+'analysis-service'
        component(1110,y,520,155,title,
                  f'HTTP :{number} · {purpose}\nAnalysisController → AnalysisWorker (asynchron)', '#fff5e9')
        if i==3:
            # A smaller title keeps the long service name within its component.
            ax.texts[-2].set_fontsize(18*.72)
        assembly(620,1110,y+105,label=f'{interface} · '+('Start / Retry' if i==0 else 'Retry')+f' · :{number}')
        assembly(620,1110,y+143,provider='left',color=GREEN)
        text(865,y+150,'ICallback · Status / Result · :8082',15,GREEN,ha='center')
        if i<3:
            vertical(1415,y+155,y+220,f'{["IThermal","IElectrical","IEngine"][i]} [CB]',ORANGE)

    text(1110,1450,'Alle vier Analyse-Services: zustandslos; je ein eigener Prozess.',17,GRAY)
    text(160,1430,'Blau: Aufträge / Lesen     Grün: Rückmeldungen\nOrange: dezentrale Weitergabe zum nächsten Algorithmus',17,GRAY)

    # A separate projection avoids cluttering the business view with 6 health edges.
    text(70,1530,'Monitoring · dieselben Komponenten und Ports',23,weight='bold')
    rect(70,1575,1660,190,'#f7f9fb', '#c8d2dc',1)
    rect(100,1628,235,72,'white',BLUE)
    text(217,1647,'web-ui / Nginx',19,weight='bold',ha='center')
    assembly(335,705,1664,label='IHealth [6×]',color=GRAY)
    rect(705,1600,990,137,'white','#c8d2dc',1)
    text(725,1612,'Provided je Service: GET /actuator/health',19,weight='bold')
    text(725,1650,'configuration :8081   ·   analysis-management :8082   ·   fluid :8083\nthermal :8084   ·   electrical :8085   ·   engine-management :8086',17)
    text(100,1718,'Browser: /monitor/<key>/actuator/health → Nginx → <service-name>:<port>/actuator/health',16,GRAY)

    text(70,1798,'Interface-Verträge',23,weight='bold')
    text(930,1798,'Ports & externe Erreichbarkeit',23,weight='bold')
    contracts=[
        ('IWeb','GET /  ·  HTML / JS / CSS; API- und Monitoring-Proxy'),
        ('IConfiguration','POST /api/configurations\nGET /api/configurations/{configurationId}'),
        ('IAnalysis','POST /api/analyses\nGET /api/analyses/{analysisId}\nPOST /api/analyses/{analysisId}/algorithms/{algorithm}/retry'),
        ('IFluid / IThermal / IElectrical / IEngine','POST /internal/analyses  →  202 Accepted\nDTO: analysisId, configuration, previousResults'),
        ('ICallback','PUT /internal/analyses/{analysisId}/algorithms/{algorithm}/status\nPUT /internal/analyses/{analysisId}/algorithms/{algorithm}/result')]
    y=1845
    for title,body in contracts:
        text(70,y,title,18,weight='bold')
        text(70,y+27,body,15,GRAY)
        y+=43+len(body.splitlines())*23
    text(930,1845,'Compose-Mapping: Host → Container',18,weight='bold')
    text(930,1880,'web-ui                              3000 → 80\nconfiguration-service        8081 → 8081\nanalysis-management         8082 → 8082\nfluid / thermal / electrical / engine: 8083–8086 → identisch',17)
    text(930,1992,'Intern: <Compose-Service-Name>:<Container-Port>.\nExtern: <Docker-Host>:<Host-Port>, z. B. Postman.\nAuch /internal/* ist über die publizierten Ports erreichbar;\n„intern“ bezeichnet hier die fachliche Nutzung.\nUML-Port ≠ TCP-Port: mehrere logische Interfaces\nteilen sich pro Service denselben HTTP-Listener.',17,GRAY)

    # Legend with actual UML symbols.
    line([(960,2190),(990,2190)],INK);ball(998,2190)
    text(1022,2178,'Provided Interface (angeboten)',16)
    line([(960,2230),(984,2230)],INK);socket(998,2230,'right')
    text(1022,2218,'Required Interface (benötigt)',16)
    port(970,2270);text(1022,2258,'Quadrat = Port; Kreis + Halbkreis = Assembly',16)
    text(930,2300,'«delegate»: System-Port → Port der inneren Komponente.\n[CB]: Resilience4j am aufrufenden Client.\nWiederholte Symbole = einzelne Verbindungen; kein Bus.',16,GRAY)
    line([(70,2390),(1730,2390)],'#c8d2dc',1)
    text(70,2410,'Quellen: docker-compose.yml · frontend/nginx.conf · REST-Controller und HTTP-Clients · Stand: docs/latex',16,GRAY)

    output_dir.mkdir(parents=True,exist_ok=True)
    for suffix in ('svg','png'):
        fig.savefig(output_dir/f'building-blocks.{suffix}',dpi=dpi,
                    facecolor='white',metadata={'Creator':'generate_building_blocks.py'} if suffix=='svg' else None)
    plt.close(fig)
    # Compact palette PNG retains crisp text at 2880 x 3936 pixels.
    png = output_dir/'building-blocks.png'
    with Image.open(png) as raster:
        raster.convert('RGB').quantize(colors=256, dither=Image.Dither.NONE).save(png,optimize=True)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir',type=Path,default=Path(__file__).resolve().parent)
    parser.add_argument('--dpi',type=int,default=160)
    args=parser.parse_args()
    render(args.output_dir,args.dpi)
