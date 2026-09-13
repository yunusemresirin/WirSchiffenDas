package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import java.util.List;

public record AnalysisCommand(
        String analysisId,
        ConfigurationSnapshot configuration,
        List<PreviousResult> previousResults) {
}
