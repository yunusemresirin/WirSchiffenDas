package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class CircuitBreakerRecoveryProbeTest {
    @Test
    void scansAgainWithoutAnotherTransitionAndKeepsTargetsIsolated() {
        var starter = mock(AnalysisServiceStarter.class);
        var service = mock(AnalysisApplicationService.class);
        for (var algorithm : AlgorithmName.values()) {
            when(starter.circuitBreaker(algorithm)).thenReturn(CircuitBreaker.ofDefaults(algorithm.name()));
        }
        var thermal = starter.circuitBreaker(AlgorithmName.THERMAL);
        thermal.transitionToOpenState();
        var probe = new CircuitBreakerRecoveryProbe(starter, service);
        probe.probeWhenHalfOpen();
        verify(service, never()).resumeRecoverableFailures(AlgorithmName.THERMAL);
        thermal.transitionToHalfOpenState();
        doAnswer(invocation -> { thermal.transitionToClosedState(); return null; })
                .when(starter).probe(AlgorithmName.THERMAL);
        probe.probeWhenHalfOpen();
        probe.probeWhenHalfOpen();
        verify(starter).probe(AlgorithmName.THERMAL);
        verify(service, times(2)).resumeRecoverableFailures(AlgorithmName.THERMAL);
        verify(service, times(3)).resumeRecoverableFailures(AlgorithmName.FLUID);
    }
}
