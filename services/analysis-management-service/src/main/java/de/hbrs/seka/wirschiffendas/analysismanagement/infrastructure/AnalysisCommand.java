package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import java.util.List;

public record AnalysisCommand(
        String analysisId,
        String attemptId,
        ConfigurationSnapshot configuration,
        List<PreviousResult> previousResults) {
}