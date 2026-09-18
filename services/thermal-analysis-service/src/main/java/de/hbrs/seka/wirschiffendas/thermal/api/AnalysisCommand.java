package de.hbrs.seka.wirschiffendas.thermal.api;

import java.util.List;
import java.util.Map;

/**
 * Auftrag an die thermische Analyse mit Konfiguration und bisherigen Ergebnissen.
 */
public record AnalysisCommand(
        String analysisId,
        Map<String, String> configuration,
        List<Map<String, String>> previousResults) {
}
