package de.hbrs.seka.wirschiffendas.analysismanagement.application;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisResult;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisRun;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisStatus;
import de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure.AnalysisRunRepository;
import de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure.AnalysisServiceStarter;
import de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure.ConfigurationServiceClient;
import de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure.ConfigurationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisApplicationServiceRecoveryTest {

    @Mock
    private AnalysisRunRepository repository;

    @Mock
    private ConfigurationServiceClient configurationClient;

    @Mock
    private AnalysisServiceStarter serviceStarter;

    private AnalysisApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisApplicationService(repository, configurationClient, serviceStarter);
    }

    @Test
    void resumesRunAtAlgorithmThatFailedBecauseServiceWasUnavailable() {
        AnalysisRun run = AnalysisRun.start("A-1", "C-1");
        run.execution(AlgorithmName.FLUID)
                .updateResult(AnalysisStatus.READY, AnalysisResult.OK, null);
        run.execution(AlgorithmName.THERMAL)
                .updateStatus(AnalysisStatus.FAILED, "thermal-analysis-service unavailable");
        run.recalculateOverallResult();

        ConfigurationSnapshot configuration = new ConfigurationSnapshot(
                "C-1", "STANDARD", "PREMIUM", "STANDARD", "PREMIUM", "ADVANCED");

        when(repository.findAll()).thenReturn(List.of(run));
        when(repository.findById("A-1")).thenReturn(Optional.of(run));
        when(configurationClient.get("C-1")).thenReturn(configuration);
        when(repository.saveAndFlush(ArgumentMatchers.any(AnalysisRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecoveryResult recovery = service.resumeRecoverableFailures(AlgorithmName.THERMAL);

        assertThat(recovery.resumed()).isEqualTo(1);
        assertThat(recovery.remaining()).isZero();
        assertThat(run.execution(AlgorithmName.FLUID).getStatus()).isEqualTo(AnalysisStatus.READY);
        assertThat(run.execution(AlgorithmName.THERMAL).getStatus()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(run.execution(AlgorithmName.THERMAL).getResult()).isNull();

        verify(serviceStarter).start(
                eq(AlgorithmName.THERMAL),
                argThat(command ->
                        command.analysisId().equals("A-1")
                                && command.previousResults().size() == 1
                                && command.previousResults().getFirst().algorithm() == AlgorithmName.FLUID
                                && command.previousResults().getFirst().result() == AnalysisResult.OK));
    }

    @Test
    void keepsFailedResumePendingWhenRetryStillCannotStart() {
        AnalysisRun run = AnalysisRun.start("A-3", "C-3");
        run.execution(AlgorithmName.THERMAL)
                .updateStatus(AnalysisStatus.FAILED, "thermal-analysis-service unavailable");
        run.recalculateOverallResult();

        ConfigurationSnapshot configuration = new ConfigurationSnapshot(
                "C-3", "STANDARD", "PREMIUM", "STANDARD", "PREMIUM", "ADVANCED");

        when(repository.findAll()).thenReturn(List.of(run));
        when(repository.findById("A-3")).thenReturn(Optional.of(run));
        when(configurationClient.get("C-3")).thenReturn(configuration);
        when(repository.saveAndFlush(ArgumentMatchers.any(AnalysisRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("still unavailable"))
                .when(serviceStarter)
                .start(eq(AlgorithmName.THERMAL), any());

        RecoveryResult recovery = service.resumeRecoverableFailures(AlgorithmName.THERMAL);

        assertThat(recovery.resumed()).isZero();
        assertThat(recovery.remaining()).isEqualTo(1);
        assertThat(run.execution(AlgorithmName.THERMAL).getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(run.execution(AlgorithmName.THERMAL).getMessage()).containsIgnoringCase("unavailable");
    }

    @Test
    void doesNotAutomaticallyRetryBusinessFailure() {
        AnalysisRun run = AnalysisRun.start("A-2", "C-2");
        run.execution(AlgorithmName.THERMAL)
                .updateResult(
                        AnalysisStatus.FAILED,
                        AnalysisResult.FAILED,
                        "cooling configuration invalid");
        run.recalculateOverallResult();

        when(repository.findAll()).thenReturn(List.of(run));

        RecoveryResult recovery = service.resumeRecoverableFailures(AlgorithmName.THERMAL);

        assertThat(recovery.resumed()).isZero();
        assertThat(recovery.remaining()).isZero();
        verifyNoInteractions(configurationClient, serviceStarter);
        verify(repository, never()).findById(anyString());
    }
}
