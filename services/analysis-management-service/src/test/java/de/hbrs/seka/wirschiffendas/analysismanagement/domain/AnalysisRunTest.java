package de.hbrs.seka.wirschiffendas.analysismanagement.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import static de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName.*;
import static de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisStatus.*;
import static org.assertj.core.api.Assertions.*;

class AnalysisRunTest {
    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");
    private static final String ATTEMPT = "attempt-1";
    private AnalysisRun newRun() { return AnalysisRun.start("A-1", "C-1", ATTEMPT, NOW); }
    private Map<String, AnalysisResult> ok(AlgorithmName algorithm) {
        return algorithm.equipmentNames().stream().collect(Collectors.toMap(name -> name, name -> AnalysisResult.OK));
    }
    private void complete(AnalysisRun run, AlgorithmName algorithm, String attempt) {
        run.updateStatus(algorithm, attempt, RUNNING, null, NOW);
        run.updateResult(algorithm, attempt, READY, AnalysisResult.OK, null, ok(algorithm), NOW);
    }

    @Test
    void newRunSharesAttemptAndOnlyStartsAnchor() {
        AnalysisRun run = newRun();
        assertThat(run.getExecutions()).hasSize(4).allMatch(item -> ATTEMPT.equals(item.getAttemptId()));
        assertThat(run.execution(FLUID).getStatus()).isEqualTo(RUNNING);
        assertThat(run.execution(THERMAL).getStatus()).isEqualTo(PENDING);
        assertThat(run.getOverallResult()).isNull();
    }

