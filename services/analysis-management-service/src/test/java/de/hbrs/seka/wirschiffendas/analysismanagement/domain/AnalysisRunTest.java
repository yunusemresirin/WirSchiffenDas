package de.hbrs.seka.wirschiffendas.analysismanagement.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisRunTest {

    @Test
    void newAnalysisStartsWithFourPendingAlgorithms() {
        AnalysisRun run = AnalysisRun.start("A-1", "C-1");

        assertThat(run.getExecutions()).hasSize(4);
        assertThat(run.getExecutions())
                .allMatch(execution -> execution.getStatus() == AnalysisStatus.PENDING);
        assertThat(run.getOverallResult()).isNull();
    }

    @Test
    void failedAlgorithmMakesOverallResultFailed() {
        AnalysisRun run = AnalysisRun.start("A-1", "C-1");
        run.execution(AlgorithmName.THERMAL)
                .updateStatus(AnalysisStatus.FAILED, "service unavailable");

        run.recalculateOverallResult();

        assertThat(run.getOverallResult()).isEqualTo(AnalysisResult.FAILED);
    }

    @Test
    void fourSuccessfulAlgorithmsMakeOverallResultOk() {
        AnalysisRun run = AnalysisRun.start("A-1", "C-1");

        for (AlgorithmName algorithm : AlgorithmName.values()) {
            run.execution(algorithm)
                    .updateResult(AnalysisStatus.READY, AnalysisResult.OK, null);
        }

        run.recalculateOverallResult();

        assertThat(run.getOverallResult()).isEqualTo(AnalysisResult.OK);
    }

    @Test
    void retryRunningStateClearsOldFailedResult() {
        AnalysisRun run = AnalysisRun.start("A-1", "C-1");
        AlgorithmExecution thermal = run.execution(AlgorithmName.THERMAL);
        thermal.updateResult(AnalysisStatus.FAILED, AnalysisResult.FAILED, "temporary failure");

        thermal.updateStatus(AnalysisStatus.RUNNING, null);

        assertThat(thermal.getStatus()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(thermal.getResult()).isNull();
    }
}
