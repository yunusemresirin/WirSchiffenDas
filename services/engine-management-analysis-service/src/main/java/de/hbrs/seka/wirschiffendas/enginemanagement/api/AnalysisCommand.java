package de.hbrs.seka.wirschiffendas.enginemanagement.api;

import java.util.List;
import java.util.Map;

/**
 * Auftrag an die Engine-Management-Analyse mit Konfiguration und bisherigen Ergebnissen.
 */
public record AnalysisCommand(
        String analysisId,
        Map<String, String> configuration,
        List<Map<String, String>> previousResults) {
}
