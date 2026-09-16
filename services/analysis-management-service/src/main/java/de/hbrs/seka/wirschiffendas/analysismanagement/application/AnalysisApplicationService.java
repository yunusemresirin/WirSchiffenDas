package de.hbrs.seka.wirschiffendas.analysismanagement.application;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.*;
import de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AnalysisApplicationService {
    private final AnalysisRunRepository repository;
    private final ConfigurationServiceClient configurationClient;
    private final AnalysisServiceStarter serviceStarter;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration inactivityTimeout;

    public AnalysisApplicationService(AnalysisRunRepository repository, ConfigurationServiceClient configurationClient,
            AnalysisServiceStarter serviceStarter, PlatformTransactionManager transactionManager, Clock clock,
            @Value("${analysis.timeout.inactivity:30s}") Duration inactivityTimeout) {
        this.repository = repository;
        this.configurationClient = configurationClient;
        this.serviceStarter = serviceStarter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        if (inactivityTimeout.isZero() || inactivityTimeout.isNegative()) {
            throw new IllegalArgumentException("The analysis timeout must be positive");
        }
        this.inactivityTimeout = inactivityTimeout;
    }

    public AnalysisRun start(String configurationId) {
        ConfigurationSnapshot configuration = configurationClient.get(configurationId);
        String attemptId = UUID.randomUUID().toString();
        AnalysisRun run = transactions.execute(transaction -> repository.saveAndFlush(
                AnalysisRun.start("A-" + UUID.randomUUID(), configurationId, attemptId, clock.instant())));
        dispatch(AlgorithmName.FLUID, new AnalysisCommand(run.getAnalysisId(), attemptId, configuration, List.of()));
        return get(run.getAnalysisId());
    }

    public AnalysisRun get(String analysisId) {
        return repository.findById(analysisId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found"));
    }

    public void updateStatus(String analysisId, AlgorithmName algorithm, String attemptId,
                             AnalysisStatus status, String message) {
        mutate(analysisId, run -> run.updateStatus(algorithm, attemptId, status, message, clock.instant()));
    }

    public void updateResult(String analysisId, AlgorithmName algorithm, String attemptId,
            AnalysisStatus status, AnalysisResult result, String message, Map<String, AnalysisResult> equipmentResults) {
        mutate(analysisId, run -> run.updateResult(algorithm, attemptId, status, result, message,
                equipmentResults, clock.instant()));
    }

    public AnalysisRun retry(String analysisId, AlgorithmName algorithm) {
        // Fetch outside the database transaction; remote delays must not hold a row lock.
        ConfigurationSnapshot configuration = configurationClient.get(get(analysisId).getConfigurationId());
        String attemptId = UUID.randomUUID().toString();
        AnalysisRun run = mutate(analysisId, current -> current.retry(algorithm, attemptId, clock.instant()));
        List<PreviousResult> previousResults = run.getExecutions().stream()
                .filter(item -> item.getAlgorithm().ordinal() < algorithm.ordinal())
                .map(item -> new PreviousResult(item.getAlgorithm(), item.getResult())).toList();
        dispatch(algorithm, new AnalysisCommand(analysisId, attemptId, configuration, previousResults));
        return get(analysisId);
    }

    public void expireInactiveRuns() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(inactivityTimeout);
        for (String analysisId : repository.findInactiveIds(cutoff)) {
            mutate(analysisId, run -> run.expireBefore(cutoff, now));
        }
    }

    private void dispatch(AlgorithmName algorithm, AnalysisCommand command) {
        try {
            serviceStarter.start(algorithm, command);
        } catch (RuntimeException exception) {
            if (definitelyNotAccepted(exception)) {
                mutate(command.analysisId(), run -> run.dispatchFailed(algorithm, command.attemptId(),
                        algorithm + " service unavailable", clock.instant()));
            }
            // A lost response does not establish that the worker rejected the command.
            // Keep accepting callbacks; the persisted inactivity deadline handles lost jobs.
        }
    }

    private boolean definitelyNotAccepted(Throwable exception) {
        if (exception instanceof CallNotPermittedException) return true;
        if (exception instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError()) {
            return true;
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException) return true;
        }
        return false;
    }

    private AnalysisRun mutate(String analysisId, Consumer<AnalysisRun> change) {
        try {
            return transactions.execute(transaction -> {
                AnalysisRun run = repository.findForUpdate(analysisId).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found"));
                change.accept(run);
                return run;
            });
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }
}