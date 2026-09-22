package de.hbrs.seka.wirschiffendas.fluid.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CircuitBreakerRecoveryProbeTest {

    @Test
    void requestsRecoveryWhenRegularCallClosesHalfOpenBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .permittedNumberOfCallsInHalfOpenState(1)
                .failureRateThreshold(50)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker breaker = registry.circuitBreaker("nextService");
        AnalysisManagementClient managementClient = mock(AnalysisManagementClient.class);

        CircuitBreakerRecoveryProbe probe = new CircuitBreakerRecoveryProbe(
                registry,
                RestClient.builder(),
                "http://localhost:1",
                managementClient);

        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();
        assertThat(breaker.tryAcquirePermission()).isTrue();
        breaker.onSuccess(0, TimeUnit.NANOSECONDS);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        probe.probeWhenHalfOpen();

        verify(managementClient).requestRecovery("THERMAL");
    }
}
