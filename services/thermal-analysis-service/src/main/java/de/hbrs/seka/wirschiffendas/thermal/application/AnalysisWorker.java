package de.hbrs.seka.wirschiffendas.thermal.application;

import de.hbrs.seka.wirschiffendas.thermal.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.thermal.infrastructure.AnalysisManagementClient;
import de.hbrs.seka.wirschiffendas.thermal.infrastructure.NextServiceClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AnalysisWorker {
    private static final String ALGORITHM = "THERMAL";
    private final AnalysisManagementClient managementClient;
    private final NextServiceClient nextServiceClient;
    public AnalysisWorker(AnalysisManagementClient managementClient, NextServiceClient nextServiceClient) {
        this.managementClient = managementClient;
        this.nextServiceClient = nextServiceClient;
    }
    @Async
    public void execute(AnalysisCommand command) {
        managementClient.reportStatus(command.analysisId(), ALGORITHM, "RUNNING", null);
        if (!pause(command.analysisId())) return;
        boolean ok = valid(command.configuration().get("coolingSystem"));
        String result = ok ? "OK" : "FAILED";
        managementClient.reportResult(command.analysisId(), ALGORITHM, ok ? "READY" : "FAILED", result, null);
        if (ok) nextServiceClient.startNext(command, result);
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
