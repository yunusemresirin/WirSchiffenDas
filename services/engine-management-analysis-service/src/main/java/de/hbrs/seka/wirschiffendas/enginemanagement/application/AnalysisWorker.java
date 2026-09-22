package de.hbrs.seka.wirschiffendas.enginemanagement.application;

import de.hbrs.seka.wirschiffendas.enginemanagement.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.enginemanagement.infrastructure.AnalysisManagementClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Führt die Engine-Management-Analyse asynchron aus. Sie ist der letzte Schritt der Kette.
 */
@Service
public class AnalysisWorker {

    private final AnalysisManagementClient managementClient;
    private static final String ALGORITHM = "ENGINE_MANAGEMENT";

    public AnalysisWorker(AnalysisManagementClient managementClient) { this.managementClient = managementClient; }

    /**
     * Meldet RUNNING, prüft Vorergebnisse und Konfiguration und meldet anschließend das Ergebnis.
     */
    @Async
    public void execute(AnalysisCommand command) {
        managementClient.reportStatus(
            command.analysisId(),
            ALGORITHM,
            "RUNNING",
            null
        );

        if (!pause(command.analysisId())) return;

        // Alle Vorgänger-Algorithmen müssen erfolgreich gewesen sein
        boolean previousOk = command.previousResults().stream().noneMatch(result -> "FAILED".equalsIgnoreCase(result.get("result")));

        boolean configOk = validConfiguration(command);

        boolean ok = previousOk && configOk;
        String message = ok
                ? null
                : !configOk
                    ? "Invalid engine management configuration: engineManagementSystem must be STANDARD, PREMIUM or ADVANCED"
                    : "Engine management analysis requires successful predecessor results";

        managementClient.reportResult(
                command.analysisId(),
                ALGORITHM,
                ok ? "READY" : "FAILED",
                ok ? "OK" : "FAILED",
                message);
    }

    /**
     * Simuliert die Rechenzeit; meldet bei Unterbrechung einen Fehler.
     */
    private boolean pause(String analysisId) {
        try {
            Thread.sleep(2000);
            return true;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            managementClient.reportResult(
                analysisId,
                ALGORITHM,
                "FAILED",
                "FAILED",
                "Analysis interrupted"
            );
            return false;
        }
    }

    /**
     * Nur die kontrollierten fachlich gültigen Demo-Varianten werden verarbeitet.
     * INVALID bleibt als absichtlicher Fehlerfall für den Demonstrator verfügbar.
     */
    boolean validConfiguration(AnalysisCommand command) {
        return valid(command.configuration().get("engineManagementSystem"));
    }

    private boolean valid(String value) {
        return "STANDARD".equalsIgnoreCase(value)
                || "PREMIUM".equalsIgnoreCase(value)
                || "ADVANCED".equalsIgnoreCase(value);
    }
}
