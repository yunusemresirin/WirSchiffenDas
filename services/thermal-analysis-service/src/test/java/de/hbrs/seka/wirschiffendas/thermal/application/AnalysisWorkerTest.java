package de.hbrs.seka.wirschiffendas.thermal.application;

import de.hbrs.seka.wirschiffendas.thermal.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.thermal.infrastructure.AnalysisManagementClient;
import de.hbrs.seka.wirschiffendas.thermal.infrastructure.NextServiceClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

class AnalysisWorkerTest {
    private final AnalysisManagementClient management = mock(AnalysisManagementClient.class);
    private final NextServiceClient next = mock(NextServiceClient.class);
    private final AnalysisWorker worker = new AnalysisWorker(management, next);

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
        config.put("coolingSystem", variant);
        var command = command(config);
        worker.execute(command);
        verify(management).reportResult("A-test", "THERMAL", "READY", "OK", null);
        verify(next).startNext(command, "OK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"coolingSystem"})
    void invalidStopsAtResponsibleWorker(String field) {
        var config = configuration();
        config.put(field, "INVALID");
        worker.execute(command(config));
        verify(management).reportResult(eq("A-test"), eq("THERMAL"),
                eq("FAILED"), eq("FAILED"), contains("Invalid"));
        verifyNoInteractions(next);
    }
}
