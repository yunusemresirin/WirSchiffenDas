package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AnalysisServiceStarterTest {
    @Test
    void thermalFailureDoesNotBlockFluidAndThermalProbeUsesThermal() {
        var registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1).permittedNumberOfCallsInHalfOpenState(1).build());
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var starter = new AnalysisServiceStarter(registry, builder,
                "http://fluid", "http://thermal", "http://electrical", "http://engine");
        server.expect(requestTo("http://thermal/internal/analyses")).andRespond(withServerError());
        server.expect(requestTo("http://fluid/internal/analyses")).andRespond(withSuccess());
        server.expect(requestTo("http://thermal/actuator/health/liveness")).andRespond(withSuccess());
        var command = new AnalysisCommand("A-1", null, List.of());
        assertThatThrownBy(() -> starter.start(AlgorithmName.THERMAL, command)).isInstanceOf(RuntimeException.class);
        assertThat(starter.circuitBreaker(AlgorithmName.THERMAL).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        starter.start(AlgorithmName.FLUID, command);
        assertThat(starter.circuitBreaker(AlgorithmName.FLUID).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        starter.circuitBreaker(AlgorithmName.THERMAL).transitionToHalfOpenState();
        starter.probe(AlgorithmName.THERMAL);
        assertThat(starter.circuitBreaker(AlgorithmName.THERMAL).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }
}
