package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Anfrage zum Starten einer Analyse für eine bestehende Konfiguration.
 */
public record StartAnalysisRequest(@NotBlank String configurationId) {
}
