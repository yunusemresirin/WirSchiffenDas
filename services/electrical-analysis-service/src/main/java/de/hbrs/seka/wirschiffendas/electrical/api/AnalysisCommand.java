package de.hbrs.seka.wirschiffendas.electrical.api;

import java.util.List;
import java.util.Map;

/**
 * Auftrag an die elektrische Analyse mit Konfiguration und bisherigen Ergebnissen.
 */
public record AnalysisCommand(
        String analysisId,
        Map<String, String> configuration,
        List<Map<String, String>> previousResults) {
}
