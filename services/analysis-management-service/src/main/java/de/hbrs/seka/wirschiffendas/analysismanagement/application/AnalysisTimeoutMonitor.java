package de.hbrs.seka.wirschiffendas.analysismanagement.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "analysis.timeout.enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisTimeoutMonitor {
    private final AnalysisApplicationService service;
    public AnalysisTimeoutMonitor(AnalysisApplicationService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${analysis.timeout.scan-interval-ms:1000}")
    public void expireInactiveRuns() { service.expireInactiveRuns(); }
}