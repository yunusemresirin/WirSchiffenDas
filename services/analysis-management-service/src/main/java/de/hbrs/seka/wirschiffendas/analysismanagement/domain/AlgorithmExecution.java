package de.hbrs.seka.wirschiffendas.analysismanagement.domain;

import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
    private String attemptId;
    @Column(length = 1000)
    private String message;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "equipment_results", joinColumns = @JoinColumn(name = "execution_id"))
    @MapKeyColumn(name = "equipment_name")
    @Column(name = "result")
    @Enumerated(EnumType.STRING)
    private Map<String, AnalysisResult> equipmentResults = new HashMap<>();

    protected AlgorithmExecution() { }

    AlgorithmExecution(AnalysisRun analysisRun, AlgorithmName algorithm, String attemptId) {
        this.analysisRun = analysisRun;
        this.algorithm = algorithm;
        reset(attemptId);
    }

    void reset(String attemptId) {
        this.attemptId = Objects.requireNonNull(attemptId);
        this.status = AnalysisStatus.PENDING;
        this.result = null;
        this.message = null;
        equipmentResults.clear();
    }

    boolean accepts(String incomingAttemptId) {
        return Objects.equals(attemptId, incomingAttemptId) && !terminal();
    }

    boolean terminal() { return status == AnalysisStatus.READY || status == AnalysisStatus.FAILED; }

    boolean markRunning(String message) {
        if (status == AnalysisStatus.RUNNING) return false;
        if (status != AnalysisStatus.PENDING) throw new IllegalStateException("Only pending algorithms can start");
        status = AnalysisStatus.RUNNING;
        this.message = message;
        return true;
    }

    void fail(String message) {
        status = AnalysisStatus.FAILED;
        result = AnalysisResult.FAILED;
        this.message = message;
        equipmentResults.clear();
    }

    void complete(AnalysisStatus status, AnalysisResult result, String message,
                  Map<String, AnalysisResult> equipmentResults) {
        if (this.status != AnalysisStatus.RUNNING) {
            throw new IllegalStateException("Results require a running algorithm");
        }
        if (status != AnalysisStatus.READY && status != AnalysisStatus.FAILED) {
            throw new IllegalArgumentException("Results require READY or FAILED status");
        }
        if (equipmentResults == null || !algorithm.equipmentNames().equals(equipmentResults.keySet())
                || equipmentResults.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Complete equipment results are required for " + algorithm);
        }
        boolean allOk = equipmentResults.values().stream().allMatch(value -> value == AnalysisResult.OK);
        if (allOk != (status == AnalysisStatus.READY) || result != (allOk ? AnalysisResult.OK : AnalysisResult.FAILED)) {
            throw new IllegalArgumentException("Status and result must match the equipment results");
        }
        this.status = status;
        this.result = result;
        this.message = message;
        this.equipmentResults.clear();
        this.equipmentResults.putAll(equipmentResults);
    }

    public boolean successful() {
        return status == AnalysisStatus.READY && result == AnalysisResult.OK
                // Hibernate's PersistentMap key-set view does not implement value-based equals.
                && algorithm.equipmentNames().equals(equipmentResults.keySet())
                && equipmentResults.values().stream().allMatch(value -> value == AnalysisResult.OK);
    }

    public AlgorithmName getAlgorithm() { return algorithm; }
    public AnalysisStatus getStatus() { return status; }
    public AnalysisResult getResult() { return result; }
    public String getAttemptId() { return attemptId; }
    public String getMessage() { return message; }
    public Map<String, AnalysisResult> getEquipmentResults() { return Map.copyOf(equipmentResults); }
}
