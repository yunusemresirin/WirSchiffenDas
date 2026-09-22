package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.EnumMap;
import java.util.Map;

/**
 * Startet den passenden Analyse-Service für einen Algorithmus.
 *
 * Jeder Zielservice besitzt einen eigenen Circuit Breaker, damit ein Ausfall beim
 * Retry eines Algorithmus keine Aufrufe zu einem anderen Ziel beeinflusst.
 */
@Component
public class AnalysisServiceStarter {

    private final Map<AlgorithmName, RestClient> clients = new EnumMap<>(AlgorithmName.class);
    private final Map<AlgorithmName, CircuitBreaker> circuitBreakers = new EnumMap<>(AlgorithmName.class);

    public AnalysisServiceStarter(
            RestClient.Builder builder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${services.fluid.url}") String fluidUrl,
            @Value("${services.thermal.url}") String thermalUrl,
            @Value("${services.electrical.url}") String electricalUrl,
            @Value("${services.engine-management.url}") String engineManagementUrl) {
        clients.put(AlgorithmName.FLUID, builder.clone().baseUrl(fluidUrl).build());
        clients.put(AlgorithmName.THERMAL, builder.clone().baseUrl(thermalUrl).build());
        clients.put(AlgorithmName.ELECTRICAL, builder.clone().baseUrl(electricalUrl).build());
        clients.put(AlgorithmName.ENGINE_MANAGEMENT, builder.clone().baseUrl(engineManagementUrl).build());

        for (AlgorithmName algorithm : AlgorithmName.values()) {
            circuitBreakers.put(
                    algorithm,
                    circuitBreakerRegistry.circuitBreaker(breakerName(algorithm)));
        }
    }

    /**
     * Sendet den Analyseauftrag an den zum Algorithmus gehörenden Service.
     */
    public void start(AlgorithmName algorithm, AnalysisCommand command) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(algorithm);
        RestClient client = clients.get(algorithm);

        circuitBreaker.executeRunnable(() ->
                client.post()
                        .uri("/internal/analyses")
                        .body(command)
                        .retrieve()
                        .toBodilessEntity());
    }

    static String breakerName(AlgorithmName algorithm) {
        return switch (algorithm) {
            case FLUID -> "analysisServiceStarterFluid";
            case THERMAL -> "analysisServiceStarterThermal";
            case ELECTRICAL -> "analysisServiceStarterElectrical";
            case ENGINE_MANAGEMENT -> "analysisServiceStarterEngineManagement";
        };
    }
}
