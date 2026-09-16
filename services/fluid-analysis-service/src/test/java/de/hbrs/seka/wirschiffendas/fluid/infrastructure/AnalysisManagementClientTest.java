package de.hbrs.seka.wirschiffendas.fluid.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AnalysisManagementClientTest {
    private static final String URL = "http://management/internal/analyses/analysis/algorithms/FLUID/status";
    private MockRestServiceServer server;
    private AnalysisManagementClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AnalysisManagementClient(builder, "http://management", 3, Duration.ZERO);
    }

    private void send() {
        client.reportStatus("analysis", "attempt", "FLUID", "RUNNING", "started");
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"INTERNAL_SERVER_ERROR", "BAD_GATEWAY", "SERVICE_UNAVAILABLE", "GATEWAY_TIMEOUT"})
    void retriesTransientFailures(HttpStatus status) {
        server.expect(times(2), requestTo(URL)).andExpect(jsonPath("$.attemptId").value("attempt")).andRespond(withStatus(status));
        server.expect(requestTo(URL)).andRespond(withNoContent());
        send();
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "CONFLICT", "TOO_MANY_REQUESTS", "NOT_IMPLEMENTED"})
    void doesNotRetryPermanentOrClientErrors(HttpStatus status) {
        server.expect(requestTo(URL)).andRespond(withStatus(status));
        assertThrows(RestClientException.class, this::send);
        server.verify();
    }

    @Test
    void retriesTransportFailure() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timeout")));
        server.expect(requestTo(URL)).andRespond(withNoContent());
        send();
        server.verify();
    }

    @Test
    void stopsAfterConfiguredMaximum() {
        server.expect(times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(RestClientException.class, this::send);
        server.verify();
    }

    @Test
    void resultContainsAttemptAndEveryEquipmentResult() {
        server.expect(requestTo("http://management/internal/analyses/analysis/algorithms/FLUID/result"))
                .andExpect(jsonPath("$.attemptId").value("attempt"))
                .andExpect(jsonPath("$.equipmentResults.oilSystem").value("FAILED"))
                .andExpect(jsonPath("$.equipmentResults.length()").value(2))
                .andRespond(withNoContent());
        client.reportResult("analysis", "attempt", "FLUID", "FAILED", "FAILED", "invalid equipment", Map.of("oilSystem", "FAILED", "fuelSystem", "OK"));
        server.verify();
    }
}
