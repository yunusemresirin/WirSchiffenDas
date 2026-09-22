package de.hbrs.seka.wirschiffendas.electrical.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CircuitBreakerRecoveryProbeTest {
    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .minimumNumberOfCalls(1).permittedNumberOfCallsInHalfOpenState(1).build());
    private final AnalysisManagementClient management = mock(AnalysisManagementClient.class);

    @Test
    void regularHalfOpenCallTriggersRecoveryAndFailedNotificationIsRetried() {
        var probe = new CircuitBreakerRecoveryProbe(registry, RestClient.builder(), "http://next", management);
        var breaker = registry.circuitBreaker("nextService");
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();
        breaker.executeRunnable(() -> {}); // regulärer erfolgreicher Aufruf, kein geplanter Probe
        doThrow(new RuntimeException("management offline")).doNothing().when(management).requestRecovery("ENGINE_MANAGEMENT");
        probe.probeWhenHalfOpen();
        probe.probeWhenHalfOpen();
        probe.probeWhenHalfOpen();
        verify(management, times(2)).requestRecovery("ENGINE_MANAGEMENT");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void failedLivenessReopensBreakerAndSuccessfulLivenessRequestsRecovery() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var probe = new CircuitBreakerRecoveryProbe(registry, builder, "http://next", management);
        var breaker = registry.circuitBreaker("nextService");
        server.expect(requestTo("http://next/actuator/health/liveness")).andRespond(withServerError());
        server.expect(requestTo("http://next/actuator/health/liveness")).andRespond(withSuccess());
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();
        probe.probeWhenHalfOpen();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verifyNoInteractions(management);
        breaker.transitionToHalfOpenState();
        probe.probeWhenHalfOpen();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(management).requestRecovery("ENGINE_MANAGEMENT");
        server.verify();
    }
}
