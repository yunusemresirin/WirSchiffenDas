package de.hbrs.seka.wirschiffendas.enginemanagement.application;

import de.hbrs.seka.wirschiffendas.enginemanagement.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.enginemanagement.infrastructure.AnalysisManagementClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

class AnalysisWorkerTest {
    private final AnalysisManagementClient management = mock(AnalysisManagementClient.class);
    private final AnalysisWorker worker = new AnalysisWorker(management);

    private Map<String, String> configuration() {
        return new HashMap<>(Map.of("oilSystem", "STANDARD", "fuelSystem", "PREMIUM",
                "coolingSystem", "STANDARD", "electricalSystem", "PREMIUM",
                "engineManagementSystem", "ADVANCED"));
    }

    private AnalysisCommand command(Map<String, String> configuration) {
        return new AnalysisCommand("A-test", configuration, List.of(
                Map.of("algorithm", "FLUID", "result", "OK"),
                Map.of("algorithm", "THERMAL", "result", "OK"),
                Map.of("algorithm", "ELECTRICAL", "result", "OK")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STANDARD", "PREMIUM", "ADVANCED"})
    void acceptsEveryValidVariant(String variant) {
        var config = configuration();
        config.put("engineManagementSystem", variant);
        var command = command(config);
        worker.execute(command);
        verify(management).reportResult("A-test", "ENGINE_MANAGEMENT", "READY", "OK", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"engineManagementSystem"})
    void invalidStopsAtResponsibleWorker(String field) {
        var config = configuration();
        config.put(field, "INVALID");
        worker.execute(command(config));
        verify(management).reportResult(eq("A-test"), eq("ENGINE_MANAGEMENT"),
                eq("FAILED"), eq("FAILED"), contains("Invalid"));
    }
}
