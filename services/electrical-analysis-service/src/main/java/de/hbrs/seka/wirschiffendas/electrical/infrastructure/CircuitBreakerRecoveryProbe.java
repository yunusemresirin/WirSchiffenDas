package de.hbrs.seka.wirschiffendas.electrical.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Führt in HALF_OPEN automatisch einen Liveness-Probe gegen den Engine-Management-Service aus.
 */
@Component
public class CircuitBreakerRecoveryProbe {

    private final CircuitBreaker circuitBreaker;
    private final RestClient probeClient;

    public CircuitBreakerRecoveryProbe(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RestClient.Builder builder,
            @Value("${services.next.url}") String nextUrl) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("nextService");
        this.probeClient = builder.baseUrl(nextUrl).build();
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
        } catch (RuntimeException ignored) {
            // Fehlgeschlagener Probe wird vom Breaker gezählt und führt wieder nach OPEN.
        }
    }
}
