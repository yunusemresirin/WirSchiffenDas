package de.hbrs.seka.wirschiffendas.enginemanagement.application;

import de.hbrs.seka.wirschiffendas.enginemanagement.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.enginemanagement.infrastructure.AnalysisManagementClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AnalysisWorker {
    private static final String ALGORITHM = "ENGINE_MANAGEMENT";
    private final AnalysisManagementClient managementClient;
    public AnalysisWorker(AnalysisManagementClient managementClient) { this.managementClient = managementClient; }
    @Async
    public void execute(AnalysisCommand command) {
        managementClient.reportStatus(command.analysisId(), ALGORITHM, "RUNNING", null);
        if (!pause(command.analysisId())) return;
        boolean previousOk = command.previousResults().stream().noneMatch(result -> "FAILED".equalsIgnoreCase(result.get("result")));
        boolean configOk = valid(command.configuration().get("engineManagementSystem"));
        boolean ok = previousOk && configOk;
        managementClient.reportResult(command.analysisId(), ALGORITHM, ok ? "READY" : "FAILED", ok ? "OK" : "FAILED", null);
    }
    private boolean pause(String analysisId) {
        try { Thread.sleep(2000); return true; }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            managementClient.reportResult(analysisId, ALGORITHM, "FAILED", "FAILED", "Analysis interrupted");
            return false;
        }
    }
    private boolean valid(String value) { return value != null && !"INVALID".equalsIgnoreCase(value); }
}
