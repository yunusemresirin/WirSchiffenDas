from pathlib import Path
from xml.sax.saxutils import escape
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
W, H, SCALE = 1600, 1080, 2
im = Image.new('RGB', (W*SCALE, H*SCALE), 'white')
d = ImageDraw.Draw(im)
svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', '<rect width="100%" height="100%" fill="white"/>']

def text(x, y, label, size=20, bold=False, center=False):
    font = ImageFont.truetype('C:/Windows/Fonts/' + ('arialbd.ttf' if bold else 'arial.ttf'), size*SCALE)
    d.text((x*SCALE,y*SCALE),label,font=font,fill='black',anchor='mt' if center else 'lt')
    svg.append(f'<text x="{x}" y="{y+size*.82}" font-family="Arial, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" text-anchor="{"middle" if center else "start"}">{escape(label)}</text>')

def rect(x,y,w,h):
    d.rectangle((x*SCALE,y*SCALE,(x+w)*SCALE,(y+h)*SCALE),fill='white',outline='black',width=2*SCALE)
    svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="white" stroke="black" stroke-width="2"/>')

def line(points, arrow=False, dashed=False):
    import math
    for (x1,y1),(x2,y2) in zip(points,points[1:]):
        length=math.hypot(x2-x1,y2-y1)
        if dashed:
            for pos in range(0,int(length),12):
                end=min(pos+7,length)
                d.line(((x1+(x2-x1)*pos/length)*SCALE,(y1+(y2-y1)*pos/length)*SCALE,(x1+(x2-x1)*end/length)*SCALE,(y1+(y2-y1)*end/length)*SCALE),fill='black',width=2*SCALE)
        else:
            d.line((x1*SCALE,y1*SCALE,x2*SCALE,y2*SCALE),fill='black',width=2*SCALE)
    svg.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in points)+'" fill="none" stroke="black" stroke-width="2"'+(' stroke-dasharray="7 5"' if dashed else '')+'/>')
    if arrow:
        (a,b),(x,y)=points[-2:]; angle=math.atan2(y-b,x-a)
        p=[(x-13*math.cos(angle-.42),y-13*math.sin(angle-.42)),(x,y),(x-13*math.cos(angle+.42),y-13*math.sin(angle+.42))]
        line(p)

def circle(x,y,r):
    d.ellipse(((x-r)*SCALE,(y-r)*SCALE,(x+r)*SCALE,(y+r)*SCALE),fill='white',outline='black',width=2*SCALE)
    svg.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="white" stroke="black" stroke-width="2"/>')

def container(x,y,w,title,detail,port):
    rect(x,y,w,110)
    text(x+14,y+12,'«Docker_Container»',18)
    text(x+14,y+43,title,21,True)
    text(x+14,y+77,detail,18)
    rect(x+w/2-9,y-9,18,18)
    text(x+w/2+16,y-25,port,17)

text(50,24,'WirSchiffenDas — Docker-Verteilungssicht',30,True)
text(50,65,'development · Stand e5ee4fd · HTTP / REST',18)
circle(1110,64,23)
text(1150,47,'Browser',21,True)
text(1150,77,'http://localhost:3000',20)
line([(1110,87),(1110,190)],True)
rect(40,135,1520,730)
text(58,151,'«Docker_Compose»  WirSchiffenDas',23,True)
text(58,187,'Netzwerk: wirschiffendas-network',20)
# Host port at the compose boundary and internal web port.
rect(1100,125,20,20)
line([(1110,145),(1110,229)],True)
text(1134,153,'Host :3000 → Container :80',19)
container(920,238,380,'web-ui','Nginx + React SPA','80')
container(95,380,350,'configuration-service','Spring Boot · H2-Dateidatenbank','8081')
container(650,380,405,'analysis-management-service','Spring Boot · H2-Dateidatenbank','8082')
line([(920,300),(270,300),(270,371)],True)
text(335,269,'/api/configurations',19)
line([(1110,348),(1110,399),(1055,399)],True)
text(1140,371,'/api/analyses',19)
line([(650,445),(445,445)],True)
text(465,397,'Konfiguration laden',17)
text(465,421,'GET /api/configurations/{id}',14)
# Persistent volumes, shown inside the compose definition.
rect(95,531,350,65)
text(110,541,'«Docker_Volume» configuration-data',18)
text(110,567,'Mount: /app/data',18)
line([(270,490),(270,531)],True)
rect(650,531,405,65)
text(665,541,'«Docker_Volume» analysis-data',18)
text(665,567,'Mount: /app/data',18)
line([(852,490),(852,531)],True)
# Pipeline with normal first-stage entry.
line([(1055,465),(1485,465),(1485,630),(250,630),(250,681)],True)
text(1090,432,'Start: POST /internal/analyses',18)
container(90,690,320,'fluid-analysis-service','Spring Boot','8083')
container(455,690,320,'thermal-analysis-service','Spring Boot','8084')
container(820,690,320,'electrical-analysis-service','Spring Boot','8085')
container(1185,690,335,'engine-management-', 'analysis-service · Spring Boot','8086')
for x in [410,775,1140]:
    line([(x,745),(x+45,745)],True)
text(805,824,'Weitergabe entlang der Kette: POST /internal/analyses · Auflösung über Compose-Service-Namen',19,center=True)
# Notes keep cross-cutting connections readable instead of introducing crossing lines.
# Cross-cutting calls are explained below the diagram.


line([(145,865),(145,900)],True,True)
rect(40,900,360,125)
text(55,913,'«specification»',18)
text(55,945,'docker-compose.yml',22,True)
text(55,980,'alternative_docker-compose.yml',17)
text(55,1003,'gleiche Topologie, lokale Backend-Builds',15)
text(440,892,'Rückmeldungen: alle vier Analyse-Container → analysis-management-service:8082',18)
text(440,922,'POST /internal/analyses/{id}/algorithms/{algorithm}/status bzw. /result',17)
text(440,957,'Wiederholung: Management kann jeden Analyse-Container direkt starten.',18)
text(440,989,'Monitoring: web-ui → alle sechs Backends über /monitor/<service>/…',18)
text(440,1021,'Backend-Ports zusätzlich am Host freigegeben: 8081–8086 (jeweils 1:1).',18)
text(50,1054,'UML-nahe Darstellung · Pfeile zeigen Aufrufrichtung; Antwortverkehr ist nicht separat dargestellt.',16)
svg.append('</svg>')
(OUT/'docker-deployment.svg').write_text('\n'.join(svg),encoding='utf-8')
im.save(OUT/'docker-deployment.png')
print('Generated docker-deployment.svg and docker-deployment.png')

