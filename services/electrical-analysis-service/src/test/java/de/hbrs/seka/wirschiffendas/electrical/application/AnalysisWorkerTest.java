package de.hbrs.seka.wirschiffendas.electrical.application;

import de.hbrs.seka.wirschiffendas.electrical.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.electrical.infrastructure.AnalysisManagementClient;
import de.hbrs.seka.wirschiffendas.electrical.infrastructure.NextServiceClient;
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
        config.put("electricalSystem", variant);
        var command = command(config);
        worker.execute(command);
        verify(management).reportResult("A-test", "ELECTRICAL", "READY", "OK", null);
        verify(next).startNext(command, "OK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"electricalSystem"})
    void invalidStopsAtResponsibleWorker(String field) {
        var config = configuration();
        config.put(field, "INVALID");
        worker.execute(command(config));
        verify(management).reportResult(eq("A-test"), eq("ELECTRICAL"),
                eq("FAILED"), eq("FAILED"), contains("Invalid"));
        verifyNoInteractions(next);
    }
}