    @Test
    void incompleteEquipmentResultsCannotCompleteAnAlgorithm() {
        AnalysisRun run = newRun();
        assertThatThrownBy(() -> run.updateResult(FLUID, ATTEMPT, READY, AnalysisResult.OK, null,
                Map.of("oilSystem", AnalysisResult.OK), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(run.execution(FLUID).getStatus()).isEqualTo(RUNNING);
        assertThat(run.getOverallResult()).isNull();
    }

    @Test
    void legacyReadyStatesWithoutResultsNeverProduceOverallOk() {
        AnalysisRun run = newRun();
        run.getExecutions().forEach(item -> ReflectionTestUtils.setField(item, "status", READY));
        run.recalculateOverallResult();
        assertThat(run.getOverallResult()).isNull();
    }

    @Test
    void statusOnlyReadyAndContradictoryResultAreRejected() {
        AnalysisRun run = newRun();
        assertThatThrownBy(() -> run.updateStatus(FLUID, ATTEMPT, READY, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run.updateResult(FLUID, ATTEMPT, FAILED, AnalysisResult.FAILED,
                null, ok(FLUID), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run.updateStatus(THERMAL, ATTEMPT, RUNNING, null, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aggregateOnlyBecomesOkWithFourCompleteSuccessfulAlgorithms() {
        AnalysisRun run = newRun();
        for (AlgorithmName algorithm : AlgorithmName.values()) {
            assertThat(run.getOverallResult()).isNull();
            complete(run, algorithm, ATTEMPT);
        }
        assertThat(run.getOverallResult()).isEqualTo(AnalysisResult.OK);
    }

    @Test
    void resultRequiresRunningStage() {
        AnalysisRun run = newRun();
        complete(run, FLUID, ATTEMPT);
        assertThatThrownBy(() -> run.updateResult(THERMAL, ATTEMPT, READY, AnalysisResult.OK,
                null, ok(THERMAL), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void independentEquipmentFailuresRemainVisible() {
        AnalysisRun run = newRun();
        run.updateResult(FLUID, ATTEMPT, FAILED, AnalysisResult.FAILED, null,
                Map.of("oilSystem", AnalysisResult.FAILED, "fuelSystem", AnalysisResult.OK), NOW);
        assertThat(run.getOverallResult()).isEqualTo(AnalysisResult.FAILED);
        assertThat(run.execution(FLUID).getEquipmentResults()).containsEntry("fuelSystem", AnalysisResult.OK);
    }

    @Test
    void retryRotatesTargetAndSuccessorsButPreservesSuccessfulPredecessors() {
        AnalysisRun run = newRun();
        complete(run, FLUID, ATTEMPT);
        run.updateStatus(THERMAL, ATTEMPT, FAILED, "unavailable", NOW);
        run.retry(THERMAL, "attempt-2", NOW.plusSeconds(1));
        assertThat(run.execution(FLUID).getAttemptId()).isEqualTo(ATTEMPT);
        assertThat(run.execution(FLUID).successful()).isTrue();
        assertThat(run.execution(THERMAL).getStatus()).isEqualTo(RUNNING);
        assertThat(run.execution(THERMAL).getResult()).isNull();
        assertThat(run.execution(ELECTRICAL).getAttemptId()).isEqualTo("attempt-2");
        assertThat(run.execution(ENGINE_MANAGEMENT).getAttemptId()).isEqualTo("attempt-2");
        assertThat(run.getOverallResult()).isNull();
        assertThatThrownBy(() -> run.retry(THERMAL, "attempt-3", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void oldAttemptAndTerminalCallbacksCannotResurrectOrOverwriteResults() {
        AnalysisRun run = newRun();
        run.updateStatus(FLUID, ATTEMPT, FAILED, "failed", NOW);
        run.retry(FLUID, "attempt-2", NOW.plusSeconds(1));
        assertThat(run.updateResult(FLUID, ATTEMPT, READY, AnalysisResult.OK, null, ok(FLUID), NOW)).isFalse();
        complete(run, FLUID, "attempt-2");
        assertThat(run.updateStatus(FLUID, "attempt-2", RUNNING, null, NOW.plusSeconds(20))).isFalse();
        assertThat(run.updateStatus(FLUID, "attempt-2", FAILED, "late failure", NOW.plusSeconds(20))).isFalse();
        assertThat(run.updateResult(FLUID, "attempt-2", READY, AnalysisResult.OK, null, ok(FLUID), NOW)).isFalse();
        assertThat(run.execution(FLUID).successful()).isTrue();
    }

    @Test
    void duplicateRunningCannotExtendInactivityDeadline() {
        AnalysisRun run = newRun();
        assertThat(run.updateStatus(FLUID, ATTEMPT, RUNNING, null, NOW.plusSeconds(20))).isFalse();
        assertThat(run.getLastProgressAt()).isEqualTo(NOW);
        assertThat(run.expireBefore(NOW.plusSeconds(1), NOW.plusSeconds(31))).isTrue();
        assertThat(run.execution(FLUID).getStatus()).isEqualTo(FAILED);
        assertThat(run.execution(THERMAL).getStatus()).isEqualTo(PENDING);
        assertThat(run.updateResult(FLUID, ATTEMPT, READY, AnalysisResult.OK, null, ok(FLUID), NOW)).isFalse();
        run.retry(FLUID, "attempt-2", NOW.plusSeconds(32));
        assertThat(run.execution(FLUID).getStatus()).isEqualTo(RUNNING);
    }

    @Test
    void stalledHandoffTimesOutOnlyFirstPendingStage() {
        AnalysisRun run = newRun();
        complete(run, FLUID, ATTEMPT);
        run.expireBefore(NOW.plusSeconds(1), NOW.plusSeconds(31));
        assertThat(run.execution(THERMAL).getStatus()).isEqualTo(FAILED);
        assertThat(run.execution(ELECTRICAL).getStatus()).isEqualTo(PENDING);
        assertThat(run.execution(FLUID).successful()).isTrue();
    }

    @Test
    void legacyOpenRunHasExplicitRecoveryWithoutDestroyingHistoricalResults() {
        AnalysisRun run = newRun();
        run.getExecutions().forEach(item -> ReflectionTestUtils.setField(item, "attemptId", null));
        ReflectionTestUtils.setField(run, "lastProgressAt", null);
        assertThat(run.expireBefore(NOW.minusSeconds(30), NOW)).isTrue();
        assertThat(run.execution(FLUID).getMessage()).contains("Legacy", "new analysis");
        assertThatThrownBy(() -> run.retry(FLUID, "new-attempt", NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Legacy");
    }
}
