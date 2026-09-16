package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
public class AnalysisServiceStarter {
    private final Map<AlgorithmName, RestClient> clients;
    private final CircuitBreakerRegistry breakers;
    private static final Map<AlgorithmName, String> BREAKER_NAMES = Map.of(
            AlgorithmName.FLUID, "startFluid", AlgorithmName.THERMAL, "startThermal",
            AlgorithmName.ELECTRICAL, "startElectrical", AlgorithmName.ENGINE_MANAGEMENT, "startEngineManagement");

    public AnalysisServiceStarter(RestClient.Builder builder, CircuitBreakerRegistry breakers,
            @Value("${services.fluid.url}") String fluidUrl,
            @Value("${services.thermal.url}") String thermalUrl,
            @Value("${services.electrical.url}") String electricalUrl,
            @Value("${services.engine-management.url}") String engineManagementUrl) {
        this.breakers = breakers;
        // Each target has an immutable client and its own breaker state.
        clients = Map.of(
                AlgorithmName.FLUID, builder.clone().baseUrl(fluidUrl).build(),
                AlgorithmName.THERMAL, builder.clone().baseUrl(thermalUrl).build(),
                AlgorithmName.ELECTRICAL, builder.clone().baseUrl(electricalUrl).build(),
                AlgorithmName.ENGINE_MANAGEMENT, builder.clone().baseUrl(engineManagementUrl).build());
    }

    public void start(AlgorithmName algorithm, AnalysisCommand command) {
        breakers.circuitBreaker(BREAKER_NAMES.get(algorithm)).executeRunnable(() ->
                clients.get(algorithm).post().uri("/internal/analyses").body(command)
                        .retrieve().toBodilessEntity());
    }
}