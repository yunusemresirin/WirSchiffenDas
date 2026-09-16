package de.hbrs.seka.wirschiffendas.fluid.infrastructure;

import de.hbrs.seka.wirschiffendas.fluid.api.AnalysisCommand;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Map;

@Component
public class NextServiceClient {
    private static final Logger log = LoggerFactory.getLogger(NextServiceClient.class);
    private final RestClient client;
    private final AnalysisManagementClient managementClient;

    public NextServiceClient(RestClient.Builder builder, @Value("${services.next.url}") String nextUrl,
                             AnalysisManagementClient managementClient) {
        this.client = builder.clone().baseUrl(nextUrl).build();
        this.managementClient = managementClient;
    }

    @CircuitBreaker(name = "nextService", fallbackMethod = "fallback")
    public void startNext(AnalysisCommand command, String currentResult) {
        var results = new ArrayList<>(command.previousResults());
        results.add(Map.of("algorithm", "FLUID", "result", currentResult));
        AnalysisCommand nextCommand = new AnalysisCommand(command.analysisId(), command.attemptId(), command.configuration(), results);
        client.post().uri("/internal/analyses").body(nextCommand).retrieve().toBodilessEntity();
    }

    private void fallback(AnalysisCommand command, String currentResult, Throwable throwable) {
        if (!definitelyNotAccepted(throwable)) {
            // The target may already be executing. Preserve its chance to deliver a successful callback.
            log.warn("Uncertain handoff to thermal-analysis-service for analysis {} attempt {}; management timeout handles recovery",
                    command.analysisId(), command.attemptId(), throwable);
            return;
        }
        managementClient.reportStatus(command.analysisId(), command.attemptId(), "THERMAL", "FAILED",
                "thermal-analysis-service could not accept the analysis: " + throwable.getClass().getSimpleName());
    }

    private boolean definitelyNotAccepted(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) return true;
        if (throwable instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError()) {
            return true;
        }
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException) return true;
        }
        return false;
    }
}
