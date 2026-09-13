package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisResult;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisStatus;
import jakarta.validation.constraints.NotNull;

public record ResultUpdateRequest(
        @NotNull AnalysisStatus status,
        @NotNull AnalysisResult result,
        String message) {
}
