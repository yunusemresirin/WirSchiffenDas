# arc42 LaTeX-Dokumentation

Die Architekturbeschreibung für das SEKA-Semesterprojekt liegt in [`architecture.tex`](architecture.tex). Die LaTeX-Quelle ist bewusst modularisiert; die fachlichen Kapitel befinden sich unter [`sections/`](sections/).

## Layout-Ziel

Die Dokumentation priorisiert **Lesbarkeit vor maximaler Kompression**. Sie verwendet 11-pt-Fließtext, großzügigere A4-Seitenränder, klar getrennte arc42-Kapitel sowie ausreichend große Tabellen und Diagramme. Lange Service-Namen werden kontrolliert umgebrochen, statt über Tabellenränder hinauszulaufen.

Der Umfang ist deshalb nicht künstlich auf fünf Seiten gepresst. Titelblatt und Inhaltsverzeichnis sind separat; die eigentliche Architekturbeschreibung verteilt sich auf mehrere gut lesbare Seiten.

## Inhalt

- Einführung, Ziele und Randbedingungen
- Kontextabgrenzung
- Lösungsstrategie mit Strategic DDD / Bounded Contexts
- Bausteinsicht
- Laufzeitsicht mit Choreographie, Circuit Breaker und Retry
- Verteilungssicht mit Docker/Docker Compose
- querschnittliche Konzepte
- Architekturentscheidungen (ADRs)
- priorisierte Architecture Smells / Anti-Patterns
- Qualitätsszenarien, Risiken und technische Schulden
- prüfungsrelevante Zusammenfassung

Die H-BRS-Kopfmarke wird direkt mit TikZ erzeugt, sodass für den Build keine externe Bilddatei benötigt wird.

## Lokaler Build

Voraussetzung ist eine TeX-Live- oder MiKTeX-Installation mit den verwendeten Standardpaketen.

```bash
cd docs/latex
latexmk -pdf -interaction=nonstopmode -halt-on-error architecture.tex
```

Alternativ kann `pdflatex` zweimal ausgeführt werden, damit Inhaltsverzeichnis und Seitenreferenzen vollständig aufgelöst werden.

## Automatischer PDF-Build

Der GitHub-Actions-Workflow kompiliert `architecture.tex` bei jedem Push und Pull Request. Die erzeugte `architecture.pdf` wird als Workflow-Artifact bereitgestellt. Ein fehlerhafter LaTeX-Build lässt den Workflow fehlschlagen.

## Quellenbasis

Die inhaltliche Struktur ist aus dem realisierten Projekt und den SEKA-Unterlagen abgeleitet: Übungsblätter 4, 5, 6 und 8, Case Study WirSchiffenDas, das Pflichtpaper von Schirgi & Brenner (2021) sowie die im Repository vorhandenen Detaildokumente `arc42.md`, `ddd.md`, `requirements.md`, `architecture.md` und `testing.md`.
