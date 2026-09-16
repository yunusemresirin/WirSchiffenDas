package de.hbrs.seka.wirschiffendas.electrical.application;

import de.hbrs.seka.wirschiffendas.electrical.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.electrical.infrastructure.AnalysisManagementClient;
import de.hbrs.seka.wirschiffendas.electrical.infrastructure.NextServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisWorker {
    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);
    private static final String ALGORITHM = "ELECTRICAL";
    private static final List<String> EQUIPMENT = List.of("electricalSystem");

    private final AnalysisManagementClient managementClient;
    private final NextServiceClient nextServiceClient;
    private final CommandRegistry registry;
    private final Duration processingDelay;

    public AnalysisWorker(AnalysisManagementClient managementClient, NextServiceClient nextServiceClient, CommandRegistry registry,
                          @Value("${worker.processing-delay:PT2S}") Duration processingDelay) {
        if (processingDelay.isNegative()) throw new IllegalArgumentException("Processing delay cannot be negative");
        this.managementClient = managementClient;
        this.nextServiceClient = nextServiceClient;
        this.registry = registry;
        this.processingDelay = processingDelay;
    }

    @Async
    public void execute(AnalysisCommand command) {
        if (!registry.begin(command.analysisId(), command.attemptId())) return;
        try {
            managementClient.reportStatus(command.analysisId(), command.attemptId(), ALGORITHM, "RUNNING", "Analysis started");
            if (!pause(command)) return;

            Map<String, String> equipmentResults = new LinkedHashMap<>();
            for (String equipment : EQUIPMENT) {
                equipmentResults.put(equipment, valid(command.configuration().get(equipment)) ? "OK" : "FAILED");
            }
            List<String> failedEquipment = equipmentResults.entrySet().stream()
                    .filter(entry -> "FAILED".equals(entry.getValue())).map(Map.Entry::getKey).toList();
            boolean ok = failedEquipment.isEmpty();
            String result = ok ? "OK" : "FAILED";
            String message = ok ? "All equipment checks passed"
                    : "Invalid or missing equipment: " + String.join(", ", failedEquipment);
            managementClient.reportResult(command.analysisId(), command.attemptId(), ALGORITHM,
                    ok ? "READY" : "FAILED", result, message, equipmentResults);
            if (ok) nextServiceClient.startNext(command, result);
        } catch (RestClientException exception) {
            // Delivery may have succeeded remotely. Do not send a contradictory result or continue the chain.
            log.warn("Stopping {} for analysis {} attempt {} after a delivery failure; management timeout handles recovery",
                    ALGORITHM, command.analysisId(), command.attemptId(), exception);
        } finally {
            registry.complete(command.analysisId(), command.attemptId());
        }
    }

    private boolean pause(AnalysisCommand command) {
        try {
            Thread.sleep(processingDelay.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            managementClient.reportStatus(command.analysisId(), command.attemptId(), ALGORITHM, "FAILED", "Analysis interrupted");
            return false;
        }
    }

    private boolean valid(String value) {
        return value != null && !value.isBlank() && !"INVALID".equalsIgnoreCase(value.trim());
    }
}
