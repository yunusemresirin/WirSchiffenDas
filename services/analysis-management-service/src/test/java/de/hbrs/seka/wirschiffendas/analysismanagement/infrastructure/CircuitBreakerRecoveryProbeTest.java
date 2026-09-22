package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CircuitBreakerRecoveryProbeTest {

    @ParameterizedTest
    @CsvSource({
            "FLUID, http://fluid.test",
            "THERMAL, http://thermal.test",
            "ELECTRICAL, http://electrical.test",
            "ENGINE_MANAGEMENT, http://engine.test"
    })
    void probesMatchingTargetAndResumesSameAlgorithm(
            AlgorithmName algorithm,
            String expectedBaseUrl) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .permittedNumberOfCallsInHalfOpenState(1)
                .failureRateThreshold(50)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AnalysisApplicationService analysisService = mock(AnalysisApplicationService.class);

        CircuitBreakerRecoveryProbe probe = new CircuitBreakerRecoveryProbe(
                registry,
                builder,
                "http://fluid.test",
                "http://thermal.test",
                "http://electrical.test",
                "http://engine.test",
                analysisService);

        CircuitBreaker breaker =
                registry.circuitBreaker(AnalysisServiceStarter.breakerName(algorithm));
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();

        server.expect(once(), requestTo(expectedBaseUrl + "/actuator/health/liveness"))
                .andRespond(withSuccess());

        probe.probeWhenHalfOpen();

        server.verify();
        verify(analysisService).resumeRecoverableFailures(algorithm);
        verifyNoMoreInteractions(analysisService);
    }
}
