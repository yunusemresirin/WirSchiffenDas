package de.hbrs.seka.wirschiffendas.enginemanagement.application;

import de.hbrs.seka.wirschiffendas.enginemanagement.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.enginemanagement.infrastructure.AnalysisManagementClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalysisWorkerVariantTest {

    private final AnalysisWorker worker = new AnalysisWorker(mock(AnalysisManagementClient.class));

    @ParameterizedTest
    @ValueSource(strings = {"STANDARD", "PREMIUM", "ADVANCED"})
    void acceptsValidVariants(String value) {
        AnalysisCommand command =
                new AnalysisCommand("A-test", Map.of("engineManagementSystem", value), List.of());

        assertThat(worker.validConfiguration(command)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID"})
    void rejectsInvalidVariant(String value) {
        AnalysisCommand command =
                new AnalysisCommand("A-test", Map.of("engineManagementSystem", value), List.of());

        assertThat(worker.validConfiguration(command)).isFalse();
    }
}
