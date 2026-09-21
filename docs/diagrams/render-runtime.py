from pathlib import Path
from xml.sax.saxutils import escape
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
W, H, SCALE = 2260, 2000, 2
im = Image.new('RGB', (W*SCALE, H*SCALE), 'white')
d = ImageDraw.Draw(im)
svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', '<rect width="100%" height="100%" fill="white"/>']

def text(x, y, label, size=20, bold=False, center=False):
    font = ImageFont.truetype('C:/Windows/Fonts/' + ('arialbd.ttf' if bold else 'arial.ttf'), size*SCALE)
    width = font.getlength(label)/SCALE
    left = x-width/2 if center else x
    d.rectangle(((left-3)*SCALE,(y-2)*SCALE,(left+width+3)*SCALE,(y+size+2)*SCALE),fill='white')
    svg.append(f'<rect x="{left-3}" y="{y-2}" width="{width+6}" height="{size+4}" fill="white"/>')
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

def message(a,b,y,labels,reply=False):
    for i,label in enumerate(labels):
        text((a+b)/2,y-27-(len(labels)-1-i)*25,label,20,False,True)
    line([(a,y),(b,y)],True,reply)

def selfcall(x,y,labels):
    line([(x,y),(x+65,y),(x+65,y+32),(x,y+32)],True)
    for i,label in enumerate(labels):
        text(x+80,y-5+i*24,label,19)

def bar(x,y,h):
    rect(x-7,y,14,h)

text(60,40,'Laufzeitsicht',38,True)
text(60,96,'WirSchiffenDas · erfolgreicher Analyselauf',25)
text(60,144,'Voraussetzung: gespeicherte, gültige Motorkonfiguration; alle Services erreichbar.',21)
xs=[100,370,740,1080,1360,1620,1880,2140]
headers=[('Ingenieur',[]),('«boundary»',['Weboberfläche','React / Nginx']),('«control»',['Analysis','Management']),('«service»',['Configuration']),('«service»',['Fluid Analysis']),('«service»',['Thermal Analysis']),('«service»',['Electrical Analysis']),('«service»',['Engine Management','Analysis'])]
for x,(stereo,rows) in zip(xs,headers):
    if x==100:
        circle(x,223,17); line([(x,240),(x,278)]); line([(x-28,255),(x+28,255)]); line([(x,278),(x-25,308)]); line([(x,278),(x+25,308)])
        text(x,317,stereo,20,True,True)
    else:
        width=250 if x==740 else 210
        rect(x-width/2,205,width,108)
        text(x,218,stereo,18,False,True)
        for i,label in enumerate(rows): text(x,249+i*26,label,19 if x==2140 else 21,True,True)
    line([(x,350),(x,1740)],dashed=True)

bar(370,375,410); bar(740,440,295); bar(1080,505,45); bar(1360,645,45)
message(100,363,375,['Analyse starten'])
message(377,733,440,['POST /api/analyses','configurationId'])
message(747,1073,505,['GET /api/configurations/{id}'])
message(1073,747,550,['200 · Konfiguration'],True)
# Persistenz als Selbstaufruf, ohne zusätzliche Datenbank-Lebenslinie.
selfcall(747,570,['Lauf anlegen und in H2 speichern'])
message(747,1353,645,['POST /internal/analyses · Analyseauftrag'])
message(1353,747,690,['202 Accepted'],True)
message(733,377,735,['202 · AnalysisResponse mit analysisId'],True)
message(363,100,785,['Analyselauf anzeigen'],True)

# UML-par-Fragment: Verarbeitung und Polling sind nebenläufig.
# Rahmen transparent zeichnen, damit Lebenslinien sichtbar bleiben.
line([(275,835),(2220,835),(2220,1690),(275,1690),(275,835)])
rect(275,835,65,36); text(290,842,'par',21,True)
text(355,845,'Asynchrone Verarbeitung der Analysekette',22,True)
for x,y,label in [(1360,915,'FLUID'),(1620,1060,'THERMAL'),(1880,1205,'ELECTRICAL'),(2140,1350,'ENGINE_MANAGEMENT')]:
    bar(x,y-10,120)
    message(x-7,747,y,[f'PUT Status: {label} = RUNNING'])
    if x<2140:
        text(x+18,y+20,'Konfiguration prüfen',18)
    else:
        text(x-250,y+12,'Konfiguration und',18)
        text(x-250,y+35,'Vorergebnisse prüfen',18)
    message(x-7,747,y+70,[f'PUT Ergebnis: {label} = READY / OK'])
    if x<2140:
        message(x+7,x+260-7,y+112,['POST Analyseauftrag'])
        # HTTP-202-Antworten der Folgeschritte sind in der Legende zusammengefasst.
text(760,1490,'Management speichert jedes Update; nach dem letzten Erfolg: overallResult = OK.',20)
line([(275,1530),(2220,1530)],dashed=True)
text(295,1545,'Parallel: Statusabfrage etwa jede Sekunde, solange PENDING oder RUNNING',21,True)
message(377,733,1610,['GET /api/analyses/{analysisId}'])
message(733,377,1670,['200 · Status, Einzelergebnisse, overallResult'],True)
message(363,100,1745,['Ergebnis anzeigen: OK'],True)

line([(60,1810),(2200,1810)])
text(60,1837,'Leseschlüssel und Abstraktionen',23,True)
text(60,1878,'Durchgezogen: Aufruf · gestrichelt: Antwort · schmale Balken: ausgewählte Ausführungsphasen.',20)
text(60,1915,'Callbacks: PUT /internal/analyses/{analysisId}/algorithms/{algorithm}/status bzw. /result.',20)
text(1190,1878,'Worker arbeiten mit @Async; RUNNING kann vor der 202-Antwort eintreffen.',20)
text(1190,1915,'Weitergabe: POST /internal/analyses, jeweils mit Konfiguration und Vorergebnissen.',19)
text(60,1954,'React im Browser und Nginx-Proxy sind hier zusammengefasst. Fehler und Retry sind separate Szenarien.',19)
text(1190,1954,'202-Antworten der Folgeschritte und Callback-Antworten sind ausgeblendet.',19)
svg.append('</svg>')
(OUT/'runtime.svg').write_text('\n'.join(svg),encoding='utf-8')
im.save(OUT/'runtime.png')
print('Laufzeitsicht direkt als SVG und PNG erstellt.')
