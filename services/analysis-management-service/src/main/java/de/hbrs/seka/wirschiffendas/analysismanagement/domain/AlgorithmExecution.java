package de.hbrs.seka.wirschiffendas.analysismanagement.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "algorithm_executions")
public class AlgorithmExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private AnalysisRun analysisRun;

    @Enumerated(EnumType.STRING)
    private AlgorithmName algorithm;

    @Enumerated(EnumType.STRING)
    private AnalysisStatus status;

    @Enumerated(EnumType.STRING)
    private AnalysisResult result;

    private String message;

    protected AlgorithmExecution() {
    }

    AlgorithmExecution(AnalysisRun analysisRun, AlgorithmName algorithm) {
        this.analysisRun = analysisRun;
        this.algorithm = algorithm;
        this.status = AnalysisStatus.PENDING;
    }

    public void updateStatus(AnalysisStatus status, String message) {
        this.status = status;
        this.message = message;

        if (status == AnalysisStatus.RUNNING || status == AnalysisStatus.PENDING) {
            this.result = null;
        } else if (status == AnalysisStatus.FAILED) {
            this.result = AnalysisResult.FAILED;
        }
    }

    public void updateResult(AnalysisStatus status, AnalysisResult result, String message) {
        this.status = status;
        this.result = result;
        this.message = message;
    }

    public AlgorithmName getAlgorithm() { return algorithm; }
    public AnalysisStatus getStatus() { return status; }
    public AnalysisResult getResult() { return result; }
    public String getMessage() { return message; }
}
