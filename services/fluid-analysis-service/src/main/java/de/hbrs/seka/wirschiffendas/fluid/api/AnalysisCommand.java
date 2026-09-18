package de.hbrs.seka.wirschiffendas.fluid.api;

import java.util.List;
import java.util.Map;

/**
 * Auftrag an die Fluid-Analyse mit Konfiguration und bisherigen Ergebnissen.
 */
public record AnalysisCommand(
        String analysisId,
        Map<String, String> configuration,
        List<Map<String, String>> previousResults) {
}
