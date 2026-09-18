package de.hbrs.seka.wirschiffendas.electrical.infrastructure;

import de.hbrs.seka.wirschiffendas.electrical.api.AnalysisCommand;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.ArrayList;
import java.util.Map;

@Component
public class NextServiceClient {
    private final RestClient client;
    private final AnalysisManagementClient managementClient;
    public NextServiceClient(RestClient.Builder builder, @Value("${services.next.url}") String nextUrl, AnalysisManagementClient managementClient) { this.client = builder.baseUrl(nextUrl).build(); this.managementClient = managementClient; }
    @CircuitBreaker(name = "nextService", fallbackMethod = "fallback")
    public void startNext(AnalysisCommand command, String currentResult) {
        var results = new ArrayList<>(command.previousResults());
        results.add(Map.of("algorithm", "ELECTRICAL", "result", currentResult));
        client.post().uri("/internal/analyses").body(new AnalysisCommand(command.analysisId(), command.configuration(), results)).retrieve().toBodilessEntity();
    }
    private void fallback(AnalysisCommand command, String currentResult, Throwable throwable) {
        managementClient.reportStatus(command.analysisId(), "ENGINE_MANAGEMENT", "FAILED", "engine-management-analysis-service unavailable");
    }
}
