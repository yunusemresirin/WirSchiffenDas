package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    @CircuitBreaker(name = "analysisServiceStarter")
    public void start(AlgorithmName algorithm, AnalysisCommand command) {
        String baseUrl = switch (algorithm) {
            case FLUID -> fluidUrl;
            case THERMAL -> thermalUrl;
            case ELECTRICAL -> electricalUrl;
            case ENGINE_MANAGEMENT -> engineManagementUrl;
        };

        builder.baseUrl(baseUrl)
                .build()
                .post()
                .uri("/internal/analyses")
                .body(command)
                .retrieve()
                .toBodilessEntity();
    }
}
