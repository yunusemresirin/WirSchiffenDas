package de.hbrs.seka.wirschiffendas.enginemanagement.application;

import de.hbrs.seka.wirschiffendas.enginemanagement.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.enginemanagement.infrastructure.AnalysisManagementClient;
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
    private AnalysisWorker worker;

    @BeforeEach
    void setUp() {
        management = mock(AnalysisManagementClient.class);
        worker = new AnalysisWorker(management, new CommandRegistry(100, Duration.ofHours(1)), Duration.ZERO);
    }

    private AnalysisCommand command(Map<String, String> configuration) {
        return new AnalysisCommand("analysis", ATTEMPT, configuration, List.of(Map.of("algorithm", "FLUID", "result", "OK"), Map.of("algorithm", "THERMAL", "result", "OK"), Map.of("algorithm", "ELECTRICAL", "result", "OK")));
    }

    @Test
    void reportsEveryEquipmentAndPreservesAttempt() {
        AnalysisCommand command = command(Map.of("engineManagementSystem", "STANDARD"));
        worker.execute(command);
        verify(management).reportStatus("analysis", ATTEMPT, "ENGINE_MANAGEMENT", "RUNNING", "Analysis started");
        verify(management).reportResult("analysis", ATTEMPT, "ENGINE_MANAGEMENT", "READY", "OK",
                "All equipment checks passed", Map.of("engineManagementSystem", "OK"));
    }

    @Test
    void invalidEquipmentDoesNotSkipOtherEquipment() {
        var configuration = new HashMap<>(Map.of("engineManagementSystem", "STANDARD"));
        configuration.put("engineManagementSystem", "INVALID");
        worker.execute(command(configuration));
        verify(management).reportResult("analysis", ATTEMPT, "ENGINE_MANAGEMENT", "FAILED", "FAILED",
                "Invalid or missing equipment: engineManagementSystem", Map.of("engineManagementSystem", "FAILED"));
    }

    @Test
    void missingEquipmentFailsWithCompleteEquipmentMap() {
        var configuration = new HashMap<>(Map.of("engineManagementSystem", "STANDARD"));
        configuration.remove("engineManagementSystem");
        worker.execute(command(configuration));
        verify(management).reportResult("analysis", ATTEMPT, "ENGINE_MANAGEMENT", "FAILED", "FAILED",
                "Invalid or missing equipment: engineManagementSystem", Map.of("engineManagementSystem", "FAILED"));
    }

    @Test
    void repeatedCommandDoesNotRunOrForwardTwice() {
        AnalysisCommand command = command(Map.of("engineManagementSystem", "STANDARD"));
        worker.execute(command);
        worker.execute(command);
        verify(management, times(1)).reportResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void resultDeliveryFailureDoesNotContinueOrInventAContradictoryResult() {
        doThrow(new ResourceAccessException("response lost")).when(management)
                .reportResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        worker.execute(command(Map.of("engineManagementSystem", "STANDARD")));
        verify(management, never()).reportStatus(anyString(), anyString(), anyString(), eq("FAILED"), anyString());
    }

    @Test
    void initialCallbackFailureStopsProcessing() {
        doThrow(new ResourceAccessException("offline")).when(management)
                .reportStatus(anyString(), anyString(), anyString(), eq("RUNNING"), anyString());
        worker.execute(command(Map.of("engineManagementSystem", "STANDARD")));
        verify(management, never()).reportResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void rejectsEmptyMissingDuplicateUnknownAndFailedPrerequisites() {
        List<List<Map<String, String>>> invalid = List.of(
                List.of(),
                List.of(Map.of("algorithm", "FLUID", "result", "OK")),
                List.of(Map.of("algorithm", "FLUID", "result", "OK"), Map.of("algorithm", "FLUID", "result", "OK"), Map.of("algorithm", "ELECTRICAL", "result", "OK")),
                List.of(Map.of("algorithm", "FLUID", "result", "OK"), Map.of("algorithm", "UNKNOWN", "result", "OK"), Map.of("algorithm", "ELECTRICAL", "result", "OK")),
                List.of(Map.of("algorithm", "FLUID", "result", "OK"), Map.of("algorithm", "THERMAL", "result", "FAILED"), Map.of("algorithm", "ELECTRICAL", "result", "OK")));
        int index = 0;
        for (var previous : invalid) {
            worker.execute(new AnalysisCommand("invalid-" + index++, ATTEMPT, Map.of("engineManagementSystem", "STANDARD"), previous));
        }
        verify(management, times(invalid.size())).reportStatus(anyString(), eq(ATTEMPT), eq("ENGINE_MANAGEMENT"), eq("FAILED"),
                eq("Engine management requires exactly one OK result from FLUID, THERMAL and ELECTRICAL"));
        verify(management, never()).reportResult(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
    }

}
