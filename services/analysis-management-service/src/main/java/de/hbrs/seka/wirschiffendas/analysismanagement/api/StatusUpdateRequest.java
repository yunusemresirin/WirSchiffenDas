package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotBlank String attemptId,
        @NotNull AnalysisStatus status,
        @Size(max = 1000) String message) {
}