package de.hbrs.seka.wirschiffendas.fluid.application;

import de.hbrs.seka.wirschiffendas.fluid.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.fluid.infrastructure.AnalysisManagementClient;
import de.hbrs.seka.wirschiffendas.fluid.infrastructure.NextServiceClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalysisWorkerVariantTest {

    private final AnalysisWorker worker =
            new AnalysisWorker(mock(AnalysisManagementClient.class), mock(NextServiceClient.class));

    @ParameterizedTest
    @CsvSource({
            "oilSystem, STANDARD", "oilSystem, PREMIUM", "oilSystem, ADVANCED",
            "fuelSystem, STANDARD", "fuelSystem, PREMIUM", "fuelSystem, ADVANCED"
    })
    void acceptsValidVariantForEachFluidField(String field, String value) {
        assertThat(worker.validConfiguration(command(field, value))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"oilSystem", "fuelSystem"})
    void rejectsInvalidVariantForEachFluidField(String field) {
        assertThat(worker.validConfiguration(command(field, "INVALID"))).isFalse();
    }

    private AnalysisCommand command(String field, String value) {
        Map<String, String> configuration = new HashMap<>();
        configuration.put("oilSystem", "STANDARD");
        configuration.put("fuelSystem", "STANDARD");
        configuration.put(field, value);
        return new AnalysisCommand("A-test", configuration, List.of());
    }
}
