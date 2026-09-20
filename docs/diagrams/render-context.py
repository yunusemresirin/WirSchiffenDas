from pathlib import Path
from xml.sax.saxutils import escape
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
W, H, SCALE = 1600, 900, 2
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

# Fachliche Kontextsicht: bewusst keine internen Komponenten oder erfundenen Fremdsysteme.
text(70,50,'Kontextsicht',38,True)
text(70,103,'WirSchiffenDas · Qualitätsanalyse von Motorkonfigurationen',24)

# Akteur
circle(190,342,25)
line([(190,367),(190,451)])
line([(142,395),(238,395)])
line([(190,451),(149,502)])
line([(190,451),(231,502)])
text(190,530,'Ingenieur / Ingenieurin',24,True,True)
text(190,568,'bedient das System',19,False,True)

# UML-Systempaket mit grauer Systemfläche.
rect(980,231,205,42)
text(996,243,'«system»',20)
rect(980,273,545,365)
d.rectangle((982*SCALE,275*SCALE,1523*SCALE,636*SCALE),fill='#eeeeee')
svg.append('<rect x="982" y="275" width="541" height="361" fill="#eeeeee"/>')
text(1252,315,'WirSchiffenDas',34,True,True)
text(1252,375,'Motorkonfigurationen verwalten',23,False,True)
text(1252,420,'Qualitätsanalysen durchführen',23,False,True)
text(1252,465,'Status und Ergebnisse bereitstellen',23,False,True)
text(1252,510,'Fehlgeschlagene Schritte wiederholen',23,False,True)
text(1252,581,'Das zu betrachtende System',19,False,True)

# Zwei gerichtete fachliche Informationsflüsse.
text(615,260,'Eingaben und Aufträge',23,True,True)
text(615,302,'Motorkonfiguration · Konfigurations-ID',21,False,True)
text(615,337,'Analyse starten · Schritt erneut starten',21,False,True)
line([(285,391),(980,391)],True)

line([(980,495),(285,495)],True)
text(615,526,'Rückmeldungen und Ergebnisse',23,True,True)
text(615,568,'Konfiguration und IDs · Analysestatus',21,False,True)
text(615,603,'Einzelergebnisse · Gesamtergebnis · Servicezustand',20,False,True)

line([(70,713),(1525,713)])
text(70,746,'Systemgrenze',22,True)
text(70,785,'Weboberfläche, Backend-Services und Datenhaltung gehören zum System.',21)
text(70,822,'Keine externen fachlichen Systeme angebunden. Bedienung über Browser oder REST-API.',21)
svg.append('</svg>')
(OUT/'context.svg').write_text('\n'.join(svg),encoding='utf-8')
im.save(OUT/'context.png')
print('Kontextsicht direkt als SVG und PNG erzeugt.')
