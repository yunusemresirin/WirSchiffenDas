package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Isoliert Start- und Probe-Aufrufe je Zielservice mit demselben Circuit Breaker. */
@Component
public class AnalysisServiceStarter {
    private final CircuitBreakerRegistry registry;
    private final Map<AlgorithmName, RestClient> clients;

    public AnalysisServiceStarter(CircuitBreakerRegistry registry, RestClient.Builder builder,
            @Value("${services.fluid.url}") String fluidUrl,
            @Value("${services.thermal.url}") String thermalUrl,
            @Value("${services.electrical.url}") String electricalUrl,
            @Value("${services.engine-management.url}") String engineManagementUrl) {
        this.registry = registry;
        this.clients = Map.of(
                AlgorithmName.FLUID, builder.clone().baseUrl(fluidUrl).build(),
                AlgorithmName.THERMAL, builder.clone().baseUrl(thermalUrl).build(),
                AlgorithmName.ELECTRICAL, builder.clone().baseUrl(electricalUrl).build(),
                AlgorithmName.ENGINE_MANAGEMENT, builder.clone().baseUrl(engineManagementUrl).build());
    }

    public CircuitBreaker circuitBreaker(AlgorithmName algorithm) {
        String name = switch (algorithm) {
            case FLUID -> "analysisServiceStarterFluid";
            case THERMAL -> "analysisServiceStarterThermal";
            case ELECTRICAL -> "analysisServiceStarterElectrical";
            case ENGINE_MANAGEMENT -> "analysisServiceStarterEngineManagement";
        };
        return registry.circuitBreaker(name);
    }

    public void start(AlgorithmName algorithm, AnalysisCommand command) {
        circuitBreaker(algorithm).executeRunnable(() -> clients.get(algorithm).post()
                .uri("/internal/analyses").body(command).retrieve().toBodilessEntity());
    }

    public void probe(AlgorithmName algorithm) {
        circuitBreaker(algorithm).executeRunnable(() -> clients.get(algorithm).get()
                .uri("/actuator/health/liveness").retrieve().toBodilessEntity());
    }
}
