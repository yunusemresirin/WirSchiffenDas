package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import jakarta.validation.constraints.NotBlank;

public record StartAnalysisRequest(@NotBlank String configurationId) {
}
