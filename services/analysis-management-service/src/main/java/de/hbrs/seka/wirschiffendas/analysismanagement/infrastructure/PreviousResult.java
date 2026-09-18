package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisResult;

/**
 * Ergebnis eines bereits abgeschlossenen Vorgänger-Algorithmus.
 */
public record PreviousResult(AlgorithmName algorithm, AnalysisResult result) {
}
