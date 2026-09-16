package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AnalysisServiceStarterTest {
    @Test
    void failedThermalRetryDoesNotOpenFluidOrOtherTargets() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://thermal/internal/analyses")).andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        server.expect(requestTo("http://fluid/internal/analyses")).andRespond(withNoContent());
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(1).failureRateThreshold(50).build());
        AnalysisServiceStarter starter = new AnalysisServiceStarter(builder, breakers,
                "http://fluid", "http://thermal", "http://electrical", "http://engine");
        AnalysisCommand command = new AnalysisCommand("A-1", "attempt-1",
                new ConfigurationSnapshot("C-1", "STANDARD", "STANDARD", "STANDARD", "STANDARD", "STANDARD"),
                List.of());
        assertThatThrownBy(() -> starter.start(AlgorithmName.THERMAL, command)).isInstanceOf(RuntimeException.class);
        assertThat(breakers.circuitBreaker("startThermal").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        starter.start(AlgorithmName.FLUID, command);
        assertThat(breakers.circuitBreaker("startFluid").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breakers.circuitBreaker("startElectrical").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breakers.circuitBreaker("startEngineManagement").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }
}