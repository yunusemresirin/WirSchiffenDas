from pathlib import Path
from xml.sax.saxutils import escape
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
W, H, SCALE = 1800, 1310, 2
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

def polygon(points, fill='white'):
    d.polygon([(x*SCALE,y*SCALE) for x,y in points],fill=fill)
    d.line([(x*SCALE,y*SCALE) for x,y in points+[points[0]]],fill='black',width=2*SCALE)
    svg.append('<polygon points="'+' '.join(f'{x},{y}' for x,y in points)+'" fill="'+fill+'" stroke="black" stroke-width="2"/>')

def node(x,y,w,h,stereotype,title,size=24):
    offset=15
    polygon([(x,y),(x+offset,y-offset),(x+w+offset,y-offset),(x+w,y)])
    polygon([(x+w,y),(x+w+offset,y-offset),(x+w+offset,y+h-offset),(x+w,y+h)],'#dddddd')
    rect(x,y,w,h)
    text(x+18,y+16,stereotype,18)
    text(x+18,y+47,title,size,True)

def component(x,y,w,h,rows):
    rect(x,y,w,h)
    text(x+12,y+10,'«component»',16)
    for i,label in enumerate(rows):
        text(x+12,y+29+i*25,label,18,i==0)

text(60,40,'Verteilungssicht',38,True)
text(60,94,'WirSchiffenDas · Geräte, Laufzeitumgebungen und persistente Daten',24)

# Geräte und Ausführungsumgebungen gemäß UML-Vorbild.
node(55,225,280,370,'«device»','Client-Gerät')
node(80,340,220,220,'«executionEnvironment»','Webbrowser',21)
component(100,445,180,85,['React SPA'])
# Docker-Host und gemeinsame Container-Laufzeit.
node(420,225,1310,785,'«device»','Docker-Host')
node(455,325,1235,635,'«executionEnvironment»','Docker Engine',25)
text(700,372,'Docker Compose · Netzwerk: wirschiffendas-network',20)

node(480,450,280,175,'«container»','web-ui',22)
text(498,528,'HTTP :80',18)
component(495,560,250,52,['Nginx · Dateien / Proxy'])
node(820,450,355,175,'«container»','configuration-service',21)
text(838,528,'HTTP :8081',18)
component(835,560,325,52,['Spring Boot / JVM + H2'])
node(1240,450,420,175,'«container»','analysis-management-service',21)
text(1258,528,'HTTP :8082',18)
component(1255,560,390,52,['Spring Boot / JVM + H2'])

# Persistente Docker-Volumes; H2 ist Teil der beiden JVM-Prozesse.
for x,w,label in [(820,355,'configuration-data'),(1240,420,'analysis-data')]:
    rect(x,665,w,65)
    text(x+14,675,'«volume» '+label,19,True)
    text(x+14,703,'H2-Dateien · Mount: /app/data',18)
    line([(x+w/2,625),(x+w/2,665)])

# Vier eigenständige Analyse-Container.
workers=[(480,'fluid-analysis-',8083),(790,'thermal-analysis-',8084),(1100,'electrical-analysis-',8085),(1410,'engine-management-',8086)]
for x,name,port in workers:
    node(x,805,260,125,'«container»',name,20)
    text(x+18,878,'analysis-service' if port==8086 else 'service',20,True)
    text(x+18,909,'JVM / Spring Boot · :'+str(port),16)

# Kommunikation: durchgezogene Geräte-Verbindung, gestrichelte Nutzungsabhängigkeit.
line([(350,280),(420,280)])
text(60,149,'Kommunikationspfad: HTTP / TCP',20)
text(60,178,'Host :3000 → web-ui :80',20)
line([(300,487),(390,487),(390,590),(495,590)],True,True)
text(62,639,'React lädt Ressourcen und nutzt',18)
text(62,667,'REST-APIs über Nginx.',18)

# Nummerierte Aufrufe mit ausführlicher Legende statt eng gesetzter URL-Texte.
line([(760,505),(820,505)],True)
text(788,472,'1',20,True,True)

# Verbindung oberhalb der drei Container führt vom Nginx zum Management.
line([(650,450),(650,420),(1450,420),(1450,450)],True)
text(1020,393,'2 · /api/analyses',18,True,True)
line([(1240,505),(1190,505)],True)
text(1207,472,'3',20,True,True)
line([(1660,595),(1680,595),(1680,780),(610,780),(610,805)],True)
text(1120,751,'4 · Analyse starten',18,True,True)
for x in [740,1050,1360]:
    line([(x+15,858),(x+50,858)],True)
text(57,752,'Client und Docker-Host dürfen',18)
text(57,780,'derselbe Rechner sein.',18)

line([(60,1053),(1745,1053)])
text(60,1080,'Kommunikation und Datenhaltung',23,True)
text(60,1120,'1  Nginx → Configuration: /api/configurations',20)
text(60,1154,'2  Nginx → Analysis Management: /api/analyses',20)
text(60,1188,'3  Management → Configuration: Konfiguration laden',20)
text(60,1222,'4  Management → Fluid; dann Fluid → Thermal → Electrical → Engine Management',19)
text(910,1120,'Alle Service-Aufrufe: HTTP / REST über Compose-DNS.',19)
text(910,1154,'Callbacks: alle Analyse-Services → Management; Retry: Management → jeweiliger Service.',17)
text(910,1188,'Monitoring: Nginx → alle Backends über /monitor/<service>/…',19)
text(910,1222,'Backend-Ports 8081–8086 zusätzlich 1:1 am Host veröffentlicht.',19)
text(60,1272,'Spezifikation: docker-compose.yml · H2 ist eingebettet, kein separater Datenbank-Container.',18)
svg.append('</svg>')
(OUT/'deployment.svg').write_text('\n'.join(svg),encoding='utf-8')
im.save(OUT/'deployment.png')
print('Verteilungssicht als SVG und PNG erstellt.')
