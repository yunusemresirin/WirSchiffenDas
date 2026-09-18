package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.*;

import java.util.List;

/**
 * API-Antwort mit dem Zustand eines Analyse-Laufs und allen Algorithmus-Ausführungen.
 */
public record AnalysisResponse(
        String analysisId,
        String configurationId,
        AnalysisResult overallResult,
        List<AlgorithmResponse> algorithms) {

    public static AnalysisResponse from(AnalysisRun run) {
        return new AnalysisResponse(
                run.getAnalysisId(),
                run.getConfigurationId(),
                run.getOverallResult(),
                run.getExecutions().stream().map(AlgorithmResponse::from).toList());
    }

    public record AlgorithmResponse(
            AlgorithmName algorithm,
            AnalysisStatus status,
            AnalysisResult result,
            String message) {

        static AlgorithmResponse from(AlgorithmExecution execution) {
            return new AlgorithmResponse(
                    execution.getAlgorithm(),
                    execution.getStatus(),
                    execution.getResult(),
                    execution.getMessage());
        }
    }
}
