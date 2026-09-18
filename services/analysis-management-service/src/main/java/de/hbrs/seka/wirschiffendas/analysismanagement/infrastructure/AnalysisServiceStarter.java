package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Startet den passenden Analyse-Service für einen Algorithmus und schützt den Aufruf per Circuit Breaker.
 */
@Component
public class AnalysisServiceStarter {

    private final RestClient.Builder builder;
    private final String fluidUrl;
    private final String thermalUrl;
    private final String electricalUrl;
    private final String engineManagementUrl;

    public AnalysisServiceStarter(
            RestClient.Builder builder,
            @Value("${services.fluid.url}") String fluidUrl,
            @Value("${services.thermal.url}") String thermalUrl,
            @Value("${services.electrical.url}") String electricalUrl,
            @Value("${services.engine-management.url}") String engineManagementUrl) {
        this.builder = builder;
        this.fluidUrl = fluidUrl;
        this.thermalUrl = thermalUrl;
        this.electricalUrl = electricalUrl;
        this.engineManagementUrl = engineManagementUrl;
    }

    /**
     * Sendet den Analyseauftrag an den zum Algorithmus gehörenden Service.
     */
    @CircuitBreaker(name = "analysisServiceStarter")
    public void start(AlgorithmName algorithm, AnalysisCommand command) {
        // Basis-URL des Zielservice anhand des Algorithmus wählen
        String baseUrl = switch (algorithm) {
            case FLUID -> fluidUrl;
            case THERMAL -> thermalUrl;
            case ELECTRICAL -> electricalUrl;
            case ENGINE_MANAGEMENT -> engineManagementUrl;
        };

        builder.baseUrl(baseUrl)
                .build()
                .post() // POST-Request
                .uri("/internal/analyses") // interner Start-Endpunkt des Analyse-Service
                .body(command) // Analyseauftrag als JSON serialisieren
                .retrieve() // Request ausführen
                .toBodilessEntity(); // Antwort ohne Body verarbeiten
    }
}
