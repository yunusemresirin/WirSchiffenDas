package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import java.util.List;

/**
 * Auftrag an einen Analyse-Service mit Konfiguration und bisherigen Ergebnissen.
 */
public record AnalysisCommand(
        String analysisId,
        ConfigurationSnapshot configuration,
        List<PreviousResult> previousResults) {
}
