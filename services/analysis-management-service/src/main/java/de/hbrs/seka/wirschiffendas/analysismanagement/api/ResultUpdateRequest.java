package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisResult;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ResultUpdateRequest(
        @NotBlank String attemptId,
        @NotNull AnalysisStatus status,
        @NotNull AnalysisResult result,
        @Size(max = 1000) String message,
        @NotNull Map<String, AnalysisResult> equipmentResults) {
}