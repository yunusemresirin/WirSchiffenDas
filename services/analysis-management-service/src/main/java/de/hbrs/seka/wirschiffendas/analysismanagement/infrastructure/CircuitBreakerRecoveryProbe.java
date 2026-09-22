package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Wiederholt persistierte technische Fehler, bis ihr Start gelingt. */
@Component
public class CircuitBreakerRecoveryProbe {
    private final AnalysisServiceStarter starter;
    private final AnalysisApplicationService analysisService;

    public CircuitBreakerRecoveryProbe(AnalysisServiceStarter starter,
            AnalysisApplicationService analysisService) {
        this.starter = starter;
        this.analysisService = analysisService;
    }

    @Scheduled(fixedDelayString = "${circuit-breaker.recovery-probe-interval-ms:2000}")
    public void probeWhenHalfOpen() {
        for (AlgorithmName algorithm : AlgorithmName.values()) {
            try {
                CircuitBreaker breaker = starter.circuitBreaker(algorithm);
                if (breaker.getState() == CircuitBreaker.State.HALF_OPEN) {
                    starter.probe(algorithm);
                }
                // Auch nach verlorenem Recovery-Hook, Neustart oder fehlgeschlagenem
                // Configuration-GET erneut versuchen. Fachliche Fehler bleiben unberührt.
                if (breaker.getState() == CircuitBreaker.State.CLOSED) {
                    analysisService.resumeRecoverableFailures(algorithm);
                }
            } catch (RuntimeException ignored) {
                // Ein Ziel darf die Recovery der anderen Ziele nicht verhindern.
            }
        }
    }
}
