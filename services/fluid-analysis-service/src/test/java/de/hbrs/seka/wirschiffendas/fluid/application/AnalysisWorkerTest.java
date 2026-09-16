package de.hbrs.seka.wirschiffendas.fluid.application;

import de.hbrs.seka.wirschiffendas.fluid.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.fluid.infrastructure.AnalysisManagementClient;
import de.hbrs.seka.wirschiffendas.fluid.infrastructure.NextServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalysisWorkerTest {
    private static final String ATTEMPT = "d9ef0df2-b59b-414e-a3b1-8f908abf8352";
    private AnalysisManagementClient management;
    private NextServiceClient next;
    private AnalysisWorker worker;

    @BeforeEach
    void setUp() {
        management = mock(AnalysisManagementClient.class);
        next = mock(NextServiceClient.class);
        worker = new AnalysisWorker(management, next, new CommandRegistry(100, Duration.ofHours(1)), Duration.ZERO);
    }

    private AnalysisCommand command(Map<String, String> configuration) {
        return new AnalysisCommand("analysis", ATTEMPT, configuration, List.of());
    }

    @Test
    void reportsEveryEquipmentAndPreservesAttempt() {
        AnalysisCommand command = command(Map.of("oilSystem", "STANDARD", "fuelSystem", "STANDARD"));
        worker.execute(command);
        verify(management).reportStatus("analysis", ATTEMPT, "FLUID", "RUNNING", "Analysis started");
        verify(management).reportResult("analysis", ATTEMPT, "FLUID", "READY", "OK",
                "All equipment checks passed", Map.of("oilSystem", "OK", "fuelSystem", "OK"));
        verify(next).startNext(command, "OK");
    }

    @Test
    void invalidEquipmentDoesNotSkipOtherEquipment() {
        var configuration = new HashMap<>(Map.of("oilSystem", "STANDARD", "fuelSystem", "STANDARD"));
        configuration.put("oilSystem", "INVALID");
        worker.execute(command(configuration));
        verify(management).reportResult("analysis", ATTEMPT, "FLUID", "FAILED", "FAILED",
                "Invalid or missing equipment: oilSystem", Map.of("oilSystem", "FAILED", "fuelSystem", "OK"));
        verifyNoInteractions(next);
    }

    @Test
    void missingEquipmentFailsWithCompleteEquipmentMap() {
        var configuration = new HashMap<>(Map.of("oilSystem", "STANDARD", "fuelSystem", "STANDARD"));
        configuration.remove("oilSystem");
        worker.execute(command(configuration));
        verify(management).reportResult("analysis", ATTEMPT, "FLUID", "FAILED", "FAILED",
                "Invalid or missing equipment: oilSystem", Map.of("oilSystem", "FAILED", "fuelSystem", "OK"));
    }

    @Test
    void repeatedCommandDoesNotRunOrForwardTwice() {
        AnalysisCommand command = command(Map.of("oilSystem", "STANDARD", "fuelSystem", "STANDARD"));
        worker.execute(command);
        worker.execute(command);
        verify(management, times(1)).reportResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        verify(next, times(1)).startNext(command, "OK");
    }

    @Test
    void resultDeliveryFailureDoesNotContinueOrInventAContradictoryResult() {
        doThrow(new ResourceAccessException("response lost")).when(management)
                .reportResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        worker.execute(command(Map.of("oilSystem", "STANDARD", "fuelSystem", "STANDARD")));
        verify(management, never()).reportStatus(anyString(), anyString(), anyString(), eq("FAILED"), anyString());
        verifyNoInteractions(next);
    }

    @Test
    void initialCallbackFailureStopsProcessing() {
        doThrow(new ResourceAccessException("offline")).when(management)
                .reportStatus(anyString(), anyString(), anyString(), eq("RUNNING"), anyString());
        worker.execute(command(Map.of("oilSystem", "STANDARD", "fuelSystem", "STANDARD")));
        verify(management, never()).reportResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        verifyNoInteractions(next);
    }

}
