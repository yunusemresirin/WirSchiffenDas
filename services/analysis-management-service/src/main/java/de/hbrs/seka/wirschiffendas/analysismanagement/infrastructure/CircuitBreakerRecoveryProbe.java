package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Führt im HALF_OPEN-Zustand einen leichten Liveness-Probe gegen den Fluid-Service aus.
 *
 * Resilience4j wechselt nach der OPEN-Wartezeit zwar automatisch nach HALF_OPEN,
 * erzeugt aber selbst keinen Testaufruf. Dieser Probe liefert genau diesen erlaubten
 * Testaufruf, damit der Breaker ohne manuellen Retry wieder CLOSED oder OPEN werden kann.
 */
@Component
public class CircuitBreakerRecoveryProbe {

    private final CircuitBreaker circuitBreaker;
    private final RestClient probeClient;
    private final AnalysisApplicationService analysisService;

    public CircuitBreakerRecoveryProbe(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RestClient.Builder builder,
            @Value("${services.fluid.url}") String fluidUrl,
            AnalysisApplicationService analysisService) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("analysisServiceStarter");
        this.probeClient = builder.baseUrl(fluidUrl).build();
        this.analysisService = analysisService;
    }

    @Scheduled(fixedDelayString = "${circuit-breaker.recovery-probe-interval-ms:2000}")
    public void probeWhenHalfOpen() {
        if (circuitBreaker.getState() != CircuitBreaker.State.HALF_OPEN) {
            return;
        }

        try {
            circuitBreaker.executeRunnable(() ->
                    probeClient.get()
                            .uri("/actuator/health/liveness")
                            .retrieve()
                            .toBodilessEntity());

            if (circuitBreaker.getState() == CircuitBreaker.State.CLOSED) {
                analysisService.resumeRecoverableFailures(AlgorithmName.FLUID);
            }
        } catch (RuntimeException ignored) {
            // Der Circuit Breaker wertet den fehlgeschlagenen Probe aus und wechselt zurück nach OPEN.
        }
    }
}
