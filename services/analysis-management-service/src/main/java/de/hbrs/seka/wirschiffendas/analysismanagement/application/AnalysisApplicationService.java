package de.hbrs.seka.wirschiffendas.analysismanagement.application;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.*;
import de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AnalysisApplicationService {

    private final AnalysisRunRepository repository;
    private final ConfigurationServiceClient configurationClient;
    private final AnalysisServiceStarter serviceStarter;

    public AnalysisApplicationService(
            AnalysisRunRepository repository,
            ConfigurationServiceClient configurationClient,
            AnalysisServiceStarter serviceStarter) {
        this.repository = repository;
        this.configurationClient = configurationClient;
        this.serviceStarter = serviceStarter;
    }

    public AnalysisRun start(String configurationId) {
        ConfigurationSnapshot configuration = configurationClient.get(configurationId);
        AnalysisRun run = AnalysisRun.start("A-" + UUID.randomUUID(), configurationId);
        AlgorithmExecution fluid = run.execution(AlgorithmName.FLUID);
        fluid.updateStatus(AnalysisStatus.RUNNING, null);

        // Repository transaction is committed before the external HTTP call.
        // This avoids a race where an asynchronous callback arrives before AnalysisRun exists.
        run = repository.saveAndFlush(run);

        try {
            serviceStarter.start(
                    AlgorithmName.FLUID,
                    new AnalysisCommand(run.getAnalysisId(), configuration, List.of()));
        } catch (RuntimeException exception) {
            fluid = run.execution(AlgorithmName.FLUID);
            fluid.updateStatus(AnalysisStatus.FAILED, "Fluid analysis service unavailable");
            run.recalculateOverallResult();
            run = repository.saveAndFlush(run);
        }

        return run;
    }

    public AnalysisRun get(String analysisId) {
        return find(analysisId);
    }

    public AnalysisRun updateStatus(
            String analysisId,
            AlgorithmName algorithm,
            AnalysisStatus status,
            String message) {
        AnalysisRun run = find(analysisId);
        run.execution(algorithm).updateStatus(status, message);
        run.recalculateOverallResult();
        return repository.save(run);
    }

    public AnalysisRun updateResult(
            String analysisId,
            AlgorithmName algorithm,
            AnalysisStatus status,
            AnalysisResult result,
            String message) {
        AnalysisRun run = find(analysisId);
        run.execution(algorithm).updateResult(status, result, message);
        run.recalculateOverallResult();
        return repository.save(run);
    }

    public AnalysisRun retry(String analysisId, AlgorithmName algorithm) {
        AnalysisRun run = find(analysisId);
        AlgorithmExecution execution = run.execution(algorithm);

        if (execution.getStatus() != AnalysisStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed algorithms can be retried");
        }

        ConfigurationSnapshot configuration = configurationClient.get(run.getConfigurationId());

        // A retry gets only successful results from predecessor algorithms.
        // The failed result of the retried algorithm itself must not be propagated.
        List<PreviousResult> previousResults = run.getExecutions().stream()
                .filter(item -> item.getResult() != null)
                .filter(item -> item.getAlgorithm().ordinal() < algorithm.ordinal())
                .map(item -> new PreviousResult(item.getAlgorithm(), item.getResult()))
                .toList();

        execution.updateStatus(AnalysisStatus.RUNNING, null);
        run.recalculateOverallResult();
        run = repository.saveAndFlush(run);

        try {
            serviceStarter.start(
                    algorithm,
                    new AnalysisCommand(run.getAnalysisId(), configuration, previousResults));
        } catch (RuntimeException exception) {
            execution = run.execution(algorithm);
            execution.updateStatus(AnalysisStatus.FAILED, algorithm + " service unavailable");
            run.recalculateOverallResult();
            run = repository.saveAndFlush(run);
        }

        return run;
    }

    private AnalysisRun find(String analysisId) {
        return repository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found"));
    }
}
