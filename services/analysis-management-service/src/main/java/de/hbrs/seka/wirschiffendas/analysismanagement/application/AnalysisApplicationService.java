package de.hbrs.seka.wirschiffendas.analysismanagement.application;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.*;
import de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Anwendungsdienst, der Analyse-Läufe startet, aktualisiert und wiederholt.
 */
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

    /**
     * Startet einen neuen Analyse-Lauf mit dem Fluid-Algorithmus als erstem Schritt.
     */
    public AnalysisRun start(String configurationId) {
        ConfigurationSnapshot configuration = configurationClient.get(configurationId);
        AnalysisRun run = AnalysisRun.start("A-" + UUID.randomUUID(), configurationId);
        AlgorithmExecution fluid = run.execution(AlgorithmName.FLUID);
        fluid.updateStatus(AnalysisStatus.RUNNING, null);

        // Die Repository-Transaktion wird vor dem externen HTTP-Aufruf committet.
        // Das verhindert ein Race, bei dem ein asynchroner Callback eintrifft, bevor der AnalysisRun existiert.
        run = repository.saveAndFlush(run);

        try {
            serviceStarter.start(
                    AlgorithmName.FLUID,
                    new AnalysisCommand(run.getAnalysisId(), configuration, List.of()));
        } catch (RuntimeException exception) {
            // Start fehlgeschlagen: Fluid-Algorithmus als FAILED markieren
            fluid = run.execution(AlgorithmName.FLUID);
            fluid.updateStatus(AnalysisStatus.FAILED, "Fluid analysis service unavailable");
            run.recalculateOverallResult();
            run = repository.saveAndFlush(run);
        }

        return run;
    }

    /**
     * Liefert einen Analyse-Lauf anhand seiner ID.
     */
    public AnalysisRun get(String analysisId) {
        return find(analysisId);
    }

    /**
     * Aktualisiert den Status eines Algorithmus und berechnet das Gesamtergebnis neu.
     */
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

    /**
     * Aktualisiert das Ergebnis eines Algorithmus und berechnet das Gesamtergebnis neu.
     */
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

    /**
     * Wiederholt einen fehlgeschlagenen Algorithmus.
     */
    public AnalysisRun retry(String analysisId, AlgorithmName algorithm) {
        AnalysisRun run = find(analysisId);
        AlgorithmExecution execution = run.execution(algorithm);

        // Nur fehlgeschlagene Algorithmen dürfen erneut ausgeführt werden
        if (execution.getStatus() != AnalysisStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed algorithms can be retried");
        }

        ConfigurationSnapshot configuration = configurationClient.get(run.getConfigurationId());

        // Ein Retry erhält nur erfolgreiche Ergebnisse der Vorgänger-Algorithmen.
        // Das fehlgeschlagene Ergebnis des wiederholten Algorithmus selbst darf nicht weitergegeben werden.
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
            // Start fehlgeschlagen: Algorithmus als FAILED markieren
            execution = run.execution(algorithm);
            execution.updateStatus(AnalysisStatus.FAILED, algorithm + " service unavailable");
            run.recalculateOverallResult();
            run = repository.saveAndFlush(run);
        }

        return run;
    }

    /**
     * Setzt alle durch einen vorübergehend nicht erreichbaren Service fehlgeschlagenen
     * Analyse-Läufe an genau diesem Algorithmus automatisch fort.
     *
     * Fachliche FAILED-Ergebnisse werden bewusst nicht automatisch wiederholt.
     */
    public RecoveryResult resumeRecoverableFailures(AlgorithmName algorithm) {
        List<String> analysisIds = repository.findAll().stream()
                .filter(run -> isRecoverableFailure(run.execution(algorithm)))
                .map(AnalysisRun::getAnalysisId)
                .toList();

        int resumed = 0;
        for (String analysisId : analysisIds) {
            try {
                AnalysisRun retried = retry(analysisId, algorithm);
                if (!isRecoverableFailure(retried.execution(algorithm))) {
                    resumed++;
                }
            } catch (RuntimeException ignored) {
                // Der Lauf bleibt FAILED und kann beim nächsten Recovery-Zyklus erneut versucht werden.
            }
        }

        int remaining = (int) repository.findAll().stream()
                .filter(run -> isRecoverableFailure(run.execution(algorithm)))
                .count();

        return new RecoveryResult(resumed, remaining);
    }

    private boolean isRecoverableFailure(AlgorithmExecution execution) {
        String message = execution.getMessage();
        return execution.getStatus() == AnalysisStatus.FAILED
                && message != null
                && message.toLowerCase(Locale.ROOT).contains("unavailable");
    }

    /**
     * Lädt einen Analyse-Lauf oder wirft 404, wenn er nicht existiert.
     */
    private AnalysisRun find(String analysisId) {
        return repository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found"));
    }
}
