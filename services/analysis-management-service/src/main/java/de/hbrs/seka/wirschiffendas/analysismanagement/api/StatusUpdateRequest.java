package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Interner Callback-Body für ein Status-Update eines Algorithmus.
 */
public record StatusUpdateRequest(
        @NotNull AnalysisStatus status,
        String message) {
}
