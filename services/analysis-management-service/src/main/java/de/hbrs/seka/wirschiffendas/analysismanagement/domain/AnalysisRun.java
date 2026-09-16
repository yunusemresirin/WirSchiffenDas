package de.hbrs.seka.wirschiffendas.analysismanagement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "analysis_runs")
public class AnalysisRun {
    @Id
    private String analysisId;
    private String configurationId;
    @Enumerated(EnumType.STRING)
    private AnalysisResult overallResult;
    private Instant lastProgressAt;
    @OneToMany(mappedBy = "analysisRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AlgorithmExecution> executions = new ArrayList<>();

    protected AnalysisRun() { }

    public static AnalysisRun start(String analysisId, String configurationId, String attemptId, Instant now) {
        AnalysisRun run = new AnalysisRun();
        run.analysisId = analysisId;
        run.configurationId = configurationId;
        run.lastProgressAt = now;
        for (AlgorithmName algorithm : AlgorithmName.values()) {
            run.executions.add(new AlgorithmExecution(run, algorithm, attemptId));
        }
        run.execution(AlgorithmName.FLUID).markRunning(null);
        return run;
    }

    public AlgorithmExecution execution(AlgorithmName algorithm) {
        return executions.stream().filter(item -> item.getAlgorithm() == algorithm).findFirst().orElseThrow();
    }

    public boolean updateStatus(AlgorithmName algorithm, String attemptId, AnalysisStatus status,
                                String message, Instant now) {
        AlgorithmExecution execution = execution(algorithm);
        if (!execution.accepts(attemptId)) return false;
        if (status != AnalysisStatus.RUNNING && status != AnalysisStatus.FAILED) {
            throw new IllegalArgumentException("Status callbacks may only report RUNNING or FAILED");
        }
        requireSuccessfulPredecessors(algorithm);
        if (status == AnalysisStatus.RUNNING) {
            if (!execution.markRunning(message)) return false;
        } else {
            execution.fail(message);
        }
        progressed(now);
        return true;
    }

    public boolean updateResult(AlgorithmName algorithm, String attemptId, AnalysisStatus status,
                                AnalysisResult result, String message, Map<String, AnalysisResult> equipmentResults,
                                Instant now) {
        AlgorithmExecution execution = execution(algorithm);
        if (!execution.accepts(attemptId)) return false;
        requireSuccessfulPredecessors(algorithm);
        execution.complete(status, result, message, equipmentResults);
        progressed(now);
        return true;
    }

    public void retry(AlgorithmName algorithm, String newAttemptId, Instant now) {
        if (legacy()) {
            throw new IllegalStateException("Legacy analysis: create a new analysis for this configuration; historical results are preserved");
        }
        if (execution(algorithm).getStatus() != AnalysisStatus.FAILED) {
            throw new IllegalStateException("Only failed algorithms can be retried");
        }
        requireSuccessfulPredecessors(algorithm);
        executions.stream().filter(item -> item.getAlgorithm().ordinal() >= algorithm.ordinal())
                .forEach(item -> item.reset(newAttemptId));
        execution(algorithm).markRunning(null);
        progressed(now);
    }

    public boolean dispatchFailed(AlgorithmName algorithm, String attemptId, String message, Instant now) {
        AlgorithmExecution execution = execution(algorithm);
        if (!execution.accepts(attemptId)) return false;
        execution.fail(message);
        progressed(now);
        return true;
    }

    public boolean expireBefore(Instant cutoff, Instant now) {
        if (overallResult != null || (lastProgressAt != null && !lastProgressAt.isBefore(cutoff))) return false;
        return getExecutions().stream().filter(item -> !item.terminal()).findFirst().map(item -> {
            item.fail(legacy()
                    ? "Legacy analysis timeout: create a new analysis for this configuration; historical results are preserved"
                    : "No progress within the analysis timeout; retry this algorithm");
            progressed(now);
            return true;
        }).orElse(false);
    }

    private void requireSuccessfulPredecessors(AlgorithmName algorithm) {
        if (executions.stream().filter(item -> item.getAlgorithm().ordinal() < algorithm.ordinal())
                .anyMatch(item -> !item.successful())) {
            throw new IllegalStateException("All predecessor algorithms must have complete successful results");
        }
    }

    private boolean legacy() {
        return executions.stream().anyMatch(item -> item.getAttemptId() == null);
    }

    private void progressed(Instant now) {
        lastProgressAt = now;
        recalculateOverallResult();
    }

    public void recalculateOverallResult() {
        if (executions.stream().anyMatch(item -> item.getStatus() == AnalysisStatus.FAILED
                || item.getResult() == AnalysisResult.FAILED)) {
            overallResult = AnalysisResult.FAILED;
        } else {
            overallResult = executions.size() == AlgorithmName.values().length
                    && executions.stream().allMatch(AlgorithmExecution::successful) ? AnalysisResult.OK : null;
        }
    }

    public String getAnalysisId() { return analysisId; }
    public String getConfigurationId() { return configurationId; }
    public AnalysisResult getOverallResult() { return overallResult; }
    public Instant getLastProgressAt() { return lastProgressAt; }
    public List<AlgorithmExecution> getExecutions() {
        return executions.stream().sorted(Comparator.comparing(AlgorithmExecution::getAlgorithm)).toList();
    }
}
