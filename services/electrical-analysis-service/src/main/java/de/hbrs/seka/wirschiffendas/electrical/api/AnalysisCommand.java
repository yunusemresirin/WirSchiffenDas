package de.hbrs.seka.wirschiffendas.electrical.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AnalysisCommand(
        @NotBlank String analysisId,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String attemptId,
        @NotNull Map<String, String> configuration,
        List<Map<String, String>> previousResults) {

    public AnalysisCommand {
        if (configuration != null) configuration = Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        previousResults = previousResults == null ? List.of() : previousResults.stream()
                .map(result -> result == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(result)))
                .toList();
    }
}
