package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Führt HALF_OPEN-Liveness-Probes pro Zielservice aus.
 *
 * Probe-Ziel, Circuit Breaker und fortzusetzender Algorithmus gehören dabei
 * immer zum selben Recovery-Target.
 */
@Component
public class CircuitBreakerRecoveryProbe {

    private final List<RecoveryTarget> targets;
    private final AnalysisApplicationService analysisService;

    public CircuitBreakerRecoveryProbe(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RestClient.Builder builder,
            @Value("${services.fluid.url}") String fluidUrl,
            @Value("${services.thermal.url}") String thermalUrl,
            @Value("${services.electrical.url}") String electricalUrl,
            @Value("${services.engine-management.url}") String engineManagementUrl,
            AnalysisApplicationService analysisService) {
        this.targets = List.of(
                target(circuitBreakerRegistry, builder, AlgorithmName.FLUID, fluidUrl),
                target(circuitBreakerRegistry, builder, AlgorithmName.THERMAL, thermalUrl),
                target(circuitBreakerRegistry, builder, AlgorithmName.ELECTRICAL, electricalUrl),
                target(circuitBreakerRegistry, builder, AlgorithmName.ENGINE_MANAGEMENT, engineManagementUrl));
        this.analysisService = analysisService;
    }

    @Scheduled(fixedDelayString = "${circuit-breaker.recovery-probe-interval-ms:2000}")
    public void probeWhenHalfOpen() {
        targets.forEach(this::probe);
    }

    private void probe(RecoveryTarget target) {
        if (target.circuitBreaker().getState() != CircuitBreaker.State.HALF_OPEN) {
            return;
        }

        try {
            target.circuitBreaker().executeRunnable(() ->
                    target.probeClient().get()
                            .uri("/actuator/health/liveness")
                            .retrieve()
                            .toBodilessEntity());

            if (target.circuitBreaker().getState() == CircuitBreaker.State.CLOSED) {
                analysisService.resumeRecoverableFailures(target.algorithm());
            }
        } catch (RuntimeException ignored) {
            // Der fehlgeschlagene Probe wird vom zugehörigen Breaker gewertet.
        }
    }

    private RecoveryTarget target(
            CircuitBreakerRegistry registry,
            RestClient.Builder builder,
            AlgorithmName algorithm,
            String baseUrl) {
        return new RecoveryTarget(
                algorithm,
                registry.circuitBreaker(AnalysisServiceStarter.breakerName(algorithm)),
                builder.clone().baseUrl(baseUrl).build());
    }

    private record RecoveryTarget(
            AlgorithmName algorithm,
            CircuitBreaker circuitBreaker,
            RestClient probeClient) {
    }
}
