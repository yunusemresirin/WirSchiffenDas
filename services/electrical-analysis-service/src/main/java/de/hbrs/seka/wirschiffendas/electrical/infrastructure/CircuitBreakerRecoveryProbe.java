package de.hbrs.seka.wirschiffendas.electrical.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Führt in HALF_OPEN automatisch einen Liveness-Probe gegen den Engine-Management-Service aus.
 */
@Component
public class CircuitBreakerRecoveryProbe {

    private final CircuitBreaker circuitBreaker;
    private final RestClient probeClient;
    private final AnalysisManagementClient managementClient;
    private final AtomicBoolean recoveryNotificationPending = new AtomicBoolean();

    public CircuitBreakerRecoveryProbe(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RestClient.Builder builder,
            @Value("${services.next.url}") String nextUrl,
            AnalysisManagementClient managementClient) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("nextService");
        this.probeClient = builder.baseUrl(nextUrl).build();
        this.managementClient = managementClient;
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition() == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
                recoveryNotificationPending.set(true);
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

        if (circuitBreaker.getState() == CircuitBreaker.State.CLOSED
                && recoveryNotificationPending.compareAndSet(true, false)) {
            try {
                managementClient.requestRecovery("ENGINE_MANAGEMENT");

            } catch (RuntimeException ignored) {
                // Ein neuer Zustandswechsel während des Requests darf nicht verloren gehen.
                recoveryNotificationPending.set(true);
            }
        }
    }
}

