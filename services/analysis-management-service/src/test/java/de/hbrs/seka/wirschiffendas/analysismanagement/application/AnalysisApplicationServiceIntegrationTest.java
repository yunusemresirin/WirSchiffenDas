package de.hbrs.seka.wirschiffendas.analysismanagement.application;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.*;
import de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName.*;
import static de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:analysis-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.jpa.hibernate.ddl-auto=create-drop", "analysis.timeout.enabled=false"
})
class AnalysisApplicationServiceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");
    @Autowired AnalysisApplicationService service;
    @Autowired AnalysisRunRepository repository;
    @MockitoBean ConfigurationServiceClient configurations;
    @MockitoBean AnalysisServiceStarter starter;
    @MockitoBean Clock clock;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(clock.instant()).thenReturn(NOW);
        when(configurations.get(anyString())).thenReturn(
                new ConfigurationSnapshot("C-1", "STANDARD", "STANDARD", "STANDARD", "STANDARD", "STANDARD"));
    }

    @Test
    void rowLockAllowsExactlyOneConcurrentRetryAndOneDispatch() throws Exception {
        AnalysisRun run = service.start("C-1");
        String attempt = run.execution(FLUID).getAttemptId();
        service.updateStatus(run.getAnalysisId(), FLUID, attempt, FAILED, "unavailable");
        clearInvocations(starter);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertThat(go.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    service.retry(run.getAnalysisId(), FLUID);
                    return "accepted";
                } catch (ResponseStatusException exception) {
                    return String.valueOf(exception.getStatusCode().value());
                }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("accepted", "409");
        }
        verify(starter, times(1)).start(eq(FLUID), any());
        assertThat(service.get(run.getAnalysisId()).execution(FLUID).getAttemptId()).isNotEqualTo(attempt);
    }

    @Test
    void persistedTimeoutAllowsRetryAndRejectsOldCallback() {
        AnalysisRun run = service.start("C-1");
        String attempt = run.execution(FLUID).getAttemptId();
        when(clock.instant()).thenReturn(NOW.plusSeconds(31));
        service.expireInactiveRuns();
        assertThat(service.get(run.getAnalysisId()).execution(FLUID).getStatus()).isEqualTo(FAILED);
        assertThat(service.get(run.getAnalysisId()).execution(THERMAL).getStatus()).isEqualTo(PENDING);
        AnalysisRun retried = service.retry(run.getAnalysisId(), FLUID);
        service.updateResult(run.getAnalysisId(), FLUID, attempt, READY, AnalysisResult.OK, null,
                Map.of("oilSystem", AnalysisResult.OK, "fuelSystem", AnalysisResult.OK));
        AnalysisRun current = service.get(run.getAnalysisId());
        assertThat(current.execution(FLUID).getStatus()).isEqualTo(RUNNING);
        assertThat(current.execution(FLUID).getAttemptId()).isEqualTo(retried.execution(FLUID).getAttemptId());
    }

    @Test
    void uncertainTransportFailureKeepsAcceptingWorkerResult() {
        doThrow(new ResourceAccessException("response lost", new SocketTimeoutException("read timeout")))
                .when(starter).start(eq(FLUID), any());
        AnalysisRun run = service.start("C-1");
        assertThat(run.execution(FLUID).getStatus()).isEqualTo(RUNNING);
        service.updateResult(run.getAnalysisId(), FLUID, run.execution(FLUID).getAttemptId(), READY,
                AnalysisResult.OK, null, Map.of("oilSystem", AnalysisResult.OK, "fuelSystem", AnalysisResult.OK));
        AlgorithmExecution completed = service.get(run.getAnalysisId()).execution(FLUID);
        assertThat(completed.getStatus()).isEqualTo(READY);
        assertThat(completed.getResult()).isEqualTo(AnalysisResult.OK);
        assertThat(completed.getEquipmentResults()).containsExactlyInAnyOrderEntriesOf(
                Map.of("oilSystem", AnalysisResult.OK, "fuelSystem", AnalysisResult.OK));
        assertThat(completed.successful()).isTrue();
    }

    @Test
    void callbackCanCommitDuringDispatchAndLaterTransportErrorCannotOverwriteIt() {
        doAnswer(invocation -> {
            AnalysisCommand command = invocation.getArgument(1);
            service.updateResult(command.analysisId(), FLUID, command.attemptId(), READY, AnalysisResult.OK, null,
                    Map.of("oilSystem", AnalysisResult.OK, "fuelSystem", AnalysisResult.OK));
            throw new ResourceAccessException("connection failed after callback", new ConnectException("refused"));
        }).when(starter).start(eq(FLUID), any());
        AnalysisRun run = service.start("C-1");
        assertThat(run.execution(FLUID).getStatus()).isEqualTo(READY);
        assertThat(run.execution(FLUID).getResult()).isEqualTo(AnalysisResult.OK);
        assertThat(run.execution(FLUID).successful()).isTrue();
        assertThat(run.execution(FLUID).getEquipmentResults()).hasSize(2);
    }

    @Test
    void persistedEquipmentResultsAllowTheNextStepAndOverallSuccess() {
        AnalysisRun started = service.start("C-1");
        String attempt = started.execution(FLUID).getAttemptId();
        for (AlgorithmName algorithm : AlgorithmName.values()) {
            service.updateStatus(started.getAnalysisId(), algorithm, attempt, RUNNING, null);
            Map<String, AnalysisResult> details = algorithm.equipmentNames().stream().collect(
                    java.util.stream.Collectors.toMap(name -> name, name -> AnalysisResult.OK));
            service.updateResult(started.getAnalysisId(), algorithm, attempt, READY, AnalysisResult.OK, null, details);
            AlgorithmExecution stored = service.get(started.getAnalysisId()).execution(algorithm);
            assertThat(stored.getEquipmentResults()).containsExactlyInAnyOrderEntriesOf(details);
            assertThat(stored.successful()).isTrue();
        }
        assertThat(service.get(started.getAnalysisId()).getOverallResult()).isEqualTo(AnalysisResult.OK);
    }

    @Test
    void definiteConnectionFailureIsImmediatelyRetryable() {
        doThrow(new ResourceAccessException("cannot connect", new ConnectException("refused")))
                .when(starter).start(eq(FLUID), any());
        AnalysisRun run = service.start("C-1");
        assertThat(run.execution(FLUID).getStatus()).isEqualTo(FAILED);
    }

    @Test
    void invalidResultRollsBackAndDoesNotDiscardPreviousProgress() {
        AnalysisRun run = service.start("C-1");
        assertThatThrownBy(() -> service.updateResult(run.getAnalysisId(), FLUID,
                run.execution(FLUID).getAttemptId(), READY, AnalysisResult.OK, null,
                Map.of("oilSystem", AnalysisResult.OK))).isInstanceOf(ResponseStatusException.class);
        assertThat(service.get(run.getAnalysisId()).execution(FLUID).getStatus()).isEqualTo(RUNNING);
        assertThat(service.get(run.getAnalysisId()).getLastProgressAt()).isEqualTo(NOW);
    }
}
