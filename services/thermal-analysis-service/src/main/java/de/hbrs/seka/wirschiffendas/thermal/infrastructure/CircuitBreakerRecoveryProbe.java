package de.hbrs.seka.wirschiffendas.thermal.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Führt in HALF_OPEN automatisch einen Liveness-Probe gegen den Electrical-Service aus.
 */
@Component
public class CircuitBreakerRecoveryProbe {

    private final CircuitBreaker circuitBreaker;
    private final RestClient probeClient;
    private final AnalysisManagementClient managementClient;
    private volatile boolean recoveryNotificationPending;

    public CircuitBreakerRecoveryProbe(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RestClient.Builder builder,
            @Value("${services.next.url}") String nextUrl,
            AnalysisManagementClient managementClient) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("nextService");
        this.probeClient = builder.baseUrl(nextUrl).build();
        this.managementClient = managementClient;
        this.circuitBreaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition() == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
                recoveryNotificationPending = true;
            }
        });
    }

    @Scheduled(fixedDelayString = "${circuit-breaker.recovery-probe-interval-ms:2000}")
    public void probeWhenHalfOpen() {
        if (circuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN) {
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

        if (recoveryNotificationPending && circuitBreaker.getState() == CircuitBreaker.State.CLOSED) {
            try {
                managementClient.requestRecovery("ELECTRICAL");
                recoveryNotificationPending = false;
            } catch (RuntimeException ignored) {
                // Analysis Management kann vorübergehend nicht erreichbar sein; beim nächsten Tick erneut versuchen.
            }
        }
    }
}
