# arc42 LaTeX-Dokumentation

Die finale, kompakte Architekturbeschreibung für das SEKA-Semesterprojekt liegt in [`architecture.tex`](architecture.tex).

## Inhalt

Die Dokumentation ist auf den geforderten Umfang von ungefähr fünf Seiten ausgerichtet und umfasst:

- Einführung, Ziele und Randbedingungen
- Kontextsicht und Strategic DDD / Bounded Contexts
- Bausteinsicht
- Laufzeitsicht mit Choreographie, Circuit Breaker und Retry
- Verteilungssicht mit Docker/Docker Compose
- zentrale Architekturentscheidungen (ADRs)
- priorisierte Architecture Smells / Anti-Patterns nach Schirgi & Brenner
- Qualitätsanforderungen, Restrisiken und Lessons Learned
- prüfungsrelevante Zusammenfassung

Die H-BRS-Kopfmarke wird direkt mit TikZ erzeugt, sodass für den Build keine externe Bilddatei benötigt wird.

## Lokaler Build

Voraussetzung ist eine TeX-Live- oder MiKTeX-Installation mit den Standardpaketen.

```bash
cd docs/latex
pdflatex -interaction=nonstopmode -halt-on-error architecture.tex
pdflatex -interaction=nonstopmode -halt-on-error architecture.tex
```

Der zweite Lauf löst die Seitenreferenz im Footer vollständig auf.

Alternativ:

```bash
latexmk -pdf -interaction=nonstopmode -halt-on-error architecture.tex
```

## Quellenbasis

Die inhaltliche Struktur ist aus dem realisierten Projekt und den SEKA-Unterlagen abgeleitet: Übungsblätter 4, 5, 6 und 8, Case Study WirSchiffenDas, das Pflichtpaper von Schirgi & Brenner (2021) sowie die im Repository vorhandenen Detaildokumente `arc42.md`, `ddd.md`, `requirements.md`, `architecture.md` und `testing.md`.

## Prüfung

Für den 10–13-minütigen Einzelvortrag sollte aus diesem Dokument insbesondere Folgendes übernommen werden: Anforderungen in Kurzform, 4-Sichten-Modell, 1–2 Folien mit Entwurfsentscheidungen, ein kurzer Code-Walkthrough, Demo des Happy Paths und eines Circuit-Breaker-/Retry-Falls sowie Fazit/Lessons Learned.
