package de.hbrs.seka.wirschiffendas.thermal.infrastructure;

import de.hbrs.seka.wirschiffendas.thermal.api.AnalysisCommand;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.ArrayList;
import java.util.Map;

/**
 * Startet den nächsten Algorithmus in der Analyse-Kette und meldet bei Nichterreichbarkeit einen Fehler.
 */
@Component
public class NextServiceClient {

    private final RestClient client;
    private final AnalysisManagementClient managementClient;

    public NextServiceClient(
        RestClient.Builder builder, 
        @Value("${services.next.url}") String nextUrl, 
        AnalysisManagementClient managementClient
    ) {
        this.client = builder.baseUrl(nextUrl).build(); 
        this.managementClient = managementClient;
    }

    /**
     * Übergibt das eigene Ergebnis an den nächsten Service der Kette.
     */
    @CircuitBreaker(name = "nextService", fallbackMethod = "fallback")
    public void startNext(AnalysisCommand command, String currentResult) {
        var results = new ArrayList<>(command.previousResults());

        results.add(Map.of(
            "algorithm", "THERMAL", 
            "result", currentResult
        ));

        client
            .post() // POST-Request
            .uri("/internal/analyses") // Endpunkt des nächsten Service
            .body(new AnalysisCommand(command.analysisId(), command.configuration(), results)) // Folgeauftrag mit angereicherten Ergebnissen
            .retrieve() // Request ausführen
            .toBodilessEntity(); // Antwort ohne Body verarbeiten
    }

    /**
     * Fallback des Circuit Breakers: meldet den nächsten Service als nicht erreichbar.
     */
    private void fallback(AnalysisCommand command, String currentResult, Throwable throwable) {
        managementClient.reportStatus(
            command.analysisId(), 
            "ELECTRICAL", 
            "FAILED", 
            "electrical-analysis-service unavailable"
        );
    }
}
