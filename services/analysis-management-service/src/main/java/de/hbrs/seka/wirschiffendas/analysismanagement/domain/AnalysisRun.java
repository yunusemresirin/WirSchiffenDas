package de.hbrs.seka.wirschiffendas.analysismanagement.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregat eines Analyse-Laufs mit allen Algorithmus-Ausführungen und dem Gesamtergebnis.
 */
@Entity
@Table(name = "analysis_runs")
public class AnalysisRun {

    @Id
    private String analysisId;

    private String configurationId;

    @Enumerated(EnumType.STRING)
    private AnalysisResult overallResult;

    @OneToMany(mappedBy = "analysisRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AlgorithmExecution> executions = new ArrayList<>();

    protected AnalysisRun() {
    }

    private AnalysisRun(String analysisId, String configurationId) {
        this.analysisId = analysisId;
        this.configurationId = configurationId;
        for (AlgorithmName algorithm : AlgorithmName.values()) {
            executions.add(new AlgorithmExecution(this, algorithm));
        }
    }

    public static AnalysisRun start(String analysisId, String configurationId) {
        return new AnalysisRun(analysisId, configurationId);
    }

    public AlgorithmExecution execution(AlgorithmName algorithm) {
        return executions.stream()
                .filter(execution -> execution.getAlgorithm() == algorithm)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Leitet das Gesamtergebnis aus den Einzelergebnissen ab: FAILED bei Fehler, sonst OK oder null solange offen.
     */
    public void recalculateOverallResult() {
        boolean failed = executions.stream()
                .anyMatch(e -> e.getStatus() == AnalysisStatus.FAILED || e.getResult() == AnalysisResult.FAILED);

        if (failed) {
            overallResult = AnalysisResult.FAILED;
            return;
        }

        boolean unfinished = executions.stream()
                .anyMatch(e -> e.getStatus() == AnalysisStatus.PENDING || e.getStatus() == AnalysisStatus.RUNNING);

        overallResult = unfinished ? null : AnalysisResult.OK;
    }

    public String getAnalysisId() { return analysisId; }
    public String getConfigurationId() { return configurationId; }
    public AnalysisResult getOverallResult() { return overallResult; }
    public List<AlgorithmExecution> getExecutions() { return List.copyOf(executions); }
}
