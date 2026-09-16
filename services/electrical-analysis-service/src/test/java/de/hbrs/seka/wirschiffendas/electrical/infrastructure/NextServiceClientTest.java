package de.hbrs.seka.wirschiffendas.electrical.infrastructure;

import de.hbrs.seka.wirschiffendas.electrical.api.AnalysisCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

class NextServiceClientTest {
    private static final String ATTEMPT = "d9ef0df2-b59b-414e-a3b1-8f908abf8352";

    @ParameterizedTest
    @MethodSource("definiteRejections")
    void definiteRejectionFailsOnlyTheTargetAndPreservesAttempt(Throwable failure) {
        var management = mock(AnalysisManagementClient.class);
        var next = new NextServiceClient(RestClient.builder(), "http://next", management);
        var command = new AnalysisCommand("analysis", ATTEMPT, Map.of(), List.of());

        ReflectionTestUtils.invokeMethod(next, "fallback", command, "OK", failure);

        verify(management).reportStatus(eq("analysis"), eq(ATTEMPT), eq("ENGINE_MANAGEMENT"), eq("FAILED"),
                contains("could not accept the analysis"));
        verifyNoMoreInteractions(management);
    }

    @ParameterizedTest
    @MethodSource("uncertainOutcomes")
    void uncertainAcceptanceDoesNotSendFailureThatWouldRejectALaterSuccess(Throwable failure) {
        var management = mock(AnalysisManagementClient.class);
        var next = new NextServiceClient(RestClient.builder(), "http://next", management);
        var command = new AnalysisCommand("analysis", ATTEMPT, Map.of(), List.of());

        ReflectionTestUtils.invokeMethod(next, "fallback", command, "OK", failure);

        // No terminal callback: the target can still report success before the inactivity deadline.
        verifyNoInteractions(management);
    }

    static Stream<Throwable> definiteRejections() {
        return Stream.of(
                new ResourceAccessException("connection refused", new ConnectException("refused")),
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("nextService")),
                new HttpClientErrorException(HttpStatus.BAD_REQUEST),
                new HttpClientErrorException(HttpStatus.NOT_FOUND));
    }

    static Stream<Throwable> uncertainOutcomes() {
        return Stream.of(
                new ResourceAccessException("response timed out", new SocketTimeoutException("read timed out")),
                new ResourceAccessException("response was lost"),
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpServerErrorException(HttpStatus.BAD_GATEWAY),
                new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE),
                new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void forwardsSameAttemptAndAppendsOwnResultWithoutChangingOriginal() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var next = new NextServiceClient(builder, "http://next", mock(AnalysisManagementClient.class));
        var command = new AnalysisCommand("analysis", ATTEMPT, Map.of(), List.of(Map.of("algorithm", "BEFORE", "result", "OK")));
        server.expect(requestTo("http://next/internal/analyses"))
                .andExpect(jsonPath("$.analysisId").value("analysis"))
                .andExpect(jsonPath("$.attemptId").value(ATTEMPT))
                .andExpect(jsonPath("$.previousResults[1].algorithm").value("ELECTRICAL"))
                .andExpect(jsonPath("$.previousResults[1].result").value("OK"))
                .andRespond(withNoContent());
        next.startNext(command, "OK");
        assertEquals(1, command.previousResults().size());
        server.verify();
    }
}
